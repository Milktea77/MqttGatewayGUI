package org.liang.get;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.util.LinkedHashMap;

public class PeopleSensorDecoder implements IDataParser {
    @Override
    public JSONObject parse(JSONObject input, long now) {
        JSONObject devItem = new JSONObject(new LinkedHashMap<>());
        JSONArray dArray = new JSONArray();

        // 定义需要兼容的人数统计字段名
        String[] targetKeys = {"people_count_all", "current_total"};

        for (String key : targetKeys) {
            if (input.containsKey(key)) {
                JSONObject metric = new JSONObject(new LinkedHashMap<>());
                // v: 提取对应的数值
                metric.put("v", input.get(key));
                metric.put("dq", 0);
                // m: 保持原样，报文里是哪个 key，解析出来就是哪个 key
                metric.put("m", key);
                metric.put("ts", now);
                dArray.add(metric);
            }
        }

        // 提取设备标识：统一从 devEUI 提取
        String devSn = input.getString("devEUI");

        devItem.put("d", dArray);
        devItem.put("dev", devSn != null ? devSn : "");

        return devItem;
    }
}