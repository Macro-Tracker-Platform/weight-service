package com.olehprukhnytskyi.macrotrackerweightservice.dto;

import com.olehprukhnytskyi.util.WeightSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request object for logging or updating user weight")
public class WeightLogRequestDto {
    @Schema(description = "Weight value in kilograms", example = "75.5")
    @NotNull(message = "Weight cannot be null")
    @DecimalMin(value = "20.0", message = "Weight is too low")
    @DecimalMax(value = "300.0", message = "Weight is too high")
    private BigDecimal weight;

    @Schema(description = "Date the weight was recorded", example = "2023-10-28")
    @NotNull(message = "Date cannot be null")
    private LocalDate date;

    @Schema(description = "Source of the weight data (e.g., MANUAL, SCALE)", example = "MANUAL")
    @NotNull(message = "Source cannot be null")
    private WeightSource source;
}
