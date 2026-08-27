package com.codementor.apigateway.controller;

import com.codementor.apigateway.security.JwtUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LOCAL TESTING HELPER ONLY — remove before production.
 * <p>
 * Exposes {@code GET /api/v1/get-token} to generate a real signed JWT with the
 * gateway's {@code jwt.secret}. The endpoint is whitelisted in
 * {@code JwtGlobalFilter} / {@code JwtAuthenticationFilter} so no existing token
 * is required to obtain one.
 * <p>
 * Fetch the token, then call the real flow with:
 * {@code Authorization: Bearer <token>}
 */
@RestController
public class TestTokenController {

    private final JwtUtil jwtUtil;

    public TestTokenController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/api/v1/get-token")
    public String generateToken() {

        return jwtUtil.generateToken("test-user");
    }
}