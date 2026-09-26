package orders.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Order evidence used before and after payment. */
public record OrderResponse(
        long id,
        long userId,
        String status,
        BigDecimal totalAmount,
        String currency,
        Long paymentId,
        LocalDateTime createdAt,
        List<OrderItemResponse> items) {}
