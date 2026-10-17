## 1. Multiple authentication schemes

This video explains the authentication architecture in the Test Automation Framework. The goal is to understand how password login, OAuth client credentials, static bearer tokens, API keys, and composite authentication fit into one execution model. We will stay at a practical architecture level. The examples come from the current framework implementation, and the emphasis is on the responsibilities of each class, the runtime flow, and what a test author actually writes.

## 2. Two responsibilities

The design starts by separating two responsibilities that are often combined. A token provider answers where an access token comes from. It might call a username and password login endpoint, perform an OAuth client credentials grant, or return a token supplied by an external secret store. A request authenticator answers how credentials modify an outgoing HTTP request. A bearer authenticator adds the Authorization header. An API key authenticator adds a header or query parameter. This separation lets several acquisition protocols feed the same bearer mechanism. It also keeps caching and refresh logic independent from request construction.

## 3. Configured catalogs

The YAML contains two authentication catalogs. Token providers describe acquisition. Profiles describe request authentication. The checkout-user provider names the auth service, username, logical password reference, login path, and refresh path. The user-jwt profile points to that provider and declares bearer authentication. Services can then name a default profile. Secrets never appear in YAML. A logical reference such as checkout-user-password becomes the environment variable TAF_SECRET_CHECKOUT_USER_PASSWORD. ConfigLoader converts this data into immutable records and validates references before any test runs.

## 4. Suite and test lifecycles

At suite startup, SuiteConfigListener loads the selected environment, validates the complete catalog, constructs the authentication runtime, and stores both objects as TestNG suite attributes. AuthenticationRuntimeFactory builds one runtime provider for every named token provider, then builds every named request profile. Token caches are suite scoped, so parallel tests using checkout-user share one cache. A TestContext remains test scoped and carries correlation information, not tokens. BaseApiTest retrieves the shared runtime and creates an ApiClient for a named service and the current test context. This lifecycle avoids repeated login while keeping mutable test state isolated.

## 5. Password login and bearer

For the ordinary user flow, order-service declares user-jwt as its default profile. When a test calls orders.create, the authentication runtime selects user-jwt. BearerAuthenticator asks the checkout-user access token provider for a token. On the first request, the caching provider invokes the password-login TokenAcquirer. That strategy creates an unauthenticated client for auth-service, posts LoginRequest to the configured login path, maps LoginResponse, and converts it into a framework TokenGrant. The bearer authenticator then adds Authorization colon Bearer plus the token. The test never requests or passes that token.

## 6. Token cache and refresh

CachingAccessTokenProvider keeps the token lifecycle out of both tests and request authenticators. A normal request reads the volatile cached value and returns immediately when the token is still usable. A stale or missing value enters a lock. The cache checks again after acquiring the lock because another test thread may already have refreshed it. This produces single-flight behavior: many parallel requests cause one acquisition or refresh. The provider refreshes early, using ten percent of the token lifetime with a safety margin bounded between one and thirty seconds. Password login can use its refresh token. OAuth client credentials has no refresh token, so it performs a new acquisition.

## 7. OAuth client credentials

The automation-client provider represents a machine identity. Its public client ID and requested scopes remain in YAML, while the client secret comes from the credential source. AuthApi sends the token request with HTTP Basic credentials and a form body containing the client credentials grant and scope. OAuthTokenResponse becomes a TokenGrant with no refresh token. The service-oauth profile uses the same BearerAuthenticator used by the password flow. The only change is the AccessTokenProvider behind it. Near expiry, the shared cache calls acquire again rather than refresh. An API operation can own the service-oauth profile so normal tests only call the operation.

## 8. Static bearer tokens

StaticAccessTokenProvider supports environments where another system already issues the token. Configuration stores only a token credential reference. Suite startup resolves the value and constructs a provider that returns it unchanged. The same BearerAuthenticator attaches it to requests. This provider has no expiry calculation, refresh call, or JWT decoding. Secret rotation becomes visible only when a new suite runtime is constructed. Static tokens are useful for controlled external integrations, but a renewable provider is safer for long suites when the authentication service is available. The sample catalog supports this type, although it does not currently declare a static provider.

## 9. API key authentication

ApiKeyAuthenticator receives the resolved key, its placement, and the configured parameter name. Header placement can produce X-API-Key. Query placement can append an encoded api_key parameter without rebuilding unrelated query values. In both cases, an explicit value supplied by the caller wins. That rule is important for negative tests because an intentionally invalid key must not be replaced by the valid configured key. The API key is a request credential, so it does not need an AccessTokenProvider or token cache. The current test-api-key profile reads TAF_SECRET_TEST_API_KEY and attaches it as an X-API-Key header.

## 10. Composite authentication

CompositeAuthenticator applies every member in declaration order. The configured user-jwt-and-api-key profile first applies the bearer authenticator and then the API key authenticator. Each member receives the immutable request copy returned by the previous member, so credentials accumulate on one outgoing request. Composite means all requirements, not fallback. The framework does not send one request with bearer and retry with an API key. ConfigLoader verifies member names, rejects duplicate members, and performs cycle detection. The runtime factory then recursively materializes the already validated graph and reuses authenticators that were built earlier.

## 11. Profile selection

AuthenticationRuntime resolves one effective profile with deterministic precedence. An explicit request profile wins. Otherwise the operation profile wins. Otherwise the service default applies. If none exists, the request has no authentication. DISABLED is different from ordinary inheritance. It deliberately suppresses operation and service defaults, which enables missing-authentication tests. The framework does not infer profiles from paths and never tries another profile after a rejected response. Stable policy normally belongs to the API operation or service configuration. Request-level selection should remain an exception for authentication-specific tests.

## 12. Request pipeline

ApiClient prepares the neutral HttpRequest before executing interceptors. When an AuthenticationRuntime exists, it prepends AuthenticationInterceptor for that request. The interceptor passes the request selection, operation profile, service default, and TestContext to AuthenticationRuntime, then forwards the authenticated request. Common headers and correlation follow, and logging runs after those mutations so diagnostics describe the final request. Sensitive values must therefore be redacted. The transport remains unaware of profiles and token providers. Token endpoint clients deliberately omit AuthenticationRuntime, preventing an infinite cycle in which obtaining a token would itself require a token.

## 13. Test author experience

The intended test experience is intentionally boring. A normal order test creates a context, creates OrderApi for order-service, calls create, and asserts the response. The service default supplies user-jwt without token plumbing in the test. Negative tests retain explicit control. disableAuthentication sends no framework credentials. Supplying an Authorization header preserves an invalid token because BearerAuthenticator does not overwrite caller input. The same principle applies to API keys. Tests own expected status codes and business assertions, while authentication infrastructure owns secret access, acquisition, caching, refresh, and request modification.

## 14. Patterns and boundaries

The framework uses Strategy for token providers, token acquirers, and request authenticators. AuthenticationRuntimeFactory acts as the composition root. AuthenticationRuntime is a named registry with deterministic policy. CompositeAuthenticator implements the Composite pattern, and AuthenticationInterceptor joins the HTTP chain of responsibility. The design follows single responsibility and dependency inversion. It also sets conservative boundaries. Tokens remain opaque. A four-oh-one does not trigger automatic refresh or replay. The runtime does not guess a scheme from the URL. Those choices prevent hidden retries, accidental duplicate side effects, and authentication defects that appear to pass.

## 15. Current verification gaps

The existing tests cover direct login and refresh behavior, service-default JWT bearer calls, authenticated checkout, missing authentication, and invalid bearer tokens. The repository also configures OAuth, API key, and composite profiles and provides AuthenticationScenarioApi methods for them. However, the current TestNG suite does not appear to invoke those scenario methods. Static token support is implemented but not configured. Focused unit tests for cache timing and single-flight concurrency are also missing. The architecture is coherent, but verification should be expanded around those paths before treating every scheme as proven.

## 16. Working mental model

Keep four words in mind. Provider means where a token comes from. Profile means how credentials modify a request. Runtime means which profile applies. Interceptor means where authentication joins execution. Password login, OAuth, and static tokens all supply bearer authentication. API keys modify requests directly. Composite profiles apply several authenticators to one request. Configuration defines stable policy, suite startup constructs shared runtime state, and ordinary tests express business intent. With that model, each class has a clear place and adding a future authentication mechanism becomes a deliberate extension rather than another conditional inside ApiClient.
