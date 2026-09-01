package com.codementor.codeservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger tanimi.
 * <p>
 * Endpoint'ler api-gateway (8080) arkasinda calisir; gateway JWT'yi dogrulayip
 * X-User-Id header'ina cevirdigi icin Swagger UI'da manuel test yaparken
 * "Authorize" ile access token verilmesi yeterlidir.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI codeMentorOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CodeMentor code-service API")
                        .version("v1")
                        .description("Auth (/api/v1/auth/**) ve kod analizi (/api/v1/**) endpoint'leri. "
                                + "Auth disindaki tum cagrilar Bearer access token gerektirir."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("api-gateway'in dogruladigi HS256 access token")))
                // Force Swagger "Try it out" traffic to the current host (gateway),
                // not directly to the downstream service port.
                .servers(List.of(new Server()
                        .url("/")
                        .description("API Gateway")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
