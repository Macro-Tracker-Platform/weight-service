package com.olehprukhnytskyi.macrotrackerweightservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaterTemplateSyncItemDto {
    private Long id;

    @Min(1)
    @Max(10000)
    private Integer amountMl;

    private Boolean active;
    private Instant updatedAt;
    private boolean deleted;
    private Long version;
}
