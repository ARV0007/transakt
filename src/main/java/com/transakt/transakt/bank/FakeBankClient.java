package com.transakt.transakt.bank;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class FakeBankClient implements BankClient {

    private final int minLatencyMs;
    private final int maxLatencyMs;
    private final double declineRate;
    private final Map<String, BankResult> decisions = new ConcurrentHashMap<>();

    public FakeBankClient(@Value("${bank.latency.min-ms:200}") int minLatencyMs,
                          @Value("${bank.latency.max-ms:800}") int maxLatencyMs,
                          @Value("${bank.decline-rate:0.1}") double declineRate) {
        this.minLatencyMs = minLatencyMs;
        this.maxLatencyMs = maxLatencyMs;
        this.declineRate = declineRate;
    }

    @Override
    public BankResult authorize(String paymentId, long amountPaise, String currency) {
        sleepLikeARealBank();

        BankResult result = ThreadLocalRandom.current().nextDouble() < declineRate
                ? BankResult.DECLINED
                : BankResult.APPROVED;

        log.info("Bank returned {} for payment {}", result, paymentId);
        decisions.put(paymentId, result);
        return result;
    }

    @Override
    public BankResult lookup(String paymentId) {
        return decisions.get(paymentId);
    }

    private void sleepLikeARealBank() {
        if (maxLatencyMs <= 0) {
            return;
        }
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(minLatencyMs, maxLatencyMs + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the bank", e);
        }
    }
}