package orders.model;

/** One product and quantity requested for an order. */
public record OrderItemRequest(long productId, int quantity) {}
