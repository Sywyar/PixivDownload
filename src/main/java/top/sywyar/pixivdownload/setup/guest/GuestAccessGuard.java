package top.sywyar.pixivdownload.setup.guest;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import top.sywyar.pixivdownload.download.db.ArtworkRecord;
import top.sywyar.pixivdownload.download.db.PixivDatabase;
import top.sywyar.pixivdownload.download.db.TagDto;
import top.sywyar.pixivdownload.i18n.LocalizedException;

import java.util.List;

/**
 * 访客邀请的越界守卫。在单作品访问端点入口处调用 {@link #requireVisible(HttpServletRequest, long)}，
 * 越界（年龄分级超出 / 标签作者均不在白名单）抛 403。
 *
 * <p>请求未携带 GuestInviteSession 时直接放行（管理员或非访客访问不受此守卫影响）。
 */
@Component
@RequiredArgsConstructor
public class GuestAccessGuard {

    private final PixivDatabase pixivDatabase;

    /**
     * 抽出当前请求挂载的访客邀请会话；可能为 {@code null}。
     */
    public static GuestInviteSession extractSession(HttpServletRequest request) {
        if (request == null) return null;
        Object attr = request.getAttribute(GuestInviteSession.REQUEST_ATTR);
        return attr instanceof GuestInviteSession s ? s : null;
    }

    /**
     * 若当前请求是访客身份，校验作品是否在其可见范围内；越界抛 403。
     */
    public void requireVisible(HttpServletRequest request, long artworkId) {
        GuestInviteSession session = extractSession(request);
        if (session == null) return; // 非访客身份直接放行
        if (!isVisibleToGuest(artworkId, session)) {
            throw new LocalizedException(HttpStatus.FORBIDDEN,
                    "guest.invite.forbidden",
                    "该作品不在你的可见范围内");
        }
    }

    /**
     * 单作品可见性判定：
     * 1) 年龄分级必须在允许集合内；
     * 2) OR 语义白名单：作品任一标签命中 {@code tagIds} 或作者在 {@code authorIds} 即可见。
     *    某维度 {@code unrestricted=true} 视为无限制。
     */
    public boolean isVisibleToGuest(long artworkId, GuestInviteSession session) {
        ArtworkRecord rec = pixivDatabase.getArtwork(artworkId);
        if (rec == null) return false;
        if (!matchesAgeRating(rec.xRestrict(), session)) return false;
        return matchesWhitelist(rec, session);
    }

    private boolean matchesAgeRating(Integer xRestrict, GuestInviteSession session) {
        int rating = xRestrict == null ? 0 : xRestrict;
        return switch (rating) {
            case 0 -> session.allowSfw();
            case 1 -> session.allowR18();
            case 2 -> session.allowR18g();
            default -> session.allowR18(); // 兼容未知值，按 R18 处理
        };
    }

    private boolean matchesWhitelist(ArtworkRecord rec, GuestInviteSession session) {
        boolean tagPass = session.tagUnrestricted() || hasTagHit(rec.artworkId(), session.tagIds());
        if (tagPass) return true;
        return session.authorUnrestricted()
                || (rec.authorId() != null && session.authorIds().contains(rec.authorId()));
    }

    private boolean hasTagHit(long artworkId, java.util.Set<Long> whitelist) {
        if (whitelist == null || whitelist.isEmpty()) return false;
        List<TagDto> tags = pixivDatabase.getArtworkTags(artworkId);
        if (tags == null) return false;
        for (TagDto tag : tags) {
            if (tag.getTagId() != null && whitelist.contains(tag.getTagId())) return true;
        }
        return false;
    }
}
