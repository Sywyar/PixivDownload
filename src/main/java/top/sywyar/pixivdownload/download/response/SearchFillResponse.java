package top.sywyar.pixivdownload.download.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class SearchFillResponse {
    private final List<SearchResponse.SearchItem> items;
    private final int total;
    private final int startPage;
    private final int endPage;
    private final int requestedPages;
    private final int acceptedPages;
    private final int fetchedPages;
    private final int limitPage;
}
