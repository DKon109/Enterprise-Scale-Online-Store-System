package com.comp5348.messaging;

import com.rabbitmq.client.*;

public class NotificationConsumer {

    private static final String QUEUE_NAME = "notification_queue";

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");

        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.queueDeclare(QUEUE_NAME, true, false, false, null);
        System.out.println("[Consumer] Waiting for messages...");

        DeliverCallback deliverCallback = (tag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            System.out.println("[Consumer] Received -> " + message);
            simulateEmail(message);
        };

        channel.basicConsume(QUEUE_NAME, true, deliverCallback, tag -> {});
    }

    private static void simulateEmail(String message) {
        System.out.println("[EmailService] Email sent: " + message);
    }
}