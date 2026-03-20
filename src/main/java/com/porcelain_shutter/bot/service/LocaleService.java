package com.porcelain_shutter.bot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class LocaleService {

    @Value("${telegram.bot.localization.tag:uk-UA}")
    private String localeTag;

    private final MessageSource messageSource;

    public String get(String key) {
        return messageSource.getMessage(key, null, Locale.forLanguageTag(localeTag));
    }

    public String fmt(String key, Object... args) {
        return String.format(get(key), args);
    }

    public List<String> pool(String key) {
        return Arrays.stream(get(key).split("\\|\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public String pick(String key) {
        List<String> values = pool(key);
        return values.get(new Random().nextInt(values.size()));
    }

    public String indexedPoolPick(String key, long seed) {
        List<String> values = pool(key);
        int idx = (int) (seed % values.size());
        return values.get(idx);
    }
}
