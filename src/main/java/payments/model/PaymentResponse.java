package payments.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Successful payment evidence returned by payment-service. */
public record PaymentResponse(
        long id,
        long orderId,
        BigDecimal amount,
        String currency,
        String method,
        String status,
        LocalDateTime createdAt) {}
