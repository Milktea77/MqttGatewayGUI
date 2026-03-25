package org.liang.cmd;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

/**
 * Milesight WS51x 智能插座下行指令转换器
 *
 * 报文格式参考手册 5.3 节，每条指令由通道号(1B) + 类型(1B) + 数据(nB) 组成。
 * 下行字段与对应通道的映射关系：
 *   socket_status   -> CH 08: 00 00 ff (断电) / 01 00 ff (通电)
 *   report_interval -> CH ff 03: 2字节小端序，单位: s
 */
public class WS51xCommandTransformer implements ICommandTransformer {

    @Override
    public String buildDownlinkData(String m, Object v) {
        byte[] payload = switch (m) {
            case "socket_status" -> buildSocketControl(v);
            case "report_interval" -> buildReportInterval(v);
            default -> throw new IllegalArgumentException("WS51x 不支持的指令: " + m);
        };
        return Base64.getEncoder().encodeToString(payload);
    }

    /**
     * CH 08 - 插座通断电
     * ON:  08 01 00 ff
     * OFF: 08 00 00 ff
     */
    private static byte[] buildSocketControl(Object v) {
        boolean on;
        if (v instanceof Boolean b) {
            on = b;
        } else if (v instanceof Number n) {
            on = n.intValue() != 0;
        } else {
            String s = String.valueOf(v).trim();
            on = "1".equals(s) || "true".equalsIgnoreCase(s) || "on".equalsIgnoreCase(s);
        }
        return new byte[]{ 0x08, on ? (byte) 0x01 : 0x00, 0x00, (byte) 0xff };
    }

    /**
     * CH ff 03 - 设置上报周期
     * ff 03 [2字节小端序，单位: s]
     * 例: 1200s (20分钟) -> ff 03 b0 04
     */
    private static byte[] buildReportInterval(Object v) {
        int seconds;
        if (v instanceof Number n) {
            seconds = n.intValue();
        } else {
            seconds = Integer.parseInt(String.valueOf(v).trim());
        }
        ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0xff);
        buf.put((byte) 0x03);
        buf.putShort((short) seconds);
        return buf.array();
    }
}
