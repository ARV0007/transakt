package com.transakt.transakt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transakt.transakt.merchant.Merchant;
import com.transakt.transakt.merchant.MerchantRepository;
import com.transakt.transakt.webhook.WebhookConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit test, for the same reason RateLimitServiceTest and OutboxPublisherTest are:
 * the rules worth pinning need a dependency to FAIL ON DEMAND, which a mock does
 * cleanly and a real merchant server does not.
 *
 * MockRestServiceServer intercepts the RestClient and answers from a script, so
 * request building and serialisation are real while the network is not.
 */
class WebhookConsumerTest {

    private static final String PAYLOAD = """
            {"paymentId":"pay-123","merchantId":"merch-1","status":"CAPTURED","amountPaise":50000}
            """;

    private MerchantRepository merchantRepository;
    private MockRestServiceServer mockServer;
    private WebhookConsumer consumer;

    @BeforeEach
    void setUp() {
        merchantRepository = mock(MerchantRepository.class);

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();

        consumer = new WebhookConsumer(merchantRepository, new ObjectMapper(), builder.build());
    }

    @Test
    void makesNoCallWhenMerchantHasNoWebhookUrl() throws Exception {
        Merchant merchant = new Merchant();
        merchant.setWebhookUrl(null);
        when(merchantRepository.findById(anyString())).thenReturn(Optional.of(merchant));

        consumer.deliver(PAYLOAD);

        // No expectation was set, so any HTTP call at all would fail this.
        mockServer.verify();
    }

    @Test
    void postsThePayloadToTheMerchantsUrl() throws Exception {
        Merchant merchant = new Merchant();
        merchant.setWebhookUrl("https://shop.example.com/hook");
        when(merchantRepository.findById(anyString())).thenReturn(Optional.of(merchant));

        mockServer.expect(requestTo("https://shop.example.com/hook"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(PAYLOAD))
                .andRespond(withSuccess());

        consumer.deliver(PAYLOAD);

        mockServer.verify();
    }

    @Test
    void throwsWhenTheMerchantsServerFails() {
        Merchant merchant = new Merchant();
        merchant.setWebhookUrl("https://broken.example.com/hook");
        when(merchantRepository.findById(anyString())).thenReturn(Optional.of(merchant));

        mockServer.expect(requestTo("https://broken.example.com/hook"))
                .andRespond(withServerError());

        // Throwing is the contract. DefaultErrorHandler owns retries and the
        // dead-letter topic, and it only fires on an exception. A try/catch here
        // would look like good hygiene and would silently delete failed webhooks.
        assertThatThrownBy(() -> consumer.deliver(PAYLOAD))
                .isInstanceOf(Exception.class);

        mockServer.verify();
    }
}