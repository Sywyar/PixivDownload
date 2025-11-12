package top.sywyar.pixivdownload.download.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class StatisticsResponse {
    private boolean success;
    private int totalArtworks;
    private int totalImages;
    private String message;
}
