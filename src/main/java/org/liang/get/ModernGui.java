package org.liang.get;

import com.formdev.flatlaf.FlatDarkLaf;
import org.liang.cmd.ControlCommandEngine;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ModernGui extends JFrame {
    private JTextField brokerF, subF, pubF, pKeyF, snF;
    private JComboBox<String> typeCombo;
    private JTextArea logArea;
    private JButton startBtn, helpBtn; // 新增使用说明按钮
    private MqttManager mqttManager;
    private boolean isRunning = false;

    public ModernGui() {
        setupTheme();
        setTitle("LoRA/星纵 MQTT报文转换器");
        setSize(1100, 750);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        mqttManager = new MqttManager(this::appendLog);

        JTabbedPane tabbedPane = new JTabbedPane();
        // 设置选项卡区域深色背景以统一视觉
        tabbedPane.putClientProperty("JTabbedPane.tabType", "card");

        tabbedPane.addTab(" 数据协议转换 ", createTransformerPanel());

        try {
            ControlCommandEngine cmdEngine = new ControlCommandEngine();
            tabbedPane.addTab(" 指令下发控制 ", cmdEngine.getContentPane());
        } catch (Exception e) {
            appendLog("❌ 指令引擎加载失败: " + e.getMessage());
        }

        add(tabbedPane);
    }

    private JPanel createTransformerPanel() {
        // 1. 创建主面板，背景设置为深色调
        JPanel panel = new JPanel(new  BorderLayout(15, 15));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        panel.setBackground(new Color(35, 35, 35));

        // --- 左侧配置区 ---
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setPreferredSize(new Dimension(300, 0));
        leftPanel.setOpaque(false); // 使其背景透明，透出 panel 的颜色

        // 2. 添加输入组件，内部已处理对齐
        brokerF = addInput(leftPanel, "服务器地址:", "tcp://192.168.5.218:1883");
        subF = addInput(leftPanel, "订阅原始主题:", "/LoRA_Message/+/get");
        pubF = addInput(leftPanel, "发布标准主题:", "/LoRA_Message/transformed");
        pKeyF = addInput(leftPanel, "Product Key:", "Light");
        snF = addInput(leftPanel, "Serial Number:", "003");

        // 3. 解析器选择器居中处理
        JLabel typeLab = new JLabel("解析器类型:");
        typeLab.setForeground(new Color(200, 200, 200));
        typeLab.setAlignmentX(Component.CENTER_ALIGNMENT); // 水平居中
        leftPanel.add(typeLab);

        typeCombo = new JComboBox<>(new String[]{"Switch", "PeopleSensor", "ContactSensor"});
        typeCombo.setMaximumSize(new Dimension(280, 35));
        typeCombo.setAlignmentX(Component.CENTER_ALIGNMENT); // 水平居中
        leftPanel.add(typeCombo);

        // 4. 弹性间距推动按钮
        leftPanel.add(Box.createVerticalStrut(25));

        // 5. 启动按钮居中
        startBtn = new JButton("启动转换服务");
        startBtn.setMaximumSize(new Dimension(220, 40));
        startBtn.putClientProperty("JButton.buttonType", "roundRect");
        startBtn.setAlignmentX(Component.CENTER_ALIGNMENT); // 水平居中
        startBtn.addActionListener(e -> handleService());
        leftPanel.add(startBtn);

        leftPanel.add(Box.createVerticalStrut(12));

        // 6. 使用说明按钮居中
        helpBtn = new JButton(" 使用说明 ");
        helpBtn.setMaximumSize(new Dimension(220, 40));
        helpBtn.putClientProperty("JButton.buttonType", "roundRect");
        helpBtn.putClientProperty("JButton.outlineWidth", 1);
        helpBtn.setAlignmentX(Component.CENTER_ALIGNMENT); // 水平居中
        helpBtn.addActionListener(e -> showHelpDialog());
        leftPanel.add(helpBtn);

        // --- 右侧日志区 ---
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(new Color(25, 25, 25)); // 保持深色背景
        logArea.setForeground(new Color(180, 180, 180));
        logArea.setFont(new Font("Microsoft Yahei", Font.PLAIN, 12));
        logArea.setBorder(new EmptyBorder(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));

        panel.add(leftPanel, BorderLayout.WEST);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 配合使用的 addInput 辅助方法，确保所有配置组件都居中对齐
     */
    private JTextField addInput(JPanel p, String lab, String def) {
        JLabel label = new JLabel(lab);
        label.setForeground(new Color(200, 200, 200));
        label.setAlignmentX(Component.CENTER_ALIGNMENT); // 标签水平居中
        p.add(label);

        JTextField f = new JTextField(def);
        f.setMaximumSize(new Dimension(280, 35));
        f.setAlignmentX(Component.CENTER_ALIGNMENT); // 输入框水平居中
//        f.setHorizontalAlignment(JTextField.CENTER); // 文字内容居中
        p.add(f);

        p.add(Box.createVerticalStrut(10));
        return f;
    }

    // --- 新增：弹出帮助对话框的方法 ---
    private void showHelpDialog() {
        String helpText = "<html>" +
                "<body style='width: 350px; padding: 10px; font-family: Microsoft YaHei;'>" +
                "<h2 style='color: #4CAF50;'>📖 使用说明</h2>" +
                "<hr>" +
                "<b>1. 服务器配置：</b><br>" +
                "输入 MQTT Broker 的地址（如 tcp://ip:1883）。<br><br>" +
                "<b>2. 主题配置：</b><br>" +
                "<ul>" +
                "  <li><b>订阅主题：</b>网关接收 LoRA 设备原始报文的主题。</li>" +
                "  <li><b>发布主题：</b>网关将转换后的标准 JSON 发送到的主题。</li>" +
                "</ul>" +
                "<b>3. 解析器选择：</b><br>" +
                "<ul>" +
                "  <li><b>Switch:</b> 通用逻辑，自动排除网关字段。</li>" +
                "  <li><b>PeopleSensor:</b> 针对人体/区域人数统计器逻辑。</li>" +
                "  <li><b>ContactSensor:</b> 针对门磁/磁吸状态传感器逻辑。</li>" +
                "</ul>" +
                "<b>4. 运行状态：</b><br>" +
                "点击启动后，右侧日志将实时显示转换成功或失败的信息。" +
                "<hr>" +
                "<footer style='font-size: 10px; color: gray;'>v2.1 合并版 | 基于 FlatLaf 现代主题</footer>" +
                "</body></html>";

        JOptionPane.showMessageDialog(this, helpText, "使用指南", JOptionPane.INFORMATION_MESSAGE);
    }

    private void handleService() {
        if (!isRunning) {
            String selectedType = (String) typeCombo.getSelectedItem();
            mqttManager.start(
                    brokerF.getText(), subF.getText(), pubF.getText(),
                    pKeyF.getText(), snF.getText(), selectedType
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
            logArea.append("[" + java.time.LocalTime.now().withNano(0) + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void setupTheme() {
        FlatDarkLaf.setup();
        UIManager.put("defaultFont", new Font("Microsoft YaHei", Font.PLAIN, 13));
        UIManager.put("TabbedPane.showTabSeparators", true);
        UIManager.put("TabbedPane.background", new Color(40, 40, 40));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ModernGui().setVisible(true));
    }
}