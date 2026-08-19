package com.codementor.apigateway.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RateLimiterConfigTest {

    @Test
    void extractSubject_returnsSubClaimFromJwtPayload() {
        String jwt = buildJwt("{\"sub\":\"42\",\"role\":\"dev\"}");

        assertEquals("42", RateLimiterConfig.extractSubject(jwt));
    }

    @Test
    void extractSubject_returnsNull_whenNoSubClaim() {
        String jwt = buildJwt("{\"role\":\"dev\"}");

        assertNull(RateLimiterConfig.extractSubject(jwt));
    }

    @Test
    void extractSubject_returnsNull_whenMalformedJwt() {
        assertNull(RateLimiterConfig.extractSubject("not-a-jwt"));
        assertNull(RateLimiterConfig.extractSubject(""));
        assertNull(RateLimiterConfig.extractSubject("a.b.c.d.e"));
    }

    private static String buildJwt(String payloadJson) {
        String header = base64Url("{\"alg\":\"none\"}");
        String payload = base64Url(payloadJson);
        return header + "." + payload + ".signature";
    }

    private static String base64Url(String raw) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
