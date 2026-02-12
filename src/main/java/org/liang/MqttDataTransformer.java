package org.liang;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.time.Instant;
import java.util.*;

public class MqttDataTransformer {
    // 排除掉网关自带的固定字段，剩下的全部视为传感器功能点
    private static final Set<String> BLACK_LIST = new HashSet<>(Arrays.asList(
            "applicationID", "cellularIP", "devEUI", "deviceName", "gatewayTime"
    ));

    public static String transform(String rawJson, String pKey, String sn) {
        JSONObject input = JSON.parseObject(rawJson);
        long now = Instant.now().getEpochSecond();

        JSONObject output = new JSONObject(new LinkedHashMap<>());
        JSONArray devsArray = new JSONArray();
        JSONObject devItem = new JSONObject(new LinkedHashMap<>());
        JSONArray dArray = new JSONArray();

        // 动态遍历：只要不是黑名单里的，且不以 _change 结尾，都转为功能 m
        input.forEach((key, value) -> {
            if (!BLACK_LIST.contains(key) && !key.endsWith("_change")) {
                JSONObject metric = new JSONObject(new LinkedHashMap<>());
                metric.put("v", value); // 保持原始类型
                metric.put("dq", 0);
                metric.put("m", key);
                metric.put("ts", now);
                dArray.add(metric);
            }
        });

        devItem.put("d", dArray);
        // 优先取 devEUI 作为设备标识，没有则取名字
        String devId = input.getString("devEUI") != null ? input.getString("devEUI") : input.getString("deviceName");
        devItem.put("dev", devId);
        devsArray.add(devItem);

        output.put("devs", devsArray);
        output.put("ver", "1.1.0");
        output.put("pKey", pKey);
        output.put("sn", sn);
        output.put("ts", now);

        return output.toJSONString();
    }
}