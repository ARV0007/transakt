package com.transakt.transakt.payment;

import com.transakt.transakt.bank.BankClient;
import com.transakt.transakt.bank.BankResult;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PaymentProcessor {

    private final PaymentService paymentService;
    private final BankClient bankClient;

    public PaymentProcessor(PaymentService paymentService, BankClient bankClient) {
        this.paymentService = paymentService;
        this.bankClient = bankClient;
    }

    public Payment process(Payment payment, String idempotencyKey) {
        Payment pending = paymentService.createPending(payment, idempotencyKey);

        BankResult result;
        try {
            result = bankClient.authorize(
                    pending.getId(), pending.getAmountPaise(), pending.getCurrency());
        } catch (RuntimeException e) {
            log.warn("Bank call failed for payment {} — leaving PENDING for reconciliation",
                    pending.getId(), e);
            return pending;
        }
        return paymentService.settle(pending.getId(), result == BankResult.APPROVED);
    }
}