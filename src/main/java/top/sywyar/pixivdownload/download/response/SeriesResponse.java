package top.sywyar.pixivdownload.download.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class SeriesResponse {
    private final SeriesMeta series;
    private final List<SeriesItem> items;
    private final int page;
    private final boolean isLastPage;

    @Getter
    @AllArgsConstructor
    public static class SeriesMeta {
        private final long seriesId;
        private final String title;
        private final Long authorId;
        private final String authorName;
        private final int total;
    }

    @Getter
    @AllArgsConstructor
    public static class SeriesItem {
        private final String id;
        private final String title;
        private final int illustType;
        @JsonProperty("xRestrict")
        private final int xRestrict;
        @JsonProperty("aiType")
        private final int aiType;
        private final String thumbnailUrl;
        private final int pageCount;
        private final String userId;
        private final String userName;
        private final int seriesOrder;
    }
}
