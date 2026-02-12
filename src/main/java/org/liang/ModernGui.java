package org.liang;

import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ModernGui extends JFrame {
    private JTextField brokerF, subF, pubF, pKeyF, snF;
    private JTextArea logArea;
    private JButton startBtn;
    private MqttManager mqttManager;
    private boolean isRunning = false;

    public ModernGui() {
        FlatDarkLaf.setup(); // 应用现代皮肤
        setTitle("MQTT 智能网关转换器");
        setSize(900, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // 主布局
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // 左侧配置区
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setPreferredSize(new Dimension(280, 0));

        brokerF = addInput(leftPanel, "服务器地址:", "tcp://192.168.5.218:1883");
        subF = addInput(leftPanel, "订阅主题:", "/LoRA_Message/+/get");
        pubF = addInput(leftPanel, "发布主题:", "/transformed/data");
        pKeyF = addInput(leftPanel, "Product Key:", "Light");
        snF = addInput(leftPanel, "Serial Number:", "003");

        startBtn = new JButton("启动转换服务");
        startBtn.setPreferredSize(new Dimension(250, 45));
        startBtn.putClientProperty("JButton.buttonType", "roundRect"); // 圆角
        startBtn.addActionListener(e -> handleService());
        leftPanel.add(Box.createVerticalStrut(10));
        leftPanel.add(startBtn);

        // 右侧日志区
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(new Color(35, 35, 35));
        // 这里的字体必须支持中文，Consolas 无法显示中文
        logArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));

        mainPanel.add(leftPanel, BorderLayout.WEST);
        mainPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        add(mainPanel);

        mqttManager = new MqttManager(msg -> SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }));
    }

    private JTextField addInput(JPanel p, String lab, String def) {
        p.add(new JLabel(lab));
        JTextField f = new JTextField(def);
        f.setMaximumSize(new Dimension(280, 35));
        p.add(f);
        p.add(Box.createVerticalStrut(10));
        return f;
    }

    private void handleService() {
        if (!isRunning) {
            mqttManager.start(brokerF.getText(), subF.getText(), pubF.getText(), pKeyF.getText(), snF.getText());
            startBtn.setText("停止服务");
            isRunning = true;
        } else {
            mqttManager.stop();
            startBtn.setText("启动转换服务");
            isRunning = false;
        }
    }

    public static void main(String[] args) {
        // 1. 强制设置皮肤
        com.formdev.flatlaf.FlatDarkLaf.setup();

        // 2. 注入全局中文支持字体
        Font chineseFont = new Font("Microsoft YaHei", Font.PLAIN, 13); // 微软雅黑
        java.util.Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof javax.swing.plaf.FontUIResource) {
                UIManager.put(key, chineseFont);
            }
        }

        SwingUtilities.invokeLater(() -> new ModernGui().setVisible(true));
    }
}