package com.olehprukhnytskyi.macrotrackerweightservice.consumer;

import com.olehprukhnytskyi.event.UserDeletedEvent;
import com.olehprukhnytskyi.exception.EventProcessingException;
import com.olehprukhnytskyi.exception.error.EventErrorCode;
import com.olehprukhnytskyi.macrotrackerweightservice.service.WeightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEventConsumer {
    private final WeightService weightService;

    @KafkaListener(topics = "user-deleted", groupId = "weight-service")
    public void handleUserDeleted(UserDeletedEvent event) {
        try {
            log.info("Processing user-deleted event for userId={}", event.getUserId());
            weightService.deleteUserWeightsRecursively(event.getUserId());
            log.info("Successfully processed batch or completed deletion for userId={}",
                    event.getUserId());
        } catch (Exception e) {
            log.error("Error processing user-deleted event for userId={}", event.getUserId(), e);
            throw new EventProcessingException(EventErrorCode.KAFKA_PROCESSING_ERROR,
                    "Failed to process user-deleted event", e);
        }
    }
}
