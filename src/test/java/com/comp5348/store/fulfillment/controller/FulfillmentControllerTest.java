package com.comp5348.store.fulfillment.controller;

import com.comp5348.store.fulfillment.model.Fulfillment;
import com.comp5348.store.fulfillment.service.FulfillmentService;
import com.comp5348.store.order.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Focused controller unit tests using MockMvc in standalone mode.
 * Ensures UUID inputs are accepted without triggering validator errors.
 */
@ExtendWith(MockitoExtension.class)
class FulfillmentControllerTest {

    @Mock
    private FulfillmentService fulfillmentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FulfillmentController controller = new FulfillmentController(fulfillmentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void reserve_withUuidPayload_returnsCreated() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID fulfillmentId = UUID.randomUUID();

        Fulfillment fulfillment = new Fulfillment(buildOrder(orderId), "42 Test Street");
        fulfillment.markReserved();
        ReflectionTestUtils.setField(fulfillment, "id", fulfillmentId);

        when(fulfillmentService.reserve(eq(orderId), eq("42 Test Street"), eq(99L), eq(3)))
                .thenReturn(fulfillment);

        mockMvc.perform(post("/fulfillments/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "%s",
                                  "address": "42 Test Street",
                                  "productId": 99,
                                  "quantity": 3
                                }
                                """.formatted(orderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(fulfillmentId.toString()))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()));

        verify(fulfillmentService, times(1)).reserve(eq(orderId), eq("42 Test Street"), eq(99L), eq(3));
    }

    @Test
    void listByOrder_withUuidQuery_invokesServiceAndReturnsOk() throws Exception {
        UUID orderId = UUID.randomUUID();
        Fulfillment fulfillment = new Fulfillment(buildOrder(orderId), "99 Example Ave");
        fulfillment.markReserved();
        ReflectionTestUtils.setField(fulfillment, "id", UUID.randomUUID());

        when(fulfillmentService.listByOrderId(orderId)).thenReturn(List.of(fulfillment));

        mockMvc.perform(get("/fulfillments")
                        .param("orderId", orderId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(orderId.toString()));

        ArgumentCaptor<UUID> captor = ArgumentCaptor.forClass(UUID.class);
        verify(fulfillmentService).listByOrderId(captor.capture());
        assertThat(captor.getValue()).isEqualTo(orderId);
    }

    private static Order buildOrder(UUID orderId) {
        return new Order(orderId, UUID.randomUUID(), "ITEM-1", 5);
    }
}
