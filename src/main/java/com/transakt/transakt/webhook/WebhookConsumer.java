package com.transakt.transakt.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transakt.transakt.merchant.Merchant;
import com.transakt.transakt.merchant.MerchantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Delivers payment.settled events to the merchant's webhook URL.
 *
 * Throws on failure ON PURPOSE. The error handler configured in KafkaConfig owns
 * retries and the dead-letter topic; this method is written as if it always
 * succeeds. A merchant whose server is down blocks nothing — the retries happen
 * on the consumer's thread, never on the payment request thread.
 */
@Slf4j
@Component
public class WebhookConsumer {

    private final MerchantRepository merchantRepository;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public WebhookConsumer(MerchantRepository merchantRepository, ObjectMapper objectMapper) {
        this.merchantRepository = merchantRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${outbox.topic}", groupId = "transakt-webhooks")
    public void deliver(String payload) throws Exception {
        JsonNode event = objectMapper.readTree(payload);
        String merchantId = event.get("merchantId").asText();

        Optional<Merchant> merchant = merchantRepository.findById(merchantId);
        if (merchant.isEmpty() || merchant.get().getWebhookUrl() == null) {
            log.debug("No webhook URL for merchant {} — nothing to deliver", merchantId);
            return;
        }

        String url = merchant.get().getWebhookUrl();
        restClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        log.info("Delivered {} to {}", event.get("paymentId").asText(), url);
    }
}