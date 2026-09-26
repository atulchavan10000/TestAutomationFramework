package orders.model;

import java.math.BigDecimal;

/** Price snapshot captured by order-service when stock is reserved. */
public record OrderItemResponse(long productId, int quantity, BigDecimal unitPrice) {}
