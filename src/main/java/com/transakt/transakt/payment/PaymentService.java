package com.transakt.transakt.payment;

import com.transakt.transakt.common.ResourceNotFoundException;
import com.transakt.transakt.ledger.EntryDirection;
import com.transakt.transakt.ledger.LedgerEntry;
import com.transakt.transakt.ledger.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public PaymentService(PaymentRepository paymentRepository,
                          LedgerEntryRepository ledgerEntryRepository) {
        this.paymentRepository = paymentRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public Payment create(Payment payment) {
        payment.setId(UUID.randomUUID().toString());
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setCreatedAt(Instant.now());
        Payment saved = paymentRepository.save(payment);

        LedgerEntry credit = new LedgerEntry();
        credit.setId(UUID.randomUUID().toString());
        credit.setPaymentId(saved.getId());
        credit.setAccount("merchant:" + saved.getMerchantId());
        credit.setDirection(EntryDirection.CREDIT);
        credit.setAmountPaise(saved.getAmountPaise());
        credit.setCreatedAt(Instant.now());
        ledgerEntryRepository.save(credit);

        LedgerEntry debit = new LedgerEntry();
        debit.setId(UUID.randomUUID().toString());
        debit.setPaymentId(saved.getId());
        debit.setAccount("gateway:incoming");
        debit.setDirection(EntryDirection.DEBIT);
        debit.setAmountPaise(saved.getAmountPaise());
        debit.setCreatedAt(Instant.now());
        ledgerEntryRepository.save(debit);

        return saved;
    }

    public Payment getById(String id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
    }
    public List<LedgerEntry> getLedgerForPayment(String paymentId) {
        return ledgerEntryRepository.findByPaymentId(paymentId);
    }
}