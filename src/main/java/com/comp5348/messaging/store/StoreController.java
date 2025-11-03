package com.comp5348.messaging.store;

import com.comp5348.messaging.events.EventMessage;
import com.comp5348.messaging.config.RabbitMQConfig;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/store")
public class StoreController {

    private static final Logger log = LoggerFactory.getLogger(StoreController.class);
    private final StoreMessageProducer producer;

    public StoreController(StoreMessageProducer producer) {
        this.producer = producer;
    }

    @PostMapping("/event")
    public String sendEvent(@RequestBody EventMessage event) {
        log.info(" [StoreController] Received event request: type={}, orderId={}, amount={}",
                event.getType(), event.getOrderId(), event.getAmount());

        producer.publishEvent(event);

        log.info(" [StoreController] Event {} submitted to message producer", event.getType());

        return "Event sent: " + event.getType();
    }

}
