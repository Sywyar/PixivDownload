package top.sywyar.pixivdownload.i18n;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WebI18nBundleRegistry {

    private final Map<String, String> namespaces = new LinkedHashMap<>();

    public WebI18nBundleRegistry() {
        namespaces.put("common", "i18n.web.common");
        namespaces.put("gallery", "i18n.web.gallery");
    }

    public String resolveBaseName(String namespace) {
        return namespaces.get(namespace);
    }

    public List<String> supportedNamespaces() {
        return List.copyOf(namespaces.keySet());
    }
}
