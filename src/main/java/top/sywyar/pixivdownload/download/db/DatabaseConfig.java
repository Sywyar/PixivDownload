package top.sywyar.pixivdownload.download.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.sqlite.SQLiteConfig;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
@Configuration
public class DatabaseConfig {

    private final String rootFolder;

    public DatabaseConfig(@Value("${download.root-folder:pixiv-download}") String rootFolder) {
        this.rootFolder = rootFolder;
    }

    @Bean
    public DataSource dataSource() throws IOException {
        Files.createDirectories(Paths.get(rootFolder));
        String url = "jdbc:sqlite:" + rootFolder + "/pixiv_download.db";
        log.info("SQLite 数据库路径: {}", url);

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
