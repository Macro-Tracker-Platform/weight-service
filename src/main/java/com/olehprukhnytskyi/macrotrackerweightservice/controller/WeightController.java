package com.olehprukhnytskyi.macrotrackerweightservice.controller;

import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogDeltaResponseDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogPatchDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogRequestDto;
import com.olehprukhnytskyi.macrotrackerweightservice.dto.WeightLogResponseDto;
import com.olehprukhnytskyi.macrotrackerweightservice.service.WeightService;
import com.olehprukhnytskyi.util.CustomHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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
@Validated
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
    public ResponseEntity<WeightLogResponseDto> logWeight(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestHeader(CustomHeaders.X_REQUEST_ID)
            @NotBlank @Size(max = 100) String requestId,
            @Valid @RequestBody WeightLogRequestDto requestDto) {
        log.debug("Logging new weight entry for userId={} date={}", userId, requestDto.getDate());
        WeightLogResponseDto savedRecord = weightService.logWeight(userId, requestId, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedRecord);
    }

    @Operation(
            summary = "Get weight changes",
            description = "Retrieve an ordered delta stream, including deletion tombstones"
    )
    @GetMapping("/delta")
    public ResponseEntity<WeightLogDeltaResponseDto> getDelta(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestParam(defaultValue = "0") Long cursor,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(weightService.getDelta(userId, cursor, limit));
    }

    @Operation(
            summary = "Get weight records by date range",
            description = "Internal endpoint used by BFF export to fetch weight rows "
                    + "for a bounded inclusive period"
    )
    @GetMapping("/range")
    public ResponseEntity<List<WeightLogResponseDto>> getHistoryByDateRange(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(weightService.getHistoryByDateRange(userId, startDate, endDate));
    }

    @Operation(
            summary = "Partially update weight entry",
            description = "Modify an existing weight record by its ID"
    )
    @PatchMapping("/{id}")
    public ResponseEntity<WeightLogResponseDto> patchWeight(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @PathVariable @Positive Long id,
            @Valid @RequestBody WeightLogPatchDto patchDto) {
        log.debug("Updating weight entry id={} for userId={}", id, userId);
        WeightLogResponseDto updatedRecord = weightService.updateWeight(id, userId, patchDto);
        return ResponseEntity.ok(updatedRecord);
    }

    @Operation(
            summary = "Delete weight entry",
            description = "Remove a user's weight record by its canonical ID"
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWeight(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @PathVariable @Positive Long id) {
        log.debug("Deleting weight entry id={} for userId={}", id, userId);
        weightService.deleteWeight(userId, id);
        return ResponseEntity.noContent().build();
    }
}
