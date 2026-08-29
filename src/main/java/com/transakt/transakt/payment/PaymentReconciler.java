package com.transakt.transakt.payment;

import com.transakt.transakt.bank.BankClient;
import com.transakt.transakt.bank.BankResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class PaymentReconciler {

    private static final Duration STRANDED_AFTER = Duration.ofMinutes(5);

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final BankClient bankClient;

    public PaymentReconciler(PaymentRepository paymentRepository,
                             PaymentService paymentService,
                             BankClient bankClient) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.bankClient = bankClient;
    }

    @Scheduled(fixedDelayString = "PT1M")
    public int reconcileStrandedPayments() {
        Instant cutoff = Instant.now().minus(STRANDED_AFTER);
        List<Payment> stranded =
                paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, cutoff);

        if (stranded.isEmpty()) {
            return 0;
        }

        log.info("Reconciling {} stranded payment(s)", stranded.size());
        int settled = 0;

        for (Payment payment : stranded) {
            BankResult result = bankClient.lookup(payment.getId());

            if (result == null) {
                log.warn("Bank has no record of payment {} — leaving PENDING", payment.getId());
                continue;
            }

            paymentService.settle(payment.getId(), result == BankResult.APPROVED);
            settled++;
            log.info("Reconciled payment {} as {}", payment.getId(), result);
        }

        return settled;
    }
}