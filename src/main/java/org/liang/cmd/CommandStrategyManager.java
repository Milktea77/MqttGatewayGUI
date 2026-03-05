package org.liang.cmd;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CommandStrategyManager {
    private static final Logger logger = LogManager.getLogger(CommandStrategyManager.class);

    /**
     * @param category UI 下拉框选中的设备类型 (SWITCH / DUCTLESS_AC)
     */
    public static String getBase64Payload(String category, String m, Object v) {
        logger.info("🎯 执行手动指定策略: [类型: {}] | 指令: {}", category, m);

        return switch (category) {
            case "Switch" -> SwitchCommandTransformer.buildDownlinkData(m, (Boolean) v);
            case "DuctlessAC" -> DuctlessAcCommandTransformer.buildDownlinkData(m, v);
            default -> throw new IllegalArgumentException("未选择有效的设备转换类型");
        };
    }
}