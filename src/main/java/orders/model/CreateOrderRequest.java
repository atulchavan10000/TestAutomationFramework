package orders.model;

import java.util.List;

/** The user ID is deliberately absent; order-service derives it from the JWT. */
public record CreateOrderRequest(List<OrderItemRequest> items) {
    public CreateOrderRequest {
        items = List.copyOf(items);
    }
}
