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
 *   <li>CSP frame-ancestors 'none' - defense in depth for framing;</li>
 *   <li>the OpenAPI docs paths get a relaxed, same-origin CSP ({@link #DOCS_CSP})
 *       so Swagger UI can load its own assets.</li>
 * </ul>
 */
public final class SecurityHeaders {

    static final String HSTS = "max-age=31536000; includeSubDomains";
    static final String FRAME_OPTIONS = "DENY";
    static final String CONTENT_TYPE_OPTIONS = "nosniff";
    static final String REFERRER_POLICY = "strict-origin-when-cross-origin";
    static final String CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'";
    /**
     * CSP for the OpenAPI documentation paths only. Swagger UI is a real web page: it
     * loads its own bundle, stylesheets, favicons and fetches the api-docs JSON, so
     * {@code default-src 'none'} blanks it out. Everything stays same-origin ('self');
     * API traffic keeps the strict {@link #CSP} above.
     */
    static final String DOCS_CSP = "default-src 'none'; "
            + "script-src 'self' 'unsafe-inline'; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; "
            + "font-src 'self' data:; "
            + "connect-src 'self'; "
            + "frame-ancestors 'none'; base-uri 'self'";

    private SecurityHeaders() {
    }

    /** Strict header set for API traffic. */
    public static void apply(HttpHeaders headers) {
        apply(headers, CSP);
    }

    /** Same headers, but with the Swagger-UI-compatible CSP ({@link #DOCS_CSP}). */
    public static void applyForDocs(HttpHeaders headers) {
        apply(headers, DOCS_CSP);
    }

    /**
     * True for the OpenAPI documentation paths served through the gateway
     * (see the {@code code-service-docs} route).
     */
    public static boolean isDocsPath(String path) {
        return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }

    private static void apply(HttpHeaders headers, String csp) {
        headers.add("Strict-Transport-Security", HSTS);
        headers.add("X-Frame-Options", FRAME_OPTIONS);
        headers.add("X-Content-Type-Options", CONTENT_TYPE_OPTIONS);
        headers.add("Referrer-Policy", REFERRER_POLICY);
        headers.add("Content-Security-Policy", csp);
    }
}
