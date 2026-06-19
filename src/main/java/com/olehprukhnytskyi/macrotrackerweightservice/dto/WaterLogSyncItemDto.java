package com.olehprukhnytskyi.macrotrackerweightservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaterLogSyncItemDto {
    private Long id;
    private String requestId;

    @Min(1)
    @Max(10000)
    private Integer amountMl;

    @Positive
    private Long createdAt;

    private LocalDate date;
    private Instant updatedAt;
    private boolean deleted;
    private Long version;
}
