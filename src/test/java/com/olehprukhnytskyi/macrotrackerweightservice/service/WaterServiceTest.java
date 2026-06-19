package com.olehprukhnytskyi.macrotrackerweightservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterLogDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterLogRequestDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterLogSyncItemDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterSyncPushRequestDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterSyncResponseDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterTemplateDto;
import com.olehprukhnytskyi.macrotrackerweightservice.mapper.WaterMapper;
import com.olehprukhnytskyi.macrotrackerweightservice.model.WaterLog;
import com.olehprukhnytskyi.macrotrackerweightservice.model.WaterTemplate;
import com.olehprukhnytskyi.macrotrackerweightservice.producer.CacheInvalidationProducer;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WaterLogRepository;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WaterTemplateRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WaterServiceTest {
    private static final Long USER_ID = 42L;
    private static final LocalDate DATE = LocalDate.of(2026, 6, 12);

    @Mock
    private WaterLogRepository waterLogRepository;

    @Mock
    private WaterTemplateRepository waterTemplateRepository;

    @Mock
    private CacheInvalidationProducer cacheInvalidationProducer;

    @Mock
    private WaterMapper mapper;

    @InjectMocks
    private WaterService waterService;

    @Test
    @DisplayName("When water request is retried, should return existing log")
    void addWater_whenRequestIsRetried_shouldReturnExistingLog() {
        // Given
        WaterLog existingLog = waterLog(7L, 350, 1781265600000L);
        WaterLogDto existingLogDto = waterLogDto(7L, 350, 1781265600000L);
        when(waterLogRepository.findAnyByUserIdAndRequestId(USER_ID, "request-1"))
                .thenReturn(Optional.of(existingLog));
        when(mapper.toDto(existingLog)).thenReturn(existingLogDto);

        // When
        WaterLogDto result = waterService.addWater(
                USER_ID,
                "request-1",
                WaterLogRequestDto.builder()
                        .amountMl(500)
                        .createdAt(1781265700000L)
                        .date(DATE)
                .build()
        );

        // Then
        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getAmountMl()).isEqualTo(350);
        verify(waterLogRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("When logs are requested, should return logs for user and date")
    void getWaterLogs_whenLogsAreRequested_shouldReturnLogsForUserAndDate() {
        // Given
        List<WaterLog> waterLogs = List.of(
                waterLog(2L, 500, 1781265700000L),
                waterLog(1L, 250, 1781265600000L)
        );
        List<WaterLogDto> waterLogDtos = List.of(
                waterLogDto(2L, 500, 1781265700000L),
                waterLogDto(1L, 250, 1781265600000L)
        );
        when(waterLogRepository.findAllByUserIdAndRecordDateOrderByCreatedAtDesc(USER_ID, DATE))
                .thenReturn(waterLogs);
        when(mapper.toWaterLogDtos(waterLogs)).thenReturn(waterLogDtos);

        // When
        List<WaterLogDto> result = waterService.getWaterLogs(USER_ID, DATE);

        // Then
        assertThat(result).extracting(WaterLogDto::getAmountMl).containsExactly(500, 250);
    }

    @Test
    @DisplayName("When defaults are created, should create three templates idempotently")
    void createDefaultTemplates_whenDefaultsAreCreated_shouldCreateThreeTemplatesIdempotently() {
        // Given
        when(waterTemplateRepository.findByUserIdAndAmountMl(any(), any(Integer.class)))
                .thenReturn(Optional.empty());

        // When
        waterService.createDefaultTemplates(USER_ID);

        // Then
        verify(waterTemplateRepository, times(3)).saveAndFlush(any());
    }

    @Test
    @DisplayName("When templates are first requested, should create defaults")
    void getWaterTemplates_whenFirstRequested_shouldCreateDefaults() {
        // Given
        List<WaterTemplate> templates = List.of();
        List<WaterTemplateDto> templateDtos = List.of();
        when(waterTemplateRepository.findByUserIdAndAmountMl(any(), any(Integer.class)))
                .thenReturn(Optional.empty());
        when(waterTemplateRepository.findAllByUserIdOrderByAmountMl(USER_ID))
                .thenReturn(templates);
        when(mapper.toWaterTemplateDtos(templates)).thenReturn(templateDtos);

        // When
        List<WaterTemplateDto> result = waterService.getWaterTemplates(USER_ID);

        // Then
        assertThat(result).isEmpty();
        verify(waterTemplateRepository, times(3)).saveAndFlush(any());
    }

    @Test
    @DisplayName("When water is deleted, should soft-delete it only for owner")
    void deleteWater_whenWaterIsDeleted_shouldSoftDeleteOnlyForOwner() {
        // Given
        WaterLog waterLog = waterLog(9L, 250, 1781265600000L);
        when(waterLogRepository.findAnyByIdAndUserId(9L, USER_ID))
                .thenReturn(Optional.of(waterLog));

        // When
        waterService.deleteWater(USER_ID, 9L);

        // Then
        assertThat(waterLog.isDeleted()).isTrue();
        assertThat(waterLog.getUpdatedAt()).isNotNull();
        verify(waterLogRepository).saveAndFlush(waterLog);
    }

    @Test
    @DisplayName("When pushed water log has older client time, should apply server-received change")
    void pushSync_whenLogChangeIsOlder_shouldApplyServerReceivedChange() {
        // Given
        Instant serverUpdatedAt = Instant.parse("2026-06-19T08:00:00Z");
        WaterLog existingLog = waterLog(7L, 350, 1781265600000L);
        existingLog.setUpdatedAt(serverUpdatedAt);
        WaterLogSyncItemDto appliedDto = WaterLogSyncItemDto.builder()
                .id(7L)
                .requestId("request-7")
                .amountMl(500)
                .createdAt(1781265700000L)
                .date(DATE)
                .updatedAt(serverUpdatedAt)
                .build();
        WaterLogSyncItemDto staleChange = WaterLogSyncItemDto.builder()
                .id(7L)
                .requestId("request-7")
                .amountMl(500)
                .createdAt(1781265700000L)
                .date(DATE)
                .updatedAt(serverUpdatedAt.minusSeconds(60))
                .build();
        when(waterLogRepository.findAnyByIdAndUserId(7L, USER_ID))
                .thenReturn(Optional.of(existingLog));
        when(waterLogRepository.saveAndFlush(existingLog)).thenReturn(existingLog);
        when(mapper.toSyncDto(existingLog)).thenReturn(appliedDto);

        // When
        WaterSyncResponseDto response = waterService.pushSync(USER_ID,
                WaterSyncPushRequestDto.builder()
                        .logChanges(List.of(staleChange))
                        .templateChanges(List.of())
                        .build());

        // Then
        assertThat(response.getLogs()).extracting(WaterLogSyncItemDto::getAmountMl)
                .containsExactly(500);
        assertThat(existingLog.getAmountMl()).isEqualTo(500);
        assertThat(existingLog.getUpdatedAt()).isAfter(serverUpdatedAt);
        verify(waterLogRepository).saveAndFlush(existingLog);
    }

    @Test
    @DisplayName("When pushed water delete is older, should still tombstone existing row")
    void pushSync_whenDeleteIsOlder_shouldTombstoneExistingRow() {
        // Given
        Instant serverUpdatedAt = Instant.parse("2026-06-19T08:00:00Z");
        WaterLog existingLog = waterLog(7L, 350, 1781265600000L);
        existingLog.setUpdatedAt(serverUpdatedAt);
        WaterLogSyncItemDto tombstoneDto = WaterLogSyncItemDto.builder()
                .id(7L)
                .requestId("request-7")
                .amountMl(350)
                .createdAt(1781265600000L)
                .date(DATE)
                .updatedAt(serverUpdatedAt)
                .deleted(true)
                .build();
        WaterLogSyncItemDto staleDelete = WaterLogSyncItemDto.builder()
                .id(7L)
                .requestId("request-7")
                .updatedAt(serverUpdatedAt.minusSeconds(60))
                .deleted(true)
                .build();
        when(waterLogRepository.findAnyByIdAndUserId(7L, USER_ID))
                .thenReturn(Optional.of(existingLog));
        when(waterLogRepository.saveAndFlush(existingLog)).thenReturn(existingLog);
        when(mapper.toSyncDto(existingLog)).thenReturn(tombstoneDto);

        // When
        WaterSyncResponseDto response = waterService.pushSync(USER_ID,
                WaterSyncPushRequestDto.builder()
                        .logChanges(List.of(staleDelete))
                        .templateChanges(List.of())
                        .build());

        // Then
        assertThat(existingLog.isDeleted()).isTrue();
        assertThat(existingLog.getUpdatedAt()).isAfter(serverUpdatedAt);
        assertThat(response.getLogs()).extracting(WaterLogSyncItemDto::isDeleted)
                .containsExactly(true);
        verify(waterLogRepository).saveAndFlush(existingLog);
    }

    @Test
    @DisplayName("When template version is stale, should throw conflict")
    void updateWaterTemplate_whenVersionIsStale_shouldThrowConflict() {
        // Given
        WaterTemplate template = WaterTemplate.builder()
                .id(1L)
                .userId(USER_ID)
                .amountMl(250)
                .active(true)
                .version(3L)
                .build();
        when(waterTemplateRepository.findByUserIdAndAmountMl(USER_ID, 250))
                .thenReturn(Optional.of(template));

        // When / Then
        assertThatThrownBy(() -> waterService.updateWaterTemplate(USER_ID, 250,
                WaterTemplateDto.builder()
                        .amountMl(300)
                        .active(true)
                        .version(2L)
                        .build()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
        verify(waterTemplateRepository, never()).save(any());
    }

    private WaterLog waterLog(Long id, int amountMl, long createdAt) {
        return WaterLog.builder()
                .id(id)
                .userId(USER_ID)
                .requestId("request-" + id)
                .amountMl(amountMl)
                .createdAt(createdAt)
                .recordDate(DATE)
                .build();
    }

    private WaterLogDto waterLogDto(Long id, int amountMl, long createdAt) {
        return WaterLogDto.builder()
                .id(id)
                .amountMl(amountMl)
                .createdAt(createdAt)
                .date(DATE)
                .build();
    }
}
