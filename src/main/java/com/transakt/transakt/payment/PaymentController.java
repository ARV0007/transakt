package com.transakt.transakt.payment;

import com.transakt.transakt.common.IdempotencyService;
import com.transakt.transakt.ledger.LedgerEntry;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;
    private final PaymentProcessor paymentProcessor;

    public PaymentController(PaymentService paymentService,
                             IdempotencyService idempotencyService,
                             PaymentProcessor paymentProcessor) {
        this.paymentService = paymentService;
        this.idempotencyService = idempotencyService;
        this.paymentProcessor = paymentProcessor;
    }

    @PostMapping
    public Payment create(@Valid @RequestBody CreatePaymentRequest request,
                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                          Authentication authentication) {

        String merchantId = authentication.getName();

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return paymentProcessor.process(buildPayment(request, merchantId), null);
        }

        Optional<String> existing = idempotencyService.findPaymentId(merchantId, idempotencyKey);
        if (existing.isPresent()) {
            return paymentService.getById(existing.get(), merchantId, false);
        }

        try {
            return paymentProcessor.process(buildPayment(request, merchantId), idempotencyKey);
        } catch (DataIntegrityViolationException e) {
            return idempotencyService.findPaymentId(merchantId, idempotencyKey)
                    .map(paymentId -> paymentService.getById(paymentId, merchantId, false))
                    .orElseThrow(() -> e);
        }
    }

    private Payment buildPayment(CreatePaymentRequest request, String merchantId) {
        Payment payment = new Payment();
        payment.setMerchantId(merchantId);
        payment.setAmountPaise(request.getAmountPaise());
        payment.setCurrency(request.getCurrency());
        return payment;
    }

    @GetMapping
    public Page<Payment> getAll(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return paymentService.getAllForCaller(authentication.getName(), isAdmin(authentication), pageable);
    }

    @GetMapping("/{id}")
    public Payment getById(@PathVariable String id, Authentication authentication) {
        return paymentService.getById(id, authentication.getName(), isAdmin(authentication));
    }

    @GetMapping("/{id}/ledger")
    public List<LedgerEntry> getLedger(@PathVariable String id, Authentication authentication) {
        return paymentService.getLedgerForPayment(id, authentication.getName(), isAdmin(authentication));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}