package top.sywyar.pixivdownload.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class WebI18nService {

    private final WebI18nBundleRegistry bundleRegistry;

    public I18nBundleResponse loadBundle(String namespace, Locale locale) {
        String baseName = bundleRegistry.resolveBaseName(namespace);
        if (baseName == null) {
            throw LocalizedException.badRequest(
                    "i18n.namespace.unsupported",
                    "Unsupported i18n namespace: " + namespace,
                    namespace
            );
        }

        Locale effectiveLocale = AppLocale.normalize(locale);
        ResourceBundle bundle = ResourceBundle.getBundle(baseName, effectiveLocale);

        Map<String, String> messages = new LinkedHashMap<>();
        for (String key : new TreeSet<>(bundle.keySet())) {
            messages.put(key, bundle.getString(key));
        }

        return new I18nBundleResponse(
                namespace,
                effectiveLocale.toLanguageTag(),
                AppLocale.DEFAULT_LOCALE.toLanguageTag(),
                messages
        );
    }
}
