package org.liang.get;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用数据转换器
 * 逻辑：devEUI -> dev，其余字段全部转换为标准指标格式
 */
public class GenericSensorDecoder implements IDataParser {

    @Override
    public JSONObject parse(JSONObject input, long now) {
        // 使用 LinkedHashMap 确保输出 JSON 字段有序
        JSONObject devItem = new JSONObject(new LinkedHashMap<>());
        JSONArray dArray = new JSONArray();

        // 1. 处理设备唯一标识 (devEUI -> dev)
        String devEui = input.getString("devEUI");
        devItem.put("dev", devEui != null ? devEui : "UnknownDevice");

        // 2. 遍历所有 Key，将除 devEUI 之外的字段全部原样转换
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();

            // 跳过已经处理过的 devEUI
            if ("devEUI".equals(key)) {
                continue;
            }

            // 过滤不需要作为指标上报的元数据 (可选，如需完全原样则注释掉以下判断)
//            if ("applicationID".equals(key) || "deviceName".equals(key) || "gatewayTime".equals(key)) {
//                continue;
//            }

            // 包装为标准指标格式
            JSONObject metric = new JSONObject(new LinkedHashMap<>());
            metric.put("v", entry.getValue()); // 原样保留数值、字符串或布尔值
            metric.put("dq", 0);               // 数据质量默认 0
            metric.put("m", key);              // 键值对原样保留
            metric.put("ts", now);             // 使用当前时间戳

            dArray.add(metric);
        }

        devItem.put("d", dArray);
        return devItem;
    }
}