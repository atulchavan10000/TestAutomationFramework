package support;

import framework.client.RequestOptions;

import java.util.UUID;

/** Small stateless helpers shared by consumer tests. */
public final class ScenarioData {
    private ScenarioData() {}

    public static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    public static RequestOptions bearer(String token) {
        return RequestOptions.builder()
                .header("Authorization", "Bearer " + token)
                .build();
    }
}
