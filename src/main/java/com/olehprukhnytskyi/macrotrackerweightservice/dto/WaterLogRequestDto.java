package com.olehprukhnytskyi.macrotrackerweightservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaterLogRequestDto {
    @Min(1)
    @Max(10000)
    private int amountMl;

    @Positive
    private long createdAt;

    @NotNull
    private LocalDate date;
}
