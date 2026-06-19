package com.olehprukhnytskyi.macrotrackerweightservice.service;

import com.olehprukhnytskyi.dto.PagedResponse;
import com.olehprukhnytskyi.dto.Pagination;
import com.olehprukhnytskyi.event.UserDeletedEvent;
import com.olehprukhnytskyi.exception.BadRequestException;
import com.olehprukhnytskyi.exception.ConflictException;
import com.olehprukhnytskyi.exception.InternalServerException;
import com.olehprukhnytskyi.exception.NotFoundException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.exception.error.WeightErrorCode;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogDeltaDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogDeltaResponseDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogPatchDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogRequestDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogResponseDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogSyncItemDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogSyncPushRequestDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogSyncResponseDto;
import com.olehprukhnytskyi.macrotrackerweightservice.mapper.WeightLogMapper;
import com.olehprukhnytskyi.macrotrackerweightservice.model.WeightLog;
import com.olehprukhnytskyi.macrotrackerweightservice.model.WeightLogChange;
import com.olehprukhnytskyi.macrotrackerweightservice.model.WeightRequestIdempotency;
import com.olehprukhnytskyi.macrotrackerweightservice.producer.CacheInvalidationProducer;
import com.olehprukhnytskyi.macrotrackerweightservice.producer.UserEventProducer;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WeightLogChangeRepository;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WeightLogRepository;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WeightRequestIdempotencyRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeightService {
    private static final String WEIGHT_DOMAIN = "WEIGHT";
    private static final int DELETE_BATCH_SIZE = 1000;
    private final WeightLogRepository repository;
    private final WeightLogChangeRepository changeRepository;
    private final WeightRequestIdempotencyRepository idempotencyRepository;
    private final WeightLogMapper mapper;
    private final CacheInvalidationProducer cacheInvalidationProducer;
    private final UserEventProducer userEventProducer;

    @Value("${app.postgresql-native-upsert:true}")
    private boolean postgresqlNativeUpsert;

    @Transactional
    public WeightLogResponseDto logWeight(Long userId, String requestId,
                                          WeightLogRequestDto requestDto) {
        return logWeight(userId, requestId, requestDto, null);
    }

    @Transactional
    public WeightLogResponseDto logWeight(Long userId, String requestId,
                                          WeightLogRequestDto requestDto, String originDeviceId) {
        log.debug("Processing weight log creation/upsert for userId={} date={} source={}",
                userId, requestDto.getDate(), requestDto.getSource());
        reserveIdempotencyKey(userId, requestId);
        WeightRequestIdempotency idempotency = idempotencyRepository
                .findByUserIdAndRequestId(userId, requestId)
                .orElseThrow(() -> new InternalServerException(CommonErrorCode.INTERNAL_ERROR,
                        "Failed to reserve idempotency key"));
        if (idempotency.getWeightLogId() != null) {
            return idempotencyResponse(idempotency);
        }

        upsertWeight(userId, requestDto);
        WeightLog savedLog = repository.findByUserIdAndRecordDate(userId, requestDto.getDate())
                .orElseThrow(() -> new InternalServerException(CommonErrorCode.INTERNAL_ERROR,
                        "Failed to fetch weight log after save"));
        WeightLogResponseDto response = mapper.toDto(savedLog);
        recordChange(savedLog, false);
        idempotency.setWeightLogId(response.getId());
        idempotency.setWeight(response.getWeight());
        idempotency.setRecordDate(response.getDate());
        idempotencyRepository.save(idempotency);
        cacheInvalidationProducer.send(userId, WEIGHT_DOMAIN, originDeviceId);
        return response;
    }

    @Transactional(readOnly = true)
    public WeightLogDeltaResponseDto getDelta(Long userId, Long cursor, int limit) {
        int boundedLimit = Math.clamp(limit, 1, 500);
        List<WeightLogChange> fetched = changeRepository
                .findAllByUserIdAndIdGreaterThanOrderById(
                        userId,
                        Math.max(0L, cursor),
                        PageRequest.of(0, boundedLimit + 1)
                );
        boolean hasMore = fetched.size() > boundedLimit;
        List<WeightLogChange> page = hasMore
                ? new ArrayList<>(fetched.subList(0, boundedLimit))
                : fetched;
        List<WeightLogDeltaDto> data = page.stream()
                .map(this::toDeltaDto)
                .toList();
        Long nextCursor = page.isEmpty() ? Math.max(0L, cursor) : page.getLast().getId();
        return WeightLogDeltaResponseDto.builder()
                .data(data)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    @Transactional(readOnly = true)
    public WeightLogSyncResponseDto pullSync(Long userId, Instant since, int limit) {
        Instant snapshotTime = Instant.now();
        int boundedLimit = Math.clamp(limit, 1, 500);
        List<WeightLog> fetched = repository
                .findAllByUserIdAndUpdatedAtAfterOrderByUpdatedAtAscIdAsc(
                        userId,
                        since,
                        PageRequest.of(0, boundedLimit + 1)
                );
        boolean hasMore = fetched.size() > boundedLimit;
        List<WeightLog> page = hasMore
                ? new ArrayList<>(fetched.subList(0, boundedLimit))
                : fetched;
        Instant nextSyncTime = hasMore && !page.isEmpty()
                ? page.getLast().getUpdatedAt()
                : snapshotTime;
        return WeightLogSyncResponseDto.builder()
                .data(page.stream().map(this::toSyncItemDto).toList())
                .nextSyncTime(nextSyncTime)
                .hasMore(hasMore)
                .build();
    }

    @Transactional
    public WeightLogSyncResponseDto pushSync(Long userId, WeightLogSyncPushRequestDto requestDto) {
        return pushSync(userId, requestDto, null);
    }

    @Transactional
    public WeightLogSyncResponseDto pushSync(Long userId, WeightLogSyncPushRequestDto requestDto,
                                             String originDeviceId) {
        List<WeightLogSyncItemDto> applied = new ArrayList<>();
        for (WeightLogSyncItemDto change : requestDto.getChanges()) {
            applySyncChange(userId, change).ifPresent(applied::add);
        }
        if (!applied.isEmpty()) {
            cacheInvalidationProducer.send(userId, WEIGHT_DOMAIN, originDeviceId);
        }
        return WeightLogSyncResponseDto.builder()
                .data(applied)
                .nextSyncTime(Instant.now())
                .hasMore(false)
                .build();
    }

    @Transactional(readOnly = true)
    public PagedResponse<WeightLogResponseDto> getHistory(Long userId, int offset, int limit) {
        if (limit > 100) {
            limit = 100;
        }
        Pageable pageable = PageRequest.of(
                offset / limit,
                limit,
                Sort.by("recordDate").descending()
        );
        Page<WeightLog> page = repository.findAllByUserIdAndDeletedFalse(userId, pageable);
        Pagination pagination = new Pagination();
        pagination.setOffset(offset);
        pagination.setLimit(limit);
        pagination.setTotal((int) page.getTotalElements());

        List<WeightLogResponseDto> data = page.getContent()
                .stream()
                .map(mapper::toDto)
                .toList();
        PagedResponse<WeightLogResponseDto> response = new PagedResponse<>();
        response.setData(data);
        response.setPagination(pagination);
        return response;
    }

    @Transactional(readOnly = true)
    public List<WeightLogResponseDto> getHistoryByDateRange(Long userId, LocalDate startDate,
                                                            LocalDate endDate) {
        return repository
                .findAllByUserIdAndDeletedFalseAndRecordDateBetweenOrderByRecordDateAsc(
                        userId, startDate, endDate)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional
    public WeightLogResponseDto updateWeight(Long id, Long userId, WeightLogPatchDto patchDto) {
        return updateWeight(id, userId, patchDto, null);
    }

    @Transactional
    public WeightLogResponseDto updateWeight(Long id, Long userId, WeightLogPatchDto patchDto,
                                             String originDeviceId) {
        log.debug("Attempting to update weight log id={} for userId={}", id, userId);
        WeightLog weightLog = repository.findByIdAndUserIdAndDeletedFalse(id, userId)
                .orElseThrow(() -> {
                    log.warn("Failed to update: Weight log not found with id={} for userId={}",
                            id, userId);
                    return new NotFoundException(WeightErrorCode.WEIGHT_LOG_NOT_FOUND,
                            "Weight log not found with id" + id);
                });
        ensureVersionMatches(patchDto.getVersion(), weightLog);
        if (patchDto.getDate() != null && !patchDto.getDate().equals(weightLog.getRecordDate())) {
            boolean dateAlreadyOccupied = repository
                    .existsByUserIdAndRecordDate(userId, patchDto.getDate());
            if (dateAlreadyOccupied) {
                log.warn("Conflict: User {} already has a weight log for date {}",
                        userId, patchDto.getDate());
                throw new ConflictException(CommonErrorCode.INVALID_DATE,
                        "Weight entry for this date already exists");
            }
        }
        mapper.updateEntityFromDto(patchDto, weightLog);
        weightLog.setUpdatedAt(Instant.now());
        WeightLog savedLog = repository.saveAndFlush(weightLog);
        recordChange(savedLog, false);
        cacheInvalidationProducer.send(userId, WEIGHT_DOMAIN, originDeviceId);
        return mapper.toDto(savedLog);
    }

    @Transactional
    public void deleteWeight(Long userId, Long id) {
        deleteWeight(userId, id, null);
    }

    @Transactional
    public void deleteWeight(Long userId, Long id, String originDeviceId) {
        log.debug("Processing deletion of weight log id={} for userId={}", id, userId);
        WeightLog weightLog = repository.findByIdAndUserIdAndDeletedFalse(id, userId)
                .orElseThrow(() -> new NotFoundException(WeightErrorCode.WEIGHT_LOG_NOT_FOUND,
                        "Weight log not found with id " + id));
        weightLog.setDeleted(true);
        weightLog.setUpdatedAt(Instant.now());
        WeightLog savedLog = repository.saveAndFlush(weightLog);
        recordChange(savedLog, true);
        cacheInvalidationProducer.send(userId, WEIGHT_DOMAIN, originDeviceId);
    }

    @Transactional
    public void deleteUserWeightsRecursively(Long userId) {
        log.info("Processing batch deletion for user: {}", userId);
        int deletedCount = repository.deleteBatchByUserId(userId, DELETE_BATCH_SIZE);
        log.info("Deleted {} weight records for user {}", deletedCount, userId);
        if (deletedCount >= DELETE_BATCH_SIZE) {
            log.info("User {} still has data. Republishing event to continue deletion.", userId);
            userEventProducer.sendUserDeletedEvent(new UserDeletedEvent(userId));
        } else {
            changeRepository.deleteAllByUserId(userId);
            idempotencyRepository.deleteAllByUserId(userId);
            log.info("Data cleanup completed for user {}", userId);
        }
    }

    private WeightLogResponseDto idempotencyResponse(WeightRequestIdempotency idempotency) {
        return repository.findById(idempotency.getWeightLogId())
                .map(mapper::toDto)
                .orElseGet(() -> WeightLogResponseDto.builder()
                        .id(idempotency.getWeightLogId())
                        .weight(idempotency.getWeight())
                        .date(idempotency.getRecordDate())
                        .build());
    }

    private void reserveIdempotencyKey(Long userId, String requestId) {
        if (postgresqlNativeUpsert) {
            idempotencyRepository.reserve(userId, requestId, Instant.now());
        } else {
            idempotencyRepository.reserveForH2(userId, requestId, Instant.now());
        }
    }

    private void upsertWeight(Long userId, WeightLogRequestDto requestDto) {
        if (postgresqlNativeUpsert) {
            repository.upsertWeight(
                    userId,
                    requestDto.getWeight(),
                    requestDto.getDate(),
                    requestDto.getSource().name()
            );
        } else {
            repository.upsertWeightForH2(
                    userId,
                    requestDto.getWeight(),
                    requestDto.getDate(),
                    requestDto.getSource().name()
            );
        }
    }

    private void recordChange(WeightLog weightLog, boolean deleted) {
        changeRepository.save(WeightLogChange.builder()
                .userId(weightLog.getUserId())
                .weightLogId(weightLog.getId())
                .weight(weightLog.getWeight())
                .recordDate(weightLog.getRecordDate())
                .updatedAt(weightLog.getUpdatedAt())
                .deleted(deleted)
                .version(weightLog.getVersion())
                .build());
    }

    private WeightLogDeltaDto toDeltaDto(WeightLogChange change) {
        return WeightLogDeltaDto.builder()
                .id(change.getWeightLogId())
                .weight(change.getWeight())
                .date(change.getRecordDate())
                .updatedAt(change.getUpdatedAt())
                .deleted(change.isDeleted())
                .version(change.getVersion())
                .build();
    }

    private Optional<WeightLogSyncItemDto> applySyncChange(Long userId,
                                                           WeightLogSyncItemDto change) {
        if (change.getUpdatedAt() == null) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                    "Weight sync changes must include updatedAt");
        }
        Optional<WeightLog> existing = findExistingSyncTarget(userId, change);
        if (existing.isPresent()) {
            WeightLog weightLog = existing.get();
            if (!change.getUpdatedAt().isAfter(weightLog.getUpdatedAt())) {
                return Optional.of(toSyncItemDto(weightLog));
            }
            if (change.isDeleted()) {
                weightLog.setDeleted(true);
            } else {
                validateActiveSyncChange(change);
                weightLog.setWeight(change.getWeight());
                weightLog.setRecordDate(change.getDate());
                weightLog.setSource(change.getSource());
                weightLog.setDeleted(false);
            }
            weightLog.setUpdatedAt(change.getUpdatedAt());
            WeightLog savedLog = repository.saveAndFlush(weightLog);
            recordChange(savedLog, savedLog.isDeleted());
            return Optional.of(toSyncItemDto(savedLog));
        }

        if (change.isDeleted()) {
            return Optional.empty();
        }
        validateActiveSyncChange(change);
        WeightLog weightLog = WeightLog.builder()
                .userId(userId)
                .weight(change.getWeight())
                .recordDate(change.getDate())
                .source(change.getSource())
                .updatedAt(change.getUpdatedAt())
                .deleted(false)
                .build();
        WeightLog savedLog = repository.saveAndFlush(weightLog);
        recordChange(savedLog, false);
        return Optional.of(toSyncItemDto(savedLog));
    }

    private Optional<WeightLog> findExistingSyncTarget(Long userId, WeightLogSyncItemDto change) {
        if (change.getId() != null) {
            Optional<WeightLog> byId = repository.findByIdAndUserId(change.getId(), userId);
            if (byId.isPresent()) {
                return byId;
            }
        }
        if (change.getDate() == null) {
            return Optional.empty();
        }
        return repository.findByUserIdAndRecordDate(userId, change.getDate());
    }

    private void validateActiveSyncChange(WeightLogSyncItemDto change) {
        if (change.getWeight() == null || change.getSource() == null
                || change.getDate() == null) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                    "Active weight sync changes must include date, weight and source");
        }
    }

    private WeightLogSyncItemDto toSyncItemDto(WeightLog weightLog) {
        return WeightLogSyncItemDto.builder()
                .id(weightLog.getId())
                .weight(weightLog.getWeight())
                .date(weightLog.getRecordDate())
                .source(weightLog.getSource())
                .updatedAt(weightLog.getUpdatedAt())
                .deleted(weightLog.isDeleted())
                .version(weightLog.getVersion())
                .build();
    }

    private void ensureVersionMatches(Long clientVersion, WeightLog weightLog) {
        if (clientVersion != null && !clientVersion.equals(weightLog.getVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Weight log version is stale; pull latest data and retry");
        }
    }
}
