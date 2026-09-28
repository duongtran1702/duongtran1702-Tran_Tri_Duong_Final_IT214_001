package org.example.notifyservice.consumer;

import org.example.notifyservice.service.EmailService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedConsumer {

    private final EmailService emailService;

    public OrderCreatedConsumer(EmailService emailService) {
        this.emailService = emailService;
    }

    @KafkaListener(topics = "${notification.kafka.order-created-topic:order-created}")
    public void consume(String email) {
        if (email != null && !email.isBlank()) {
            String sanitized = email.trim();
            if (sanitized.startsWith("\"") && sanitized.endsWith("\"") && sanitized.length() >= 2) {
                sanitized = sanitized.substring(1, sanitized.length() - 1);
            }
            if (!sanitized.isBlank()) {
                emailService.sendOrderCreatedEmail(sanitized);
            }
        }
    }
}
