package com.olehprukhnytskyi.macrotrackerweightservice.dto;

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
public class WaterLogDto {
    private long id;
    private String requestId;
    private int amountMl;
    private long createdAt;
    private LocalDate date;
    private Instant updatedAt;
    private boolean deleted;
    private Long version;
}
