package org.liang.cmd;

import com.formdev.flatlaf.FlatDarkLaf;
import com.google.gson.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.paho.client.mqttv3.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ControlCommandEngine extends JFrame {
    // 1. 集成 Log4j2
    private static final Logger logger = LogManager.getLogger(ControlCommandEngine.class);

    private JTextField brokerField, subTopicField, pubTopicField;
    private JTextArea logArea;
    private JButton actionBtn;

    private MqttClient client;
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private boolean isConnected = false;

    public ControlCommandEngine() {
        // 如果是嵌入到 ModernGui，这里不需要再 setup
        setTitle("ChirpStack Downlink Transformer Pro");
        setSize(900, 650);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        initUI(); // 修复：确保调用初始化方法
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // 配置区域
        JPanel configPanel = new JPanel(new GridLayout(4, 2, 10, 10));
        Font labelFont = new Font("Microsoft YaHei", Font.PLAIN, 13);

        configPanel.add(createLabel(" MQTT Broker 地址:", labelFont));
        brokerField = new JTextField("tcp://192.168.5.218:1883");
        configPanel.add(brokerField);

        configPanel.add(createLabel(" 订阅业务控制 (Sub):", labelFont));
        subTopicField = new JTextField("/LoRA_Message/switch/down");
        configPanel.add(subTopicField);

        configPanel.add(createLabel(" 转发下行主题 (Pub):", labelFont));
        pubTopicField = new JTextField("/LoRA_Message/switch/down/transformed");
        configPanel.add(pubTopicField);

        configPanel.add(new JLabel(""));
        actionBtn = new JButton("连接并启动转换");
        actionBtn.setFocusPainted(false);
        actionBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        actionBtn.addActionListener(e -> toggleConnection());
        actionBtn.setBackground(new Color(60, 120, 60));
        actionBtn.setForeground(Color.WHITE);
        configPanel.add(actionBtn);

        // 日志区域
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(new Color(25, 25, 25));
        logArea.setForeground(new Color(0, 255, 100));
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("实时转换日志"));

        mainPanel.add(configPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        setContentPane(mainPanel);
    }

    private JLabel createLabel(String text, Font font) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        return label;
    }

    private void toggleConnection() {
        if (!isConnected) startMqtt();
        else stopMqtt();
    }

    private void startMqtt() {
        try {
            client = new MqttClient(brokerField.getText(), MqttClient.generateClientId(), null);
            MqttConnectOptions opt = new MqttConnectOptions();
            opt.setAutomaticReconnect(true);
            opt.setCleanSession(true);
            client.connect(opt);

            // 修复：记录 Topic 解决未使用警告
            client.subscribe(subTopicField.getText(), (topic, message) -> {
                String logMsg = "📥 收到指令 [Topic: " + topic + "]";
                logger.info(logMsg);
                appendLog(logMsg);

                String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                executor.submit(() -> handleIncomingControl(payload));
            });

            isConnected = true;
            updateUI(true);
            logger.info("✅ 下行控制引擎已启动: {}", brokerField.getText());
        } catch (MqttException e) {
            logger.error("❌ 连接失败: {}", e.getMessage());
            appendLog("Error: 连接失败 - " + e.getMessage());
        }
    }

    private void stopMqtt() {
        try {
            if (client != null) client.disconnect();
            isConnected = false;
            updateUI(false);
            logger.info("🛑 下行控制引擎已停止");
            appendLog("System: 已安全断开连接。");
        } catch (Exception e) { logger.error("停止服务失败", e); }
    }

    private void handleIncomingControl(String jsonStr) {
        try {
            JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
            if (!"cmd/set".equals(root.get("type").getAsString())) return;

            JsonArray dataArray = root.getAsJsonArray("data");
            for (JsonElement el : dataArray) {
                JsonObject cmd = el.getAsJsonObject();
                String m = cmd.get("m").getAsString();
                String devEui = cmd.get("dev").getAsString();

                // 根据功能点自动选择转换器 (解决 DuctlessAcCommandTransformer 未使用警告)
                String base64Data;
                if (m.startsWith("switch_")) {
                    base64Data = SwitchCommandTransformer.buildDownlinkData(m, cmd.get("v").getAsBoolean());
                } else {
                    base64Data = DuctlessAcCommandTransformer.buildDownlinkData(m, cmd.get("v"));
                }

                JsonObject downlink = new JsonObject();
                downlink.addProperty("devEUI", devEui);
                downlink.addProperty("confirmed", true);
                downlink.addProperty("fport", 85);
                downlink.addProperty("data", base64Data);

                client.publish(pubTopicField.getText(), gson.toJson(downlink).getBytes(StandardCharsets.UTF_8), 0, false);
                logger.info("🔄 转发指令: [{}] {} -> Base64: {}", devEui, m, base64Data);
            }
        } catch (Exception e) { logger.error("转换处理异常", e); }
    }

    private void updateUI(boolean connected) {
        SwingUtilities.invokeLater(() -> {
            brokerField.setEnabled(!connected);
            subTopicField.setEnabled(!connected);
            pubTopicField.setEnabled(!connected);
            actionBtn.setText(connected ? "停止服务 (Disconnect)" : "连接并启动转换");
            actionBtn.setBackground(connected ? new Color(150, 50, 50) : new Color(60, 120, 60));
        });
    }

    private void appendLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + LocalTime.now().withNano(0) + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    static void main(String[] args) { // 修复：适配 Java 25 冗余警告
        SwingUtilities.invokeLater(() -> new ControlCommandEngine().setVisible(true));
    }
}