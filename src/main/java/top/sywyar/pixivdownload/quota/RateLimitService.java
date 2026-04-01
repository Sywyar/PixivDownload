package top.sywyar.pixivdownload.quota;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多人模式下的请求速率限制服务。
 * 使用分钟级滑动窗口，追踪每个用户 UUID 在当前分钟内的请求总数。
 * 当 multi-mode.request-limit-minute <= 0 时，速率限制禁用，所有请求直接放行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final MultiModeConfig multiModeConfig;

    /** UUID → 当前分钟窗口计数器 */
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    /**
     * 检查该 UUID 在当前分钟窗口内是否允许继续发起请求。
     * 若允许则原子地将计数加一并返回 true；若已达上限则返回 false。
     */
    public boolean isAllowed(String uuid) {
        int limit = multiModeConfig.getRequestLimitMinute();
        if (limit <= 0) {
            return true;
        }
        long currentWindow = System.currentTimeMillis() / 60_000L;
        WindowCounter counter = counters.compute(uuid, (k, existing) -> {
            if (existing == null || existing.window != currentWindow) {
                return new WindowCounter(currentWindow);
            }
            return existing;
        });
        return counter.count.incrementAndGet() <= limit;
    }

    /** 每分钟清理已过期的分钟窗口计数器，防止内存泄漏。 */
    @Scheduled(fixedDelay = 60_000)
    public void cleanupExpiredCounters() {
        long currentWindow = System.currentTimeMillis() / 60_000L;
        int removed = 0;
        var iter = counters.entrySet().iterator();
        while (iter.hasNext()) {
            if (iter.next().getValue().window < currentWindow) {
                iter.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("速率限制：清理 {} 个过期计数器", removed);
        }
    }

    @RequiredArgsConstructor
    private static class WindowCounter {
        final long window;
        final AtomicInteger count = new AtomicInteger(0);
    }
}
