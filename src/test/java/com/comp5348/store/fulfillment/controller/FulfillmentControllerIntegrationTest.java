package com.comp5348.store.fulfillment.controller;

import com.comp5348.store.fulfillment.model.Fulfillment;
import com.comp5348.store.fulfillment.service.FulfillmentService;
import com.comp5348.store.order.model.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class FulfillmentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FulfillmentService fulfillmentService;

    @Test
    void reserveEndpoint_acceptsUuidPayload() throws Exception {
        UUID orderId = UUID.randomUUID();
        long fulfillmentId = 101L;
        Fulfillment fulfillment = new Fulfillment(new Order(orderId, UUID.randomUUID(), "SKU-INT-1", 2), "500 Integration Way");
        fulfillment.markReserved();
        ReflectionTestUtils.setField(fulfillment, "id", fulfillmentId);

        when(fulfillmentService.reserve(eq(orderId), eq("500 Integration Way"), eq(99L), eq(2)))
                .thenReturn(fulfillment);

        mockMvc.perform(post("/fulfillments/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"orderId\": \"%s\",
                                  \"address\": \"500 Integration Way\",
                                  \"productId\": 99,
                                  \"quantity\": 2
                                }
                                """.formatted(orderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value((int) fulfillmentId))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()));
    }

    @Test
    void listByOrder_acceptsUuidQueryParameter() throws Exception {
        UUID orderId = UUID.randomUUID();
        Fulfillment fulfillment = new Fulfillment(new Order(orderId, UUID.randomUUID(), "SKU-INT-2", 1), "List Address");
        fulfillment.markReserved();
        ReflectionTestUtils.setField(fulfillment, "id", 202L);

        when(fulfillmentService.listByOrderId(orderId)).thenReturn(List.of(fulfillment));

        mockMvc.perform(get("/fulfillments")
                        .param("orderId", orderId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(orderId.toString()));
    }

    @TestConfiguration
    static class TransactionTemplateTestConfig {
        @Bean(name = "testTransactionTemplate")
        @Primary
        com.comp5348.store.order.application.support.TransactionTemplate transactionTemplate() {
            return new com.comp5348.store.order.application.support.TransactionTemplate() {
                @Override
                public <T> T execute(java.util.function.Supplier<T> action) {
                    return action.get();
                }
            };
        }
    }
}
