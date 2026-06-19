package com.olehprukhnytskyi.macrotrackerweightservice.dto;

import com.olehprukhnytskyi.util.WeightSource;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
public class WeightLogSyncItemDto {
    private Long id;

    @DecimalMin(value = "20.0", message = "Weight is too low")
    @DecimalMax(value = "300.0", message = "Weight is too high")
    private BigDecimal weight;

    private LocalDate date;

    private WeightSource source;

    private Instant updatedAt;

    private boolean deleted;

    private Long version;
}
