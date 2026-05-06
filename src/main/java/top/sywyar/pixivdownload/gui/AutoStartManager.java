package top.sywyar.pixivdownload.gui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class AutoStartManager {

    public static final String STARTUP_ARG = "--pixivdownload-startup";

    private static final String STARTUP_ARG_ALIAS = "--startup";
    private static final String APP_EXE_NAME = "PixivDownload.exe";
    private static final String SHORTCUT_NAME = "PixivDownload.lnk";
    private static final String STARTUP_FOLDER = "Microsoft\\Windows\\Start Menu\\Programs\\Startup";
    // 用 `& { ... }` 包裹脚本块以便 powershell.exe -Command 后续位置参数能通过 $args 传入。
    // 直接 -Command "<string>" arg1 arg2 时，arg1/arg2 会被拼接到命令文本里，$args 仍为空。
    private static final String CREATE_SHORTCUT_SCRIPT = """
            & {
              param($shortcutPath, $targetPath, $arguments, $workingDirectory)
              $shell = New-Object -ComObject WScript.Shell
              $shortcut = $shell.CreateShortcut($shortcutPath)
              $shortcut.TargetPath = $targetPath
              $shortcut.Arguments = $arguments
              $shortcut.WorkingDirectory = $workingDirectory
              $shortcut.IconLocation = $targetPath
              $shortcut.Save()
            }
            """;

    private AutoStartManager() {
    }

    public static boolean isStartupLaunch(String[] args) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if (isStartupArg(arg)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isStartupArg(String arg) {
        return STARTUP_ARG.equals(arg) || STARTUP_ARG_ALIAS.equals(arg);
    }

    public static boolean isSupported() {
        return isWindows()
                && startupShortcutPath().isPresent()
                && currentApplicationExecutable().isPresent();
    }

    public static boolean isEnabled() {
        return startupShortcutPath()
                .map(Files::isRegularFile)
                .orElse(false);
    }

    public static void setEnabled(boolean enabled) throws IOException, InterruptedException {
        Optional<Path> shortcut = startupShortcutPath();
        if (shortcut.isEmpty()) {
            throw new IOException("Windows Startup folder is not available");
        }

        if (enabled) {
            Path executable = currentApplicationExecutable()
                    .orElseThrow(() -> new IOException("Current process is not PixivDownload.exe"));
            createShortcut(shortcut.get(), executable);
        } else {
            Files.deleteIfExists(shortcut.get());
        }
    }

    public static Optional<Path> currentApplicationExecutable() {
        if (!isWindows()) {
            return Optional.empty();
        }

        return ProcessHandle.current().info().command()
                .flatMap(AutoStartManager::normalizeApplicationExecutable);
    }

    static Optional<Path> normalizeApplicationExecutable(String command) {
        if (command == null || command.isBlank()) {
            return Optional.empty();
        }

        Path executable;
        try {
            executable = Path.of(command).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            return Optional.empty();
        }

        if (!Files.isRegularFile(executable)) {
            return Optional.empty();
        }

        String fileName = executable.getFileName().toString();
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".exe")) {
            return Optional.empty();
        }
        if (lowerName.equals("java.exe") || lowerName.equals("javaw.exe")) {
            return Optional.empty();
        }
        if (!fileName.equalsIgnoreCase(APP_EXE_NAME) && System.getProperty("jpackage.app-version") == null) {
            return Optional.empty();
        }

        return Optional.of(executable);
    }

    static Optional<Path> startupShortcutPath() {
        if (!isWindows()) {
            return Optional.empty();
        }

        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(Path.of(appData, STARTUP_FOLDER, SHORTCUT_NAME).toAbsolutePath().normalize());
    }

    private static void createShortcut(Path shortcut, Path executable) throws IOException, InterruptedException {
        Files.createDirectories(shortcut.getParent());

        String workingDirectory = executable.getParent() == null
                ? executable.toAbsolutePath().getParent().toString()
                : executable.getParent().toString();

        Process process = new ProcessBuilder(List.of(
                powershellExecutable(),
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                CREATE_SHORTCUT_SCRIPT,
                shortcut.toString(),
                executable.toString(),
                STARTUP_ARG,
                workingDirectory
        )).redirectErrorStream(true).start();

        byte[] outputBytes = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String output = new String(outputBytes, StandardCharsets.UTF_8).trim();
            throw new IOException(output.isBlank()
                    ? "PowerShell shortcut creation failed with exit code " + exitCode
                    : output);
        }
        if (!Files.isRegularFile(shortcut)) {
            throw new IOException("Startup shortcut was not created");
        }
    }

    private static String powershellExecutable() {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot != null && !systemRoot.isBlank()) {
            Path powershell = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
            if (Files.isRegularFile(powershell)) {
                return powershell.toString();
            }
        }
        return "powershell.exe";
    }

    private static boolean isWindows() {
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase(Locale.ROOT).contains("win");
    }
}
