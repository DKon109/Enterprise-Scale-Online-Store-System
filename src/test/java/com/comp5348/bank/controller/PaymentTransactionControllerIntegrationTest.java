package com.comp5348.bank.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.comp5348.bank.repository.PaymentTransactionRepository;
import com.comp5348.messaging.config.RabbitMQConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentTransactionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentTransactionRepository repository;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    private UUID orderId;

    @BeforeEach
    void setup() {
        repository.deleteAll();
        orderId = UUID.randomUUID();
    }

    @Test
    void createPurchaseTransactionReturns201AndPublishesMessage() throws Exception {
        PaymentPayload payload = new PaymentPayload(orderId, 200.0, "Purchase", "integration-test-001");

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "corr-int-test")
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type", is("Purchase")))
                .andExpect(jsonPath("$.status", is("Confirmed")))
                .andExpect(jsonPath("$.idempotencyKey", is("integration-test-001")));

        assert repository.findByIdempotencyKey("integration-test-001").isPresent();

        ArgumentCaptor<com.comp5348.messaging.events.EventMessage> captor = ArgumentCaptor.forClass(com.comp5348.messaging.events.EventMessage.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.BANK_QUEUE), captor.capture());

        com.comp5348.messaging.events.EventMessage message = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("payment.success", message.getType());
        org.junit.jupiter.api.Assertions.assertEquals(orderId, message.getOrderId());
        org.junit.jupiter.api.Assertions.assertEquals("integration-test-001", message.getIdempotencyKey());
        org.junit.jupiter.api.Assertions.assertEquals("corr-int-test", message.getCorrelationId());
    }

    @Test
    void duplicateIdempotencyKeyReturns200AndDoesNotPublishAgain() throws Exception {
        PaymentPayload payload = new PaymentPayload(orderId, 180.0, "Purchase", "integration-test-dup-001");

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotencyKey", is("integration-test-dup-001")))
                .andExpect(jsonPath("$.status", is("Confirmed")));

        verify(rabbitTemplate, times(1))
                .convertAndSend(eq(RabbitMQConfig.BANK_QUEUE), any(com.comp5348.messaging.events.EventMessage.class));

        long count = repository.findAll().stream()
                .filter(tx -> "integration-test-dup-001".equals(tx.getIdempotencyKey()))
                .count();
        org.junit.jupiter.api.Assertions.assertEquals(1L, count);
    }

    private record PaymentPayload(UUID orderId, Double amount, String type, String idempotencyKey) {}
}
