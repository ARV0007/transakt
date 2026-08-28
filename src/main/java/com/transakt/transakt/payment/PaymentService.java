package com.transakt.transakt.payment;

import com.transakt.transakt.common.IdempotencyKey;
import com.transakt.transakt.common.IdempotencyKeyRepository;
import com.transakt.transakt.common.ResourceNotFoundException;
import com.transakt.transakt.ledger.EntryDirection;
import com.transakt.transakt.ledger.LedgerEntry;
import com.transakt.transakt.ledger.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;

    public PaymentService(PaymentRepository paymentRepository,
                          LedgerEntryRepository ledgerEntryRepository,
                          IdempotencyKeyRepository idempotencyKeyRepository) {
        this.paymentRepository = paymentRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    @Transactional
    public Payment createPending(Payment payment, String idempotencyKey) {
        payment.setId(UUID.randomUUID().toString());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCreatedAt(Instant.now());
        Payment saved = paymentRepository.save(payment);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            IdempotencyKey key = new IdempotencyKey();
            key.setId(UUID.randomUUID().toString());
            key.setMerchantId(saved.getMerchantId());
            key.setIdempotencyKey(idempotencyKey);
            key.setPaymentId(saved.getId());
            key.setCreatedAt(Instant.now());
            idempotencyKeyRepository.save(key);
        }

        return saved;
    }

    @Transactional
    public Payment settle(String paymentId, boolean approved) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        if (!approved) {
            payment.setStatus(PaymentStatus.FAILED);
            return paymentRepository.save(payment);
        }

        payment.setStatus(PaymentStatus.CAPTURED);
        Payment captured = paymentRepository.save(payment);

        LedgerEntry credit = new LedgerEntry();
        credit.setId(UUID.randomUUID().toString());
        credit.setPaymentId(captured.getId());
        credit.setAccount("merchant:" + captured.getMerchantId());
        credit.setDirection(EntryDirection.CREDIT);
        credit.setAmountPaise(captured.getAmountPaise());
        credit.setCreatedAt(Instant.now());
        ledgerEntryRepository.save(credit);

        LedgerEntry debit = new LedgerEntry();
        debit.setId(UUID.randomUUID().toString());
        debit.setPaymentId(captured.getId());
        debit.setAccount("gateway:incoming");
        debit.setDirection(EntryDirection.DEBIT);
        debit.setAmountPaise(captured.getAmountPaise());
        debit.setCreatedAt(Instant.now());
        ledgerEntryRepository.save(debit);

        return captured;
    }

    public Page<Payment> getAllForCaller(String callerMerchantId, boolean isAdmin, Pageable pageable) {
        if (isAdmin) {
            return paymentRepository.findAll(pageable);
        }
        return paymentRepository.findByMerchantId(callerMerchantId, pageable);
    }

    public Payment getById(String id, String callerMerchantId, boolean isAdmin) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));

        if (!isAdmin && !payment.getMerchantId().equals(callerMerchantId)) {
            throw new ResourceNotFoundException("Payment not found: " + id);
        }

        return payment;
    }

    public List<LedgerEntry> getLedgerForPayment(String paymentId, String callerMerchantId, boolean isAdmin) {
        getById(paymentId, callerMerchantId, isAdmin);
        return ledgerEntryRepository.findByPaymentId(paymentId);
    }
}