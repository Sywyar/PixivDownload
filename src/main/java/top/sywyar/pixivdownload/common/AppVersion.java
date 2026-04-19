package top.sywyar.pixivdownload.common;

import lombok.experimental.UtilityClass;

import java.io.InputStream;
import java.util.Properties;

/**
 * 应用版本读取工具。
 */
@UtilityClass
public class AppVersion {

    private static final String UNKNOWN_VERSION = "unknown";
    private static final String JPACKAGE_APP_VERSION = "jpackage.app-version";
    private static final String APP_VERSION_PROPERTIES = "/app-version.properties";
    private static final String APP_VERSION_KEY = "app.version";
    private static final String MAVEN_POM_PROPERTIES =
            "/META-INF/maven/top.sywyar.lovepopup/PixivDownload/pom.properties";

    /**
     * 返回展示给用户的应用版本。
     * 优先使用 jpackage 注入的发布版本，其次读取 Maven 编译时写入的版本文件。
     */
    public static String getDisplayVersion() {
        String version = firstNonBlank(
                System.getProperty(JPACKAGE_APP_VERSION),
                readVersionFromProperties(APP_VERSION_PROPERTIES, APP_VERSION_KEY),
                AppVersion.class.getPackage().getImplementationVersion(),
                readVersionFromProperties(MAVEN_POM_PROPERTIES, "version")
        );
        return normalize(version);
    }

    private static String readVersionFromProperties(String resourcePath, String key) {
        try (InputStream stream = AppVersion.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return null;
            }
            Properties properties = new Properties();
            properties.load(stream);
            return properties.getProperty(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String normalize(String version) {
        if (version == null || version.isBlank()) {
            return UNKNOWN_VERSION;
        }

        String normalized = version.trim();
        if (normalized.length() > 1
                && (normalized.charAt(0) == 'v' || normalized.charAt(0) == 'V')
                && Character.isDigit(normalized.charAt(1))) {
            return normalized.substring(1);
        }
        return normalized;
    }
}
