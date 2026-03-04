package org.liang.get;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.formdev.flatlaf.FlatDarkLaf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.liang.cmd.ControlCommandEngine;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;

public class ModernGui extends JFrame {
    private JTextField brokerF, subF, pubF, pKeyF, snF;
    private JComboBox<String> typeCombo;
    private JTextArea logArea;
    private JButton startBtn;
    private final MqttManager mqttManager;
    private boolean isRunning = false;
    private JCheckBox logToFileCh;

    // 配置文件路径
    private static final String CONFIG_DIR = "configs";
    private static final String CONFIG_FILE = CONFIG_DIR + "/settings.json";

    private static final Logger logger =
            LogManager.getLogger(ModernGui.class);

    public ModernGui() {
        initDirectories(); // 初始化目录
        setupTheme();
        setTitle("LoRA/星纵 MQTT报文转换器");
        setSize(1100, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        mqttManager = new MqttManager(this::appendLog);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.putClientProperty("JTabbedPane.tabType", "card");
        tabbedPane.addTab(" 数据协议转换 ", createTransformerPanel());

        try {
            ControlCommandEngine cmdEngine = new ControlCommandEngine();
            tabbedPane.addTab(" 指令下发控制 ", cmdEngine.getContentPane());
        } catch (Exception e) {
            appendLog("❌ 指令引擎加载失败: " + e.getMessage());
        }

        add(tabbedPane);
        loadConfig(); // 加载配置
    }

    private void initDirectories() {
        try {
            Files.createDirectories(Paths.get("logs"));
            Files.createDirectories(Paths.get(CONFIG_DIR));
        } catch (IOException e) {
            System.err.println("无法创建必要目录: " + e.getMessage());
        }
    }

    private JPanel createTransformerPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        panel.setBackground(new Color(35, 35, 35));

        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setPreferredSize(new Dimension(300, 0));
        leftPanel.setOpaque(false);

        brokerF = addInput(leftPanel, "服务器地址:", "tcp://192.168.5.218:1883");
        subF = addInput(leftPanel, "订阅原始主题:", "/LoRA_Message/+/get");
        pubF = addInput(leftPanel, "发布标准主题:", "/LoRA_Message/transformed");
        pKeyF = addInput(leftPanel, "Product Key:", "Light");
        snF = addInput(leftPanel, "Serial Number:", "003");

        JLabel typeLab = new JLabel("解析器类型:");
        typeLab.setForeground(new Color(200, 200, 200));
        typeLab.setAlignmentX(Component.CENTER_ALIGNMENT);
        leftPanel.add(typeLab);

        // 使用 ParserFactory 获取解析器列表，解决“未使用”警告
        typeCombo = new JComboBox<>(ParserFactory.getParserNames());
        typeCombo.setMaximumSize(new Dimension(280, 35));
        typeCombo.setAlignmentX(Component.CENTER_ALIGNMENT);
        leftPanel.add(typeCombo);

        leftPanel.add(Box.createVerticalStrut(20));

        logToFileCh = new JCheckBox("保存运行日志到本地文件");
        logToFileCh.setForeground(new Color(200, 200, 200));
        logToFileCh.setAlignmentX(Component.CENTER_ALIGNMENT);
        leftPanel.add(logToFileCh);

        leftPanel.add(Box.createVerticalStrut(25));

        // 按钮组
        startBtn = createStyledButton("启动转换服务", new Color(76, 175, 80));
        startBtn.addActionListener(_ -> handleService());
        leftPanel.add(startBtn);
        leftPanel.add(Box.createVerticalStrut(12));

        JButton helpBtn = createStyledButton(" 使用说明 ", null);
        helpBtn.addActionListener(_ -> showHelpDialog());
        leftPanel.add(helpBtn);
        leftPanel.add(Box.createVerticalStrut(12));

        // 新增：打开目录按钮
        JButton folderBtn = createStyledButton(" 打开程序目录 ", null);
        folderBtn.addActionListener(_ -> openWorkDir());
        leftPanel.add(folderBtn);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(new Color(25, 25, 25));
        logArea.setForeground(new Color(180, 180, 180));
        logArea.setFont(new Font("Microsoft Yahei", Font.PLAIN, 12));
        logArea.setBorder(new EmptyBorder(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));

        panel.add(leftPanel, BorderLayout.WEST);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JButton createStyledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setMaximumSize(new Dimension(220, 40));
        btn.putClientProperty("JButton.buttonType", "roundRect");
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        if (bg != null) btn.setBackground(bg);
        return btn;
    }

    private void openWorkDir() {
        try {
            Desktop.getDesktop().open(new File("."));
        } catch (IOException e) {
            appendLog("❌ 无法打开目录: " + e.getMessage());
        }
    }

    private void saveConfig() {
        JSONObject json = new JSONObject();
        json.put("broker", brokerF.getText());
        json.put("sub", subF.getText());
        json.put("pub", pubF.getText());
        json.put("pKey", pKeyF.getText());
        json.put("sn", snF.getText());
        json.put("type", typeCombo.getSelectedItem());
        json.put("logEnabled", logToFileCh.isSelected());

        try {
            Files.writeString(Paths.get(CONFIG_FILE), json.toJSONString(), StandardCharsets.UTF_8);
            appendLog("💾 配置已自动保存");
        } catch (IOException e) {
            appendLog("⚠️ 配置保存失败: " + e.getMessage());
        }
    }

    private void loadConfig() {
        Path path = Paths.get(CONFIG_FILE);
        if (!Files.exists(path)) return;

        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            JSONObject json = JSON.parseObject(content);

            brokerF.setText(json.getString("broker"));
            subF.setText(json.getString("sub"));
            pubF.setText(json.getString("pub"));
            pKeyF.setText(json.getString("pKey"));
            snF.setText(json.getString("sn"));
            typeCombo.setSelectedItem(json.getString("type"));
            logToFileCh.setSelected(json.getBooleanValue("logEnabled"));

            // ✅ 修正：同步记录到本地日志文件
            logger.info("📂 已从本地加载历史配置: {}", CONFIG_FILE);
            appendLog("📂 已从本地加载历史配置");
        } catch (Exception e) {
            logger.error("⚠️ 配置文件读取异常", e);
            appendLog("⚠️ 配置文件读取异常");
        }
    }

    private JTextField addInput(JPanel p, String lab, String def) {
        JLabel label = new JLabel(lab);
        label.setForeground(new Color(200, 200, 200));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(label);

        JTextField f = new JTextField(def);
        f.setMaximumSize(new Dimension(280, 35));
        f.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(f);
        p.add(Box.createVerticalStrut(10));
        return f;
    }

    private void showHelpDialog() {
        String helpText = "<html><body style='width: 350px; padding: 10px; font-family: Microsoft YaHei;'>" +
                "<h2 style='color: #4CAF50;'>📖 使用说明</h2><hr>" +
                "<b>配置已支持自动保存：</b> 每次点击启动服务，当前配置将存入 configs 目录。<br><br>" +
                "<b>解析器说明：</b><br>" +
                "<ul><li><b>AirSensor:</b> 环境监测（温度/CO2/PM等）</li>" +
                "<li><b>Switch:</b> 通用开关逻辑</li></ul>" +
                "</body></html>";
        JOptionPane.showMessageDialog(this, helpText, "使用指南", JOptionPane.INFORMATION_MESSAGE);
    }

    private void handleService() {
        if (!isRunning) {
            saveConfig(); // 启动前保存当前输入的配置
            System.setProperty("log.toFile", String.valueOf(logToFileCh.isSelected()));
            Configurator.reconfigure();

            mqttManager.start(
                    brokerF.getText(), subF.getText(), pubF.getText(),
                    pKeyF.getText(), snF.getText(), (String) typeCombo.getSelectedItem(),
                    logToFileCh.isSelected()
            );
            logger.info("🚀 转换服务启动 [类型: {}]", typeCombo.getSelectedItem());
            isRunning = true;
            startBtn.setText("停止服务");
            startBtn.setBackground(new Color(180, 50, 50));
        } else {
            mqttManager.stop();
            logger.info("🛑 转换服务已停止");
            isRunning = false;
            startBtn.setText("启动转换服务");
            startBtn.setBackground(new Color(76, 175, 80));
        }
    }

    private void appendLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + LocalTime.now().withNano(0) + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void setupTheme() {
        FlatDarkLaf.setup();
        UIManager.put("defaultFont", new Font("Microsoft YaHei", Font.PLAIN, 13));
    }

    static void main() {
        SwingUtilities.invokeLater(() -> new ModernGui().setVisible(true));
    }
}