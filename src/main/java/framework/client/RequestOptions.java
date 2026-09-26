package framework.client;

import framework.http.HttpHeaders;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable caller-supplied options for one API operation.
 *
 * Example:
 * RequestOptions options = RequestOptions.builder()
 *     .header("Accept", "application/json")
 *     .pathParam("teamId", "42")
 *     .queryParam("tag", "active")
 *     .addQueryParam("tag", "verified")
 *     .timeout(Duration.ofSeconds(10))
 *     .build();
 *
 * ApiRequest carries these options. ApiClient coordinates resolving path/query
 * values, merging defaults, and selecting an effective timeout before producing
 * HttpRequest. This class only stores options; it makes no network calls.
 *
 * No method, operation path, body, response type, or expected status belongs here.
 * These are initial proposed fields, not the entire future options API.
 */
public final class RequestOptions {

    private final HttpHeaders headers;
    private final Map<String, List<String>> queryParameters;
    private final Map<String, String> pathParameters;

    // null internally means the caller did not override the configured timeout.
    // That is different from choosing zero or an unlimited timeout.
    private final Duration timeout;

    /** Takes independent immutable snapshots of the builder's mutable collections. */
    private RequestOptions(Builder builder) {
        this.headers = builder.headers.build();

        Map<String, List<String>> queryCopy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : builder.queryParameters.entrySet()) {
            queryCopy.put(entry.getKey(), Collections.unmodifiableList(
                    new ArrayList<>(entry.getValue())));
        }
        this.queryParameters = Collections.unmodifiableMap(queryCopy);

        // String keys and values are immutable, so only the map needs copying.
        this.pathParameters = Collections.unmodifiableMap(
                new LinkedHashMap<>(builder.pathParameters));

        // Duration is immutable; sharing it is safe.
        this.timeout = builder.timeout;
    }

    /** Creates a fresh builder. Keep each builder local to its caller. */
    public static Builder builder() {
        return new Builder();
    }

    /** Represents a call with no explicit overrides; defaults still apply later. */
    public static RequestOptions empty() {
        return builder().build();
    }

    /** Returns only caller-supplied headers, not the eventual merged header set. */
    public HttpHeaders headers() {
        return headers;
    }

    /**
     * Returns immutable query names and ordered values per name.
     * Several values can represent tag=active&tag=verified. Names retain their
     * casing; unlike HTTP header names, we do not normalize parameter names.
     * Names and values are unencoded; URI preparation must encode them later.
     */
    public Map<String, List<String>> queryParameters() {
        return queryParameters;
    }

    /**
     * Returns immutable placeholder values, e.g. teamId -> "42" for {teamId}.
     * Keys omit the braces. Values are unencoded; preparation must safely encode
     * each value as path data rather than inserting it verbatim into the URL.
     */
    public Map<String, String> pathParameters() {
        return pathParameters;
    }

    /** Returns an explicit timeout override, or empty so preparation can use a default. */
    public Optional<Duration> timeout() {
        return Optional.ofNullable(timeout);
    }

    /** Mutable construction helper. static/final do not make this builder thread-safe. */
    public static final class Builder {

        private final HttpHeaders.Builder headers = HttpHeaders.builder();
        private final Map<String, List<String>> queryParameters = new LinkedHashMap<>();
        private final Map<String, String> pathParameters = new LinkedHashMap<>();
        private Duration timeout;

        private Builder() {}

        /** Replaces all values for a header name, matching names case-insensitively. */
        public Builder header(String name, String value) {
            headers.set(name, value);
            return this;
        }

        /** Appends a header value; retains previously supplied values for that name. */
        public Builder addHeader(String name, String value) {
            headers.add(name, value);
            return this;
        }

        /** Replaces all query values for an exact name with one value. */
        public Builder queryParam(String name, String value) {
            Objects.requireNonNull(name, "Query parameter name must not be null");
            Objects.requireNonNull(value, "Query parameter value must not be null");
            List<String> values = new ArrayList<>();
            values.add(value);
            queryParameters.put(name, values);
            return this;
        }

        /** Adds another query value, e.g. a second tag; never joins values with commas. */
        public Builder addQueryParam(String name, String value) {
            Objects.requireNonNull(name, "Query parameter name must not be null");
            Objects.requireNonNull(value, "Query parameter value must not be null");
            List<String> values = queryParameters.get(name);
            if (values == null) {
                values = new ArrayList<>();
                queryParameters.put(name, values);
            }
            values.add(value);
            return this;
        }

        /** Sets or replaces one named path placeholder's value. */
        public Builder pathParam(String name, String value) {
            Objects.requireNonNull(name, "Path parameter name must not be null");
            Objects.requireNonNull(value, "Path parameter value must not be null");
            pathParameters.put(name, value);
            return this;
        }

        /** Sets a positive timeout override, using HttpRequest's timeout contract. */
        public Builder timeout(Duration timeout) {
            Objects.requireNonNull(timeout, "Timeout must not be null");
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("Timeout must be positive");
            }
            this.timeout = timeout;
            return this;
        }

        /** Creates a snapshot unaffected by later calls on this builder. */
        public RequestOptions build() {
            return new RequestOptions(this);
        }
    }
}
