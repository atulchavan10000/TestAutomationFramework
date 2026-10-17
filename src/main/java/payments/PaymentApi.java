package payments;

import framework.client.ApiClient;
import framework.client.ApiRequest;
import framework.client.ApiResponse;
import framework.client.RequestOptions;
import framework.http.HttpMethod;
import payments.model.PaymentRequest;
import payments.model.PaymentResponse;

import java.util.Objects;

/** Consumer API object for idempotent authenticated payments. */
public final class PaymentApi {
    private final ApiClient client;

    public PaymentApi(ApiClient client) {
        this.client = Objects.requireNonNull(client, "API client must not be null");
    }

    public ApiResponse<PaymentResponse> pay(
            PaymentRequest request,
            String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key must not be blank");
        }
        RequestOptions options = RequestOptions.builder()
                .header("Idempotency-Key", idempotencyKey)
                .build();
        return pay(request, options);
    }

    /** Explicit option path used when a test must override authentication itself. */
    public ApiResponse<PaymentResponse> pay(PaymentRequest request, RequestOptions options) {
        return client.execute(new ApiRequest(
                HttpMethod.POST, "/api/v1/payments", request, options), PaymentResponse.class);
    }
}
