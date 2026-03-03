package org.liang.get;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;

public class MqttDataTransformer {
    private static final Logger logger = LogManager.getLogger(MqttDataTransformer.class);

    public static String transform(String rawJson, String pKey, String sn, String deviceType) {
        try {
            JSONObject input = JSON.parseObject(rawJson);
            long now = System.currentTimeMillis() / 1000;

            // 1. 获取对应的解析器并执行具体的业务解析
            IDataParser parser = ParserFactory.getParser(deviceType);
            JSONObject devItem = parser.parse(input, now);

            // 2. 组装平台标准格式
            JSONObject output = new JSONObject(new LinkedHashMap<>());
            output.put("ver", "1.1.0");
            output.put("pKey", pKey);
            output.put("sn", sn);
            output.put("ts", now);

            JSONArray devsArray = new JSONArray();
            devsArray.add(devItem);
            output.put("devs", devsArray);

            return JSON.toJSONString(output);
        } catch (Exception e) {
            logger.error("数据解析转换失败: {}", e.getMessage());
            throw new RuntimeException("转换逻辑错误: " + e.getMessage());
        }
    }
}