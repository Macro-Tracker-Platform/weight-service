package com.olehprukhnytskyi.macrotrackerweightservice.producer;

import com.olehprukhnytskyi.event.UserDeletedEvent;
import com.olehprukhnytskyi.exception.EventProcessingException;
import com.olehprukhnytskyi.exception.error.EventErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendUserDeletedEvent(UserDeletedEvent event) {
        try {
            kafkaTemplate.send("user-deleted", event).get();
            log.debug("Republished user-deleted event for userId={}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to send UserDeletedEvent to Kafka for userId={}",
                    event.getUserId(), e);
            throw new EventProcessingException(EventErrorCode.KAFKA_SEND_FAILED,
                    "Cannot send Kafka event", e);
        }
    }
}
