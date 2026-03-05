package org.liang.cmd;

import com.google.gson.JsonObject;

public class DownlinkPayloadTransformer {
    public static String pack(String category, String devEui, String m, Object v) {
        // 调用策略管理器，强制走选中的转换器
        String base64Data = CommandStrategyManager.getBase64Payload(category, m, v);

        JsonObject downlink = new JsonObject();
        downlink.addProperty("devEUI", devEui);
        downlink.addProperty("confirmed", true);
        downlink.addProperty("fport", 85);
        downlink.addProperty("data", base64Data);

        return downlink.toString();
    }
}