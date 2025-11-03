package com.comp5348.bank.controller;

import com.comp5348.bank.dto.PaymentTransactionDTO;
import com.comp5348.bank.service.PaymentTransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transactions")
public class PaymentTransactionController {
    private final PaymentTransactionService paymentTransactionService;

    @Autowired
    public PaymentTransactionController(PaymentTransactionService paymentTransactionService) {
        this.paymentTransactionService = paymentTransactionService;
    }

    @GetMapping("/{transactionID}")
    public ResponseEntity<PaymentTransactionDTO> getPaymentTransaction(@PathVariable Long transactionID) {
        PaymentTransactionDTO paymentTransaction = paymentTransactionService.getPaymentTransaction(transactionID);
        return ResponseEntity.ok(paymentTransaction);
    }
}
