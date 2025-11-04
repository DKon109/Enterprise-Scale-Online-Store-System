package com.comp5348.store.order.infrastructure.outbox;

import com.comp5348.store.order.model.OutboxEvent;
import com.comp5348.store.order.repository.OutboxRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Periodically polls the Outbox table and delivers pending notifications.
 */
@Component
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
    private static final TypeReference<Map<String, String>> PAYLOAD_TYPE = new TypeReference<>() {};

    private final OutboxRepository outboxRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String emailServiceUrl;

    public OutboxWorker(
            OutboxRepository outboxRepository,
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${email.service.url:http://localhost:8082}") String emailServiceUrl) {
        this.outboxRepository = outboxRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.emailServiceUrl = emailServiceUrl;
    }

    @Scheduled(fixedDelayString = "${outbox.worker.interval:5000}")
    public void processOutbox() {
        List<OutboxEvent> pending = outboxRepository.findBySentFalseOrderByCreatedAtAsc();
        if (pending.isEmpty()) {
            return;
        }

        log.info("Processing {} outbox event(s)", pending.size());
        for (OutboxEvent event : pending) {
            Map<String, String> variables = readPayload(event);
            try {
                restTemplate.postForObject(
                        emailServiceUrl + "/send",
                        new EmailRequest(event.getOrderId(), event.getTemplate(), variables),
                        Void.class);
                event.markSent();
                outboxRepository.save(event);
                log.info("Outbox event {} dispatched successfully", event.getId());
            } catch (RestClientException ex) {
                event.markAttemptFailed();
                outboxRepository.save(event);
                log.warn("Outbox delivery failed (id={}, retry={}): {}", event.getId(), event.getRetryCount(), ex.getMessage());
            }
        }
    }

    private Map<String, String> readPayload(OutboxEvent event) {
        try {
            return objectMapper.readValue(event.getPayload(), PAYLOAD_TYPE);
        } catch (Exception ex) {
            log.warn("Failed to parse payload for outbox event {}: {}", event.getId(), ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private record EmailRequest(UUID orderId, String template, Map<String, String> variables) {}
}
