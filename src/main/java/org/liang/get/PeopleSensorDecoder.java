package org.liang.get;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.util.LinkedHashMap;

public class PeopleSensorDecoder implements IDataParser {
    @Override
    public JSONObject parse(JSONObject input, long now) {
        JSONObject devItem = new JSONObject(new LinkedHashMap<>());
        JSONArray dArray = new JSONArray();

        // 1. 提取功能点：将 current_total 转换为标准格式
        if (input.containsKey("current_total")) {
            JSONObject metric = new JSONObject(new LinkedHashMap<>());
            // v: 对应 current_total 的数值 (4)
            metric.put("v", input.get("current_total"));
            metric.put("dq", 0);
            // m: 对应键名 "current_total"
            metric.put("m", "current_total");
            metric.put("ts", now);
            dArray.add(metric);
        }

        // 2. 提取设备标识：从 device_info 层级中获取 device_sn
        String devSn = "";
        JSONObject deviceInfo = input.getJSONObject("device_info");
        if (deviceInfo != null) {
            devSn = deviceInfo.getString("device_sn");
        }

        devItem.put("d", dArray);
        devItem.put("dev", devSn);

        return devItem;
    }
}