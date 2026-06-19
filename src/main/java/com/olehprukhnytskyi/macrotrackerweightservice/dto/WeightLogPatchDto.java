package com.olehprukhnytskyi.macrotrackerweightservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
@Schema(description = "Request object for partial update of user weight")
public class WeightLogPatchDto {
    @Schema(description = "Weight value in kilograms", example = "75.5")
    @DecimalMin(value = "20.0", message = "Weight is too low")
    @DecimalMax(value = "300.0", message = "Weight is too high")
    private BigDecimal weight;

    @Schema(description = "Date the weight was recorded", example = "2023-10-28")
    private LocalDate date;

    @Schema(description = "Client's last known server version", example = "2")
    private Long version;
}
