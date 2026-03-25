package org.liang.cmd;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

/**
 * Milesight WS136 / WS156 智能场景面板下行指令转换器
 * 仅开放基础运维指令，不涉及 D2D 密钥/频率等高级配置。
 * 支持的指令 m 值:
 *   report_interval   上报周期, value: 纯数字(秒) 或 "20m" / "1200s"
 *   reboot            重启设备, value: 忽略
 *   discard_delay     丢弃延迟包功能, value: true=启用丢弃, false=禁用(默认延迟发送)
 */
public class SSPanelCommandTransformer implements ICommandTransformer {

    @Override
    public String buildDownlinkData(String m, Object value) {
        byte[] payload = switch (m) {

            // ff 03 [2字节小端, 单位:秒] — 设置上报周期
            // 例: "20m" → ff 03 b0 04
            case "report_interval" -> {
                int seconds = parseSeconds(value);
                ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                buf.put((byte) 0xff);
                buf.put((byte) 0x03);
                buf.putShort((short) seconds);
                yield buf.array();
            }

            // ff 10 ff — 重启设备
            case "reboot" -> new byte[]{ (byte) 0xff, 0x10, (byte) 0xff };

            // ff 2f [00/01] — 丢弃延迟包功能
            // true  (01) = 启用丢弃，超出队列的包直接丢弃
            // false (00) = 禁用丢弃（默认），超出的包压入延迟队列
            case "discard_delay" -> new byte[]{
                    (byte) 0xff,
                    (byte) 0x2f,
                    toBool(value) ? (byte) 0x01 : (byte) 0x00
            };

            default -> throw new IllegalArgumentException("SSPanel 不支持的指令: " + m);
        };

        return Base64.getEncoder().encodeToString(payload);
    }

    /**
     * 解析时间值，统一转换为秒
     * 支持: 纯数字(秒), "20m"(分钟), "1200s"(秒)
     */
    private static int parseSeconds(Object val) {
        String s = String.valueOf(val).trim().toLowerCase();
        if (s.endsWith("m")) return Integer.parseInt(s.replace("m", "")) * 60;
        if (s.endsWith("s")) return Integer.parseInt(s.replace("s", ""));
        return Integer.parseInt(s);
    }

    private static boolean toBool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof Number  n) return n.intValue() != 0;
        String s = String.valueOf(o).trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "on".equalsIgnoreCase(s);
    }
}