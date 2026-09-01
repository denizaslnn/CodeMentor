package com.codementor.apigateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class LocalizedMessageResolver {

    private static final String DEFAULT_LANGUAGE = "tr";
    private static final String ENGLISH = "en";

    private final MessageSource messageSource;

    public String resolve(String messageKey, ServerWebExchange exchange, Object... args) {
        Locale locale = resolveLocale(exchange);
        return resolve(messageKey, locale, args);
    }

    public String resolve(String messageKey, Locale locale, Object... args) {
        try {
            return messageSource.getMessage(messageKey, args, locale);
        } catch (Exception ignored) {
            log.warn("Message key could not be resolved: key={}, locale={}", messageKey, locale);
            return messageKey;
        }
    }

    private Locale resolveLocale(ServerWebExchange exchange) {
        List<Locale> locales = exchange.getRequest().getHeaders().getAcceptLanguageAsLocales();
        if (locales.isEmpty()) {
            return Locale.forLanguageTag(DEFAULT_LANGUAGE);
        }
        return locales.get(0);
    }
}
