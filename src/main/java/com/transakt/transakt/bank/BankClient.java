package com.transakt.transakt.bank;

public interface BankClient {

    BankResult authorize(String paymentId, long amountPaise, String currency);
}