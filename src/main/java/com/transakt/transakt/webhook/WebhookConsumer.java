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
 *
 * The RestClient is injected rather than created here, and the bean that supplies
 * it sets connect and read timeouts. Without them, a merchant endpoint that accepts
 * the connection and then never answers would block this thread indefinitely: no
 * exception, so no retry, so nothing reaches the dead-letter topic, and every
 * record behind it on the partition waits too. The retry policy protects against
 * endpoints that fail. Only a timeout protects against endpoints that hang.
 */
@Slf4j
@Component
public class WebhookConsumer {

    private final MerchantRepository merchantRepository;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public WebhookConsumer(MerchantRepository merchantRepository,
                           ObjectMapper objectMapper,
                           RestClient webhookRestClient) {
        this.merchantRepository = merchantRepository;
        this.objectMapper = objectMapper;
        this.restClient = webhookRestClient;
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