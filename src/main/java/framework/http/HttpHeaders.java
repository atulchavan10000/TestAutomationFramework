package framework.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable collection of HTTP headers.
 *
 * Header names are stored in lowercase for case-insensitive lookup.
 * Values keep their original text and insertion order.
 * Custom header names are supported; there is no enum of allowed names.
 *
 * Example:
 * HttpHeaders headers = HttpHeaders.builder()
 *     .set("Content-Type", "application/json")
 *     .set("X-Custom-Header", "custom-value")
 *     .build();
 *
 * headers.contains("CONTENT-TYPE"); // true
 * headers.firstValue("content-type"); // Optional containing application/json
 *
 * This class manages storage, not header-specific validation or wire encoding.
 */
public final class HttpHeaders {

    // final prevents reassigning this field. It does NOT make a map immutable.
    // The constructor also copies and wraps the map and every nested list.
    private final Map<String, List<String>> entries;

    // Private: callers create instances through builder().build().
    private HttpHeaders(Map<String, List<String>> source) {
        this.entries = immutableDeepCopy(source);
    }

    /** Creates a fresh, mutable builder with no headers. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns an immutable collection with no headers. */
    public static HttpHeaders empty() {
        return builder().build();
    }

    /** Checks whether a header exists, ignoring the casing of its name. */
    public boolean contains(String name) {
        return entries.containsKey(normalize(name));
    }

    /**
     * Returns all stored values for a name, or an empty list if it is absent.
     * The returned list cannot be modified; callers never receive null.
     */
    public List<String> values(String name) {
        return entries.getOrDefault(normalize(name), Collections.emptyList());
    }

    /**
     * Returns the first stored value, or Optional.empty() if the header is absent.
     * Optional represents a value that might not exist, without returning null.
     * Use values() when every value matters; this method does not combine them.
     */
    public Optional<String> firstValue(String name) {
        List<String> headerValues = values(name);
        if (headerValues.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(headerValues.get(0));
    }

    /**
     * Exposes the immutable map for iteration, for example inside an HTTP adapter.
     * Both the map and its nested lists are protected from modification.
     */
    public Map<String, List<String>> asMap() {
        return entries;
    }

    /**
     * Creates an independent builder initialized with these headers.
     * Changes to that builder never change this HttpHeaders instance.
     */
    public Builder toBuilder() {
        return new Builder(entries);
    }

    /**
     * Converts header names to the same lookup form.
     * Locale.ROOT avoids changing behavior with the computer's language settings.
     * Header values are NOT normalized: their casing and whitespace are retained.
     */
    private static String normalize(String name) {
        return Objects.requireNonNull(name, "Header name must not be null")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Copies both collection levels before making them unmodifiable.
     * Wrapping the original map alone would still allow its owner to change it,
     * and would leave its nested lists mutable.
     * Strings themselves are immutable, so their contents need no extra copies.
     */
    private static Map<String, List<String>> immutableDeepCopy(
            Map<String, List<String>> source) {

        Map<String, List<String>> copy = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            List<String> copiedValues = new ArrayList<>(entry.getValue());
            copy.put(entry.getKey(), Collections.unmodifiableList(copiedValues));
        }

        return Collections.unmodifiableMap(copy);
    }

    /**
     * Mutable construction helper. Keep each builder local to one caller/thread.
     * static means it does not require an existing HttpHeaders instance.
     * Returning this from its methods allows calls to be chained.
     */
    public static final class Builder {

        private final Map<String, List<String>> entries;

        private Builder() {
            this.entries = new LinkedHashMap<>();
        }

        // Copy each list so this builder shares no mutable collections with source.
        private Builder(Map<String, List<String>> source) {
            this();
            for (Map.Entry<String, List<String>> entry : source.entrySet()) {
                entries.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }

        /** Replaces all existing values for this name with the supplied value. */
        public Builder set(String name, String value) {
            String key = normalize(name);
            Objects.requireNonNull(value, "Header value must not be null");

            List<String> replacement = new ArrayList<>();
            replacement.add(value);
            entries.put(key, replacement);
            return this;
        }

        /**
         * Appends another value, creating the header if it is absent.
         * This preserves values separately; it does not comma-join them or decide
         * whether multiple values are valid for a particular HTTP header.
         */
        public Builder add(String name, String value) {
            String key = normalize(name);
            Objects.requireNonNull(value, "Header value must not be null");

            // Written explicitly instead of computeIfAbsent for readability.
            List<String> headerValues = entries.get(key);
            if (headerValues == null) {
                headerValues = new ArrayList<>();
                entries.put(key, headerValues);
            }
            headerValues.add(value);
            return this;
        }

        /** Removes the header and all its values; an absent name is a no-op. */
        public Builder remove(String name) {
            entries.remove(normalize(name));
            return this;
        }

        /**
         * Creates an immutable snapshot of the builder's current headers.
         * The builder can be reused: later changes do not affect earlier results.
         */
        public HttpHeaders build() {
            return new HttpHeaders(entries);
        }
    }
}
