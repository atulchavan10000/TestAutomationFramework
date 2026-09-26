package products.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Public product representation returned by product-service. */
public record ProductResponse(
        long id,
        String name,
        String category,
        BigDecimal price,
        String currency,
        int stock,
        double rating,
        String description,
        LocalDateTime createdAt) {}
