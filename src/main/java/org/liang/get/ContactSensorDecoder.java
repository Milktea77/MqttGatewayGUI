package org.liang.get;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

public class ContactSensorDecoder implements IDataParser {
    @Override
    public JSONObject parse(JSONObject input, long now) {
        JSONObject devItem = new JSONObject(new LinkedHashMap<>());
        JSONArray dArray = new JSONArray();

        // 定义需要提取的字段映射：JSON 原始 Key -> 转换后的 m
        Map<String, String> fieldMap = new LinkedHashMap<>();
        fieldMap.put("battery", "battery");
        fieldMap.put("magnet_status", "magnet_status");
        fieldMap.put("tamper_status", "tamper_status");

        // 动态遍历：存在哪个字段就转换哪个
        for (Map.Entry<String, String> entry : fieldMap.entrySet()) {
            String sourceKey = entry.getKey();
            String targetM = entry.getValue();

            if (input.containsKey(sourceKey)) {
                JSONObject metric = new JSONObject(new LinkedHashMap<>());
                metric.put("v", input.get(sourceKey)); // 自动保持原始类型（Integer/Long等）
                metric.put("dq", 0);
                metric.put("m", targetM);
                metric.put("ts", now);
                dArray.add(metric);
            }
        }

        devItem.put("d", dArray);
        // 提取 devEUI 作为设备编号
        devItem.put("dev", input.getString("devEUI"));

        return devItem;
    }
}