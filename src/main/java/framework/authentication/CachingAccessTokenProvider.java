package framework.authentication;

import framework.context.TestContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Suite-scoped token cache with a single refresh or acquisition in flight per
 * named provider. A volatile fast path keeps normal reads lock-free, while a
 * lock and second cache check prevent parallel tests from refreshing the same
 * identity more than once.
 */
public final class CachingAccessTokenProvider implements AccessTokenProvider {
    private final TokenAcquirer acquirer;
    private final Clock clock;
    private final ReentrantLock refreshLock = new ReentrantLock();
    private volatile CachedToken cached;

    /** Creates a cache using the UTC system clock. */
    public CachingAccessTokenProvider(TokenAcquirer acquirer) { this(acquirer, Clock.systemUTC()); }

    /** Package-private clock injection keeps expiry behavior deterministic in tests. */
    CachingAccessTokenProvider(TokenAcquirer acquirer, Clock clock) {
        this.acquirer = Objects.requireNonNull(acquirer, "Token acquirer must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    @Override public String accessToken(TestContext context) {
        Objects.requireNonNull(context, "Test context must not be null");

        // Most calls return here without locking while the cached grant remains usable.
        CachedToken current = cached;
        if (usable(current)) return current.grant.accessToken();

        refreshLock.lock();
        try {
            // Another thread may have populated the cache while this thread waited.
            current = cached;
            if (usable(current)) return current.grant.accessToken();

            // Refresh only when both the grant and protocol support it. OAuth
            // client credentials, for example, acquires a completely new grant.
            TokenGrant grant = current != null && current.grant.optionalRefreshToken().isPresent() && acquirer.supportsRefresh()
                    ? acquirer.refresh(current.grant.optionalRefreshToken().orElseThrow(), context)
                    : acquirer.acquire(context);
            cached = cache(grant);
            return grant.accessToken();
        } finally {
            refreshLock.unlock();
        }
    }

    /** Returns true only before the deliberately early refresh instant. */
    private boolean usable(CachedToken token) { return token != null && clock.instant().isBefore(token.refreshAt); }

    /** Calculates and stores the effective refresh time for a newly acquired grant. */
    private CachedToken cache(TokenGrant grant) {
        Instant issuedAt = clock.instant();

        // Refresh at 10% of the lifetime before expiry, bounded to 1-30 seconds.
        // This reduces the chance of a token expiring during network transit.
        Duration margin = grant.expiresIn().dividedBy(10);
        if (margin.compareTo(Duration.ofSeconds(1)) < 0) margin = Duration.ofSeconds(1);
        if (margin.compareTo(Duration.ofSeconds(30)) > 0) margin = Duration.ofSeconds(30);
        Instant expiresAt = issuedAt.plus(grant.expiresIn());
        Instant refreshAt = expiresAt.minus(margin);
        // Very short lifetimes can make the bounded margin consume the whole
        // lifetime. In that case, use the midpoint rather than an immediately
        // stale cache entry.
        if (!refreshAt.isAfter(issuedAt)) refreshAt = issuedAt.plus(grant.expiresIn().dividedBy(2));
        return new CachedToken(grant, refreshAt);
    }
    /** Internal pairing of the original grant and its calculated refresh boundary. */
    private record CachedToken(TokenGrant grant, Instant refreshAt) {}
}
