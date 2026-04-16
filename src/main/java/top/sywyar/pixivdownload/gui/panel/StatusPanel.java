package top.sywyar.pixivdownload.gui.panel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.security.cert.X509Certificate;

/**
 * "状态" 标签页：显示服务器元信息（端口、模式、启动时间），并提供快捷操作按钮。
 * 统计数据（总作品数、活跃队列）由 monitor.html 专职展示，此面板不重复。
 */
@Slf4j
public class StatusPanel extends JPanel {

    private static final int POLL_INTERVAL_MS = 3000;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 信任所有证书的 SSLContext，用于轮询本地自签名 HTTPS 接口。 */
    private static final SSLContext TRUST_ALL_SSL = buildTrustAllSslContext();

    private final JLabel portLabel   = valueLabel("—");
    private final JLabel modeLabel   = valueLabel("—");
    private final JLabel uptimeLabel = valueLabel("—");
    private final JLabel httpsLabel  = valueLabel("—");
    private final JLabel statusBadge = new JLabel("正在启动...");

    private final int serverPort;
    private final String rootFolder;
    /** 上次成功连接所用的协议，初始尝试 http。 */
    private volatile String currentScheme = "http";
    /** 服务对外域名（来自 ssl.domain），首次轮询成功后更新。 */
    private volatile String serverDomain = "localhost";
    /** 对外生效协议（来自 ssl.domain + ssl.enabled），首次轮询成功后更新。 */
    private volatile String serverScheme = "http";
    private Timer pollTimer;

    public StatusPanel(int serverPort, String rootFolder) {
        this.serverPort = serverPort;
        this.rootFolder = rootFolder;
        buildUi();
        startPolling();
    }

    // ── UI 构建 ──────────────────────────────────────────────────────────────────

    private void buildUi() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));

        // 状态徽章
        statusBadge.setFont(statusBadge.getFont().deriveFont(Font.BOLD, 12f));
        statusBadge.setForeground(new Color(180, 100, 0));
        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        badgeRow.setOpaque(false);
        badgeRow.add(statusBadge);
        add(badgeRow, BorderLayout.NORTH);

        // 状态网格（仅 monitor.html 不展示的元信息）
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 4, 6, 16);
        g.anchor = GridBagConstraints.WEST;

        int row = 0;
        addRow(grid, g, row++, "运行端口：", portLabel);
        addRow(grid, g, row++, "运行模式：", modeLabel);
        addRow(grid, g, row++, "启动时间：", uptimeLabel);
        addRow(grid, g, row++, "HTTPS：",   httpsLabel);

        // 提示：详细统计见 Web 控制台
        JLabel hint = new JLabel("下载队列、历史记录等详细统计请打开 Web 控制台查看。");
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
        hint.setForeground(Color.GRAY);
        GridBagConstraints hc = new GridBagConstraints();
        hc.gridy = row++;
        hc.gridx = 0;
        hc.gridwidth = 2;
        hc.anchor = GridBagConstraints.WEST;
        hc.insets = new Insets(12, 4, 6, 4);
        grid.add(hint, hc);

        // 填充剩余空间
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = row;
        gc.weighty = 1;
        gc.fill = GridBagConstraints.VERTICAL;
        grid.add(Box.createVerticalGlue(), gc);

        add(grid, BorderLayout.CENTER);

        // 操作按钮
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);

        JButton openMonitor = new JButton("打开 Web 控制台");
        openMonitor.addActionListener(e -> openMonitor());

        JButton openFolder = new JButton("打开下载目录");
        openFolder.addActionListener(e -> openDownloadFolder());

        JButton restart = new JButton("重启服务");
        restart.addActionListener(e -> restartService());

        buttons.add(openMonitor);
        buttons.add(openFolder);
        buttons.add(restart);

        add(buttons, BorderLayout.SOUTH);
    }

    private static void addRow(JPanel grid, GridBagConstraints g, int row, String key, JLabel value) {
        g.gridy = row;
        g.gridx = 0;
        g.weightx = 0;
        g.fill = GridBagConstraints.NONE;
        JLabel keyLabel = new JLabel(key);
        keyLabel.setForeground(Color.GRAY);
        grid.add(keyLabel, g);

        g.gridx = 1;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        grid.add(value, g);
    }

    private static JLabel valueLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        return l;
    }

    // ── 轮询 ─────────────────────────────────────────────────────────────────────

    private void startPolling() {
        pollTimer = new Timer(POLL_INTERVAL_MS, e -> fetchStatus());
        pollTimer.setInitialDelay(500);
        pollTimer.start();
    }

    private void fetchStatus() {
        Thread worker = new Thread(() -> {
            // 优先尝试上次成功的协议，失败后切换另一种（应对 SSL 启用/禁用切换）
            String[] schemes = "https".equals(currentScheme)
                    ? new String[]{"https", "http"}
                    : new String[]{"http", "https"};

            for (String scheme : schemes) {
                try {
                    URL url = new URI(scheme + "://localhost:" + serverPort + "/api/gui/status").toURL();
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    if (conn instanceof HttpsURLConnection https && TRUST_ALL_SSL != null) {
                        https.setSSLSocketFactory(TRUST_ALL_SSL.getSocketFactory());
                        https.setHostnameVerifier((h, s) -> true);
                    }
                    conn.setConnectTimeout(2000);
                    conn.setReadTimeout(2000);
                    conn.setRequestMethod("GET");

                    if (conn.getResponseCode() == 200) {
                        currentScheme = scheme;
                        try (InputStream is = conn.getInputStream()) {
                            JsonNode node = MAPPER.readTree(is);
                            SwingUtilities.invokeLater(() -> updateLabels(node));
                        }
                        return; // 成功，不再尝试下一种协议
                    }
                } catch (Exception ignored) {
                    // 此协议不通，继续尝试另一种
                }
            }
            // 两种协议均不通：Spring 尚未就绪，保持"正在启动..."
        }, "gui-status-poll");
        worker.setDaemon(true);
        worker.start();
    }

    private void updateLabels(JsonNode node) {
        statusBadge.setText("运行中");
        statusBadge.setForeground(new Color(0, 140, 0));

        portLabel.setText(textOf(node, "port"));
        modeLabel.setText(modeName(textOf(node, "mode")));
        uptimeLabel.setText(textOf(node, "startTime"));

        JsonNode httpsNode = node.get("httpsEnabled");
        boolean https = httpsNode != null && httpsNode.asBoolean(false);
        httpsLabel.setText(https ? "启用" : "未启用");
        httpsLabel.setForeground(https ? new Color(0, 140, 0) : Color.GRAY);

        String d = textOf(node, "domain");
        String s = textOf(node, "scheme");
        if (!"—".equals(d)) serverDomain = d;
        if (!"—".equals(s)) serverScheme = s;
    }

    private static String textOf(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n == null || n.isNull()) ? "—" : n.asText();
    }

    private static String modeName(String mode) {
        return switch (mode) {
            case "solo"  -> "solo（自用模式）";
            case "multi" -> "multi（多人模式）";
            default      -> mode;
        };
    }

    // ── 操作 ─────────────────────────────────────────────────────────────────────

    private void openMonitor() {
        try {
            Desktop.getDesktop().browse(new URI(serverScheme + "://" + serverDomain + ":" + serverPort + "/monitor.html"));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "无法打开浏览器：" + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openDownloadFolder() {
        File folder = new File(rootFolder);
        if (!folder.exists()) {
            JOptionPane.showMessageDialog(this, "下载目录不存在：" + folder.getAbsolutePath(),
                    "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().open(folder);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "无法打开目录：" + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void restartService() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "确认重启服务？重启期间将短暂无法访问。",
                "重启服务", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        Thread worker = new Thread(() -> {
            try {
                URL url = new URI(currentScheme + "://localhost:" + serverPort + "/api/gui/restart").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                if (conn instanceof HttpsURLConnection https && TRUST_ALL_SSL != null) {
                    https.setSSLSocketFactory(TRUST_ALL_SSL.getSocketFactory());
                    https.setHostnameVerifier((h, s) -> true);
                }
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.getResponseCode();
            } catch (Exception e) {
                log.warn("重启请求失败: {}", e.getMessage());
            }
            SwingUtilities.invokeLater(() -> {
                statusBadge.setText("正在重启...");
                statusBadge.setForeground(new Color(180, 100, 0));
            });
        }, "gui-restart");
        worker.setDaemon(true);
        worker.start();
    }

    /** 返回当前生效的 Web 控制台 URL（scheme/domain 在首次轮询成功后更新）。 */
    public String getMonitorUrl() {
        return serverScheme + "://" + serverDomain + ":" + serverPort + "/monitor.html";
    }

    public void dispose() {
        if (pollTimer != null) pollTimer.stop();
    }

    // ── SSL 工具 ──────────────────────────────────────────────────────────────────

    /**
     * 构建一个信任所有证书的 {@link SSLContext}，仅用于轮询本机 localhost 接口。
     * 绝对不能用于对外网络请求。
     */
    private static SSLContext buildTrustAllSslContext() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] c, String t) {}
                    public void checkServerTrusted(X509Certificate[] c, String t) {}
                }
            }, null);
            return ctx;
        } catch (Exception e) {
            log.warn("无法创建 trust-all SSLContext，HTTPS 轮询可能失败: {}", e.getMessage());
            return null;
        }
    }
}
