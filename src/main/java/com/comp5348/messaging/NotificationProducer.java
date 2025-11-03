package com.comp5348.messaging;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class NotificationProducer {

    private static final String QUEUE_NAME = "notification_queue";

    public static void main(String[] args) throws Exception {
        // Set up connection to RabbitMQ
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost"); // RabbitMQ running in Docker

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            // Declare queue (durable = true means it survives broker restarts)
            channel.queueDeclare(QUEUE_NAME, true, false, false, null);

            // Publish a test message
            String message = "Hello from NotificationProducer!";
            channel.basicPublish("", QUEUE_NAME, null, message.getBytes("UTF-8"));

            System.out.println("[Producer] Sent -> " + message);
        }
    }
}