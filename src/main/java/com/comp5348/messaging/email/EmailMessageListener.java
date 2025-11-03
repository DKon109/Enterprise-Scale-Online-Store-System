package com.comp5348.messaging.email;

import com.comp5348.messaging.events.EventMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class EmailMessageListener {

    @RabbitListener(queues = "email_queue")
    public void onMessage(EventMessage event) {
        System.out.println("[EmailService] Sending email to " + event.getCustomerEmail() +
                " | Subject: " + event.getType() +
                " | Message: " + event.getDescription());
    }
}
