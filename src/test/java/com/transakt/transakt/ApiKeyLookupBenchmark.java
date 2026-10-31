package com.transakt.transakt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transakt.transakt.common.ApiKeyCache;
import com.transakt.transakt.merchant.MerchantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Measures what the API key cache saves. Not part of the normal suite - the class name
 * does not end in Test, so Surefire skips it. Run it on purpose:
 *
 *   ./mvnw test -Dtest=ApiKeyLookupBenchmark
 *
 * Prints one BENCH line per measurement. Numbers come from the machine it runs on, so
 * they compare cold against warm on the same box rather than describe production.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiKeyLookupBenchmark {

    private static final int WARMUP = 300;
    private static final int RUNS = 2000;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private ApiKeyCache apiKeyCache;
    @Autowired private StringRedisTemplate redis;

    @Test
    void measure() throws Exception {
        String email = "bench-" + UUID.randomUUID() + "@shop.com";
        String body = mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "Bench", "email", email, "password", "hunter2"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode merchant = objectMapper.readTree(body);
        String id = merchant.get("id").asText();
        String apiKey = merchant.get("apiKey").asText();
        String prefix = apiKey.substring(0, 8);

        try {
            // The lookup on its own: Postgres by prefix versus Redis by prefix.
            report("lookup.database", time(() -> merchantRepository.findByApiKeyPrefix(prefix)));
            apiKeyCache.put(merchantRepository.findByApiKeyPrefix(prefix).orElseThrow());
            report("lookup.cache", time(() -> apiKeyCache.get(prefix)));

            // A whole authenticated request, cache cleared before each one versus warm.
            report("request.cold", timeRequests(apiKey, prefix, true));
            report("request.warm", timeRequests(apiKey, prefix, false));
        } finally {
            merchantRepository.deleteById(id);
            apiKeyCache.evict(prefix);
        }
    }

    private long[] timeRequests(String apiKey, String prefix, boolean cold) throws Exception {
        long[] samples = new long[RUNS];
        for (int i = -WARMUP; i < RUNS; i++) {
            if (cold) {
                redis.delete("apikey:" + prefix);
            }
            long start = System.nanoTime();
            mockMvc.perform(get("/api/v1/merchants/me").header("X-API-Key", apiKey))
                    .andExpect(status().isOk());
            long elapsed = System.nanoTime() - start;
            if (i >= 0) {
                samples[i] = elapsed;
            }
        }
        return samples;
    }

    private interface Step {
        void run() throws Exception;
    }

    private static long[] time(Step step) throws Exception {
        long[] samples = new long[RUNS];
        for (int i = -WARMUP; i < RUNS; i++) {
            long start = System.nanoTime();
            step.run();
            long elapsed = System.nanoTime() - start;
            if (i >= 0) {
                samples[i] = elapsed;
            }
        }
        return samples;
    }

    private static void report(String name, long[] nanos) {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        System.out.printf("BENCH %s runs=%d p50=%dus p95=%dus%n",
                name, sorted.length,
                sorted[sorted.length / 2] / 1000,
                sorted[(int) (sorted.length * 0.95)] / 1000);
    }
}
