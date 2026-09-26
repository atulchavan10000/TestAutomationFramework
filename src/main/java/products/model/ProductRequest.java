package products.model;

import java.math.BigDecimal;

/** Product data used when a test creates isolated stock for its own scenario. */
public record ProductRequest(
        String name,
        String category,
        BigDecimal price,
        String currency,
        int stock,
        double rating,
        String description) {}
