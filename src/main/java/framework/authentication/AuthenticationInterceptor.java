package framework.authentication;

import framework.context.TestContext;
import framework.http.HttpRequest;
import framework.http.HttpResponse;
import framework.interceptor.HttpInterceptor;
import framework.interceptor.InterceptorChain;
import java.util.Objects;

/**
 * Resolves and applies authentication before delegating to the remaining HTTP
 * pipeline. The client places this interceptor before logging so diagnostics
 * describe the final request while sensitive values are redacted.
 */
public final class AuthenticationInterceptor implements HttpInterceptor {
    private final AuthenticationRuntime runtime;
    private final AuthenticationSelection selection;
    private final String operationProfile;
    private final String serviceDefaultProfile;
    private final TestContext context;

    /** Captures all inputs required to resolve authentication for one execution. */
    public AuthenticationInterceptor(AuthenticationRuntime runtime, AuthenticationSelection selection,
            String operationProfile, String serviceDefaultProfile, TestContext context) {
        this.runtime = Objects.requireNonNull(runtime);
        this.selection = Objects.requireNonNull(selection);
        this.operationProfile = operationProfile;
        this.serviceDefaultProfile = serviceDefaultProfile;
        this.context = Objects.requireNonNull(context);
    }
    @Override public HttpResponse intercept(HttpRequest request, InterceptorChain chain) {
        HttpRequest authenticated = runtime.authenticate(
                request, selection, operationProfile, serviceDefaultProfile, context);
        return chain.proceed(authenticated);
    }
}
