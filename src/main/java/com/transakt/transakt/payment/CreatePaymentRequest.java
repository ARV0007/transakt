package com.transakt.transakt.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class CreatePaymentRequest {

    @NotBlank(message = "merchantId is required")
    private String merchantId;

    @NotNull(message = "amountPaise is required")
    @Positive(message = "amountPaise must be greater than zero")
    private Long amountPaise;

    @NotBlank(message = "currency is required")
    private String currency;

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public Long getAmountPaise() {
        return amountPaise;
    }

    public void setAmountPaise(Long amountPaise) {
        this.amountPaise = amountPaise;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}