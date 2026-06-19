package com.olehprukhnytskyi.macrotrackerweightservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaterSyncPushRequestDto {
    @Valid
    @NotNull(message = "Log changes cannot be null")
    @Size(max = 500, message = "At most 500 water log changes can be pushed at once")
    private List<WaterLogSyncItemDto> logChanges;

    @Valid
    @NotNull(message = "Template changes cannot be null")
    @Size(max = 100, message = "At most 100 water template changes can be pushed at once")
    private List<WaterTemplateSyncItemDto> templateChanges;
}
