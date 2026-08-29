package com.transakt.transakt.bank;

import java.util.UUID;

public interface BankClient {

    BankResult authorize(String paymentId, long amountPaise, String currency);
    BankResult lookup(String paymentId);
}