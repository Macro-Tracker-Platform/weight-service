package com.olehprukhnytskyi.macrotrackerweightservice.producer;

import com.olehprukhnytskyi.macrotrackerweightservice.event.CacheInvalidationEvent;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationProducer {
    private static final String TOPIC = "cache-invalidation";
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.cache-invalidation.enabled:true}")
    private boolean enabled;

    public void send(Long userId, String domain, String originDeviceId) {
        if (!enabled) {
            log.debug("Cache invalidation disabled domain={} userId={}", domain, userId);
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            publish(userId, domain, originDeviceId);
                        }
                    });
            return;
        }
        publish(userId, domain, originDeviceId);
    }

    private void publish(Long userId, String domain, String originDeviceId) {
        CacheInvalidationEvent event = CacheInvalidationEvent.builder()
                .userId(userId)
                .domain(domain)
                .changedAt(Instant.now())
                .originDeviceId(originDeviceId)
                .build();
        kafkaTemplate.send(TOPIC, String.valueOf(userId), event)
                .whenComplete((result, exception) -> {
                    if (exception != null) {
                        log.error("Failed to publish cache invalidation domain={} userId={}",
                                domain, userId, exception);
                        return;
                    }
                    log.debug("Published cache invalidation domain={} userId={}",
                            domain, userId);
                });
    }
}
