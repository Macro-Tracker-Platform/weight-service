package com.olehprukhnytskyi.macrotrackerweightservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogDeltaResponseDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogPatchDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogRequestDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogResponseDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogSyncItemDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogSyncPushRequestDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogSyncResponseDto;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WeightLogChangeRepository;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WeightLogRepository;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WeightRequestIdempotencyRepository;
import com.olehprukhnytskyi.util.WeightSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

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
        assertThat(weightLogRepository.findById(created.getId())).isPresent();
        assertThat(weightService.getHistory(USER_ID, 0, 100).getData()).isEmpty();
    }

    @Test
    @DisplayName("When pulling sync after deletion, should include soft-deleted tombstone")
    void pullSync_whenRecordWasDeleted_shouldReturnTombstone() {
        // Given
        WeightLogResponseDto created = weightService.logWeight(USER_ID, "weight-request-1",
                weightRequest("75.50", LocalDate.of(2026, 6, 12)));
        weightService.deleteWeight(USER_ID, created.getId());

        // When
        WeightLogSyncResponseDto sync = weightService.pullSync(USER_ID, Instant.EPOCH, 100);

        // Then
        assertThat(sync.getData()).hasSize(1);
        assertThat(sync.getData().getFirst().getId()).isEqualTo(created.getId());
        assertThat(sync.getData().getFirst().isDeleted()).isTrue();
        assertThat(sync.getData().getFirst().getVersion()).isPositive();
        assertThat(sync.isHasMore()).isFalse();
        assertThat(sync.getNextSyncTime()).isNotNull();
    }

    @Test
    @DisplayName("When patch uses stale version, should throw conflict")
    void updateWeight_whenVersionIsStale_shouldThrowConflict() {
        // Given
        WeightLogResponseDto created = weightService.logWeight(USER_ID, "weight-request-1",
                weightRequest("75.50", LocalDate.of(2026, 6, 12)));
        WeightLogPatchDto patch = WeightLogPatchDto.builder()
                .weight(new BigDecimal("76.00"))
                .version(created.getVersion() + 1)
                .build();

        // When / Then
        assertThatThrownBy(() -> weightService.updateWeight(created.getId(), USER_ID, patch))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    @Test
    @DisplayName("When sync push has older client time, should apply server-received change")
    void pushSync_whenClientChangeIsOlder_shouldApplyServerReceivedChange() {
        // Given
        WeightLogResponseDto created = weightService.logWeight(USER_ID, "weight-request-1",
                weightRequest("75.50", LocalDate.of(2026, 6, 12)));
        WeightLogSyncItemDto staleChange = WeightLogSyncItemDto.builder()
                .id(created.getId())
                .weight(new BigDecimal("80.00"))
                .date(created.getDate())
                .source(WeightSource.MANUAL)
                .updatedAt(created.getUpdatedAt().minusSeconds(60))
                .version(created.getVersion())
                .build();

        // When
        WeightLogSyncResponseDto response = weightService.pushSync(USER_ID,
                WeightLogSyncPushRequestDto.builder()
                        .changes(List.of(staleChange))
                        .build());

        // Then
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().getFirst().getWeight()).isEqualByComparingTo("80.00");
        assertThat(weightLogRepository.findById(created.getId()))
                .get()
                .extracting(weightLog -> weightLog.getWeight())
                .isEqualTo(new BigDecimal("80.00"));
    }

    @Test
    @DisplayName("When sync delete is older than server row, should still tombstone it")
    void pushSync_whenDeleteIsOlder_shouldTombstoneServerRow() {
        // Given
        WeightLogResponseDto created = weightService.logWeight(USER_ID, "weight-request-1",
                weightRequest("75.50", LocalDate.of(2026, 6, 12)));
        WeightLogSyncItemDto staleDelete = WeightLogSyncItemDto.builder()
                .id(created.getId())
                .date(created.getDate())
                .updatedAt(created.getUpdatedAt().minusSeconds(60))
                .deleted(true)
                .version(created.getVersion())
                .build();

        // When
        WeightLogSyncResponseDto response = weightService.pushSync(USER_ID,
                WeightLogSyncPushRequestDto.builder()
                        .changes(List.of(staleDelete))
                        .build());

        // Then
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().getFirst().isDeleted()).isTrue();
        assertThat(weightService.getHistory(USER_ID, 0, 100).getData()).isEmpty();
    }

    @Test
    @DisplayName("When sync push is newer than server row, should apply last write wins")
    void pushSync_whenClientChangeIsNewer_shouldApplyLastWriteWins() {
        // Given
        WeightLogResponseDto created = weightService.logWeight(USER_ID, "weight-request-1",
                weightRequest("75.50", LocalDate.of(2026, 6, 12)));
        WeightLogSyncItemDto newerChange = WeightLogSyncItemDto.builder()
                .id(created.getId())
                .weight(new BigDecimal("80.00"))
                .date(created.getDate())
                .source(WeightSource.MANUAL)
                .updatedAt(created.getUpdatedAt().plusSeconds(60))
                .version(created.getVersion())
                .build();

        // When
        WeightLogSyncResponseDto response = weightService.pushSync(USER_ID,
                WeightLogSyncPushRequestDto.builder()
                        .changes(List.of(newerChange))
                        .build());

        // Then
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().getFirst().getWeight()).isEqualByComparingTo("80.00");
        assertThat(weightLogRepository.findById(created.getId()))
                .get()
                .extracting(weightLog -> weightLog.getWeight())
                .isEqualTo(new BigDecimal("80.00"));
    }

    private WeightLogRequestDto weightRequest(String weight, LocalDate date) {
        return WeightLogRequestDto.builder()
                .weight(new BigDecimal(weight))
                .date(date)
                .source(WeightSource.MANUAL)
                .build();
    }
}
