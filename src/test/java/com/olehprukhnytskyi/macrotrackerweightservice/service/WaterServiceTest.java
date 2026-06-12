package com.olehprukhnytskyi.macrotrackerweightservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterLogDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterLogRequestDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterTemplateDto;
import com.olehprukhnytskyi.macrotrackerweightservice.mapper.WaterMapper;
import com.olehprukhnytskyi.macrotrackerweightservice.model.WaterLog;
import com.olehprukhnytskyi.macrotrackerweightservice.model.WaterTemplate;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WaterLogRepository;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WaterTemplateRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WaterServiceTest {
    private static final Long USER_ID = 42L;
    private static final LocalDate DATE = LocalDate.of(2026, 6, 12);

    @Mock
    private WaterLogRepository waterLogRepository;

    @Mock
    private WaterTemplateRepository waterTemplateRepository;

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
        when(waterLogRepository.findByUserIdAndRequestId(USER_ID, "request-1"))
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
    @DisplayName("When water is deleted, should delete it only for owner")
    void deleteWater_whenWaterIsDeleted_shouldDeleteOnlyForOwner() {
        // Given

        // When
        waterService.deleteWater(USER_ID, 9L);

        // Then
        verify(waterLogRepository).deleteByIdAndUserId(9L, USER_ID);
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
