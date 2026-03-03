package org.liang.get;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import java.nio.charset.StandardCharsets;

public class MqttManager {
    private MqttClient client;
    private final LogListener listener;
    // 定义 Log4j2 日志记录器
    private static final Logger logger = LogManager.getLogger(MqttManager.class);

    public interface LogListener {
        void onUpdate(String msg);
    }

    public MqttManager(LogListener listener) {
        this.listener = listener;
    }

    /**
     * 启动 MQTT 转换服务
     * @param saveLog 是否将日志保存到本地文件
     */
    public void start(String broker, String subTopic, String pubTopic, String pKey, String sn, String deviceType, boolean saveLog) {
        // 1. 设置系统属性，供 log4j2.xml 内部的 ScriptFilter 或属性读取
        System.setProperty("log.toFile", String.valueOf(saveLog));
        // 重新配置 Log4j2 以应用新的属性设置
        Configurator.reconfigure();

        new Thread(() -> {
            try {
                logger.info("正在初始化 MQTT 客户端, Broker: {}, 类型: {}", broker, deviceType);
                client = new MqttClient(broker, "Gate_" + System.currentTimeMillis(), new MemoryPersistence());

                MqttConnectOptions opt = new MqttConnectOptions();
                opt.setAutomaticReconnect(true);
                opt.setCleanSession(true);

                client.setCallback(new MqttCallbackExtended() {
                    @Override
                    public void connectComplete(boolean reconnect, String serverURI) {
                        String msg = reconnect ? "✅ 自动重连成功: " : "✅ 连接成功: ";
                        logger.info(msg + serverURI);
                        listener.onUpdate(msg + serverURI);

                        try {
                            client.subscribe(subTopic);
                            logger.info("已成功订阅主题: {}", subTopic);
                        } catch (MqttException e) {
                            // 修复空 catch: 记录日志并反馈到 UI
                            logger.error("订阅失败: {}", e.getMessage());
                            listener.onUpdate("❌ 订阅失败: " + e.getMessage());
                        }
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) {
                        String raw = new String(message.getPayload(), StandardCharsets.UTF_8);
                        try {
                            // 调用重构后的转换方法
                            String result = MqttDataTransformer.transform(raw, pKey, sn, deviceType);

                            client.publish(pubTopic, result.getBytes(StandardCharsets.UTF_8), 1, false);
                            listener.onUpdate("🔄 [" + deviceType + "] 转换成功");
                            logger.info("转发报文: {}", result);
                        } catch (Exception e) {
                            // 这里不再是空 catch，记录到 Log4j2
                            logger.error("消息处理失败: {}", e.getMessage());
                            listener.onUpdate("❌ 转发异常: " + e.getMessage());
                        }
                    }

                    @Override
                    public void connectionLost(Throwable cause) {
                        logger.warn("⚠️ MQTT 连接丢失: {}", cause != null ? cause.getMessage() : "原因未知");
                        listener.onUpdate("⚠️ 连接丢失");
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {
                        // 消息交付完成的回调
                    }
                });

                listener.onUpdate("正在尝试连接 " + broker + "...");
                client.connect(opt);

            } catch (MqttException e) {
                logger.error("MQTT 启动过程发生致命错误: {}", e.getMessage(), e);
                listener.onUpdate("❌ 启动失败: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 停止服务并释放资源
     */
    public void stop() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
                client.close();
                logger.info("MQTT 客户端已安全断开并关闭");
            }
        } catch (MqttException e) {
            logger.error("关闭 MQTT 连接时发生错误: {}", e.getMessage());
        }
    }

//    /**
//     * 根据设备类型获取对应的解析器
//     * 此处已修复 Switch 语法报错并解除了 AirSensorDecoder 等类的“未使用”警告
//     */
//    public IDataParser getParser(String type) {
//        return switch (type) {
//            case "AirSensor" -> new AirSensorDecoder();
//            case "ContactSensor" -> new ContactSensorDecoder();
//            case "PeopleSensor" -> new PeopleSensorDecoder();
//            default -> new SwitchDecoder();
//        };
//    }
}