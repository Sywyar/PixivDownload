package top.sywyar.pixivdownload.collection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.sywyar.pixivdownload.download.config.DownloadConfig;
import top.sywyar.pixivdownload.i18n.LocalizedException;
import top.sywyar.pixivdownload.i18n.TestI18nBeans;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollectionService 单元测试")
class CollectionServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private CollectionMapper collectionMapper;
    @Mock
    private CollectionIconService iconService;
    @Mock
    private DownloadConfig downloadConfig;

    private CollectionService collectionService;

    @BeforeEach
    void setUp() {
        lenient().when(downloadConfig.getRootFolder()).thenReturn(tempDir.toString());
        collectionService = new CollectionService(
                collectionMapper,
                iconService,
                TestI18nBeans.appMessages(),
                downloadConfig
        );
    }

    @Nested
    @DisplayName("download root")
    class DownloadRootTests {

        @Test
        @DisplayName("应保留 UTF-8 字符并清理模板中的不安全路径分隔符")
        void shouldResolveUtf8TemplateAsSafeRelativePath() {
            when(collectionMapper.findById(7L)).thenReturn(new Collection(
                    7L, "中文😀/unsafe", null, "{collection_name}", 0, 1700000000L, 0
            ));

            Path resolved = collectionService.resolveDownloadRoot(7L, tempDir);

            assertThat(resolved).isEqualTo(tempDir.toAbsolutePath().normalize().resolve("中文😀_unsafe"));
        }

        @Test
        @DisplayName("单斜杠开头的目录应按 download.root-folder 下的相对目录处理")
        void shouldResolveSingleSlashPrefixedPathAsRelativePath() {
            when(collectionMapper.findById(7L)).thenReturn(new Collection(
                    7L, "safe", null, "/test", 0, 1700000000L, 0
            ));

            Path resolved = collectionService.resolveDownloadRoot(7L, tempDir);

            assertThat(resolved).isEqualTo(tempDir.toAbsolutePath().normalize().resolve("test"));
        }

        @Test
        @DisplayName("相对目录不能逃出 download.root-folder")
        void shouldRejectRelativePathEscapingRootFolder() {
            when(collectionMapper.findById(7L)).thenReturn(new Collection(
                    7L, "safe", null, null, 0, 1700000000L, 0
            ));

            assertThatThrownBy(() -> collectionService.updateDownloadRoot(7L, "../outside"))
                    .isInstanceOf(LocalizedException.class);

            verify(collectionMapper, never()).updateDownloadRoot(7L, "../outside");
        }

        @Test
        @DisplayName("绝对目录应原样保存")
        void shouldAcceptAbsoluteDownloadRoot() {
            Path absoluteRoot = tempDir.resolve("absolute-root").toAbsolutePath();
            when(collectionMapper.findById(7L))
                    .thenReturn(new Collection(7L, "safe", null, null, 0, 1700000000L, 0))
                    .thenReturn(new Collection(7L, "safe", null, absoluteRoot.toString(), 0, 1700000000L, 0));

            collectionService.updateDownloadRoot(7L, absoluteRoot.toString());

            verify(collectionMapper).updateDownloadRoot(7L, absoluteRoot.toString());
        }
    }
}
