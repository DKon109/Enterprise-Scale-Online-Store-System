package com.comp5348.store.order.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.comp5348.store.order.application.service.OrderOrchestrator;
import com.comp5348.store.order.exception.OrderNotFoundException;
import com.comp5348.store.order.model.Order;
import com.comp5348.store.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Standalone MVC tests for {@link OrderController}.
 *
 * Spins up the controller with mocked dependencies to verify HTTP mapping behavior
 * without loading the full Spring Boot application context.
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private OrderOrchestrator orderOrchestrator;

    @Mock
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        OrderController controller = new OrderController(orderOrchestrator, orderService);

        this.objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();

        this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator())
                .build();
    }

    private org.springframework.validation.Validator validator() {
        LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
        factoryBean.afterPropertiesSet();
        return factoryBean;
    }

    @Test
    void placeOrderReturnsCreated() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, customerId, "SKU-001", 5);

        when(orderOrchestrator.placeOrder(eq(customerId), eq("SKU-001"), eq(5)))
                .thenReturn(orderId);
        when(orderService.getOrder(orderId)).thenReturn(order);

        OrderController.PlaceOrderRequest request =
                new OrderController.PlaceOrderRequest(customerId, "SKU-001", 5);

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/orders/" + orderId))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.itemId").value("SKU-001"))
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void placeOrderBadRequestFromValidationReturns400() throws Exception {
        UUID customerId = UUID.randomUUID();

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "%s",
                                  "itemId": "SKU-001",
                                  "quantity": 0
                                }
                                """.formatted(customerId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }

    @Test
    void getOrderFoundReturns200() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, customerId, "SKU-001", 5);

        when(orderService.getOrder(orderId)).thenReturn(order);

        mockMvc.perform(get("/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.itemId").value("SKU-001"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getOrderNotFoundReturns404() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrder(orderId)).thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(get("/orders/{orderId}", orderId))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString(orderId.toString())));
    }

    @Test
    void getOrdersByCustomerReturnsList() throws Exception {
        UUID customerId = UUID.randomUUID();
        Order order1 = new Order(UUID.randomUUID(), customerId, "SKU-001", 2);
        Order order2 = new Order(UUID.randomUUID(), customerId, "SKU-002", 3);

        when(orderService.getCustomerOrders(customerId)).thenReturn(List.of(order1, order2));

        mockMvc.perform(get("/orders").param("customerId", customerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].customerId").value(customerId.toString()));
    }

    @Test
    void cancelOrderReturns204() throws Exception {
        UUID orderId = UUID.randomUUID();
        doNothing().when(orderOrchestrator).cancel(orderId);

        mockMvc.perform(post("/orders/{orderId}/cancel", orderId))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancelOrderConflictReturns409() throws Exception {
        UUID orderId = UUID.randomUUID();
        doThrow(new IllegalStateException("cannot cancel"))
                .when(orderOrchestrator).cancel(orderId);

        mockMvc.perform(post("/orders/{orderId}/cancel", orderId))
                .andExpect(status().isConflict());
    }
}
