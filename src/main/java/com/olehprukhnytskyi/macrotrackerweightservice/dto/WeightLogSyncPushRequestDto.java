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
public class WeightLogSyncPushRequestDto {
    @Valid
    @NotNull(message = "Changes cannot be null")
    @Size(max = 500, message = "At most 500 changes can be pushed at once")
    private List<WeightLogSyncItemDto> changes;
}
