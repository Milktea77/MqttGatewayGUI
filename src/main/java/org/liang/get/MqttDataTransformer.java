package org.liang.get;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class MqttDataTransformer {
    private static final Map<String, IDataParser> PARSERS = new HashMap<>();

    static {
        // 注册解析器，key 建议全小写方便匹配
        PARSERS.put("switch", new SwitchDecoder());
        PARSERS.put("peoplesensor", new PeopleSensorDecoder());
        // 新增门磁解析器
        PARSERS.put("contactsensor", new ContactSensorDecoder());
    }

    public static String transform(String rawJson, String pKey, String sn, String type) {
        JSONObject input = JSON.parseObject(rawJson);
        long now = Instant.now().getEpochSecond();

        // 根据传入的 type 选择解析器，默认使用 switch
        IDataParser parser = PARSERS.getOrDefault(type.toLowerCase(), new SwitchDecoder());
        JSONObject devItem = parser.parse(input, now);

        // 组装最终报文
        JSONObject output = new JSONObject(new LinkedHashMap<>());
        JSONArray devsArray = new JSONArray();
        devsArray.add(devItem);

        output.put("devs", devsArray);
        output.put("ver", "1.1.0");
        output.put("pKey", pKey);
        output.put("sn", sn);
        output.put("ts", now);

        return output.toJSONString();
    }
}