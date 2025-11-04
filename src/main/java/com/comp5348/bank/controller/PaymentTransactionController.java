package com.comp5348.bank.controller;

import com.comp5348.bank.dto.PaymentTransactionDTO;
import com.comp5348.bank.dto.PaymentTransactionRequest;
import com.comp5348.bank.service.PaymentTransactionService;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping
    public ResponseEntity<PaymentTransactionDTO> createTransaction(
            @RequestBody @Valid PaymentTransactionRequest request,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        String type = request.type().trim().toLowerCase(Locale.ROOT);
        PaymentTransactionService.TransactionResult result;

        switch (type) {
            case "purchase" -> result = paymentTransactionService.createPurchaseTransaction(
                    request.orderId(),
                    request.amount(),
                    request.idempotencyKey(),
                    correlationId);
            case "refund" -> result = paymentTransactionService.createRefundTransaction(
                    request.orderId(),
                    request.amount(),
                    request.idempotencyKey(),
                    correlationId);
            default -> throw new IllegalArgumentException("Unsupported transaction type: " + request.type());
        }

        HttpStatus status = result.createdNew() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.transaction());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}
