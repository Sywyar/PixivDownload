package top.sywyar.pixivdownload.download.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.sywyar.pixivdownload.download.DownloadService;
import top.sywyar.pixivdownload.download.DownloadStatus;
import top.sywyar.pixivdownload.download.db.ArtworkRecord;
import top.sywyar.pixivdownload.download.db.PixivDatabase;
import top.sywyar.pixivdownload.download.request.DownloadRequest;
import top.sywyar.pixivdownload.download.response.*;
import top.sywyar.pixivdownload.quota.MultiModeConfig;
import top.sywyar.pixivdownload.quota.UserQuotaService;
import top.sywyar.pixivdownload.setup.SetupService;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
@Slf4j
public class DownloadController {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final DownloadService downloadService;
    private final SetupService setupService;
    private final UserQuotaService userQuotaService;
    private final MultiModeConfig multiModeConfig;
    private final PixivDatabase pixivDatabase;

    public DownloadController(DownloadService downloadService,
                              SetupService setupService,
                              UserQuotaService userQuotaService,
                              MultiModeConfig multiModeConfig,
                              PixivDatabase pixivDatabase) {
        this.downloadService = downloadService;
        this.setupService = setupService;
        this.userQuotaService = userQuotaService;
        this.multiModeConfig = multiModeConfig;
        this.pixivDatabase = pixivDatabase;
    }

    @PostMapping("/download/pixiv")
    public ResponseEntity<?> downloadPixivImages(
            @Valid @RequestBody DownloadRequest request,
            HttpServletRequest httpRequest) {
        // SSRF 防护：同步校验所有下载 URL，非法 URL 抛出 SecurityException（由全局处理器返回 400）
        if (request.getOther().isUgoira() && request.getOther().getUgoiraZipUrl() != null) {
            DownloadService.validatePixivUrl(request.getOther().getUgoiraZipUrl());
        } else {
            for (String url : request.getImageUrls()) DownloadService.validatePixivUrl(url);
        }

        // 多人模式：never-delete/timed-delete 模式下，已下载过的作品直接返回成功，不消耗配额
        if ("multi".equals(setupService.getMode())) {
            String pdMode = multiModeConfig.getPostDownloadMode();
            if ("never-delete".equals(pdMode) || "timed-delete".equals(pdMode)) {
                if (pixivDatabase.hasArtwork(request.getArtworkId())) {
                    return ResponseEntity.ok(new AlreadyDownloadedResponse(true, true, "作品已下载，无需重复下载"));
                }
            }
        }

        String userUuid = null;

        // 多人模式且配额启用时，检查下载配额
        if ("multi".equals(setupService.getMode()) && multiModeConfig.getQuota().isEnabled()) {
            userUuid = extractUserUuid(httpRequest);
            UserQuotaService.QuotaCheckResult check = userQuotaService.checkAndReserve(userUuid);

            if (!check.allowed()) {
                // 配额不足：触发打包，返回 429
                String archiveToken = userQuotaService.triggerArchive(userUuid);
                return ResponseEntity.status(429).body(new QuotaExceededResponse(
                        true,
                        "已达到下载限额，请下载已打包的文件后等待配额重置",
                        archiveToken,
                        (long) multiModeConfig.getQuota().getArchiveExpireMinutes() * 60,
                        check.artworksUsed(),
                        check.maxArtworks(),
                        check.resetSeconds()
                ));
            }
        }

        // 异步处理下载任务
        downloadService.downloadImages(
                request.getArtworkId(),
                request.getTitle(),
                request.getImageUrls(),
                request.getReferer(),
                request.getOther(),
                request.getCookie(),
                userUuid
        );

        return ResponseEntity.ok(new DownloadResponse(
                true,
                "下载任务已开始处理",
                "正在下载到作品 " + request.getArtworkId() + " 文件夹",
                request.getImageUrls().size()
        ));
    }

    /** 提取用户 UUID：优先 cookie，其次 X-User-UUID 请求头，最后基于 IP+UA 生成 */
    private String extractUserUuid(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if ("pixiv_user_id".equals(c.getName()) && c.getValue() != null
                        && !c.getValue().isBlank()) {
                    return c.getValue();
                }
            }
        }
        String headerUuid = req.getHeader("X-User-UUID");
        if (headerUuid != null && !headerUuid.isBlank() && UUID_PATTERN.matcher(headerUuid).matches()) return headerUuid;
        return UserQuotaService.generateUuidFromFingerprint(
                req.getRemoteAddr(), req.getHeader("User-Agent"));
    }

    @GetMapping("/download/status")
    public ResponseEntity<DownloadResponse> getStatus() {
        return ResponseEntity.ok(new DownloadResponse(true, "服务运行正常"));
    }

    //获取作品下载状态
    @GetMapping("/download/status/{artworkId}")
    public ResponseEntity<DownloadStatusResponse> getDownloadStatus(@PathVariable Long artworkId) {
        DownloadStatus status = downloadService.getDownloadStatus(artworkId);
        if (status == null) {
            return ResponseEntity.ok(new DownloadStatusResponse(false, "未找到该作品的下载状态", artworkId, null));
        }

        log.info("artworkId: {},totalImages: {},downloadedCount: {}", artworkId, status.getTotalImages(), status.getDownloadedCount());
        return ResponseEntity.ok(new DownloadStatusResponse(
                true,
                status.getStatusDescription(),
                artworkId,
                status.getTitle(),
                status.getTotalImages(),
                status.getDownloadedCount(),
                status.getCurrentImageIndex(),
                status.isCompleted(),
                status.isFailed(),
                status.isCancelled(),
                status.getProgressPercentage(),
                status.getDownloadPath()
        ));
    }

    @GetMapping("download/status/active")
    public ResponseEntity<List<Long>> getActiveDownload() {
        return ResponseEntity.ok(downloadService.getDownloadStatus());
    }

    //取消下载
    @PostMapping("/cancel/{artworkId}")
    public ResponseEntity<DownloadResponse> cancelDownload(@PathVariable Long artworkId) {
        downloadService.cancelDownload(artworkId);
        return ResponseEntity.ok(new DownloadResponse(true, "下载任务已取消"));
    }

    @GetMapping("/downloaded/{artworkId}")
    public ResponseEntity<DownloadedResponse> getDownloaded(@PathVariable Long artworkId) {
        DownloadedResponse downloadedResponse = getArtWorkDownloadedResponse(artworkId);
        if (downloadedResponse == null) {
            return ResponseEntity.status(400).build();
        }
        return ResponseEntity.ok(downloadedResponse);
    }

    @PostMapping("/downloaded/batch")
    public ResponseEntity<List<DownloadedResponse>> getBatchArtworks(@RequestBody List<Long> artworkIds) {
        List<DownloadedResponse> downloadedResponses = new LinkedList<>();
        for (Long artworkId : artworkIds) {
            ArtworkRecord artwork = downloadService.getDownloadedRecord(artworkId);
            if (artwork == null) {
                continue;
            }
            downloadedResponses.add(new DownloadedResponse.DownloadedResponseBuilder()
                    .setArtworkId(artwork.artworkId())
                    .setTitle(artwork.title())
                    .setFolder(artwork.folder())
                    .setCount(artwork.count())
                    .setExtensions(artwork.extensions())
                    .setTime(artwork.time())
                    .setMoved(artwork.moved())
                    .setMoveFolder(artwork.moveFolder())
                    .setMoveTime(artwork.moveTime())
                    .setR18(artwork.isR18())
                    .build());
        }
        return ResponseEntity.ok(downloadedResponses);
    }

    @GetMapping("/downloaded/statistics")
    public ResponseEntity<StatisticsResponse> getStatistics() {
        StatisticsResponse response = downloadService.getStatistics();
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/downloaded/by-move-folder")
    public ResponseEntity<ArtworkIdResponse> getArtworkByMoveFolder(@RequestParam String path) {
        ArtworkRecord artwork = pixivDatabase.getArtworkByMoveFolder(path);
        if (artwork == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new ArtworkIdResponse(artwork.artworkId()));
    }

    @PostMapping("/downloaded/move/{artworkId}")
    public ResponseEntity<DownloadResponse> moveArtWork(
            @PathVariable Long artworkId,
            @RequestBody Map<String, Object> requestBody) {
        String movePath = ((String) requestBody.get("movePath")).replaceAll("[/\\\\]+$", "");
        Long moveTime = Long.valueOf(requestBody.get("moveTime").toString());
        downloadService.moveArtWork(artworkId, movePath, moveTime);
        return ResponseEntity.ok(new DownloadResponse(true, "已尝试记录移动操作"));
    }

    @GetMapping("/downloaded/history")
    public ResponseEntity<List<String>> getHistory() {
        return ResponseEntity.ok(downloadService.getDownloadedRecord());
    }

    @GetMapping("/downloaded/history/paged")
    public ResponseEntity<PagedHistoryResponse> getPagedHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<DownloadedResponse> downloadedResponses = new LinkedList<>();
        long totalElements = downloadService.getArtworkCount();
        List<Long> artWorkIds = downloadService.getSortTimeArtworkPaged(page, size);

        for (Long artworkId : artWorkIds) {
            DownloadedResponse response = getArtWorkDownloadedResponse(artworkId);
            if (response != null) {
                downloadedResponses.add(response);
            }
        }

        int totalPages = (int) Math.ceil((double) totalElements / size);
        return ResponseEntity.ok(new PagedHistoryResponse(downloadedResponses, totalElements, page, size, totalPages));
    }

    @GetMapping("/downloaded/thumbnail/{artworkId}/{page}")
    public ResponseEntity<ImageResponse> getThumbnail(
            @PathVariable Long artworkId,
            @PathVariable int page) {
        ImageResponse image;
        if ((image = downloadService.getImageResponse(artworkId, page, true)) != null) {
            return ResponseEntity.ok(image);
        } else {
            return ResponseEntity.status(404).build();
        }
    }

    @GetMapping("/downloaded/rawfile/{artworkId}/{page}")
    public ResponseEntity<byte[]> getRawFile(
            @PathVariable Long artworkId,
            @PathVariable int page) throws Exception {
        File file = downloadService.getImageFile(artworkId, page);
        if (file == null) return ResponseEntity.notFound().build();

        byte[] bytes = Files.readAllBytes(file.toPath());
        String name = file.getName().toLowerCase();
        MediaType mediaType;
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            mediaType = MediaType.IMAGE_JPEG;
        } else if (name.endsWith(".gif")) {
            mediaType = MediaType.IMAGE_GIF;
        } else if (name.endsWith(".webp")) {
            mediaType = MediaType.parseMediaType("image/webp");
        } else {
            mediaType = MediaType.IMAGE_PNG;
        }

        return ResponseEntity.ok().contentType(mediaType).body(bytes);
    }

    @GetMapping("/downloaded/image/{artworkId}/{page}")
    public ResponseEntity<ImageResponse> getImage(
            @PathVariable Long artworkId,
            @PathVariable int page) {
        ImageResponse image;
        if ((image = downloadService.getImageResponse(artworkId, page, false)) != null) {
            return ResponseEntity.ok(image);
        } else {
            return ResponseEntity.status(404).build();
        }
    }

    public DownloadedResponse getArtWorkDownloadedResponse(Long artworkId) {
        ArtworkRecord artwork = downloadService.getDownloadedRecord(artworkId);
        if (artwork == null) {
            return null;
        }
        return new DownloadedResponse.DownloadedResponseBuilder()
                .setArtworkId(artwork.artworkId())
                .setTitle(artwork.title())
                .setFolder(artwork.folder())
                .setCount(artwork.count())
                .setExtensions(artwork.extensions())
                .setTime(artwork.time())
                .setMoved(artwork.moved())
                .setMoveFolder(artwork.moveFolder())
                .setMoveTime(artwork.moveTime())
                .setR18(artwork.isR18())
                .build();
    }
}
