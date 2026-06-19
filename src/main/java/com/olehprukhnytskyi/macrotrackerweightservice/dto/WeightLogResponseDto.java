package com.olehprukhnytskyi.macrotrackerweightservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Response object containing a single weight record")
public class WeightLogResponseDto {
    @Schema(description = "Unique identifier of the weight log", example = "105")
    private Long id;

    @Schema(description = "Recorded weight in kilograms", example = "75.5")
    private BigDecimal weight;

    @Schema(description = "Date of the weight record", example = "2023-10-28")
    private LocalDate date;

    @Schema(description = "Last server-visible modification time")
    private Instant updatedAt;

    @Schema(description = "Whether the record is a soft-deleted tombstone")
    private boolean deleted;

    @Schema(description = "Optimistic locking version")
    private Long version;
}
