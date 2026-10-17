# Authentication architecture

Authentication is infrastructure for ordinary tests. A test calls `orders.create(...)` or
`payments.pay(...)`; it does not obtain or pass a bearer token. Authentication-specific and
negative tests can use the explicit request override.

## Resolution order

The framework selects exactly one effective profile in this order:

1. Request override: `PROFILE(name)` or `DISABLED`.
2. Profile declared inside the API operation.
3. Default profile of the selected service.
4. No authentication.

`INHERIT` is the normal request state. It is different from `DISABLED`: inheritance permits an
operation or service default, while disabled deliberately sends no authentication. The framework
does not infer profiles from paths and does not try another profile after a 401 response.

## Configuration model

The consumer YAML has two independent concepts:

- A **token provider** obtains a bearer token. Built-in providers are username/password login with
  refresh, OAuth 2.0 client credentials, and a static token.
- An **authentication profile** modifies an outgoing request. Built-in profiles attach a bearer
  token, attach an API key in a header or query parameter, or apply every member of a composite.

JWT is treated as an opaque bearer-token format, not as a separate acquisition scheme. A composite
profile means all members are required; it is not an automatic choice or fallback mechanism.

The loader rejects unknown fields, missing provider/profile references, duplicate composite
members, composite cycles, invalid credential placement, and unknown service defaults before the
suite starts.

## Credentials

YAML contains logical references, never secret values. `EnvironmentCredentialSource` maps a
reference such as `checkout-user-password` to `TAF_SECRET_CHECKOUT_USER_PASSWORD`.

The sample catalog requires:

```text
TAF_SECRET_CHECKOUT_USER_PASSWORD
TAF_SECRET_OAUTH_CLIENT_SECRET
TAF_SECRET_TEST_API_KEY
```

Supply their values through the local process environment or Jenkins Credentials Binding. Concrete
credential values are intentionally absent from documentation and unavailable through Java system
properties, TestNG parameters, or YAML.

`CredentialSource` is the extension boundary for a later cloud secret store. A Vault, AWS Secrets
Manager, Azure Key Vault, or company-specific adapter can replace the environment implementation
without changing profiles or token providers.

## Token lifecycle and concurrency

`SuiteConfigListener` creates one `AuthenticationRuntime` per suite. Each named token-provider
configuration creates one provider instance and one cache. Different named identities therefore
do not accidentally share tokens.

`CachingAccessTokenProvider` stores the access token, optional refresh token, and an early refresh
instant. It uses a bounded safety margin before expiry and a lock with a second cache check, so a
parallel expiry event causes one acquisition/refresh rather than one request per test thread.
Password login uses its refresh token. OAuth client credentials obtains a new token because that
grant has no refresh token. A static token is returned unchanged.

Tokens are not stored in `TestContext`: they are provider state shared safely across the suite.
The framework does not decode JWT claims, refresh in response to every 401, replay failed requests,
or combine token refresh with general HTTP retry policy.

## Pipeline position and overrides

`AuthenticationInterceptor` runs before request logging. Logging can therefore describe the final
request while the existing redactor masks Authorization, API keys, passwords, and client secrets.
An explicitly supplied credential header is preserved, which permits invalid-token tests.

Examples:

```java
// Normal call: inherits order-service's user-jwt profile.
orders.create(request);

// Negative test: suppresses the service default.
RequestOptions noAuth = RequestOptions.builder()
        .disableAuthentication()
        .build();

// Auth-specific test: selects another named profile.
RequestOptions oauth = RequestOptions.builder()
        .authenticationProfile("service-oauth")
        .build();
```

`AuthenticationScenarioApi` shows the preferred operation-level form: each method owns its stable
profile name while the test only chooses the business operation.
