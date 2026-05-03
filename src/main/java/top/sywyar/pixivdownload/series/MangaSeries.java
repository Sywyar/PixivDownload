package top.sywyar.pixivdownload.series;

public record MangaSeries(
        long seriesId,
        String title,
        Long authorId,
        long updatedTime
) {}
