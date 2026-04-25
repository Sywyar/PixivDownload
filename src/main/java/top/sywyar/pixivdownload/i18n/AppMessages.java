package top.sywyar.pixivdownload.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class AppMessages {

    private final MessageSource messageSource;

    public String get(String code, Object... args) {
        return getOrDefault(LocaleContextHolder.getLocale(), code, code, args);
    }

    public String get(Locale locale, String code, Object... args) {
        return getOrDefault(locale, code, code, args);
    }

    public String getOrDefault(String code, String defaultMessage, Object... args) {
        return getOrDefault(LocaleContextHolder.getLocale(), code, defaultMessage, args);
    }

    public String getOrDefault(Locale locale, String code, String defaultMessage, Object... args) {
        return messageSource.getMessage(
                code,
                args,
                defaultMessage,
                AppLocale.normalize(locale)
        );
    }
}
