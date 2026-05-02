package top.sywyar.pixivdownload.maintenance.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import top.sywyar.pixivdownload.maintenance.MaintenanceContext;
import top.sywyar.pixivdownload.maintenance.MaintenanceTask;
import top.sywyar.pixivdownload.setup.guest.GuestInviteService;

/**
 * 维护任务：清理过期/吊销的访客邀请，并裁剪 30 天以前的访问统计桶。
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class GuestInviteCleanupTask implements MaintenanceTask {

    private final GuestInviteService guestInviteService;

    @Override
    public String name() {
        return "guest-invite-cleanup";
    }

    @Override
    public void execute(MaintenanceContext context) {
        long now = System.currentTimeMillis();
        int purged = guestInviteService.purgeExpiredAndRevoked(now);
        int trimmed = guestInviteService.trimOldAccessStats();
        log.info("Guest invite cleanup: {} expired/revoked invite(s) purged, {} stale access bucket(s) trimmed",
                purged, trimmed);
    }
}
