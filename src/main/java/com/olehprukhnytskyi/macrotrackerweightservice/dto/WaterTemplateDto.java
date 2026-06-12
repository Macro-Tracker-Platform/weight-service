package com.olehprukhnytskyi.macrotrackerweightservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaterTemplateDto {
    @Min(1)
    @Max(10000)
    private int amountMl;

    private boolean active;
}
