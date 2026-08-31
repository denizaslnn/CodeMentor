package com.codementor.apigateway.security;

import org.springframework.http.HttpHeaders;

/**
 * Centralized HTTP security headers applied to EVERY response leaving the gateway.
 * <p>
 * Applied centrally on the success path by
 * {@link com.codementor.apigateway.filter.SecurityHeadersWebFilter} and on
 * 401/403 responses by {@link com.codementor.apigateway.filter.UnauthorizedResponseWriter},
 * so the security posture is consistent regardless of the outcome.
 * <p>
 * Rationale per header:
 * <ul>
 *   <li>HSTS - forces HTTPS once the site is reachable over TLS (no downgrade);</li>
 *   <li>X-Frame-Options: DENY - clickjacking defense;</li>
 *   <li>X-Content-Type-Options: nosniff - MIME sniffing defense;</li>
 *   <li>Referrer-Policy - limits referrer leakage;</li>
 *   <li>CSP frame-ancestors 'none' - defense in depth for framing.</li>
 * </ul>
 */
public final class SecurityHeaders {

    static final String HSTS = "max-age=31536000; includeSubDomains";
    static final String FRAME_OPTIONS = "DENY";
    static final String CONTENT_TYPE_OPTIONS = "nosniff";
    static final String REFERRER_POLICY = "strict-origin-when-cross-origin";
    static final String CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'";

    private SecurityHeaders() {
    }

    public static void apply(HttpHeaders headers) {
        headers.add("Strict-Transport-Security", HSTS);
        headers.add("X-Frame-Options", FRAME_OPTIONS);
        headers.add("X-Content-Type-Options", CONTENT_TYPE_OPTIONS);
        headers.add("Referrer-Policy", REFERRER_POLICY);
        headers.add("Content-Security-Policy", CSP);
    }
}
