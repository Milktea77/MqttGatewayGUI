package org.liang.get;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.util.*;

public class SwitchDecoder implements IDataParser {
    private static final Set<String> BLACK_LIST = new HashSet<>(Arrays.asList(
            "applicationID", "cellularIP", "devEUI", "deviceName", "gatewayTime"
    ));

    @Override
    public JSONObject parse(JSONObject input, long now) {
        JSONObject devItem = new JSONObject(new LinkedHashMap<>());
        JSONArray dArray = new JSONArray();

        input.forEach((key, value) -> {
            if (!BLACK_LIST.contains(key) && !key.endsWith("_change")) {
                JSONObject metric = new JSONObject(new LinkedHashMap<>());
                metric.put("v", value);
                metric.put("dq", 0);
                metric.put("m", key);
                metric.put("ts", now);
                dArray.add(metric);
            }
        });

        devItem.put("d", dArray);
        String devId = input.getString("devEUI") != null ? input.getString("devEUI") : input.getString("deviceName");
        devItem.put("dev", devId);
        return devItem;
    }
}