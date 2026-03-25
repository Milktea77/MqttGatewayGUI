package org.liang.cmd;

import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 下行报文组装器 + 设备转换器注册表
 * 新增设备时，只需在下方 static 块中添加一行 register()，
 * UI 下拉框会自动出现新设备类型，其余代码无需改动。
 */
public class DownlinkPayloadTransformer {
    private static final Logger logger = LogManager.getLogger(DownlinkPayloadTransformer.class);

    // 设备类型注册表（有序，决定 UI 下拉框顺序）
    private static final Map<String, ICommandTransformer> REGISTRY = new LinkedHashMap<>();

    static {
        // ↓↓↓ 新增设备：在此处追加 register() 一行即可 ↓↓↓
        register("Switch",     new SwitchCommandTransformer());
        register("DuctlessAC", new DuctlessAcCommandTransformer());
        register("WS51x",      new WS51xCommandTransformer());
        register("WS52x",      new WS51xCommandTransformer()); // 协议与 WS51x 完全相同，共用同一 Transformer
    }

    /** 注册设备类型（也可在运行时动态调用） */
    public static void register(String category, ICommandTransformer transformer) {
        REGISTRY.put(category, transformer);
    }

    /** 返回所有已注册的设备类型，供 UI 下拉框使用 */
    public static String[] getCategories() {
        return REGISTRY.keySet().toArray(new String[0]);
    }

    /**
     * 组装完整下行报文 JSON
     *
     * @param category 设备类型（与注册表 key 一致）
     * @param devEui   目标设备 EUI
     * @param m        指令名称
     * @param v        指令值
     * @param fPort    应用端口（由用户在界面输入）
     * @return 序列化后的 JSON 字符串
     */
    public static String pack(String category, String devEui, String m, Object v, int fPort) {
        ICommandTransformer transformer = REGISTRY.get(category);
        if (transformer == null) {
            throw new IllegalArgumentException("未注册的设备类型: " + category);
        }

        logger.info("🎯 执行转换策略: [类型: {}] | 指令: {} | fPort: {}", category, m, fPort);
        String base64Data = transformer.buildDownlinkData(m, v);

        JsonObject downlink = new JsonObject();
        downlink.addProperty("devEUI",    devEui);
        downlink.addProperty("confirmed", true);
        downlink.addProperty("fPort",     fPort);
        downlink.addProperty("data",      base64Data);

        return downlink.toString();
    }
}
