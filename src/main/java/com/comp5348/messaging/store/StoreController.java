package com.comp5348.messaging.store;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/store")
public class StoreController {

    private final StoreMessageProducer producer;

    public StoreController(StoreMessageProducer producer) {
        this.producer = producer;
    }

    @PostMapping("/send")
    public String sendEvent(@RequestParam Long transactionID,
                            @RequestParam Long orderId,
                            @RequestParam Double amount,
                            @RequestParam String type,
                            @RequestParam String status,
                            @RequestParam Long bankReferenceID
                            ) {
        producer.sendPaymentEvent(transactionID, orderId, amount, type, status, bankReferenceID);
        return "Transaction processing: " + transactionID + " for order " + orderId;
    }
}