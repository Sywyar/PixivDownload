package top.sywyar.pixivdownload.download.db;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.sqlite.SQLiteConfig;
import top.sywyar.pixivdownload.config.RuntimeFiles;
import top.sywyar.pixivdownload.download.config.DownloadConfig;
import top.sywyar.pixivdownload.i18n.AppMessages;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DatabaseConfig {

    private final DownloadConfig downloadConfig;
    private final AppMessages messages;

    @Bean
    public DataSource dataSource() throws IOException {
        Path databasePath = RuntimeFiles.resolveDatabasePath(downloadConfig.getRootFolder());
        Files.createDirectories(databasePath.getParent());
        String url = "jdbc:sqlite:" + databasePath;
        log.info(messages.getForLog("download.db.log.path", url));

        // 每条新连接都会携带这些 PRAGMA，确保并发写时等待而不是立即失败
        SQLiteConfig sqliteConfig = new SQLiteConfig();
        sqliteConfig.setBusyTimeout(5000);
        sqliteConfig.setJournalMode(SQLiteConfig.JournalMode.WAL);

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl(url);
        dataSource.setConnectionProperties(sqliteConfig.toProperties());
        return dataSource;
    }
}
