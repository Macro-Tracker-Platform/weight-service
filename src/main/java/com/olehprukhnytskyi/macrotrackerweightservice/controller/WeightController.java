package com.olehprukhnytskyi.macrotrackerweightservice.controller;

import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogPatchDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogRequestDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogResponseDto;
import com.olehprukhnytskyi.macrotrackerweightservice.service.WeightService;
import com.olehprukhnytskyi.util.CustomHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/weights")
@Tag(
        name = "Weight Management API",
        description = "Track and manage user weight records and history"
)
public class WeightController {
    private final WeightService weightService;

    @Operation(
            summary = "Log new weight",
            description = "Record a new weight entry for the user on a specific date"
    )
    @PostMapping
    public ResponseEntity<Void> logWeight(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @Valid @RequestBody WeightLogRequestDto requestDto) {
        log.debug("Logging new weight entry for userId={} date={}", userId, requestDto.getDate());
        weightService.logWeight(userId, requestDto);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Get weight history",
            description = "Retrieve the user's weight history for "
                          + "a specified date range or a specific number of days"
    )
    @GetMapping
    public ResponseEntity<List<WeightLogResponseDto>> getWeightHistory(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,
            @RequestParam(defaultValue = "30") int days) {
        log.debug("Fetching weight history for userId={} startDate={} endDate={} days={}",
                userId, startDate, endDate, days);
        List<WeightLogResponseDto> history = weightService
                .getHistory(userId, startDate, endDate, days);
        log.debug("Fetched {} weight records for userId={}", history.size(), userId);
        return ResponseEntity.ok(history);
    }

    @Operation(
            summary = "Partially update weight entry",
            description = "Modify an existing weight record by its ID"
    )
    @PatchMapping("/{id}")
    public ResponseEntity<Void> patchWeight(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @PathVariable Long id,
            @Valid @RequestBody WeightLogPatchDto patchDto) {
        log.debug("Updating weight entry id={} for userId={}", id, userId);
        weightService.updateWeight(id, userId, patchDto);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Delete weight entry",
            description = "Remove a user's weight record for a specific date"
    )
    @DeleteMapping("/{date}")
    public ResponseEntity<Void> deleteWeight(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @PathVariable LocalDate date) {
        log.debug("Deleting weight entry for userId={} date={}", userId, date);
        weightService.deleteWeight(userId, date);
        return ResponseEntity.noContent().build();
    }
}
