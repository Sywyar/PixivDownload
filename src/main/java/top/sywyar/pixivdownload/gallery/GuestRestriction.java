package top.sywyar.pixivdownload.gallery;

import top.sywyar.pixivdownload.setup.guest.GuestInviteSession;

import java.util.List;
import java.util.Set;

/**
 * 画廊查询/单作品访问的访客限制条件，从 {@link GuestInviteSession} 派生。
 *
 * <p>OR 语义：作品的任一标签命中 {@code tagIds} <b>或</b> 作者命中 {@code authorIds} 即可见。
 * {@code tagUnrestricted}/{@code authorUnrestricted} 为 {@code true} 表示该维度无限制。
 *
 * @param allowedXRestricts 允许的 R-18 维度值集合（0 = SFW，1 = R-18，2 = R-18G）
 */
public record GuestRestriction(
        Set<Integer> allowedXRestricts,
        boolean tagUnrestricted,
        List<Long> tagIds,
        boolean authorUnrestricted,
        List<Long> authorIds) {

    public static GuestRestriction from(GuestInviteSession s) {
        if (s == null) return null;
        java.util.Set<Integer> allowed = new java.util.LinkedHashSet<>();
        if (s.allowSfw()) allowed.add(0);
        if (s.allowR18()) allowed.add(1);
        if (s.allowR18g()) allowed.add(2);
        return new GuestRestriction(
                Set.copyOf(allowed),
                s.tagUnrestricted(),
                List.copyOf(s.tagIds()),
                s.authorUnrestricted(),
                List.copyOf(s.authorIds()));
    }

    public boolean fullyOpen() {
        return tagUnrestricted && authorUnrestricted;
    }
}
