package com.comp5348.messaging.warehouse;

import com.comp5348.messaging.events.EventMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class WarehouseMessageListener {

    @RabbitListener(queues = "warehouse_queue")
    public void onMessage(EventMessage event) {
        switch (event.getType()) {
            case "item.preparing" -> System.out.println("[Warehouse] Preparing item for order " + event.getOrderId());
            case "item.shipped" -> System.out.println("[Warehouse] Order " + event.getOrderId() + " is on the way.");
            case "item.delivered" -> System.out.println("[Warehouse] Order " + event.getOrderId() + " has been delivered to customer.");
            default -> System.out.println("[Warehouse] ⚠️ Unknown event: " + event);
        }
    }
}
