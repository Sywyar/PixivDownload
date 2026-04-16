package top.sywyar.pixivdownload.gui.panel;

import lombok.extern.slf4j.Slf4j;
import top.sywyar.pixivdownload.gui.config.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

/**
 * "配置" 标签页：Schema 驱动的字段渲染，按 group 分为子标签页。
 * 保存时调用 ConfigFileEditor 行内替换，保留注释和格式。
 */
@Slf4j
public class ConfigPanel extends JPanel {

    private final Path configPath;
    private final ConfigFileEditor editor;

    /** key → 渲染后的字段（含取值/赋值方法） */
    private final Map<String, FieldRenderer.RenderedField> renderedFields = new LinkedHashMap<>();

    /** 提示条（保存后显示） */
    private final JLabel noticeBar = new JLabel(" ");

    public ConfigPanel(Path configPath) {
        this.configPath = configPath;
        this.editor = new ConfigFileEditor(configPath);
        buildUi();
        loadCurrentValues();
        checkFieldDrift();
    }

    // ── UI 构建 ──────────────────────────────────────────────────────────────────

    private void buildUi() {
        setLayout(new BorderLayout(0, 0));

        // 子标签页（按 group）
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        for (String group : ConfigFieldRegistry.GROUPS) {
            tabs.addTab(group, buildGroupPanel(group));
        }
        add(tabs, BorderLayout.CENTER);

        // 底部面板：提示条 + 按钮
        add(buildBottomPanel(), BorderLayout.SOUTH);
    }

    private JScrollPane buildGroupPanel(String group) {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        List<ConfigFieldSpec> fields = ConfigFieldRegistry.ALL_FIELDS.stream()
                .filter(f -> group.equals(f.group()))
                .toList();

        for (ConfigFieldSpec spec : fields) {
            FieldRenderer.RenderedField rf = FieldRenderer.render(spec);
            renderedFields.put(spec.key(), rf);
            attachChangeListener(rf.control());
            rf.panel().setAlignmentX(Component.LEFT_ALIGNMENT);
            rf.panel().setMaximumSize(new Dimension(Integer.MAX_VALUE, rf.panel().getPreferredSize().height + 20));
            content.add(rf.panel());
            content.add(Box.createVerticalStrut(2));
        }
        content.add(Box.createVerticalGlue());

        JScrollPane sp = new JScrollPane(content);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    private JPanel buildBottomPanel() {
        // 提示条
        noticeBar.setOpaque(true);
        noticeBar.setBackground(new Color(255, 243, 205));
        noticeBar.setForeground(new Color(133, 100, 4));
        noticeBar.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        noticeBar.setVisible(false);

        // 按钮行
        JButton save = new JButton("保存");
        save.addActionListener(e -> saveConfig());

        JButton reset = new JButton("重置为默认值");
        reset.addActionListener(e -> resetToDefaults());

        JButton openFile = new JButton("打开 config.yaml");
        openFile.addActionListener(e -> openConfigFile());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        btnRow.add(openFile);
        btnRow.add(reset);
        btnRow.add(save);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(noticeBar, BorderLayout.NORTH);
        bottom.add(btnRow, BorderLayout.CENTER);
        return bottom;
    }

    // ── 数据加载 ──────────────────────────────────────────────────────────────────

    /**
     * 从 config.yaml 加载当前值并填充控件；key 不存在或被注释时回退到字段默认值，
     * 并将所有缺失的 key 连同默认值自动补全到 config.yaml（与 AppConfigGenerator 效果一致）。
     */
    private void loadCurrentValues() {
        if (!configPath.toFile().exists()) return;
        try {
            Map<String, String> values = editor.readAll(renderedFields.keySet());
            Map<String, String> missing = new LinkedHashMap<>();

            for (ConfigFieldSpec spec : ConfigFieldRegistry.ALL_FIELDS) {
                FieldRenderer.RenderedField rf = renderedFields.get(spec.key());
                if (rf == null) continue;
                if (values.containsKey(spec.key())) {
                    rf.setValue().accept(values.get(spec.key()));
                } else {
                    // key 不存在或被注释掉：用默认值填充控件，并记录待补全
                    rf.setValue().accept(spec.defaultValue());
                    missing.put(spec.key(), spec.defaultValue());
                }
            }

            // 自动将缺失的 key 补全到 config.yaml
            if (!missing.isEmpty()) {
                try {
                    editor.writeAll(missing);
                    log.info("已自动补全 {} 个缺失的配置项: {}",
                            missing.size(), String.join(", ", missing.keySet()));
                } catch (IOException ex) {
                    log.warn("自动补全配置项失败: {}", ex.getMessage());
                }
            }

            updateEnabledStates();
        } catch (IOException e) {
            log.warn("读取配置文件失败: {}", e.getMessage());
            JOptionPane.showMessageDialog(this, "读取配置文件失败：" + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 根据 visibleWhen / enabledWhen 谓词显示/隐藏、启用/禁用控件。
     * 在每次值变更时触发，以反映字段间依赖。
     */
    private void updateEnabledStates() {
        ConfigSnapshot snap = buildSnapshot();
        boolean layoutChanged = false;
        for (ConfigFieldSpec spec : ConfigFieldRegistry.ALL_FIELDS) {
            FieldRenderer.RenderedField rf = renderedFields.get(spec.key());
            if (rf == null) continue;
            boolean visible = spec.visibleWhen().test(snap);
            if (rf.panel().isVisible() != visible) {
                rf.panel().setVisible(visible);
                layoutChanged = true;
            }
            if (visible) {
                setControlEnabled(rf.panel(), spec.enabledWhen().test(snap));
            }
        }
        if (layoutChanged) {
            revalidate();
            repaint();
        }
    }

    private void setControlEnabled(Container c, boolean enabled) {
        for (Component child : c.getComponents()) {
            child.setEnabled(enabled);
            if (child instanceof Container container) {
                setControlEnabled(container, enabled);
            }
        }
    }

    private ConfigSnapshot buildSnapshot() {
        Map<String, String> snap = new HashMap<>();
        for (Map.Entry<String, FieldRenderer.RenderedField> e : renderedFields.entrySet()) {
            snap.put(e.getKey(), e.getValue().getValue().get());
        }
        return new ConfigSnapshot(snap);
    }

    // ── 保存 ─────────────────────────────────────────────────────────────────────

    private void saveConfig() {
        // 验证（仅验证可见且已启用的字段）
        List<String> errors = new ArrayList<>();
        for (ConfigFieldSpec spec : ConfigFieldRegistry.ALL_FIELDS) {
            FieldRenderer.RenderedField rf = renderedFields.get(spec.key());
            if (rf == null || !rf.panel().isVisible() || !rf.control().isEnabled()) continue;
            String val = rf.getValue().get();
            String err = spec.validator().validate(val);
            if (err != null) {
                errors.add(spec.label() + "：" + err);
            }
        }
        if (!errors.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "以下字段有误，请修正后再保存：\n" + String.join("\n", errors),
                    "验证失败", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 收集所有值：隐藏字段写入空值，Spring Boot 对空值不加载对应证书
        Map<String, String> values = new LinkedHashMap<>();
        for (ConfigFieldSpec spec : ConfigFieldRegistry.ALL_FIELDS) {
            FieldRenderer.RenderedField rf = renderedFields.get(spec.key());
            if (rf == null) continue;
            values.put(spec.key(), rf.panel().isVisible() ? rf.getValue().get() : "");
        }

        try {
            editor.writeAll(values);
            showNotice("配置已保存，需重启服务生效");
            log.info("配置已保存到: {}", configPath);
        } catch (IOException e) {
            log.error("保存配置失败: {}", e.getMessage());
            JOptionPane.showMessageDialog(this, "保存配置失败：" + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void resetToDefaults() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "确认将所有字段重置为默认值？（仅更新界面，需点击[保存]才写入文件）",
                "重置为默认值", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        for (ConfigFieldSpec spec : ConfigFieldRegistry.ALL_FIELDS) {
            FieldRenderer.RenderedField rf = renderedFields.get(spec.key());
            if (rf != null) {
                rf.setValue().accept(spec.defaultValue());
            }
        }
        updateEnabledStates();
    }

    private void openConfigFile() {
        try {
            Desktop.getDesktop().open(configPath.toFile());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "无法打开文件：" + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── 字段漂移检查 ──────────────────────────────────────────────────────────────

    /**
     * 对比 ALL_FIELDS 与 config.yaml 中实际存在的 key，漂移时打日志警告。
     */
    private void checkFieldDrift() {
        if (!configPath.toFile().exists()) return;
        try {
            Map<String, String> existing = editor.readAll(
                    ConfigFieldRegistry.ALL_FIELDS.stream()
                            .map(ConfigFieldSpec::key).toList());
            for (ConfigFieldSpec spec : ConfigFieldRegistry.ALL_FIELDS) {
                if (!existing.containsKey(spec.key())) {
                    log.warn("配置字段漂移：key '{}' 在 config.yaml 中不存在，GUI 将使用默认值", spec.key());
                }
            }
        } catch (IOException e) {
            log.warn("字段漂移检查失败: {}", e.getMessage());
        }
    }

    // ── 工具 ─────────────────────────────────────────────────────────────────────

    /**
     * 为控件添加变更监听，任何值变化都触发一次 enabledWhen 重算。
     * 这样 ENUM/BOOL 类控件改变后，依赖它的字段会立即启用/禁用。
     */
    private void attachChangeListener(JComponent control) {
        if (control instanceof JCheckBox cb) {
            cb.addItemListener(e -> updateEnabledStates());
        } else if (control instanceof JComboBox<?> combo) {
            combo.addActionListener(e -> updateEnabledStates());
        } else if (control instanceof JSpinner sp) {
            sp.addChangeListener(e -> updateEnabledStates());
        } else if (control instanceof JTextField tf) {
            tf.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { updateEnabledStates(); }
                public void removeUpdate(DocumentEvent e) { updateEnabledStates(); }
                public void changedUpdate(DocumentEvent e) { updateEnabledStates(); }
            });
        }
    }

    private void showNotice(String msg) {
        noticeBar.setText("  " + msg);
        noticeBar.setVisible(true);
        // 10 秒后自动隐藏
        javax.swing.Timer t = new javax.swing.Timer(10_000, e -> noticeBar.setVisible(false));
        t.setRepeats(false);
        t.start();
    }
}
