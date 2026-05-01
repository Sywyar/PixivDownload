package top.sywyar.pixivdownload.setup;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import top.sywyar.pixivdownload.quota.MultiModeConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Static resource request rate limit by client IP.
 * Disabled when multi-mode.static-resource-request-limit-minute <= 0.
 */
@Service
@RequiredArgsConstructor
public class StaticResourceRateLimitService {

    private final MultiModeConfig multiModeConfig;

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public boolean isAllowed(String ip) {
        int limit = multiModeConfig.getStaticResourceRequestLimitMinute();
        if (limit <= 0) {
            return true;
        }
        long currentWindow = System.currentTimeMillis() / 60_000L;
        WindowCounter counter = counters.compute(ip, (k, existing) -> {
            if (existing == null || existing.window != currentWindow) {
                return new WindowCounter(currentWindow);
            }
            return existing;
        });
        return counter.count.incrementAndGet() <= limit;
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanupExpiredCounters() {
        long currentWindow = System.currentTimeMillis() / 60_000L;
        counters.entrySet().removeIf(e -> e.getValue().window < currentWindow);
    }

    @RequiredArgsConstructor
    private static class WindowCounter {
        final long window;
        final AtomicInteger count = new AtomicInteger(0);
    }
}
