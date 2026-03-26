package top.sywyar.pixivdownload.setup;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.sywyar.pixivdownload.download.response.ErrorResponse;
import top.sywyar.pixivdownload.setup.response.AuthCheckResponse;
import top.sywyar.pixivdownload.setup.response.AuthResponse;
import top.sywyar.pixivdownload.setup.response.SetupInitResponse;
import top.sywyar.pixivdownload.setup.response.SetupStatusResponse;

import java.io.IOException;
import java.util.Map;

@RestController
@Slf4j
public class SetupController {

    private final SetupService setupService;

    public SetupController(SetupService setupService) {
        this.setupService = setupService;
    }

    // ---- Setup endpoints -----------------------------------------------

    @GetMapping("/api/setup/status")
    public SetupStatusResponse status() {
        return new SetupStatusResponse(
                setupService.isSetupComplete(),
                setupService.getMode() != null ? setupService.getMode() : ""
        );
    }

    @PostMapping("/api/setup/init")
    public ResponseEntity<?> init(@RequestBody Map<String, String> body) throws IOException {
        if (setupService.isSetupComplete()) {
            return ResponseEntity.status(403).body(new ErrorResponse("已完成配置，不可重复初始化"));
        }
        String username = body.get("username");
        String password = body.get("password");
        String mode     = body.get("mode");

        if (username == null || username.isBlank())
            return ResponseEntity.badRequest().body(new ErrorResponse("用户名不能为空"));
        if (password == null || password.length() < 6)
            return ResponseEntity.badRequest().body(new ErrorResponse("密码长度至少 6 位"));
        if (!"solo".equals(mode) && !"multi".equals(mode))
            return ResponseEntity.badRequest().body(new ErrorResponse("无效的使用模式"));

        setupService.init(username, password, mode);
        return ResponseEntity.ok(new SetupInitResponse(true, mode));
    }

    // ---- Auth endpoints ------------------------------------------------

    @PostMapping("/api/auth/login")
    public ResponseEntity<?> login(
            @RequestBody Map<String, Object> body,
            HttpServletResponse response) {
        String username  = (String) body.get("username");
        String password  = (String) body.get("password");
        boolean remember = Boolean.TRUE.equals(body.get("rememberMe"));

        if (!setupService.checkLogin(username, password)) {
            return ResponseEntity.status(401).body(new ErrorResponse("用户名或密码错误"));
        }

        String token = setupService.createSession(remember);
        // SameSite=Strict 防止 CSRF
        String maxAge = remember ? "; Max-Age=" + (30 * 24 * 3600) : "";
        response.addHeader(HttpHeaders.SET_COOKIE,
                "pixiv_session=" + token + "; Path=/; HttpOnly" + maxAge + "; SameSite=Strict");

        return ResponseEntity.ok(new AuthResponse(true));
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<AuthResponse> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        String token = extractToken(request);
        setupService.removeSession(token);

        response.addHeader(HttpHeaders.SET_COOKIE,
                "pixiv_session=; Path=/; HttpOnly; Max-Age=0; SameSite=Strict");

        return ResponseEntity.ok(new AuthResponse(true));
    }

    @GetMapping("/api/auth/check")
    public AuthCheckResponse check(HttpServletRequest request) {
        return new AuthCheckResponse(setupService.isValidSession(extractToken(request)));
    }

    // ---- 工具 ----------------------------------------------------------

    private String extractToken(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if ("pixiv_session".equals(c.getName())) return c.getValue();
            }
        }
        return req.getHeader("X-Session-Token");
    }
}
