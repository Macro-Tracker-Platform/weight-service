package com.olehprukhnytskyi.macrotrackerweightservice.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeightLogDeltaResponseDto {
    private List<WeightLogDeltaDto> data;
    private Long nextCursor;
    private boolean hasMore;
}
