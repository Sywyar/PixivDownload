package top.sywyar.pixivdownload.quota.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AdminPackRequest {

    @NotEmpty
    private List<Long> artworkIds;
}
