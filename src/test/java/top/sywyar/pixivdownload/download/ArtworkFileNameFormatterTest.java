package top.sywyar.pixivdownload.download;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ArtworkFileNameFormatter")
class ArtworkFileNameFormatterTest {

    @Test
    @DisplayName("应展开变量并合规化标题和作者名")
    void shouldFormatVariablesAndSanitizeNameParts() {
        List<String> names = ArtworkFileNameFormatter.formatAll(
                "{artwork_id}-{artwork_title}-{author_id}-{author_name}-{timestamp}-{page}-{count}-{ai+}-{R18+}",
                12345L,
                "A/B:C*D?",
                777L,
                "CON",
                1700000000L,
                2,
                true,
                2
        );

        assertThat(names).containsExactly(
                "12345-A_B_C_D_-777-_CON-1700000000-0-2-AI-R18G",
                "12345-A_B_C_D_-777-_CON-1700000000-1-2-AI-R18G"
        );
    }

    @Test
    @DisplayName("重复文件名应自动追加页码")
    void shouldMakeDuplicateNamesUnique() {
        List<String> names = ArtworkFileNameFormatter.formatAll(
                "{artwork_title}",
                12345L,
                "Same",
                null,
                null,
                1700000000L,
                3,
                false,
                0
        );

        assertThat(names).containsExactly("Same", "Same_p1", "Same_p2");
    }
}
