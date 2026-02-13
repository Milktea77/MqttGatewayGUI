package org.liang.cmd;

import com.formdev.flatlaf.FlatDarkLaf;
import com.google.gson.*;
import org.eclipse.paho.client.mqttv3.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ControlCommandEngine extends JFrame {
    private JTextField brokerField, subTopicField, pubTopicField;
    private JTextArea logArea;
    private JButton actionBtn;

    private MqttClient client;
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private boolean isConnected = false;

    public ControlCommandEngine() {
        // 1. 初始化界面前先设定外观
        FlatDarkLaf.setup();

        setTitle("ChirpStack Downlink Transformer Pro");
        setSize(900, 650);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // 居中显示
        initUI();
    }

    private void initUI() {
        // 使用更现代的边距
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // --- 配置区域 ---
        JPanel configPanel = new JPanel(new GridLayout(4, 2, 10, 10));

        // 自定义组件样式
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

        configPanel.add(new JLabel("")); // 占位
        actionBtn = new JButton("连接并启动转换");
        actionBtn.setFocusPainted(false);
        actionBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        actionBtn.addActionListener(e -> toggleConnection());
        // 初始状态颜色
        actionBtn.setBackground(new Color(60, 120, 60));
        actionBtn.setForeground(Color.WHITE);
        configPanel.add(actionBtn);

        // --- 日志区域 ---
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setBackground(new Color(25, 25, 25)); // 纯黑背景
        logArea.setForeground(new Color(0, 255, 100)); // 荧光绿
        logArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
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
        if (!isConnected) {
            startMqtt();
        } else {
            stopMqtt();
        }
    }

    private void startMqtt() {
        try {
            client = new MqttClient(brokerField.getText(), MqttClient.generateClientId(), null);
            MqttConnectOptions opt = new MqttConnectOptions();
            opt.setAutomaticReconnect(true);
            opt.setCleanSession(true);
            client.connect(opt);

            client.subscribe(subTopicField.getText(), (topic, message) -> {
                executor.submit(() -> handleIncomingControl(new String(message.getPayload(), StandardCharsets.UTF_8)));
            });

            isConnected = true;
            updateUI(true);
            appendLog("System: 成功连接至 MQTT Broker，开始转发业务逻辑。");
        } catch (MqttException e) {
            appendLog("Error: 连接失败 - " + e.getMessage());
        }
    }

    private void stopMqtt() {
        try {
            if (client != null) client.disconnect();
            isConnected = false;
            updateUI(false);
            appendLog("System: 已安全断开连接。");
        } catch (Exception e) { appendLog("Error: " + e.getMessage()); }
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
                boolean value = cmd.get("v").getAsBoolean();

                String base64Data = CommandTransformer.buildDownlinkData(m, value);

                JsonObject downlink = new JsonObject();
                downlink.addProperty("devEUI", devEui);
                downlink.addProperty("confirmed", true);
                downlink.addProperty("fport", 85);
                downlink.addProperty("data", base64Data);

                client.publish(pubTopicField.getText(), gson.toJson(downlink).getBytes(StandardCharsets.UTF_8), 0, false);

                appendLog("Forward: [" + devEui + "] " + m + " 为 " + (value ? "开启" : "关闭") + " (Base64: " + base64Data + ")");
            }
        } catch (Exception e) {
            appendLog("Transform Error: " + e.getMessage());
        }
    }

    private void updateUI(boolean connected) {
        SwingUtilities.invokeLater(() -> {
            brokerField.setEnabled(!connected);
            subTopicField.setEnabled(!connected);
            pubTopicField.setEnabled(!connected);
            actionBtn.setText(connected ? "停止服务 (Disconnect)" : "连接并启动转换");
            // 根据连接状态切换按钮背景色
            actionBtn.setBackground(connected ? new Color(150, 50, 50) : new Color(60, 120, 60));
        });
    }

    private void appendLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + LocalTime.now().withNano(0) + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        // 启动主程序
        SwingUtilities.invokeLater(() -> {
            ControlCommandEngine app = new ControlCommandEngine();
            app.setVisible(true);
        });
    }
}