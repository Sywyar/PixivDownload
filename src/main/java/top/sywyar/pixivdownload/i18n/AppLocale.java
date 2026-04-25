package top.sywyar.pixivdownload.i18n;

import java.util.List;
import java.util.Locale;

public final class AppLocale {

    public static final String LANGUAGE_COOKIE_NAME = "pixiv_lang";
    public static final String LANGUAGE_PARAM_NAME = "lang";
    public static final Locale DEFAULT_LOCALE = Locale.SIMPLIFIED_CHINESE;
    public static final List<Locale> SUPPORTED_LOCALES = List.of(
            Locale.SIMPLIFIED_CHINESE,
            Locale.US
    );

    private AppLocale() {
    }

    public static Locale parse(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }

        Locale locale = Locale.forLanguageTag(candidate.trim().replace('_', '-'));
        if (locale.getLanguage().isBlank()) {
            return null;
        }
        return matchSupported(locale);
    }

    public static Locale normalize(Locale locale) {
        Locale matched = matchSupported(locale);
        return matched != null ? matched : DEFAULT_LOCALE;
    }

    public static Locale resolveAcceptLanguage(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return DEFAULT_LOCALE;
        }

        try {
            Locale matched = Locale.lookup(Locale.LanguageRange.parse(headerValue), SUPPORTED_LOCALES);
            return matched != null ? matched : DEFAULT_LOCALE;
        } catch (IllegalArgumentException ignored) {
            return DEFAULT_LOCALE;
        }
    }

    public static Locale matchSupported(Locale locale) {
        if (locale == null || locale.getLanguage().isBlank()) {
            return null;
        }

        for (Locale supportedLocale : SUPPORTED_LOCALES) {
            if (supportedLocale.toLanguageTag().equalsIgnoreCase(locale.toLanguageTag())) {
                return supportedLocale;
            }
        }

        for (Locale supportedLocale : SUPPORTED_LOCALES) {
            if (supportedLocale.getLanguage().equalsIgnoreCase(locale.getLanguage())) {
                return supportedLocale;
            }
        }

        return null;
    }

    public static String displayName(Locale targetLocale, Locale currentLocale) {
        Locale normalizedCurrentLocale = normalize(currentLocale);
        boolean englishUi = Locale.ENGLISH.getLanguage().equals(normalizedCurrentLocale.getLanguage());

        return switch (targetLocale.toLanguageTag()) {
            case "zh-CN" -> englishUi ? "Simplified Chinese" : "简体中文";
            case "en-US" -> "English";
            default -> targetLocale.toLanguageTag();
        };
    }
}
