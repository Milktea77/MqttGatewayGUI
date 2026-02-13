package org.liang.get;

import com.formdev.flatlaf.FlatDarkLaf;
import org.liang.cmd.ControlCommandEngine;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ModernGui extends JFrame {
    // --- 成员变量（必须定义在类级别，方法才能访问） ---
    private JTextField brokerF, subF, pubF, pKeyF, snF;
    private JComboBox<String> typeCombo;
    private JTextArea logArea;
    private JButton startBtn;
    private MqttManager mqttManager;
    private boolean isRunning = false;

    public ModernGui() {
        setupTheme();
        setTitle("LoRA/星纵 MQTT报文转换器");
        setSize(1100, 750);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // 初始化 MQTT 管理器（回调指向本地的 appendLog）
        mqttManager = new MqttManager(this::appendLog);

        JTabbedPane tabbedPane = new JTabbedPane();

        // 添加第一个选项卡：数据转换
        tabbedPane.addTab("数据协议转换", createTransformerPanel());

        // 添加第二个选项卡：指令控制
        // 提取 ControlCommandEngine 的内容面板
        try {
            ControlCommandEngine cmdEngine = new ControlCommandEngine();
            tabbedPane.addTab("指令下发控制", cmdEngine.getContentPane());
        } catch (Exception e) {
            appendLog("❌ 指令引擎加载失败: " + e.getMessage());
        }

        add(tabbedPane);
    }

    private JPanel createTransformerPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // --- 左侧配置区 ---
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setPreferredSize(new Dimension(300, 0));

        brokerF = addInput(leftPanel, "服务器地址:", "tcp://192.168.5.218:1883");
        subF = addInput(leftPanel, "订阅原始主题:", "/LoRA_Message/+/get");
        pubF = addInput(leftPanel, "发布标准主题:", "/LoRA_Message/transformed");
        pKeyF = addInput(leftPanel, "Product Key:", "Light");
        snF = addInput(leftPanel, "Serial Number:", "003");

        leftPanel.add(new JLabel("解析器类型:"));
        typeCombo = new JComboBox<>(new String[]{"Switch", "PeopleSensor", "ContactSensor"});
        typeCombo.setMaximumSize(new Dimension(300, 35));
        leftPanel.add(typeCombo);

        leftPanel.add(Box.createVerticalStrut(15));
        startBtn = new JButton("启动转换服务");
        startBtn.setPreferredSize(new Dimension(280, 45));
        startBtn.putClientProperty("JButton.buttonType", "roundRect");
        // 这里关联 handleService 方法
        startBtn.addActionListener(e -> handleService());
        leftPanel.add(startBtn);

        // --- 右侧日志区 ---
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(180, 180, 180));
        logArea.setFont(new Font("Microsoft Yahei", Font.PLAIN, 12));

        panel.add(leftPanel, BorderLayout.WEST);
        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        return panel;
    }

    // --- 业务逻辑处理 ---
    private void handleService() {
        if (!isRunning) {
            String selectedType = (String) typeCombo.getSelectedItem();
            mqttManager.start(
                    brokerF.getText(),
                    subF.getText(),
                    pubF.getText(),
                    pKeyF.getText(),
                    snF.getText(),
                    selectedType
            );
            isRunning = true;
            startBtn.setText("停止服务");
            startBtn.setBackground(new Color(180, 50, 50));
        } else {
            mqttManager.stop();
            isRunning = false;
            startBtn.setText("启动转换服务");
            startBtn.setBackground(null);
        }
    }

    private void appendLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private JTextField addInput(JPanel p, String lab, String def) {
        p.add(new JLabel(lab));
        JTextField f = new JTextField(def);
        f.setMaximumSize(new Dimension(300, 35));
        p.add(f);
        p.add(Box.createVerticalStrut(10));
        return f;
    }

    private void setupTheme() {
        FlatDarkLaf.setup();
        UIManager.put("defaultFont", new Font("Microsoft YaHei", Font.PLAIN, 13));
        UIManager.put("TabbedPane.showTabSeparators", true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ModernGui().setVisible(true));
    }
}