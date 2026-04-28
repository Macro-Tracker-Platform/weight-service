package com.olehprukhnytskyi.macrotrackerweightservice.service;

import com.olehprukhnytskyi.event.UserDeletedEvent;
import com.olehprukhnytskyi.exception.ConflictException;
import com.olehprukhnytskyi.exception.InternalServerException;
import com.olehprukhnytskyi.exception.NotFoundException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.exception.error.WeightErrorCode;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogPatchDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogRequestDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogResponseDto;
import com.olehprukhnytskyi.macrotrackerweightservice.mapper.WeightLogMapper;
import com.olehprukhnytskyi.macrotrackerweightservice.model.WeightLog;
import com.olehprukhnytskyi.macrotrackerweightservice.producer.UserEventProducer;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WeightLogRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeightService {
    private static final int DELETE_BATCH_SIZE = 1000;
    private final WeightLogRepository repository;
    private final WeightLogMapper mapper;
    private final UserEventProducer userEventProducer;

    @Transactional
    public WeightLogResponseDto logWeight(Long userId, WeightLogRequestDto requestDto) {
        log.debug("Processing weight log creation/upsert for userId={} date={} source={}",
                userId, requestDto.getDate(), requestDto.getSource());
        repository.upsertWeight(
                userId,
                requestDto.getWeight(),
                requestDto.getDate(),
                requestDto.getSource().name()
        );
        WeightLog savedLog = repository.findByUserIdAndRecordDate(userId, requestDto.getDate())
                .orElseThrow(() -> new InternalServerException(CommonErrorCode.INTERNAL_ERROR,
                        "Failed to fetch weight log after save"));
        return mapper.toDto(savedLog);
    }

    @Transactional(readOnly = true)
    public List<WeightLogResponseDto> getHistory(Long userId, LocalDate startDate,
                                                 LocalDate endDate, int days) {
        LocalDate tempStart;
        LocalDate tempEnd;
        if (startDate != null && endDate != null) {
            tempStart = startDate;
            tempEnd = endDate;
        } else if (startDate != null) {
            tempStart = startDate;
            tempEnd = startDate.plusDays(days);
        } else if (endDate != null) {
            tempEnd = endDate;
            tempStart = endDate.minusDays(days);
        } else {
            tempEnd = LocalDate.now();
            tempStart = tempEnd.minusDays(days);
        }
        if (tempEnd.isAfter(LocalDate.now())) {
            tempEnd = LocalDate.now();
        }
        log.debug("Fetching weight history from DB for userId={} between {} and {}",
                userId, tempStart, tempEnd);
        List<WeightLog> logs = repository.findAllByUserIdAndRecordDateBetweenOrderByRecordDateAsc(
                userId, tempStart, tempEnd);
        log.debug("Found {} weight records in DB for userId={}", logs.size(), userId);
        return logs.stream()
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
        WeightLog savedLog = repository.save(weightLog);
        return mapper.toDto(savedLog);
    }

    @Transactional
    public void deleteWeight(Long userId, LocalDate date) {
        log.debug("Processing deletion of weight log for userId={} date={}", userId, date);
        repository.deleteByUserIdAndRecordDate(userId, date);
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
            log.info("Data cleanup completed for user {}", userId);
        }
    }
}
