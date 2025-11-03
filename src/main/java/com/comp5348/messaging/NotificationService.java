package com.comp5348.messaging;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.nio.charset.StandardCharsets;

public class NotificationService {

    private static final String QUEUE_NAME = "notification_queue";
    private Channel channel;
    private Connection connection;

    public NotificationService() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("localhost");
            connection = factory.newConnection();
            channel = connection.createChannel();
            channel.queueDeclare(QUEUE_NAME, true, false, false, null);
        } catch (Exception e) {
            System.err.println("[NotificationService] Failed to connect to RabbitMQ: " + e.getMessage());
        }
    }

    public void notify(String type, String message) {
        try {
            String payload = type + ":" + message;
            channel.basicPublish("", QUEUE_NAME, null, payload.getBytes(StandardCharsets.UTF_8));
            System.out.println("[NotificationService] Sent -> " + payload);
        } catch (Exception e) {
            System.err.println("[NotificationService] Failed to send message: " + e.getMessage());
        }
    }

    public void close() {
        try {
            channel.close();
            connection.close();
        } catch (Exception e) {
            System.err.println("[NotificationService] Failed to close connection: " + e.getMessage());
        }
    }
}