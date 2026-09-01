package com.codementor.apigateway.filter;

import com.codementor.apigateway.config.LocalizedMessageResolver;
// Spring Boot 4 -> Jackson 3: databind paketi tools.jackson.databind, ve
// auto-configure edilen ObjectMapper bean'i de bu tiptedir.
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Filtre testleri icin gercek bir {@link UnauthorizedResponseWriter} kurar.
 * Mesajlar src/main/resources'taki bundle'dan cozulur; eksik key testi
 * patlatmasin diye key'in kendisi default mesaj olarak kullanilir.
 */
final class TestResponseWriter {

    private TestResponseWriter() {
    }

    static UnauthorizedResponseWriter create() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setUseCodeAsDefaultMessage(true);
        return new UnauthorizedResponseWriter(new ObjectMapper(), new LocalizedMessageResolver(messageSource));
    }
}
