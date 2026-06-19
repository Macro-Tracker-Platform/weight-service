package com.olehprukhnytskyi.macrotrackerweightservice.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheInvalidationEvent {
    private Long userId;
    private String domain;
    private Instant changedAt;
    private String originDeviceId;
}
