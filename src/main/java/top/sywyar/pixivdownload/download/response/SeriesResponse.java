package top.sywyar.pixivdownload.download.response;

import java.util.List;

public record SeriesResponse(
        SeriesMeta series,
        List<SeriesItem> items,
        int page,
        boolean isLastPage
) {
    public record SeriesMeta(
            long seriesId,
            String title,
            Long authorId,
            String authorName,
            int total
    ) {}

    public record SeriesItem(
            String id,
            String title,
            int illustType,
            int xRestrict,
            int aiType,
            String thumbnailUrl,
            int pageCount,
            String userId,
            String userName,
            int seriesOrder
    ) {}
}