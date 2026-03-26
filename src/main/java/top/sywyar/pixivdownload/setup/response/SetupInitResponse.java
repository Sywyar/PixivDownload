package top.sywyar.pixivdownload.setup.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SetupInitResponse {
    private final boolean ok;
    private final String mode;
}
