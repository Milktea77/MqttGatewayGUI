package org.liang;

import com.formdev.flatlaf.FlatDarkLaf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.charset.StandardCharsets;

public class ModernGui extends JFrame {
    private static final Logger logger = LogManager.getLogger(ModernGui.class);
    private JTextField brokerF, subF, pubF, pKeyF, snF;
    private JTextArea logArea;
    private JButton startBtn;
    private MqttClient mqttClient;
    private boolean isRunning = false;

    public ModernGui() {
        setupTheme();
        setTitle("MQTT 智能协议转换网关");
        setSize(950, 650);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // 左侧配置面板
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setPreferredSize(new Dimension(300, 0));

        brokerF = addInput(leftPanel, "服务器地址:", "tcp://192.168.5.218:1883");
        subF = addInput(leftPanel, "订阅原始主题:", "/LoRA_Message/+/get");
        pubF = addInput(leftPanel, "发布标准主题:", "/LoRA_Message/transformed");
        pKeyF = addInput(leftPanel, "Product Key:", "Light");
        snF = addInput(leftPanel, "Serial Number:", "003");

        startBtn = new JButton("启动转换服务");
        startBtn.setPreferredSize(new Dimension(280, 45));
        startBtn.putClientProperty("JButton.buttonType", "roundRect");
        startBtn.addActionListener(e -> handleService());
        leftPanel.add(Box.createVerticalStrut(15));
        leftPanel.add(startBtn);

        // 右侧日志面板
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(180, 180, 180));
        logArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));

        mainPanel.add(leftPanel, BorderLayout.WEST);
        mainPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        add(mainPanel);
    }

    private void setupTheme() {
        FlatDarkLaf.setup();
        UIManager.put("defaultFont", new Font("Microsoft YaHei", Font.PLAIN, 13));
    }

    private JTextField addInput(JPanel p, String lab, String def) {
        p.add(new JLabel(lab));
        JTextField f = new JTextField(def);
        f.setMaximumSize(new Dimension(300, 35));
        p.add(f);
        p.add(Box.createVerticalStrut(10));
        return f;
    }

    private void handleService() {
        if (!isRunning) {
            startMqtt();
        } else {
            stopMqtt();
        }
    }

    private void startMqtt() {
        new Thread(() -> {
            try {
                mqttClient = new MqttClient(brokerF.getText(), "Gate_" + System.currentTimeMillis(), new MemoryPersistence());
                MqttConnectOptions opt = new MqttConnectOptions();
                opt.setAutomaticReconnect(true);
                opt.setCleanSession(true);

                mqttClient.setCallback(new MqttCallbackExtended() {
                    public void connectComplete(boolean r, String u) {
                        appendLog("✅ 连接成功: " + u);
                        try { mqttClient.subscribe(subF.getText()); } catch (Exception e) {}
                    }
                    public void messageArrived(String t, MqttMessage m) {
                        String raw = new String(m.getPayload(), StandardCharsets.UTF_8);
                        String result = MqttDataTransformer.transform(raw, pKeyF.getText(), snF.getText());
                        try {
                            mqttClient.publish(pubF.getText(), result.getBytes(StandardCharsets.UTF_8), 1, false);
                            appendLog("🔄 转换成功: " + result);
                        } catch (Exception e) { appendLog("❌ 转发失败: " + e.getMessage()); }
                    }
                    public void connectionLost(Throwable cause) { appendLog("⚠️ 连接丢失"); }
                    public void deliveryComplete(IMqttDeliveryToken token) {}
                });

                mqttClient.connect(opt);
                isRunning = true;
                SwingUtilities.invokeLater(() -> startBtn.setText("停止服务"));
            } catch (Exception e) {
                appendLog("❌ 启动失败: " + e.getMessage());
            }
        }).start();
    }

    private void stopMqtt() {
        try {
            if (mqttClient != null) mqttClient.disconnect();
            isRunning = false;
            startBtn.setText("启动转换服务");
            appendLog("⏹ 服务已手动停止");
        } catch (Exception e) { appendLog("停止出错: " + e.getMessage()); }
    }

    private void appendLog(String msg) {
        logger.info(msg); // 写入文件
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            // 自动清理：只保留最后 200 行
            if (logArea.getLineCount() > 200) {
                try {
                    int end = logArea.getLineEndOffset(logArea.getLineCount() - 201);
                    logArea.replaceRange("", 0, end);
                } catch (Exception ignored) {}
            }
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ModernGui().setVisible(true));
    }
}