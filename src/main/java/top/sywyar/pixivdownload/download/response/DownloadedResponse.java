package top.sywyar.pixivdownload.download.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DownloadedResponse {
    private final Long artworkId;
    private final String title;
    private final String folder;
    private final int count;
    private final String extensions;
    private final Long time;
    private final boolean moved;
    private final String moveFolder;
    private final Long moveTime;
    @JsonProperty("R18")
    private final Boolean isR18;

    public static class DownloadedResponseBuilder {
        private Long artworkId;
        private String title;
        private String folder;
        private int count = 0;
        private String extensions;
        private Long time;
        private boolean moved = false;
        private String moveFolder;
        private Long moveTime;
        private Boolean isR18 = null;

        public DownloadedResponseBuilder setArtworkId(Long artworkId) {
            this.artworkId = artworkId;
            return this;
        }

        public DownloadedResponseBuilder setTitle(String title) {
            this.title = title;
            return this;
        }

        public DownloadedResponseBuilder setFolder(String folder) {
            this.folder = folder;
            return this;
        }

        public DownloadedResponseBuilder setCount(int count) {
            this.count = count;
            return this;
        }

        public DownloadedResponseBuilder setExtensions(String extensions) {
            this.extensions = extensions;
            return this;
        }

        public DownloadedResponseBuilder setTime(Long time) {
            this.time = time;
            return this;
        }

        public DownloadedResponseBuilder setMoved(boolean moved) {
            this.moved = moved;
            return this;
        }

        public DownloadedResponseBuilder setMoveFolder(String moveFolder) {
            this.moveFolder = moveFolder;
            return this;
        }

        public DownloadedResponseBuilder setMoveTime(Long moveTime) {
            this.moveTime = moveTime;
            return this;
        }

        public DownloadedResponseBuilder setR18(Boolean isR18) {
            this.isR18 = isR18;
            return this;
        }

        public DownloadedResponse build() {
            boolean flag = artworkId != null && title != null && folder != null && count != 0 && time != null;
            boolean flag2 = !moved || (moveFolder != null && moveTime != null);

            if (flag && flag2) {
                return new DownloadedResponse(artworkId, title, folder, count, extensions, time, moved, moveFolder, moveTime, isR18);
            } else {
                throw new RuntimeException("缺少必要值，artworkId = " + artworkId + ",title=" + title);
            }
        }
    }
}
