package top.sywyar.pixivdownload.migration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/migration")
@CrossOrigin(origins = "*")
@Slf4j
public class MigrationController {

    @Autowired
    private JsonToSqliteMigration migration;

    /**
     * 触发 JSON → SQLite 迁移。
     * 幂等操作，已迁移的数据不会重复写入。
     */
    @PostMapping("/json-to-sqlite")
    public ResponseEntity<JsonToSqliteMigration.MigrationResult> migrate() {
        JsonToSqliteMigration.MigrationResult result = migration.migrate();
        if (result.success()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.internalServerError().body(result);
        }
    }
}
