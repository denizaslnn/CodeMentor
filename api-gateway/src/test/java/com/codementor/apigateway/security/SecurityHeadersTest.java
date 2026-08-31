package com.codementor.apigateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.junit.jupiter.api.Assertions.*;

class SecurityHeadersTest {

    @Test
    void apply_setsAllSecurityHeaders() {
        HttpHeaders headers = new HttpHeaders();
        SecurityHeaders.apply(headers);

        assertEquals(SecurityHeaders.HSTS, headers.getFirst("Strict-Transport-Security"));
        assertEquals(SecurityHeaders.FRAME_OPTIONS, headers.getFirst("X-Frame-Options"));
        assertEquals(SecurityHeaders.CONTENT_TYPE_OPTIONS, headers.getFirst("X-Content-Type-Options"));
        assertEquals(SecurityHeaders.REFERRER_POLICY, headers.getFirst("Referrer-Policy"));
        assertEquals(SecurityHeaders.CSP, headers.getFirst("Content-Security-Policy"));
    }

    @Test
    void apply_addsHeadersNotReplaces() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Frame-Options", "SAMEORIGIN");
        SecurityHeaders.apply(headers);

        assertEquals(2, headers.get("X-Frame-Options").size());
    }
}
