package com.olehprukhnytskyi.macrotrackerweightservice.dto;

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
    private int amountMl;
    private long createdAt;
    private LocalDate date;
}
