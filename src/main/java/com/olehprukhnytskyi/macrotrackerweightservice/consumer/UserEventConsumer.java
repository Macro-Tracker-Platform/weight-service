package com.olehprukhnytskyi.macrotrackerweightservice.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "user-deleted", groupId = "weight-service")
    public void handleUserDeleted(String message) {
        UserDeletedEvent event;
        try {
            event = objectMapper.readValue(message, UserDeletedEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Invalid user-deleted event payload. Message: {}", message, e);
            throw new EventProcessingException(EventErrorCode.EVENT_DESERIALIZATION_FAILED,
                    "Failed to parse user-deleted event", e);
        }
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
