package com.olehprukhnytskyi.macrotrackerweightservice.service;

import com.olehprukhnytskyi.exception.BadRequestException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterLogDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterLogRequestDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterLogSyncItemDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterSyncPushRequestDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterSyncResponseDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterTemplateDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterTemplateSyncItemDto;
import com.olehprukhnytskyi.macrotrackerweightservice.mapper.WaterMapper;
import com.olehprukhnytskyi.macrotrackerweightservice.model.WaterLog;
import com.olehprukhnytskyi.macrotrackerweightservice.model.WaterTemplate;
import com.olehprukhnytskyi.macrotrackerweightservice.producer.CacheInvalidationProducer;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WaterLogRepository;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WaterTemplateRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class WaterService {
    private static final String WATER_DOMAIN = "WATER";
    private static final List<Integer> DEFAULT_TEMPLATE_AMOUNTS = List.of(250, 350, 500);
    private final WaterLogRepository waterLogRepository;
    private final WaterTemplateRepository waterTemplateRepository;
    private final CacheInvalidationProducer cacheInvalidationProducer;
    private final WaterMapper mapper;

    public WaterLogDto addWater(Long userId, String requestId, WaterLogRequestDto requestDto) {
        return addWater(userId, requestId, requestDto, null);
    }

    public WaterLogDto addWater(Long userId, String requestId, WaterLogRequestDto requestDto,
                                String originDeviceId) {
        return waterLogRepository.findAnyByUserIdAndRequestId(userId, requestId)
                .map(mapper::toDto)
                .orElseGet(() -> saveWaterLog(userId, requestId, requestDto, originDeviceId));
    }

    @Transactional(readOnly = true)
    public List<WaterLogDto> getWaterLogs(Long userId, LocalDate date) {
        List<WaterLog> waterLogs =
                waterLogRepository.findAllByUserIdAndRecordDateOrderByCreatedAtDesc(userId, date);
        return mapper.toWaterLogDtos(waterLogs);
    }

    @Transactional(readOnly = true)
    public List<WaterLogDto> getWaterLogs(Long userId, LocalDate startDate, LocalDate endDate) {
        List<WaterLog> waterLogs = waterLogRepository
                .findAllByUserIdAndRecordDateBetweenOrderByRecordDateAscCreatedAtAsc(
                        userId, startDate, endDate);
        return mapper.toWaterLogDtos(waterLogs);
    }

    @Transactional
    public void deleteWater(Long userId, Long id) {
        deleteWater(userId, id, null);
    }

    @Transactional
    public void deleteWater(Long userId, Long id, String originDeviceId) {
        waterLogRepository.findAnyByIdAndUserId(id, userId)
                .filter(waterLog -> !waterLog.isDeleted())
                .ifPresent(waterLog -> {
                    waterLog.setDeleted(true);
                    waterLog.setUpdatedAt(Instant.now());
                    waterLogRepository.saveAndFlush(waterLog);
                    cacheInvalidationProducer.send(userId, WATER_DOMAIN, originDeviceId);
                });
    }

    @Transactional(readOnly = true)
    public WaterSyncResponseDto pullSync(Long userId, Instant since, int limit) {
        Instant snapshotTime = Instant.now();
        int boundedLimit = Math.clamp(limit, 1, 500);
        List<WaterLog> fetchedLogs = waterLogRepository.findAllChangedAfter(
                userId,
                since,
                PageRequest.of(0, boundedLimit + 1)
        );
        List<WaterTemplate> fetchedTemplates = waterTemplateRepository.findAllChangedAfter(
                userId,
                since,
                PageRequest.of(0, boundedLimit + 1)
        );
        boolean logsHaveMore = fetchedLogs.size() > boundedLimit;
        boolean templatesHaveMore = fetchedTemplates.size() > boundedLimit;
        boolean hasMore = logsHaveMore || templatesHaveMore;
        List<WaterLog> logs = page(fetchedLogs, boundedLimit);
        List<WaterTemplate> templates = page(fetchedTemplates, boundedLimit);
        return WaterSyncResponseDto.builder()
                .logs(logs.stream().map(mapper::toSyncDto).toList())
                .templates(templates.stream().map(mapper::toSyncDto).toList())
                .nextSyncTime(hasMore
                        ? nextPartialSyncTime(
                                logsHaveMore ? logs : List.of(),
                                templatesHaveMore ? templates : List.of())
                        : snapshotTime)
                .hasMore(hasMore)
                .build();
    }

    @Transactional
    public WaterSyncResponseDto pushSync(Long userId, WaterSyncPushRequestDto requestDto) {
        return pushSync(userId, requestDto, null);
    }

    @Transactional
    public WaterSyncResponseDto pushSync(Long userId, WaterSyncPushRequestDto requestDto,
                                         String originDeviceId) {
        List<WaterLogSyncItemDto> logs = new ArrayList<>();
        for (WaterLogSyncItemDto change : nullSafe(requestDto.getLogChanges())) {
            applyLogSyncChange(userId, change).ifPresent(logs::add);
        }
        List<WaterTemplateSyncItemDto> templates = new ArrayList<>();
        for (WaterTemplateSyncItemDto change : nullSafe(requestDto.getTemplateChanges())) {
            applyTemplateSyncChange(userId, change).ifPresent(templates::add);
        }
        if (!logs.isEmpty() || !templates.isEmpty()) {
            cacheInvalidationProducer.send(userId, WATER_DOMAIN, originDeviceId);
        }
        return WaterSyncResponseDto.builder()
                .logs(logs)
                .templates(templates)
                .nextSyncTime(Instant.now())
                .hasMore(false)
                .build();
    }

    public List<WaterTemplateDto> getWaterTemplates(Long userId) {
        createDefaultTemplates(userId);
        List<WaterTemplate> waterTemplates =
                waterTemplateRepository.findAllByUserIdOrderByAmountMl(userId);
        return mapper.toWaterTemplateDtos(waterTemplates);
    }

    @Transactional
    public void updateWaterTemplate(Long userId, int currentAmountMl, WaterTemplateDto requestDto) {
        updateWaterTemplate(userId, currentAmountMl, requestDto, null);
    }

    @Transactional
    public WaterTemplateDto updateWaterTemplate(Long userId, int currentAmountMl,
                                                WaterTemplateDto requestDto,
                                                String originDeviceId) {
        WaterTemplate template = waterTemplateRepository
                .findByUserIdAndAmountMl(userId, currentAmountMl)
                .orElseGet(() -> WaterTemplate.builder()
                        .userId(userId)
                        .build());
        ensureVersionMatches(requestDto.getVersion(), template);
        template.setAmountMl(requestDto.getAmountMl());
        template.setActive(requestDto.isActive());
        template.setUpdatedAt(Instant.now());
        template.setDeleted(false);
        WaterTemplate saved = waterTemplateRepository.save(template);
        cacheInvalidationProducer.send(userId, WATER_DOMAIN, originDeviceId);
        return mapper.toDto(saved);
    }

    public void createDefaultTemplates(Long userId) {
        DEFAULT_TEMPLATE_AMOUNTS.forEach(amountMl -> createDefaultTemplate(userId, amountMl));
    }

    @Transactional
    public void deleteUserWaterData(Long userId) {
        waterLogRepository.deleteAllByUserId(userId);
        waterTemplateRepository.deleteAllByUserId(userId);
    }

    private WaterLogDto saveWaterLog(Long userId, String requestId,
                                     WaterLogRequestDto requestDto, String originDeviceId) {
        try {
            WaterLog savedLog = waterLogRepository.saveAndFlush(WaterLog.builder()
                    .userId(userId)
                    .requestId(requestId)
                    .amountMl(requestDto.getAmountMl())
                    .createdAt(requestDto.getCreatedAt())
                    .recordDate(requestDto.getDate())
                    .updatedAt(Instant.now())
                    .build());
            cacheInvalidationProducer.send(userId, WATER_DOMAIN, originDeviceId);
            return mapper.toDto(savedLog);
        } catch (DataIntegrityViolationException exception) {
            return waterLogRepository.findByUserIdAndRequestId(userId, requestId)
                    .map(mapper::toDto)
                    .orElseThrow(() -> exception);
        }
    }

    private void createDefaultTemplate(Long userId, int amountMl) {
        if (waterTemplateRepository.findByUserIdAndAmountMl(userId, amountMl).isPresent()) {
            return;
        }
        try {
            waterTemplateRepository.saveAndFlush(WaterTemplate.builder()
                    .userId(userId)
                    .amountMl(amountMl)
                    .active(true)
                    .updatedAt(Instant.now())
                    .build());
        } catch (DataIntegrityViolationException ignored) {
            // Another delivery or instance created the same default concurrently.
        }
    }

    private Optional<WaterLogSyncItemDto> applyLogSyncChange(Long userId,
                                                             WaterLogSyncItemDto change) {
        if (change.getUpdatedAt() == null) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                    "Water log sync changes must include updatedAt");
        }
        Optional<WaterLog> existing = findExistingLog(userId, change);
        if (existing.isPresent()) {
            WaterLog waterLog = existing.get();
            if (waterLog.isDeleted() && !change.isDeleted()) {
                return Optional.of(mapper.toSyncDto(waterLog));
            }
            if (change.isDeleted()) {
                waterLog.setDeleted(true);
                waterLog.setUpdatedAt(Instant.now());
                return Optional.of(mapper.toSyncDto(waterLogRepository.saveAndFlush(waterLog)));
            }
            validateActiveLogChange(change);
            waterLog.setRequestId(change.getRequestId());
            waterLog.setAmountMl(change.getAmountMl());
            waterLog.setCreatedAt(change.getCreatedAt());
            waterLog.setRecordDate(change.getDate());
            waterLog.setDeleted(false);
            waterLog.setUpdatedAt(Instant.now());
            return Optional.of(mapper.toSyncDto(waterLogRepository.saveAndFlush(waterLog)));
        }

        if (change.isDeleted()) {
            if (!canCreateLogTombstone(change)) {
                return Optional.empty();
            }
            validateActiveLogChange(change);
            WaterLog waterLog = WaterLog.builder()
                    .userId(userId)
                    .requestId(change.getRequestId())
                    .amountMl(change.getAmountMl())
                    .createdAt(change.getCreatedAt())
                    .recordDate(change.getDate())
                    .updatedAt(Instant.now())
                    .deleted(true)
                    .build();
            return Optional.of(mapper.toSyncDto(waterLogRepository.saveAndFlush(waterLog)));
        }
        validateActiveLogChange(change);
        WaterLog waterLog = WaterLog.builder()
                .userId(userId)
                .requestId(change.getRequestId())
                .amountMl(change.getAmountMl())
                .createdAt(change.getCreatedAt())
                .recordDate(change.getDate())
                .updatedAt(Instant.now())
                .deleted(false)
                .build();
        return Optional.of(mapper.toSyncDto(waterLogRepository.saveAndFlush(waterLog)));
    }

    private boolean canCreateLogTombstone(WaterLogSyncItemDto change) {
        return change.getRequestId() != null
                && change.getAmountMl() != null
                && change.getCreatedAt() != null
                && change.getDate() != null;
    }

    private Optional<WaterTemplateSyncItemDto> applyTemplateSyncChange(
            Long userId, WaterTemplateSyncItemDto change) {
        if (change.getUpdatedAt() == null) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                    "Water template sync changes must include updatedAt");
        }
        Optional<WaterTemplate> existing = findExistingTemplate(userId, change);
        if (existing.isPresent()) {
            WaterTemplate template = existing.get();
            if (change.isDeleted()) {
                template.setDeleted(true);
                template.setUpdatedAt(Instant.now());
                return Optional.of(mapper.toSyncDto(waterTemplateRepository
                        .saveAndFlush(template)));
            }
            applyTemplateSyncState(template, change);
            template.setUpdatedAt(Instant.now());
            return Optional.of(mapper.toSyncDto(waterTemplateRepository.saveAndFlush(template)));
        }

        if (change.isDeleted()) {
            return Optional.empty();
        }
        validateActiveTemplateChange(change);
        WaterTemplate template = WaterTemplate.builder()
                .userId(userId)
                .amountMl(change.getAmountMl())
                .active(Boolean.TRUE.equals(change.getActive()))
                .updatedAt(Instant.now())
                .deleted(false)
                .build();
        return Optional.of(mapper.toSyncDto(waterTemplateRepository.saveAndFlush(template)));
    }

    private Optional<WaterLog> findExistingLog(Long userId, WaterLogSyncItemDto change) {
        if (change.getId() != null) {
            Optional<WaterLog> byId = waterLogRepository
                    .findAnyByIdAndUserId(change.getId(), userId);
            if (byId.isPresent()) {
                return byId;
            }
        }
        if (change.getRequestId() == null) {
            return Optional.empty();
        }
        return waterLogRepository.findAnyByUserIdAndRequestId(userId, change.getRequestId());
    }

    private Optional<WaterTemplate> findExistingTemplate(
            Long userId,
            WaterTemplateSyncItemDto change
    ) {
        if (change.getId() != null) {
            Optional<WaterTemplate> byId = waterTemplateRepository
                    .findAnyByIdAndUserId(change.getId(), userId);
            if (byId.isPresent()) {
                return byId;
            }
        }
        if (change.getAmountMl() == null) {
            return Optional.empty();
        }
        return waterTemplateRepository.findAnyByUserIdAndAmountMl(userId, change.getAmountMl());
    }

    private void applyTemplateSyncState(
            WaterTemplate template,
            WaterTemplateSyncItemDto change
    ) {
        if (change.isDeleted()) {
            template.setDeleted(true);
            return;
        }
        validateActiveTemplateChange(change);
        template.setAmountMl(change.getAmountMl());
        template.setActive(Boolean.TRUE.equals(change.getActive()));
        template.setDeleted(false);
    }

    private void validateActiveLogChange(WaterLogSyncItemDto change) {
        if (change.getRequestId() == null || change.getAmountMl() == null
                || change.getCreatedAt() == null || change.getDate() == null) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                    "Active water log sync changes must include requestId, amountMl, "
                            + "createdAt and date");
        }
    }

    private void validateActiveTemplateChange(WaterTemplateSyncItemDto change) {
        if (change.getAmountMl() == null || change.getActive() == null) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                    "Active water template sync changes must include amountMl and active");
        }
    }

    private void ensureVersionMatches(Long clientVersion, WaterTemplate template) {
        if (clientVersion != null && !clientVersion.equals(template.getVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Water template version is stale; pull latest data and retry");
        }
    }

    private Instant nextPartialSyncTime(List<WaterLog> logs, List<WaterTemplate> templates) {
        return Stream.of(
                        logs.stream().map(WaterLog::getUpdatedAt).reduce((first, second) -> second),
                        templates.stream()
                                .map(WaterTemplate::getUpdatedAt)
                                .reduce((first, second) -> second)
                )
                .flatMap(Optional::stream)
                .min(Comparator.naturalOrder())
                .orElse(Instant.EPOCH)
                .minusNanos(1);
    }

    private <T> List<T> page(List<T> fetched, int limit) {
        return fetched.size() > limit ? new ArrayList<>(fetched.subList(0, limit)) : fetched;
    }

    private <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
