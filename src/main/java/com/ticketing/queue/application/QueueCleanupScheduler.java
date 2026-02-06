package com.ticketing.queue.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueCleanupScheduler {

    private final QueueService queueService;

    @Scheduled(fixedRate = 30000)  // 30초마다 실행
    public void cleanupInactiveUsers() {
        Set<String> queueKeys = queueService.getActiveQueueKeys();

        for (String queueKey : queueKeys) {
            String eventIdStr = queueKey.replace("queue:", "");
            try {
                UUID eventId = UUID.fromString(eventIdStr);
                queueService.removeInactiveUsers(eventId);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid queue key format: {}", queueKey);
            }
        }
    }
}
