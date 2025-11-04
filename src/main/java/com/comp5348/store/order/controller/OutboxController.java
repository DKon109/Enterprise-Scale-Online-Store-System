package com.comp5348.store.order.controller;

import com.comp5348.store.order.model.OutboxEvent;
import com.comp5348.store.order.model.OutboxEventStatus;
import com.comp5348.store.order.repository.OutboxRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/outbox")
public class OutboxController {

    private final OutboxRepository outboxRepository;

    public OutboxController(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @GetMapping
    public List<OutboxEventResponse> listOutboxEvents() {
        List<OutboxEvent> pending = outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);
        if (pending.isEmpty()) {
            pending = outboxRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return pending.stream()
                .map(OutboxEventResponse::from)
                .collect(Collectors.toList());
    }

    private record OutboxEventResponse(
            Long id,
            UUID orderId,
            String template,
            boolean sent,
            LocalDateTime sentAt,
            String payload,
            String eventType,
            String status,
            LocalDateTime createdAt,
            LocalDateTime processedAt,
            int retryCount,
            String lastError) {

        private static OutboxEventResponse from(OutboxEvent event) {
            return new OutboxEventResponse(
                    event.getId(),
                    event.getOrderId(),
                    event.getTemplate(),
                    event.isSent(),
                    event.getSentAt(),
                    event.getPayload(),
                    event.getTemplate(),
                    event.getStatus().name(),
                    event.getCreatedAt(),
                    event.getProcessedAt(),
                    event.getRetryCount(),
                    event.getLastError());
        }
    }
}
