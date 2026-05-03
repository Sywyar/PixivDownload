package top.sywyar.pixivdownload.series;

public record MangaSeriesDetail(
        long seriesId,
        String title,
        Long authorId,
        String authorName,
        long artworkCount,
        Long updatedTime
) {}
