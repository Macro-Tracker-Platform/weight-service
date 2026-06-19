package com.olehprukhnytskyi.macrotrackerweightservice.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaterSyncResponseDto {
    private List<WaterLogSyncItemDto> logs;
    private List<WaterTemplateSyncItemDto> templates;
    private Instant nextSyncTime;
    private boolean hasMore;
}
