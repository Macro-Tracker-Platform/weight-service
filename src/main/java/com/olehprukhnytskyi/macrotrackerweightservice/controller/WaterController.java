package com.olehprukhnytskyi.macrotrackerweightservice.controller;

import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterLogDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterLogRequestDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterSyncPushRequestDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterSyncResponseDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WaterTemplateDto;
import com.olehprukhnytskyi.macrotrackerweightservice.service.WaterService;
import com.olehprukhnytskyi.util.CustomHeaders;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/water")
public class WaterController {
    private static final String X_DEVICE_ID = "X-Device-Id";
    private final WaterService waterService;

    @PostMapping
    public ResponseEntity<WaterLogDto> addWater(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestHeader(CustomHeaders.X_REQUEST_ID) @NotBlank @Size(max = 100) String requestId,
            @RequestHeader(value = X_DEVICE_ID, required = false) String deviceId,
            @Valid @RequestBody WaterLogRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(waterService.addWater(userId, requestId, requestDto, deviceId));
    }

    @GetMapping
    public ResponseEntity<List<WaterLogDto>> getWaterLogs(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestParam LocalDate date) {
        return ResponseEntity.ok(waterService.getWaterLogs(userId, date));
    }

    @GetMapping("/range")
    public ResponseEntity<List<WaterLogDto>> getWaterLogsByDateRange(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(waterService.getWaterLogs(userId, startDate, endDate));
    }

    @GetMapping("/sync")
    public ResponseEntity<WaterSyncResponseDto> pullSync(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(defaultValue = "100") int limit) {
        Instant effectiveSince = since == null ? Instant.EPOCH : since;
        return ResponseEntity.ok(waterService.pullSync(userId, effectiveSince, limit));
    }

    @PostMapping("/sync")
    public ResponseEntity<WaterSyncResponseDto> pushSync(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestHeader(value = X_DEVICE_ID, required = false) String deviceId,
            @Valid @RequestBody WaterSyncPushRequestDto requestDto) {
        return ResponseEntity.ok(waterService.pushSync(userId, requestDto, deviceId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWater(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestHeader(value = X_DEVICE_ID, required = false) String deviceId,
            @PathVariable @Positive Long id) {
        waterService.deleteWater(userId, id, deviceId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/templates")
    public ResponseEntity<List<WaterTemplateDto>> getWaterTemplates(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId) {
        return ResponseEntity.ok(waterService.getWaterTemplates(userId));
    }

    @PutMapping("/templates/{amountMl}")
    public ResponseEntity<WaterTemplateDto> updateWaterTemplate(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @PathVariable @Positive int amountMl,
            @RequestHeader(value = X_DEVICE_ID, required = false) String deviceId,
            @Valid @RequestBody WaterTemplateDto requestDto) {
        return ResponseEntity.ok(waterService
                .updateWaterTemplate(userId, amountMl, requestDto, deviceId));
    }
}
