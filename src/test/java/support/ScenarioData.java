package support;

import framework.client.RequestOptions;

import java.util.UUID;

/** Small stateless helpers shared by consumer tests. */
public final class ScenarioData {
    /** Static utility; scenario helpers carry no state. */
    private ScenarioData() {}

    /** Appends a UUID to a readable prefix for parallel-safe scenario data. */
    public static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    /** Creates an explicit bearer override for authentication-specific tests. */
    public static RequestOptions bearer(String token) {
        return RequestOptions.builder()
                .header("Authorization", "Bearer " + token)
                .build();
    }
}
