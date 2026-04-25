package top.sywyar.pixivdownload.gui;

import top.sywyar.pixivdownload.gui.i18n.GuiMessages;
import top.sywyar.pixivdownload.gui.panel.AboutPanel;
import top.sywyar.pixivdownload.gui.panel.ConfigPanel;
import top.sywyar.pixivdownload.gui.panel.StatusPanel;
import top.sywyar.pixivdownload.gui.panel.ToolsPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.List;

/**
 * GUI 主窗口（800x600，可调整大小）。
 * 包含四个标签页：状态、配置、工具、关于。
 * 关闭窗口时缩回系统托盘，不退出进程。
 */
public class MainFrame extends JFrame {

    private final StatusPanel statusPanel;
    private final ToolsPanel toolsPanel;

    public MainFrame(int serverPort, String rootFolder, Path configPath) {
        super(GuiMessages.get("app.name"));
        setSize(800, 600);
        setMinimumSize(new Dimension(640, 480));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                setVisible(false);
            }
        });

        Image appIcon = loadAppIcon();
        if (appIcon != null) {
            setIconImages(List.of(
                    scaled(appIcon, 16),
                    scaled(appIcon, 24),
                    scaled(appIcon, 32),
                    scaled(appIcon, 48),
                    scaled(appIcon, 64)
            ));
        }

        JTabbedPane tabs = new JTabbedPane();
        statusPanel = new StatusPanel(serverPort, rootFolder, configPath);
        toolsPanel = new ToolsPanel(configPath);
        tabs.addTab(GuiMessages.get("gui.tab.status"), statusPanel);
        tabs.addTab(GuiMessages.get("gui.tab.config"), new ConfigPanel(configPath));
        tabs.addTab(GuiMessages.get("gui.tab.tools"), toolsPanel);
        tabs.addTab(GuiMessages.get("gui.tab.about"), new AboutPanel());

        setContentPane(tabs);
    }

    public String getMonitorUrl() {
        return statusPanel.getMonitorUrl();
    }

    public void showWindow() {
        if (!isVisible()) {
            setVisible(true);
        }

        int state = getExtendedState();
        if ((state & Frame.ICONIFIED) != 0) {
            setExtendedState(state & ~Frame.ICONIFIED);
        }

        toFront();
        requestFocus();
    }

    @Override
    public void dispose() {
        statusPanel.dispose();
        toolsPanel.dispose();
        super.dispose();
    }

    private static Image loadAppIcon() {
        try {
            var stream = MainFrame.class.getResourceAsStream("/static/favicon.ico");
            if (stream != null) {
                byte[] bytes = stream.readAllBytes();
                Image img = Toolkit.getDefaultToolkit().createImage(bytes);
                MediaTracker tracker = new MediaTracker(new Canvas());
                tracker.addImage(img, 0);
                tracker.waitForAll();
                if (!tracker.isErrorAny()) {
                    return img;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Image scaled(Image src, int size) {
        return src.getScaledInstance(size, size, Image.SCALE_SMOOTH);
    }
}
