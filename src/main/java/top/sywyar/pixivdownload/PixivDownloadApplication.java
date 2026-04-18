package top.sywyar.pixivdownload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import top.sywyar.pixivdownload.config.RuntimeFiles;

import java.nio.file.Path;

@SpringBootApplication
@EnableScheduling
public class PixivDownloadApplication {

    public static void main(String[] args) {
        Path configPath = RuntimeFiles.resolveConfigYamlPath();
        String rootFolder = RuntimeFiles.readDownloadRootFromConfig(configPath, RuntimeFiles.DEFAULT_DOWNLOAD_ROOT);
        RuntimeFiles.prepareRuntimeFiles(rootFolder);
        SpringApplication.run(PixivDownloadApplication.class, args);
    }

}
