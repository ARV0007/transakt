package com.transakt.transakt.payment;

import com.transakt.transakt.ledger.LedgerEntry;
import jakarta.validation.Valid;
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
    public Payment create(@Valid @RequestBody CreatePaymentRequest request) {
        Payment payment = new Payment();
        payment.setMerchantId(request.getMerchantId());
        payment.setAmountPaise(request.getAmountPaise());
        payment.setCurrency(request.getCurrency());
        return paymentService.create(payment);
    }

    @GetMapping("/{id}")
    public Payment getById(@PathVariable String id) {
        return paymentService.getById(id);
    }

    @GetMapping("/{id}/ledger")
    public List<LedgerEntry> getLedger(@PathVariable String id) {
        return paymentService.getLedgerForPayment(id);
    }
}
