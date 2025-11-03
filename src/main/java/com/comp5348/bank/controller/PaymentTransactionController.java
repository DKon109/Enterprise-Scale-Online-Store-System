package com.comp5348.bank.controller;

<<<<<<< HEAD
import com.comp5348.bank.dto.PaymentTransactionDTO;
import com.comp5348.bank.service.PaymentTransactionService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transactions")
=======
import com.comp5348.bank.service.PaymentTransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
>>>>>>> b152dbe (Added basic structure of paymentTransaction class. Included springboot application dependencies in gradle build file.)
public class PaymentTransactionController {
    private final PaymentTransactionService paymentTransactionService;

    @Autowired
    public PaymentTransactionController(PaymentTransactionService paymentTransactionService) {
        this.paymentTransactionService = paymentTransactionService;
    }
<<<<<<< HEAD

    @GetMapping("/{transactionID}")
    public ResponseEntity<PaymentTransactionDTO> getPaymentTransaction(@PathVariable Long transactionID) {
        PaymentTransactionDTO paymentTransaction = paymentTransactionService.getPaymentTransaction(transactionID);
        return ResponseEntity.ok(paymentTransaction);
    }
=======
>>>>>>> b152dbe (Added basic structure of paymentTransaction class. Included springboot application dependencies in gradle build file.)
}
