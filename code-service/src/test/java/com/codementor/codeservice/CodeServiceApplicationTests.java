package com.codementor.codeservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Context load test. A real (base64, >=256-bit) secret is supplied so that
 * {@code JwtTokenProvider}'s fail-fast strength check passes; production
 * always supplies this via the {@code JWT_SECRET} environment variable.
 */
@SpringBootTest(properties = "jwt.secret=a2V5MTIzNDU2Nzg5MTIzNDU2Nzg5MTIzNDU2Nzg5MTI=")
class CodeServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
