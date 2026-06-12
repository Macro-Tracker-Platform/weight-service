package com.olehprukhnytskyi.macrotrackerweightservice.consumer;

import com.olehprukhnytskyi.event.UserDeletedEvent;
import com.olehprukhnytskyi.exception.EventProcessingException;
import com.olehprukhnytskyi.exception.error.EventErrorCode;
import com.olehprukhnytskyi.macrotrackerweightservice.service.WaterService;
import com.olehprukhnytskyi.macrotrackerweightservice.service.WeightService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEventConsumer {
    private final WeightService weightService;
    private final WaterService waterService;

    @KafkaListener(topics = "user-created", groupId = "weight-service")
    public void handleUserCreated(Map<String, Object> event) {
        try {
            Long userId = Long.valueOf(event.get("userId").toString());
            waterService.createDefaultTemplates(userId);
            log.info("Created default water templates for userId={}", userId);
        } catch (Exception e) {
            log.error("Error processing user-created event", e);
            throw new EventProcessingException(EventErrorCode.KAFKA_PROCESSING_ERROR,
                    "Failed to process user-created event", e);
        }
    }

    @KafkaListener(topics = "user-deleted", groupId = "weight-service")
    public void handleUserDeleted(UserDeletedEvent event) {
        try {
            log.info("Processing user-deleted event for userId={}", event.getUserId());
            weightService.deleteUserWeightsRecursively(event.getUserId());
            waterService.deleteUserWaterData(event.getUserId());
            log.info("Successfully processed batch or completed deletion for userId={}",
                    event.getUserId());
        } catch (Exception e) {
            log.error("Error processing user-deleted event for userId={}", event.getUserId(), e);
            throw new EventProcessingException(EventErrorCode.KAFKA_PROCESSING_ERROR,
                    "Failed to process user-deleted event", e);
        }
    }
}
