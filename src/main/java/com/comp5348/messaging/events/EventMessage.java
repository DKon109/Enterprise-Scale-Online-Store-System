package com.comp5348.messaging.events;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventMessage {
    private String type;       // e.g., "order.cancelled"
    private Long orderId;
    private Double amount;
    private String customerEmail;
    private String description;


}
