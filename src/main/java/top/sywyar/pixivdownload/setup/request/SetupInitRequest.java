package top.sywyar.pixivdownload.setup.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SetupInitRequest {
    @NotBlank(message = "{validation.setup.username.required}")
    private String username;

    @NotBlank(message = "{validation.setup.password.required}")
    @Size(min = 6, message = "{validation.setup.password.size}")
    private String password;

    @NotBlank(message = "{validation.setup.mode.required}")
    @Pattern(regexp = "solo|multi", message = "{validation.setup.mode.pattern}")
    private String mode;
}
