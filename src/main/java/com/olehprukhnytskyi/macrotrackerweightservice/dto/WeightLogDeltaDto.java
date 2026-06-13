package com.olehprukhnytskyi.macrotrackerweightservice.dto;

import java.math.BigDecimal;
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
public class WeightLogDeltaDto {
    private Long id;
    private BigDecimal weight;
    private LocalDate date;
    private Instant updatedAt;
    private boolean deleted;
}
