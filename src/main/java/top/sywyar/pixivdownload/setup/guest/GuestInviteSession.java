package top.sywyar.pixivdownload.setup.guest;

import java.util.Set;

/**
 * 一次成功兑换的访客邀请快照，挂在请求上下文中供后续过滤/守卫使用。
 *
 * <p>白名单维度采用 OR 语义：作品任一标签命中 {@code tagIds} <b>或</b> 作者命中 {@code authorIds} 即可见。
 * {@code tagUnrestricted}/{@code authorUnrestricted} 为 {@code true} 表示该维度不受限制
 * （此时对应的 ID 集合应当为空，二者皆不限制时白名单条件等价于 {@code 1=1}）。
 */
public record GuestInviteSession(
        long id,
        String code,
        boolean allowSfw,
        boolean allowR18,
        boolean allowR18g,
        boolean tagUnrestricted,
        Set<Long> tagIds,
        boolean authorUnrestricted,
        Set<Long> authorIds) {

    public static final String REQUEST_ATTR = "guestInvite";

    /**
     * 命中（任意成员为 true）即可证明 R-18 维度对该访客有非空允许集合。
     */
    public boolean hasAnyAgeRating() {
        return allowSfw || allowR18 || allowR18g;
    }
}
