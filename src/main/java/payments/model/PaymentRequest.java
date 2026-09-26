package payments.model;

import java.math.BigDecimal;

/** Exact amount and currency expected by the pending order. */
public record PaymentRequest(
        long orderId,
        BigDecimal amount,
        String currency,
        String method) {}
