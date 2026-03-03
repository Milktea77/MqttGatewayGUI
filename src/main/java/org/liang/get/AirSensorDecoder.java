package org.liang.get;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 空气传感器解码器 (AirSensor_monitor)
 * 处理字段：humidity, light_level, pir, pm10, pm2_5, pressure, temperature, tvoc, co2, battery
 */
public class AirSensorDecoder implements IDataParser {

    @Override
    public JSONObject parse(JSONObject input, long now) {
        // 创建设备项容器，使用 LinkedHashMap 保持 JSON 字段顺序
        JSONObject devItem = new JSONObject(new LinkedHashMap<>());
        JSONArray dArray = new JSONArray();

        // 1. 定义字段映射：原始 JSON 中的 Key -> 目标协议中的功能名 m
        // 这种方式可以非常方便地适配字段名不一致的情况
        Map<String, String> fieldMap = new LinkedHashMap<>();
        fieldMap.put("humidity", "humidity");
        fieldMap.put("light_level", "light_level");
        fieldMap.put("pir", "pir");
        fieldMap.put("pm10", "pm10");
        fieldMap.put("pm2_5", "pm2_5");
        fieldMap.put("pressure", "pressure");
        fieldMap.put("temperature", "temperature");
        fieldMap.put("tvoc", "tvoc");
        fieldMap.put("co2", "co2");
        fieldMap.put("battery", "battery");

        // 2. 动态遍历处理
        for (Map.Entry<String, String> entry : fieldMap.entrySet()) {
            String sourceKey = entry.getKey();
            String targetM = entry.getValue();

            // 检查输入 JSON 中是否包含该字段
            if (input.containsKey(sourceKey)) {
                JSONObject metric = new JSONObject(new LinkedHashMap<>());

                // v: 直接获取原始值（FastJSON2 会自动识别 Double/Integer/Boolean）
                metric.put("v", input.get(sourceKey));
                // dq: 固定数据质量为 0
                metric.put("dq", 0);
                // m: 映射后的功能名
                metric.put("m", targetM);
                // ts: 外部传入的时间戳
                metric.put("ts", now);

                dArray.add(metric);
            }
        }

        // 3. 装载结果
        // 列表数据
        devItem.put("d", dArray);
        // 设备编号：使用 devEUI
        devItem.put("dev", input.getString("devEUI"));

        return devItem;
    }
}