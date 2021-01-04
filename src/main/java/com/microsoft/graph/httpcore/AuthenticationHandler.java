package com.microsoft.graph.httpcore;

import java.io.IOException;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;

import com.microsoft.graph.httpcore.middlewareoption.MiddlewareType;
import com.microsoft.graph.httpcore.middlewareoption.TelemetryOptions;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Interceptor responsible for injecting the token in the request headers
 */
public class AuthenticationHandler implements Interceptor {

    /**
     * The current middleware type
     */
    public final MiddlewareType MIDDLEWARE_TYPE = MiddlewareType.AUTHENTICATION;

    private ICoreAuthenticationProvider authProvider;

    /**
     * Initialize a the handler with a authentication provider
     * @param authProvider the authentication provider to use
     */
    public AuthenticationHandler(@Nonnull final ICoreAuthenticationProvider authProvider) {
        this.authProvider = authProvider;
    }

    @Override
    @Nullable
    public Response intercept(@Nonnull final Chain chain) throws IOException {
        Request originalRequest = chain.request();

        if(originalRequest.tag(TelemetryOptions.class) == null)
            originalRequest = originalRequest.newBuilder().tag(TelemetryOptions.class, new TelemetryOptions()).build();
        originalRequest.tag(TelemetryOptions.class).setFeatureUsage(TelemetryOptions.AUTH_HANDLER_ENABLED_FLAG);

        Request authenticatedRequest = authProvider.authenticateRequest(originalRequest);
        return chain.proceed(authenticatedRequest);
    }

}
