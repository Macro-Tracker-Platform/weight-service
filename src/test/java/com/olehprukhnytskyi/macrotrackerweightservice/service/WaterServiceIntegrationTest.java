package com.olehprukhnytskyi.macrotrackerweightservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterLogDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterLogRequestDto;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WaterLogRepository;
import com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa.WaterTemplateRepository;
import java.time.LocalDate;
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
}
