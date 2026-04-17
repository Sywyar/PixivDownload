package top.sywyar.pixivdownload.quota;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.sywyar.pixivdownload.common.UuidUtils;
import top.sywyar.pixivdownload.download.db.ArtworkRecord;
import top.sywyar.pixivdownload.download.db.PixivDatabase;
import top.sywyar.pixivdownload.download.response.ErrorResponse;
import top.sywyar.pixivdownload.quota.request.AdminPackRequest;
import top.sywyar.pixivdownload.quota.response.ArchiveStatusResponse;
import top.sywyar.pixivdownload.quota.response.PackRateLimitResponse;
import top.sywyar.pixivdownload.quota.response.QuotaInitResponse;
import top.sywyar.pixivdownload.quota.response.TriggerPackResponse;
import top.sywyar.pixivdownload.setup.SetupService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
@Slf4j
@RequiredArgsConstructor
public class ArchiveController {

    private final UserQuotaService userQuotaService;
    private final MultiModeConfig multiModeConfig;
    private final SetupService setupService;
    private final PixivDatabase pixivDatabase;

    /**
     * 初始化配额会话：返回当前用户的 UUID 和配额状态。
     * 若用户没有 UUID cookie，则自动分配并写入 cookie。
     */
    @PostMapping("/api/quota/init")
    public ResponseEntity<QuotaInitResponse> initQuota(HttpServletRequest request) {
        if (setupService.isAdminLoggedIn(request)) {
            return ResponseEntity.ok(new QuotaInitResponse(false, true, null, null, null, null, null));
        }

        if (!"multi".equals(setupService.getMode())
                || !multiModeConfig.getQuota().isEnabled()) {
            return ResponseEntity.ok(new QuotaInitResponse(false, false, null, null, null, null, null));
        }

        String existingUuid = UuidUtils.extractExistingUuid(request);
        String uuid = existingUuid != null ? existingUuid : UuidUtils.extractOrGenerateUuid(request);
        UserQuotaService.QuotaStatusResult status = userQuotaService.getQuotaStatus(uuid);

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok();
        if (existingUuid == null) {
            responseBuilder.header(HttpHeaders.SET_COOKIE, buildUuidCookie(uuid).toString());
        }

        return responseBuilder.body(new QuotaInitResponse(
                true,
                false,
                uuid,
                status.artworksUsed(),
                status.maxArtworks(),
                status.resetSeconds(),
                status.archive()
        ));
    }

    /**
     * 手动触发打包：队列全部完成后由前端调用，将当前用户已下载的文件打包。
     * 仅多人模式可用；必须提供有效 UUID（pixiv_user_id cookie 或 X-User-UUID 请求头）。
     * 在 archiveExpireMinutes 窗口内最多触发 maxArtworks 次。
     * 若用户无已记录的文件夹（如首次调用或已清空），返回 204。
     */
    @PostMapping("/api/quota/pack")
    public ResponseEntity<?> triggerPack(HttpServletRequest request) {

        if (!"multi".equals(setupService.getMode())
                || !multiModeConfig.getQuota().isEnabled()) {
            return ResponseEntity.status(403).body(new ErrorResponse("multi-mode quota not enabled"));
        }

        // UUID 必须已存在，不自动生成
        String uuid = UuidUtils.extractExistingUuid(request);
        if (uuid == null) {
            return ResponseEntity.status(401).body(new ErrorResponse("missing user UUID"));
        }

        // 频率限制：archiveExpireMinutes 窗口内最多 maxArtworks 次
        if (!userQuotaService.checkAndReservePack(uuid)) {
            int max = multiModeConfig.getQuota().getMaxArtworks();
            int windowMin = multiModeConfig.getQuota().getArchiveExpireMinutes();
            return ResponseEntity.status(429).body(new PackRateLimitResponse(
                    "pack rate limit exceeded", max, windowMin));
        }

        UserQuotaService.UserQuota quota = userQuotaService.getQuotaForUser(uuid);

        if (quota == null || quota.getDownloadedFolders().isEmpty()) {
            return ResponseEntity.status(204).build();
        }

        String token = userQuotaService.triggerArchive(uuid);
        long expireSeconds = (long) multiModeConfig.getQuota().getArchiveExpireMinutes() * 60;
        return ResponseEntity.ok(new TriggerPackResponse(token, expireSeconds));
    }

    @PostMapping("/api/archive/pack-artworks")
    public ResponseEntity<?> triggerAdminPack(@RequestBody AdminPackRequest request,
                                              HttpServletRequest httpRequest) {
        if (!setupService.isAdminLoggedIn(httpRequest)) {
            return ResponseEntity.status(401).body(new ErrorResponse("Unauthorized"));
        }

        if (request == null || request.getArtworkIds() == null || request.getArtworkIds().isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("no artworks to pack"));
        }

        Set<Path> uniqueFolders = new LinkedHashSet<>();
        for (Long artworkId : request.getArtworkIds()) {
            if (artworkId == null) {
                continue;
            }
            ArtworkRecord artwork = pixivDatabase.getArtwork(artworkId);
            if (artwork == null) {
                log.info("管理员打包跳过不存在的作品记录: artworkId={}", artworkId);
                continue;
            }

            String folderString = resolveArtworkFolder(artwork);
            if (folderString == null || folderString.isBlank()) {
                log.info("管理员打包跳过缺少文件夹的作品记录: artworkId={}", artworkId);
                continue;
            }

            Path folder = Path.of(folderString);
            if (!Files.isDirectory(folder)) {
                log.info("管理员打包跳过不存在的文件夹: artworkId={}, folder={}", artworkId, folder);
                continue;
            }
            uniqueFolders.add(folder);
        }

        if (uniqueFolders.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        List<Path> folders = new ArrayList<>(uniqueFolders);
        String token = userQuotaService.triggerAdminArchive(folders);
        long expireSeconds = (long) multiModeConfig.getQuota().getArchiveExpireMinutes() * 60;
        return ResponseEntity.ok(new TriggerPackResponse(token, expireSeconds));
    }

    /**
     * 查询压缩包状态。
     */
    @GetMapping("/api/archive/status/{token}")
    public ResponseEntity<ArchiveStatusResponse> archiveStatus(@PathVariable String token) {
        UserQuotaService.ArchiveEntry entry = userQuotaService.getArchive(token);
        if (entry == null) {
            return ResponseEntity.ok(new ArchiveStatusResponse(null, "expired", null));
        }
        long now = System.currentTimeMillis();
        if (now > entry.getExpireTime()) {
            userQuotaService.deleteArchive(token);
            return ResponseEntity.ok(new ArchiveStatusResponse(null, "expired", null));
        }
        return ResponseEntity.ok(new ArchiveStatusResponse(
                token,
                entry.getStatus(),
                (entry.getExpireTime() - now) / 1000
        ));
    }

    /**
     * 下载压缩包文件。
     */
    @GetMapping("/api/archive/download/{token}")
    public ResponseEntity<?> downloadArchive(@PathVariable String token) {
        UserQuotaService.ArchiveEntry entry = userQuotaService.getArchive(token);
        if (entry == null || System.currentTimeMillis() > entry.getExpireTime()) {
            return ResponseEntity.status(410).body(new ErrorResponse("压缩包已过期或不存在"));
        }
        if (!"ready".equals(entry.getStatus())) {
            return ResponseEntity.status(202).body(new ErrorResponse("压缩包正在准备中，请稍后再试"));
        }
        if (entry.getArchivePath() == null || !entry.getArchivePath().toFile().exists()) {
            if ("empty".equals(entry.getStatus())) {
                return ResponseEntity.status(204).build();
            }
            return ResponseEntity.status(404).body(new ErrorResponse("压缩包文件不存在"));
        }

        String filename = "pixiv_download_" + token.substring(0, 8) + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(entry.getArchivePath()));
    }

    // ---- Cookie 工具 ---------------------------------------------------------------

    private ResponseCookie buildUuidCookie(String uuid) {
        return ResponseCookie.from("pixiv_user_id", uuid)
                .path("/")
                .maxAge(Duration.ofDays(30))
                .sameSite("Strict")
                .httpOnly(true)
                .build();
    }

    private String resolveArtworkFolder(ArtworkRecord artwork) {
        if (artwork.moved() && artwork.moveFolder() != null && !artwork.moveFolder().isBlank()) {
            return artwork.moveFolder();
        }
        return artwork.folder();
    }
}
