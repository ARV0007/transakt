package com.transakt.transakt.payment;

import com.transakt.transakt.ledger.LedgerEntry;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public Payment create(@Valid @RequestBody CreatePaymentRequest request,
                          Authentication authentication) {
        Payment payment = new Payment();
        payment.setMerchantId(authentication.getName());
        payment.setAmountPaise(request.getAmountPaise());
        payment.setCurrency(request.getCurrency());
        return paymentService.create(payment);
    }

    @GetMapping
    public List<Payment> getAll(Authentication authentication) {
        return paymentService.getAllForCaller(authentication.getName(), isAdmin(authentication));
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