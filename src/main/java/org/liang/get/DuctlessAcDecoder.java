package org.liang.get;

import com.alibaba.fastjson2.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Milesight WT303 风管机面板数据转换器
 * 功能：将官方 JS 脚本生成的 MQTT 消息转换为 BMS 平台格式
 */
public class DuctlessAcDecoder implements IDataParser {

    /**
     * 核心转换逻辑：必须重写接口定义的 parse 方法
     * @param mqttData 传入的原始 JSONObject 数据
     * @param now 外部传入的时间戳
     * @return 转换后符合格式的 JSONObject
     */
    @Override
    public JSONObject parse(JSONObject mqttData, long now) {
        // 使用 LinkedHashMap 确保输出 JSON 字段有序
        JSONObject bmsData = new JSONObject(new LinkedHashMap<>());

        // 1. 设备基础信息提取 (JSONObject 直接支持 get 方法)
        bmsData.put("deviceId", mqttData.get("devEUI"));
        bmsData.put("deviceName", mqttData.get("deviceName"));
        bmsData.put("sn", mqttData.get("product_sn"));

        // 2. 环境指标 (直接透传)
        bmsData.put("env_temp", mqttData.get("temperature"));
        bmsData.put("env_humidity", mqttData.get("humidity"));
        bmsData.put("target_temp", mqttData.get("target_temperature"));

        // 3. 系统/面板状态转换
        int sysStatus = getInt(mqttData.get("system_status"));
        bmsData.put("power_state", sysStatus == 1 ? "ON" : "OFF");

        // 4. 温控模式映射
        int mode = getInt(mqttData.get("temperature_control_mode"));
        bmsData.put("work_mode", mapControlMode(mode));

        // 5. 风机状态映射
        int fanMode = getInt(mqttData.get("fan_mode"));
        bmsData.put("fan_speed_level", fanMode);
        bmsData.put("fan_speed_name", mapFanMode(fanMode));

        // 6. 阀门与运行状态
        bmsData.put("valve_opening", mqttData.get("valve_status"));

        // 7. 提取版本信息 (处理嵌套对象)
        if (mqttData.get("version") instanceof Map<?, ?> ver) {
            bmsData.put("fw_ver", ver.get("firmware_version"));
            bmsData.put("hw_ver", ver.get("hardware_version"));
        }

        // 8. 其他功能
        if (mqttData.containsKey("temperature_alarm_type")) {
            bmsData.put("alarm_code", mqttData.get("temperature_alarm_type"));
        }

        // 记得把当前时间戳也存进去（如果平台需要）
        bmsData.put("ts", now);

        return bmsData;
    }

    // --- 以下映射工具函数保持不变 ---

    private String mapControlMode(int mode) {
        return switch (mode) {
            case 0 -> "VENTILATION";
            case 1 -> "HEAT";
            case 2 -> "COOL";
            default -> "UNKNOWN";
        };
    }

    private String mapFanMode(int mode) {
        return switch (mode) {
            case 0 -> "AUTO";
            case 1 -> "LOW";
            case 2 -> "MEDIUM";
            case 3 -> "HIGH";
            default -> "OFF";
        };
    }

    private int getInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        return 0;
    }
}