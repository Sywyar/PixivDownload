package top.sywyar.pixivdownload.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AppVersion tests")
class AppVersionTest {

    private final String originalJpackageVersion = System.getProperty("jpackage.app-version");

    @AfterEach
    void tearDown() {
        restoreProperty("jpackage.app-version", originalJpackageVersion);
    }

    @Test
    @DisplayName("should prefer jpackage release version")
    void shouldPreferJpackageReleaseVersion() {
        System.setProperty("jpackage.app-version", "1.2.3");

        assertThat(AppVersion.getDisplayVersion()).isEqualTo("1.2.3");
    }

    @Test
    @DisplayName("should read version from Maven filtered resource")
    void shouldReadVersionFromMavenFilteredResource() throws Exception {
        System.clearProperty("jpackage.app-version");

        Properties properties = new Properties();
        try (InputStream stream = AppVersionTest.class.getResourceAsStream("/app-version.properties")) {
            properties.load(stream);
        }

        assertThat(properties.getProperty("app.version"))
                .isEqualTo(AppVersion.getDisplayVersion())
                .doesNotContain("@");
    }

    @Test
    @DisplayName("should normalize leading v prefix")
    void shouldNormalizeLeadingVPrefix() {
        System.setProperty("jpackage.app-version", "v2.0.1");

        assertThat(AppVersion.getDisplayVersion()).isEqualTo("2.0.1");
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }
}
