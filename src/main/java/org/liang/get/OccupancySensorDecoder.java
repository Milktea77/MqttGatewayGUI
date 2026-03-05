package org.liang.get;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 星纵 OccupancySensor (如 VS330) 数据转换器
 * 参考手册指令说明：01(电量), 03(温度), 04(湿度), 05(总人数), 07(光照), 08(检测状态)
 */
public class OccupancySensorDecoder implements IDataParser {

    @Override
    public JSONObject parse(JSONObject input, long now) {
        // 使用 LinkedHashMap 保持 JSON 字段输出顺序
        JSONObject devItem = new JSONObject(new LinkedHashMap<>());
        JSONArray dArray = new JSONArray();

        // 1. 定义功能映射：MQTT 中的 Key -> 平台识别的功能名 m
        // 对照手册：
        Map<String, String> fieldMap = new LinkedHashMap<>();
        fieldMap.put("battery", "battery");                       // 75 (电量)
        fieldMap.put("temperature", "temperature");               // 67 (温度)
        fieldMap.put("humidity", "humidity");                     // 68 (湿度)
        fieldMap.put("people_total_counts", "people_total_counts"); // fd (区域内总人数)
        fieldMap.put("detection_status", "detection_status");     // f4 (检测状态: 00-正常, 01-无法检测)
        fieldMap.put("illuminance_status", "light_status");       // ff (光照状态: 01-明亮, 00-昏暗)

        // 2. 动态遍历并转换
        for (Map.Entry<String, String> entry : fieldMap.entrySet()) {
            String sourceKey = entry.getKey();
            String targetM = entry.getValue();

            if (input.containsKey(sourceKey)) {
                JSONObject metric = new JSONObject(new LinkedHashMap<>());
                metric.put("v", input.get(sourceKey)); // 保持原始数值类型（Double/Integer）
                metric.put("dq", 0);                   // 数据质量默认 0
                metric.put("m", targetM);
                metric.put("ts", now);
                dArray.add(metric);
            }
        }

        // 3. 封装设备信息
        devItem.put("d", dArray);
        devItem.put("dev", input.getString("devEUI")); // 使用 devEUI 作为设备唯一标识
        devItem.put("ts", now);

        return devItem;
    }
}