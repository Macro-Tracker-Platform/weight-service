package com.olehprukhnytskyi.macrotrackerweightservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogDeltaResponseDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogRequestDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogResponseDto;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WeightLogChangeRepository;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WeightLogRepository;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WeightRequestIdempotencyRepository;
import com.olehprukhnytskyi.util.WeightSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class WeightServiceIntegrationTest {
    private static final Long USER_ID = 42L;

    @Autowired
    private WeightService weightService;
    @Autowired
    private WeightLogRepository weightLogRepository;
    @Autowired
    private WeightLogChangeRepository weightLogChangeRepository;
    @Autowired
    private WeightRequestIdempotencyRepository weightRequestIdempotencyRepository;

    @AfterEach
    void cleanUp() {
        weightRequestIdempotencyRepository.deleteAll();
        weightLogChangeRepository.deleteAll();
        weightLogRepository.deleteAll();
    }

    @Test
    @DisplayName("When weight request is retried, should return original canonical response")
    void logWeight_whenRequestIsRetried_shouldReturnOriginalCanonicalResponse() {
        // Given
        WeightLogRequestDto originalRequest = weightRequest("75.50", LocalDate.of(2026, 6, 12));
        WeightLogRequestDto changedRetry = weightRequest("80.00", LocalDate.of(2026, 6, 13));

        // When
        WeightLogResponseDto first = weightService.logWeight(USER_ID, "weight-request-1",
                originalRequest);
        WeightLogResponseDto retry = weightService.logWeight(USER_ID, "weight-request-1",
                changedRetry);

        // Then
        assertThat(retry).isEqualTo(first);
        assertThat(weightLogRepository.count()).isEqualTo(1);
        assertThat(weightLogChangeRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("When same date is posted again, should upsert and retain canonical ID")
    void logWeight_whenDateAlreadyExists_shouldUpsertAndRetainCanonicalId() {
        // Given
        LocalDate date = LocalDate.of(2026, 6, 12);

        // When
        WeightLogResponseDto first = weightService.logWeight(USER_ID, "weight-request-1",
                weightRequest("75.50", date));
        WeightLogResponseDto updated = weightService.logWeight(USER_ID, "weight-request-2",
                weightRequest("76.25", date));

        // Then
        assertThat(updated.getId()).isEqualTo(first.getId());
        assertThat(updated.getWeight()).isEqualByComparingTo("76.25");
        assertThat(weightLogRepository.count()).isEqualTo(1);
        assertThat(weightLogChangeRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("When weight is deleted by ID, delta should contain a tombstone")
    void deleteWeight_whenDeletedById_shouldExposeTombstoneInDelta() {
        // Given
        WeightLogResponseDto created = weightService.logWeight(USER_ID, "weight-request-1",
                weightRequest("75.50", LocalDate.of(2026, 6, 12)));

        // When
        weightService.deleteWeight(USER_ID, created.getId());
        WeightLogDeltaResponseDto delta = weightService.getDelta(USER_ID, 0L, 100);

        // Then
        assertThat(delta.getData()).hasSize(2);
        assertThat(delta.getData().getLast().getId()).isEqualTo(created.getId());
        assertThat(delta.getData().getLast().isDeleted()).isTrue();
        assertThat(delta.getNextCursor()).isPositive();
        assertThat(delta.isHasMore()).isFalse();
    }

    private WeightLogRequestDto weightRequest(String weight, LocalDate date) {
        return WeightLogRequestDto.builder()
                .weight(new BigDecimal(weight))
                .date(date)
                .source(WeightSource.MANUAL)
                .build();
    }
}
