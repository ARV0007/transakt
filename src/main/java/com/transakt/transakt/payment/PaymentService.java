package com.transakt.transakt.payment;

import com.transakt.transakt.common.IdempotencyKey;
import com.transakt.transakt.common.IdempotencyKeyRepository;
import com.transakt.transakt.common.ResourceNotFoundException;
import com.transakt.transakt.ledger.EntryDirection;
import com.transakt.transakt.ledger.LedgerEntry;
import com.transakt.transakt.ledger.LedgerEntryRepository;
import com.transakt.transakt.outbox.OutboxEvent;
import com.transakt.transakt.outbox.OutboxEventRepository;

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
    private final OutboxEventRepository outboxEventRepository;

    public PaymentService(PaymentRepository paymentRepository,
                          LedgerEntryRepository ledgerEntryRepository,
                          IdempotencyKeyRepository idempotencyKeyRepository,
                          OutboxEventRepository outboxEventRepository) {
        this.paymentRepository = paymentRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.outboxEventRepository = outboxEventRepository;
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

    /**
     * One exit on purpose. Both outcomes are terminal and both are news the merchant
     * needs, so both publish an event — writing it twice in two branches is how the
     * two copies drift apart later. Ledger entries stay inside the approved branch:
     * a declined payment moved no money and records no movement.
     */
    @Transactional
    public Payment settle(String paymentId, boolean approved) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        payment.setStatus(approved ? PaymentStatus.CAPTURED : PaymentStatus.FAILED);
        Payment settled = paymentRepository.save(payment);

        if (approved) {
            LedgerEntry credit = new LedgerEntry();
            credit.setId(UUID.randomUUID().toString());
            credit.setPaymentId(settled.getId());
            credit.setAccount("merchant:" + settled.getMerchantId());
            credit.setDirection(EntryDirection.CREDIT);
            credit.setAmountPaise(settled.getAmountPaise());
            credit.setCreatedAt(Instant.now());
            ledgerEntryRepository.save(credit);

            LedgerEntry debit = new LedgerEntry();
            debit.setId(UUID.randomUUID().toString());
            debit.setPaymentId(settled.getId());
            debit.setAccount("gateway:incoming");
            debit.setDirection(EntryDirection.DEBIT);
            debit.setAmountPaise(settled.getAmountPaise());
            debit.setCreatedAt(Instant.now());
            ledgerEntryRepository.save(debit);
        }

        // Written in THIS transaction, alongside the payment it describes. Either both
        // commit or neither does, so an event can never be lost.
        outboxEventRepository.save(new OutboxEvent(
                settled.getId(),
                "payment.settled",
                """
                {"paymentId":"%s","status":"%s","amountPaise":%d,"currency":"%s"}"""
                        .formatted(settled.getId(), settled.getStatus(),
                                settled.getAmountPaise(), settled.getCurrency())));

        return settled;
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