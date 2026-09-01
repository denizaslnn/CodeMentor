package com.codementor.aiservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class LocalizedMessageResolver {

    private static final String DEFAULT_LANGUAGE = "tr";
    private static final String ENGLISH = "en";

    private final MessageSource messageSource;

    public String resolve(String messageKey, Object... args) {
        return resolve(messageKey, LocaleContextHolder.getLocale(), args);
    }

    public String resolve(String messageKey, Locale locale, Object... args) {
        try {
            return messageSource.getMessage(messageKey, args, locale);
        } catch (Exception ignored) {
            log.warn("Message key could not be resolved: key={}, locale={}", messageKey, locale);
            return messageKey;
        }
    }
}
