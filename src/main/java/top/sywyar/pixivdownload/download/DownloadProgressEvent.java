package top.sywyar.pixivdownload.download;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class DownloadProgressEvent extends ApplicationEvent {
    private final Long artworkId;
    private final DownloadStatus downloadStatus;
    
    public DownloadProgressEvent(Object source, Long artworkId) {
        super(source);
        this.artworkId = artworkId;
        this.downloadStatus = null;
    }
    
    public DownloadProgressEvent(Object source, Long artworkId, DownloadStatus downloadStatus) {
        super(source);
        this.artworkId = artworkId;
        this.downloadStatus = downloadStatus;
    }

}