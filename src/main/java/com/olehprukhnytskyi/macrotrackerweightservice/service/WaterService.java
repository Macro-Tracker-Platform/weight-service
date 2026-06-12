package com.olehprukhnytskyi.macrotrackerweightservice.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WaterService {
    private static final List<Integer> DEFAULT_TEMPLATE_AMOUNTS = List.of(250, 350, 500);
    private final WaterLogRepository waterLogRepository;
    private final WaterTemplateRepository waterTemplateRepository;
    private final WaterMapper mapper;

    public WaterLogDto addWater(Long userId, String requestId, WaterLogRequestDto requestDto) {
        return waterLogRepository.findByUserIdAndRequestId(userId, requestId)
                .map(mapper::toDto)
                .orElseGet(() -> saveWaterLog(userId, requestId, requestDto));
    }

    @Transactional(readOnly = true)
    public List<WaterLogDto> getWaterLogs(Long userId, LocalDate date) {
        List<WaterLog> waterLogs =
                waterLogRepository.findAllByUserIdAndRecordDateOrderByCreatedAtDesc(userId, date);
        return mapper.toWaterLogDtos(waterLogs);
    }

    @Transactional
    public void deleteWater(Long userId, Long id) {
        waterLogRepository.deleteByIdAndUserId(id, userId);
    }

    public List<WaterTemplateDto> getWaterTemplates(Long userId) {
        createDefaultTemplates(userId);
        List<WaterTemplate> waterTemplates =
                waterTemplateRepository.findAllByUserIdOrderByAmountMl(userId);
        return mapper.toWaterTemplateDtos(waterTemplates);
    }

    @Transactional
    public WaterTemplateDto updateWaterTemplate(Long userId, int currentAmountMl,
                                                WaterTemplateDto requestDto) {
        WaterTemplate template = waterTemplateRepository
                .findByUserIdAndAmountMl(userId, currentAmountMl)
                .orElseGet(() -> WaterTemplate.builder()
                        .userId(userId)
                        .build());
        template.setAmountMl(requestDto.getAmountMl());
        template.setActive(requestDto.isActive());
        return mapper.toDto(waterTemplateRepository.save(template));
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
                                     WaterLogRequestDto requestDto) {
        try {
            WaterLog savedLog = waterLogRepository.saveAndFlush(WaterLog.builder()
                    .userId(userId)
                    .requestId(requestId)
                    .amountMl(requestDto.getAmountMl())
                    .createdAt(requestDto.getCreatedAt())
                    .recordDate(requestDto.getDate())
                    .build());
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
                    .build());
        } catch (DataIntegrityViolationException ignored) {
            // Another delivery or instance created the same default concurrently.
        }
    }
}
