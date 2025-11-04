package com.comp5348.bank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request payload for creating payment transactions via the Bank API.
 *
 * Supports Purchase and Refund operations invoked by the store service.
 */
public record PaymentTransactionRequest(
        @NotNull UUID orderId,
        Double amount,
        @NotBlank String type,
        @NotBlank @Size(max = 255) String idempotencyKey) {
}
