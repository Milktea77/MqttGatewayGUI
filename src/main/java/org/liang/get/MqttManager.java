package org.liang.get;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import java.nio.charset.StandardCharsets;

public class MqttManager {
    private MqttClient client;
    private final LogListener listener;

    public interface LogListener {
        void onUpdate(String msg);
    }

    public MqttManager(LogListener listener) {
        this.listener = listener;
    }

    // 增加了 deviceType 参数
    public void start(String broker, String subTopic, String pubTopic, String pKey, String sn, String deviceType) {
        new Thread(() -> {
            try {
                client = new MqttClient(broker, "Gate_" + System.currentTimeMillis(), new MemoryPersistence());
                MqttConnectOptions opt = new MqttConnectOptions();
                opt.setAutomaticReconnect(true);
                opt.setCleanSession(true);

                client.setCallback(new MqttCallbackExtended() {
                    public void connectComplete(boolean r, String u) {
                        listener.onUpdate("✅ 连接成功: " + u);
                        try { client.subscribe(subTopic); } catch (Exception e) {}
                    }
                    public void messageArrived(String t, MqttMessage m) {
                        String raw = new String(m.getPayload(), StandardCharsets.UTF_8);

                        // 修复处：传入 deviceType 参数
                        String result = MqttDataTransformer.transform(raw, pKey, sn, deviceType);

                        try {
                            client.publish(pubTopic, result.getBytes(StandardCharsets.UTF_8), 1, false);
                            listener.onUpdate("🔄 [" + deviceType + "] 转发成功: " + result);
                        } catch (Exception e) {
                            listener.onUpdate("❌ 发送失败: " + e.getMessage());
                        }
                    }
                    public void connectionLost(Throwable cause) { listener.onUpdate("⚠️ 连接丢失"); }
                    public void deliveryComplete(IMqttDeliveryToken token) {}
                });

                listener.onUpdate("正在尝试连接 " + broker + "...");
                client.connect(opt);

            } catch (MqttException e) {
                listener.onUpdate("❌ 启动失败: " + e.getMessage());
            }
        }).start();
    }

    public void stop() {
        try { if (client != null) client.disconnect(); } catch (Exception e) {}
    }
}