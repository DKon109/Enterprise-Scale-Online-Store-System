package com.comp5348.bank.mock;

import lombok.Setter;
import org.springframework.stereotype.Service;

@Setter
@Service
public class MockBankService {

    // toggle failure manually (useful for testing)
    private boolean simulateFailure = true;

    public void processPayment(Long orderId, Double amount) {
        if (simulateFailure) {
            System.out.println("[MockBankService] ATTEMPTED A CHARGE_PAYMENT but Bank system is DOWN!");
            throw new RuntimeException("Bank service unavailable");
        }
        System.out.println("[MockBankService] Payment processed for order # " + orderId + " ($" + amount + ")");
    }

    public void processRefund(Long orderId, Double amount) {
        if (simulateFailure) {
            System.out.println("[MockBankService] ATTEMPTED REFUND but Bank system is DOWN!");
            throw new RuntimeException("Bank service unavailable");
        }
        System.out.println("[MockBankService] Refund issued for order # " + orderId + " ($" + amount + ")");
    }
}
