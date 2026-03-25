package org.liang.cmd;

import com.google.gson.*;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.eclipse.paho.client.mqttv3.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ControlCommandEngine extends JFrame {
    private static final Logger logger = LogManager.getLogger(ControlCommandEngine.class);

    // ── 新增：配置文件路径（与 ModernGui 共享 configs 目录）────────────
    private static final String CONFIG_FILE = "configs/cmd_settings.json";

    private JTextField brokerField, subTopicField, pubTopicField, fPortField;
    private JComboBox<String> categoryCombo;
    private JTextArea logArea;
    private JButton actionBtn;
    private JCheckBox logToFileCh;

    private MqttClient client;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private boolean isConnected = false;

    public ControlCommandEngine() {
        setTitle("ChirpStack Downlink Transformer Pro");
        setSize(900, 720);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        initUI();
        loadConfig(); // ── 新增：启动时加载配置 ──
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel configPanel = new JPanel(new GridLayout(6, 2, 10, 10));

        configPanel.add(new JLabel(" MQTT Broker 地址:"));
        brokerField = new JTextField("tcp://192.168.5.218:1883");
        configPanel.add(brokerField);

        configPanel.add(new JLabel(" 订阅业务控制 (Sub):"));
        subTopicField = new JTextField("/LoRA_Message/switch/down");
        configPanel.add(subTopicField);

        configPanel.add(new JLabel(" 转发下行主题 (Pub):"));
        pubTopicField = new JTextField("/LoRA_Message/switch/down/transformed");
        configPanel.add(pubTopicField);

        configPanel.add(new JLabel(" 目标设备转换协议:"));
        categoryCombo = new JComboBox<>(DownlinkPayloadTransformer.getCategories());
        configPanel.add(categoryCombo);

        configPanel.add(new JLabel(" 下行端口 fPort (常用: 10 / 85):"));
        fPortField = new JTextField("85");
        configPanel.add(fPortField);

        logToFileCh = new JCheckBox("同步写入本地日志文件 (Rolling File)");
        logToFileCh.setSelected(true);
        configPanel.add(logToFileCh);

        actionBtn = new JButton("连接并启动转换");
        actionBtn.addActionListener(_ -> toggleConnection());
        actionBtn.setBackground(new Color(60, 120, 60));
        actionBtn.setForeground(Color.WHITE);
        configPanel.add(actionBtn);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(new Color(25, 25, 25));
        logArea.setForeground(new Color(0, 255, 100));
        logArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        logArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("实时指令转换日志"));

        mainPanel.add(configPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        setContentPane(mainPanel);
    }

    // ── 新增：保存配置 ────────────────────────────────────────────────────
    private void saveConfig() {
        JSONObject json = new JSONObject();
        json.put("broker",    brokerField.getText());
        json.put("sub",       subTopicField.getText());
        json.put("pub",       pubTopicField.getText());
        json.put("fPort",     fPortField.getText());
        json.put("category",  categoryCombo.getSelectedItem());
        json.put("logEnabled", logToFileCh.isSelected());

        try {
            Files.createDirectories(Paths.get("configs"));
            Files.writeString(Paths.get(CONFIG_FILE), json.toJSONString(), StandardCharsets.UTF_8);
            appendLog("💾 配置已自动保存");
            logger.info("💾 指令引擎配置已保存至 {}", CONFIG_FILE);
        } catch (IOException e) {
            appendLog("⚠️ 配置保存失败: " + e.getMessage());
            logger.warn("配置保存失败", e);
        }
    }

    // ── 新增：加载配置 ────────────────────────────────────────────────────
    private void loadConfig() {
        Path path = Paths.get(CONFIG_FILE);
        if (!Files.exists(path)) return;

        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            JSONObject json = JSON.parseObject(content);

            brokerField.setText(json.getString("broker"));
            subTopicField.setText(json.getString("sub"));
            pubTopicField.setText(json.getString("pub"));
            fPortField.setText(json.getString("fPort"));
            categoryCombo.setSelectedItem(json.getString("category"));
            logToFileCh.setSelected(json.getBooleanValue("logEnabled"));

            appendLog("📂 已从本地加载历史配置");
            logger.info("📂 已加载指令引擎配置: {}", CONFIG_FILE);
        } catch (Exception e) {
            appendLog("⚠️ 配置文件读取异常: " + e.getMessage());
            logger.error("配置读取失败", e);
        }
    }

    private void toggleConnection() {
        if (!isConnected) startMqtt();
        else stopMqtt();
    }

    private void startMqtt() {
        saveConfig(); // ── 新增：启动前保存当前配置 ──

        Level level = logToFileCh.isSelected() ? Level.INFO : Level.OFF;
        Configurator.setLevel("RollingFile", level);

        try {
            appendLog("⏳ 系统: 正在连接至 " + brokerField.getText() + "...");
            client = new MqttClient(brokerField.getText(), MqttClient.generateClientId(), null);
            MqttConnectOptions opt = new MqttConnectOptions();
            opt.setAutomaticReconnect(true);
            client.connect(opt);

            appendLog("✅ 系统: 成功连接至 MQTT Broker，已进入监听状态");
            logger.info("🚀 下行控制引擎启动成功");

            client.subscribe(subTopicField.getText(), (topic, message) -> {
                logger.info("📥 收到下行指令 | Topic: {}", topic);
                appendLog("📥 收到业务请求 [Topic: " + topic + "]");

                String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                executor.submit(() -> handleIncomingControl(payload));
            });

            isConnected = true;
            updateUI(true);
        } catch (MqttException e) {
            appendLog("❌ 系统: 连接失败 - " + e.getMessage());
            logger.error("MQTT 连接异常", e);
        }
    }

    private void handleIncomingControl(String jsonStr) {
        try {
            JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
            if (!"cmd/set".equals(root.get("type").getAsString())) return;

            String selectedCategory = (String) categoryCombo.getSelectedItem();

            JsonArray dataArray = root.getAsJsonArray("data");
            for (JsonElement el : dataArray) {
                JsonObject cmd = el.getAsJsonObject();
                String m      = cmd.get("m").getAsString();
                String devEui = cmd.get("dev").getAsString();
                Object v = cmd.get("v").isJsonPrimitive() && cmd.get("v").getAsJsonPrimitive().isBoolean()
                        ? cmd.get("v").getAsBoolean() : cmd.get("v").getAsString();

                int fPort;
                try {
                    fPort = Integer.parseInt(fPortField.getText().trim());
                } catch (NumberFormatException e) {
                    appendLog("❌ fPort 输入有误，请填写数字（如 85 或 10）");
                    return;
                }

                String finalMqttJson = DownlinkPayloadTransformer.pack(selectedCategory, devEui, m, v, fPort);
                client.publish(pubTopicField.getText(), finalMqttJson.getBytes(StandardCharsets.UTF_8), 0, false);

                appendLog("🔄 转换成功: [" + devEui + "] 已按 " + selectedCategory + " 协议封装下发");
                logger.info("✅ 转发下行报文: {}", finalMqttJson);
            }
        } catch (Exception e) {
            String err = "❌ 转换失败: " + e.getMessage();
            appendLog(err);
            logger.error(err, e);
        }
    }

    private void stopMqtt() {
        try {
            if (client != null) client.disconnect();
            isConnected = false;
            updateUI(false);
            appendLog("🛑 系统: 服务已停止。");
        } catch (Exception e) { logger.error("停止服务失败", e); }
    }

    private void updateUI(boolean connected) {
        SwingUtilities.invokeLater(() -> {
            brokerField.setEnabled(!connected);
            subTopicField.setEnabled(!connected);
            pubTopicField.setEnabled(!connected);
            categoryCombo.setEnabled(!connected);
            fPortField.setEnabled(!connected);
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

    static void main() {
        SwingUtilities.invokeLater(() -> new ControlCommandEngine().setVisible(true));
    }
}