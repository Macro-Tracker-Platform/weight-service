package com.olehprukhnytskyi.macrotrackerweightservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterLogDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterLogRequestDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterLogSyncItemDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterSyncPushRequestDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterSyncResponseDto;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WaterLogRepository;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WaterTemplateRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class WaterServiceIntegrationTest {
    private static final Long USER_ID = 42L;

    @Autowired
    private WaterService waterService;
    @Autowired
    private WaterLogRepository waterLogRepository;
    @Autowired
    private WaterTemplateRepository waterTemplateRepository;

    @AfterEach
    void cleanUp() {
        waterLogRepository.deleteAll();
        waterTemplateRepository.deleteAll();
    }

    @Test
    @DisplayName("When water request is retried, should persist log idempotently")
    void addWater_whenRequestIsRetried_shouldPersistLogIdempotently() {
        // Given
        WaterLogRequestDto requestDto = WaterLogRequestDto.builder()
                .amountMl(350)
                .createdAt(1781265600000L)
                .date(LocalDate.of(2026, 6, 12))
                .build();

        // When
        WaterLogDto first = waterService.addWater(USER_ID, "request-1", requestDto);
        WaterLogDto retry = waterService.addWater(USER_ID, "request-1", requestDto);

        // Then
        assertThat(retry.getId()).isEqualTo(first.getId());
        assertThat(waterLogRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("When templates are requested repeatedly, should create defaults idempotently")
    void getWaterTemplates_whenRequestedRepeatedly_shouldCreateDefaultsIdempotently() {
        // Given

        // When
        waterService.getWaterTemplates(USER_ID);
        waterService.getWaterTemplates(USER_ID);

        // Then
        assertThat(waterService.getWaterTemplates(USER_ID))
                .extracting(template -> template.getAmountMl())
                .containsExactly(250, 350, 500);
    }

    @Test
    @DisplayName("When water is deleted, sync should expose tombstone")
    void deleteWater_whenDeleted_shouldExposeTombstoneInSync() {
        // Given
        LocalDate date = LocalDate.of(2026, 6, 12);
        WaterLogDto created = waterService.addWater(USER_ID, "request-1",
                WaterLogRequestDto.builder()
                        .amountMl(350)
                        .createdAt(1781265600000L)
                        .date(date)
                        .build());

        // When
        waterService.deleteWater(USER_ID, created.getId());
        WaterSyncResponseDto sync = waterService.pullSync(USER_ID, Instant.EPOCH, 100);

        // Then
        assertThat(waterService.getWaterLogs(USER_ID, date)).isEmpty();
        assertThat(sync.getLogs()).hasSize(1);
        assertThat(sync.getLogs().getFirst().getId()).isEqualTo(created.getId());
        assertThat(sync.getLogs().getFirst().isDeleted()).isTrue();
    }

    @Test
    @DisplayName("When sync delete arrives before create acknowledgement, should persist tombstone")
    void pushSync_whenDeleteArrivesBeforeCreate_shouldPersistTombstone() {
        // Given
        LocalDate date = LocalDate.of(2026, 6, 12);
        WaterLogSyncItemDto tombstone = WaterLogSyncItemDto.builder()
                .requestId("request-fast-delete")
                .amountMl(350)
                .createdAt(1781265600000L)
                .date(date)
                .updatedAt(Instant.parse("2026-06-19T08:00:00Z"))
                .deleted(true)
                .build();

        // When
        WaterSyncResponseDto response = waterService.pushSync(USER_ID,
                WaterSyncPushRequestDto.builder()
                        .logChanges(List.of(tombstone))
                        .templateChanges(List.of())
                        .build());

        // Then
        assertThat(response.getLogs()).hasSize(1);
        assertThat(response.getLogs().getFirst().isDeleted()).isTrue();
        assertThat(waterService.getWaterLogs(USER_ID, date)).isEmpty();
        assertThat(waterLogRepository
                .findAnyByUserIdAndRequestId(USER_ID, "request-fast-delete")
                .orElseThrow()
                .isDeleted()).isTrue();
    }

    @Test
    @DisplayName("When stale active sync arrives after tombstone, should keep row deleted")
    void pushSync_whenActiveArrivesAfterTombstone_shouldKeepTombstone() {
        // Given
        LocalDate date = LocalDate.of(2026, 6, 12);
        WaterLogSyncItemDto tombstone = WaterLogSyncItemDto.builder()
                .requestId("request-stale-active")
                .amountMl(350)
                .createdAt(1781265600000L)
                .date(date)
                .updatedAt(Instant.parse("2026-06-19T08:00:00Z"))
                .deleted(true)
                .build();
        waterService.pushSync(USER_ID,
                WaterSyncPushRequestDto.builder()
                        .logChanges(List.of(tombstone))
                        .templateChanges(List.of())
                        .build());

        WaterLogSyncItemDto staleActive = WaterLogSyncItemDto.builder()
                .requestId("request-stale-active")
                .amountMl(350)
                .createdAt(1781265600000L)
                .date(date)
                .updatedAt(Instant.parse("2026-06-19T08:00:01Z"))
                .deleted(false)
                .build();

        // When
        WaterSyncResponseDto response = waterService.pushSync(USER_ID,
                WaterSyncPushRequestDto.builder()
                        .logChanges(List.of(staleActive))
                        .templateChanges(List.of())
                        .build());

        // Then
        assertThat(response.getLogs()).hasSize(1);
        assertThat(response.getLogs().getFirst().isDeleted()).isTrue();
        assertThat(waterService.getWaterLogs(USER_ID, date)).isEmpty();
        assertThat(waterLogRepository
                .findAnyByUserIdAndRequestId(USER_ID, "request-stale-active")
                .orElseThrow()
                .isDeleted()).isTrue();
    }
}
