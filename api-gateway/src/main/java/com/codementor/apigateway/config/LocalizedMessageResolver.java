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
        // Mesajlar yalnizca messages_tr / messages_en icinde tanimli. Desteklenmeyen
        // bir dil (orn. Accept-Language: de) dogrudan kullanilirsa bundle bulunamayip
        // ham message key donuyordu; bu yuzden yalnizca desteklenen diller kabul
        // edilir, digerleri varsayilan dile duser.
        for (Locale locale : exchange.getRequest().getHeaders().getAcceptLanguageAsLocales()) {
            if (ENGLISH.equals(locale.getLanguage())) {
                return Locale.ENGLISH;
            }
            if (DEFAULT_LANGUAGE.equals(locale.getLanguage())) {
                return Locale.forLanguageTag(DEFAULT_LANGUAGE);
            }
        }
        return Locale.forLanguageTag(DEFAULT_LANGUAGE);
    }
}
