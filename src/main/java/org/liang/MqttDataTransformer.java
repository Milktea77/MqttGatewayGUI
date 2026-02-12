package org.liang;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.*;

public class MqttDataTransformer {
    // 使用 Set 存储固定字段，查找速度更快
    private static final Set<String> FIXED_FIELDS = new HashSet<>(Arrays.asList(
            "applicationID", "cellularIP", "devEUI", "deviceName", "gatewayTime"
    ));

    public static String transform(String rawJson, String pKey, String sn) {
        JSONObject input = JSON.parseObject(rawJson);
        long now = Instant.now().getEpochSecond();

        JSONObject output = new JSONObject(new LinkedHashMap<>());
        JSONArray devsArray = new JSONArray();
        JSONObject devItem = new JSONObject(new LinkedHashMap<>());
        JSONArray dArray = new JSONArray();

        // 动态遍历
        input.forEach((key, value) -> {
            // 排除固定字段 和 _change 结尾的字段
            if (!FIXED_FIELDS.contains(key) && !key.endsWith("_change")) {
                JSONObject metric = new JSONObject(new LinkedHashMap<>());
                metric.put("v", value);
                metric.put("dq", 0);
                metric.put("m", key);
                metric.put("ts", now);
                dArray.add(metric);
            }
        });

        devItem.put("d", dArray);
        devItem.put("dev", input.getString("devEUI"));
        devsArray.add(devItem);

        output.put("devs", devsArray);
        output.put("ver", "1.1.0");
        output.put("pKey", pKey);
        output.put("sn", sn);
        output.put("ts", now);

        return output.toJSONString();
    }
}