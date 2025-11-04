package com.comp5348.store.order.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.comp5348.store.order.model.OutboxEvent;
import com.comp5348.store.order.repository.OutboxRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class OutboxControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OutboxRepository outboxRepository;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .standaloneSetup(new OutboxController(outboxRepository))
                .build();
    }

    @Test
    void listOutboxEventsReturnsPendingEntries() throws Exception {
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), "PAYMENT_FAILED", "{\"reason\":\"Insufficient funds\"}");
        when(outboxRepository.findBySentFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));

        mockMvc.perform(get("/outbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].template").value("PAYMENT_FAILED"))
                .andExpect(jsonPath("$[0].sent").value(false))
                .andExpect(jsonPath("$[0].payload").value("{\"reason\":\"Insufficient funds\"}"));
    }

    @Test
    void listOutboxEventsFallsBackToAllWhenNoPending() throws Exception {
        OutboxEvent sentEvent = new OutboxEvent(UUID.randomUUID(), "SHIPMENT_FAILED", "{}");
        sentEvent.markSent();

        when(outboxRepository.findBySentFalseOrderByCreatedAtAsc()).thenReturn(List.of());
        when(outboxRepository.findAll(any(Sort.class))).thenReturn(List.of(sentEvent));

        mockMvc.perform(get("/outbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].template").value("SHIPMENT_FAILED"))
                .andExpect(jsonPath("$[0].sent").value(true))
                .andExpect(jsonPath("$[0].sentAt").isNotEmpty());
    }
}
