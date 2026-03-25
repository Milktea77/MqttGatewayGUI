package org.liang.get;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.util.*;

/**
 * Milesight WS136 智能情景面板解码器
 *
 * 特殊逻辑：WS136 每次只上报被按下的那个按钮（值为 1），其余按钮不上报。
 * 为避免系统误认为多个按钮同时处于激活状态，本解码器在检测到任意
 * button_x = 1 时，自动补全所有 6 个按钮：被按下的保持 1，其余强制补 0。
 * 最终同一条消息中始终包含全部 6 个按钮的状态。
 */
public class WS136SSPanel implements IDataParser {

    /** WS136 固定按钮数量 */
    private static final int BUTTON_COUNT = 6;

    /** 不作为业务指标上报的元数据字段 */
    private static final Set<String> BLACK_LIST = new HashSet<>(Arrays.asList(
            "applicationID", "cellularIP", "devEUI", "deviceName",
            "gatewayTime", "rxInfo", "txInfo"
    ));

    @Override
    public JSONObject parse(JSONObject input, long now) {
        JSONObject devItem = new JSONObject(new LinkedHashMap<>());
        JSONArray dArray = new JSONArray();

        // 设备标识
        String devId = input.getString("devEUI") != null
                ? input.getString("devEUI")
                : input.getString("deviceName");
        devItem.put("dev", devId);

        // 找出本次被按下的按钮编号 (button_x = 1)，WS136 每次只会上报一个
        int pressedButton = -1;
        for (int i = 1; i <= BUTTON_COUNT; i++) {
            Object val = input.get("button_" + i);
            if (val != null && toInt(val) == 1) {
                pressedButton = i;
                break;
            }
        }

        // 遍历所有字段，跳过黑名单与 button 字段（button 统一在下方补全）
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();

            if (BLACK_LIST.contains(key)) continue;

            if (key.startsWith("button_")) {
                // 有按钮触发时，button 字段交由下方统一补全；
                // 没有任何按钮触发时（保底兜底），原样透传
                if (pressedButton == -1) {
                    dArray.add(buildMetric(key, entry.getValue(), now));
                }
                continue;
            }

            // 非 button 的业务字段直接透传
            dArray.add(buildMetric(key, entry.getValue(), now));
        }

        // 有按钮被触发：补全全部 6 个按钮，确保每条消息状态完整
        if (pressedButton != -1) {
            for (int i = 1; i <= BUTTON_COUNT; i++) {
                dArray.add(buildMetric("button_" + i, i == pressedButton ? 1 : 0, now));
            }
        }

        devItem.put("d", dArray);
        return devItem;
    }

    private static JSONObject buildMetric(String m, Object v, long ts) {
        JSONObject metric = new JSONObject(new LinkedHashMap<>());
        metric.put("v", v);
        metric.put("dq", 0);
        metric.put("m", m);
        metric.put("ts", ts);
        return metric;
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o).trim()); } catch (Exception e) { return 0; }
    }
}
