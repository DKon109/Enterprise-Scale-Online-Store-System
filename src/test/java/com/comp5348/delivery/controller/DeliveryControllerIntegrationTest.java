package com.comp5348.delivery.controller;

import com.comp5348.delivery.model.Delivery;
import com.comp5348.delivery.service.DeliveryService;
import com.comp5348.store.order.model.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class DeliveryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeliveryService deliveryService;

    @Test
    void listByOrder_acceptsUuidQueryParameter() throws Exception {
        UUID orderId = UUID.randomUUID();
        Delivery delivery = buildDelivery(orderId);
        when(deliveryService.getDeliveriesByOrderId(orderId)).thenReturn(List.of(delivery));

        mockMvc.perform(get("/deliveries")
                        .param("orderId", orderId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(orderId.toString()));
    }

    @Test
    void create_allowsUuidInRequestBody() throws Exception {
        UUID orderId = UUID.randomUUID();
        Delivery delivery = buildDelivery(orderId);
        when(deliveryService.createDelivery(eq(orderId), eq(5L), eq("123 Delivery Lane"), eq("TRACK-1")))
                .thenReturn(delivery);

        mockMvc.perform(post("/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "%s",
                                  "warehouseId": 5,
                                  "address": "123 Delivery Lane",
                                  "trackingNumber": "TRACK-1"
                                }
                                """.formatted(orderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()));
    }

    @Test
    void getByTracking_returnsNotFoundWhenMissing() throws Exception {
        when(deliveryService.getByTrackingNumber(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/deliveries/track/MISSING"))
                .andExpect(status().isNotFound());
    }

    private static Delivery buildDelivery(UUID orderId) {
        Order order = new Order(orderId, UUID.randomUUID(), "SKU-DEL-1", 1);
        Delivery delivery = new Delivery(order, 5L, "123 Delivery Lane", "TRACK-1");
        ReflectionTestUtils.setField(delivery, "id", 10L);
        return delivery;
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
