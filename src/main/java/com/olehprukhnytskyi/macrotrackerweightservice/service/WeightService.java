package com.olehprukhnytskyi.macrotrackerweightservice.service;

import com.olehprukhnytskyi.dto.PagedResponse;
import com.olehprukhnytskyi.dto.Pagination;
import com.olehprukhnytskyi.event.UserDeletedEvent;
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
import com.olehprukhnytskyi.macrotrackerweightservice.mapper.WeightLogMapper;
import com.olehprukhnytskyi.macrotrackerweightservice.model.WeightLog;
import com.olehprukhnytskyi.macrotrackerweightservice.model.WeightLogChange;
import com.olehprukhnytskyi.macrotrackerweightservice.model.WeightRequestIdempotency;
import com.olehprukhnytskyi.macrotrackerweightservice.producer.UserEventProducer;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WeightLogChangeRepository;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WeightLogRepository;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WeightRequestIdempotencyRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeightService {
    private static final int DELETE_BATCH_SIZE = 1000;
    private final WeightLogRepository repository;
    private final WeightLogChangeRepository changeRepository;
    private final WeightRequestIdempotencyRepository idempotencyRepository;
    private final WeightLogMapper mapper;
    private final UserEventProducer userEventProducer;

    @Value("${app.postgresql-native-upsert:true}")
    private boolean postgresqlNativeUpsert;

    @Transactional
    public WeightLogResponseDto logWeight(
            Long userId,
            String requestId,
            WeightLogRequestDto requestDto
    ) {
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
    public PagedResponse<WeightLogResponseDto> getHistory(
            Long userId,
            int offset,
            int limit
    ) {
        if (limit > 100) {
            limit = 100;
        }
        Pageable pageable = PageRequest.of(
                offset / limit,
                limit,
                Sort.by("recordDate").descending()
        );
        Page<WeightLog> page = repository.findAllByUserId(userId, pageable);
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
                .findAllByUserIdAndRecordDateBetweenOrderByRecordDateAsc(
                        userId, startDate, endDate)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional
    public WeightLogResponseDto updateWeight(Long id, Long userId, WeightLogPatchDto patchDto) {
        log.debug("Attempting to update weight log id={} for userId={}", id, userId);
        WeightLog weightLog = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> {
                    log.warn("Failed to update: Weight log not found with id={} for userId={}",
                            id, userId);
                    return new NotFoundException(WeightErrorCode.WEIGHT_LOG_NOT_FOUND,
                            "Weight log not found with id" + id);
                });
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
        WeightLog savedLog = repository.saveAndFlush(weightLog);
        recordChange(savedLog, false);
        return mapper.toDto(savedLog);
    }

    @Transactional
    public void deleteWeight(Long userId, Long id) {
        log.debug("Processing deletion of weight log id={} for userId={}", id, userId);
        WeightLog weightLog = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException(WeightErrorCode.WEIGHT_LOG_NOT_FOUND,
                        "Weight log not found with id " + id));
        recordChange(weightLog, true);
        repository.delete(weightLog);
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
        return WeightLogResponseDto.builder()
                .id(idempotency.getWeightLogId())
                .weight(idempotency.getWeight())
                .date(idempotency.getRecordDate())
                .build();
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
                .updatedAt(deleted ? Instant.now() : weightLog.getUpdatedAt())
                .deleted(deleted)
                .build());
    }

    private WeightLogDeltaDto toDeltaDto(WeightLogChange change) {
        return WeightLogDeltaDto.builder()
                .id(change.getWeightLogId())
                .weight(change.getWeight())
                .date(change.getRecordDate())
                .updatedAt(change.getUpdatedAt())
                .deleted(change.isDeleted())
                .build();
    }
}
