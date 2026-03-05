package org.liang.cmd;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

/**
 * Milesight WT303 风管机面板下行指令转换器
 * 对照手册指令解析：
 */
public class DuctlessAcCommandTransformer {

    public static String buildDownlinkData(String m, Object value) {
        byte[] payload;

        switch (m) {
            // 1. 系统开关 (power_state): ON/OFF
            case "power_state" -> {
                payload = new byte[2];
                payload[0] = 0x67; // 命令：67
                payload[1] = "ON".equalsIgnoreCase(String.valueOf(value)) ? (byte) 0x01 : (byte) 0x00;
            }

            // 2. 温控模式 (work_mode): VENTILATION, HEAT, COOL
            case "work_mode" -> {
                payload = new byte[2];
                payload[0] = 0x68; // 命令：68
                String mode = String.valueOf(value).toUpperCase();
                payload[1] = switch (mode) {
                    case "HEAT" -> (byte) 0x01;
                    case "COOL" -> (byte) 0x02;
                    default -> (byte) 0x00; // VENTILATION 或其他默认为通风
                };
            }

            // 3. 目标温度: 手册区分了制热(6b)和制冷(6c)目标温度
            // 此处通过键值区分，或默认使用制冷设置(6c)
            case "target_temp", "target_temp_cool" -> payload = buildTempPayload((byte) 0x6c, value);
            case "target_temp_heat" -> payload = buildTempPayload((byte) 0x6b, value);

            // 4. 风机模式 (fan_speed_level): 0-3
            case "fan_speed_level" -> {
                payload = new byte[2];
                payload[0] = 0x72; // 命令：72
                payload[1] = (byte) getInt(value);
            }

            // 5. 童锁 (child_lock): 1=启用, 0=禁用
            case "child_lock" -> {
                payload = new byte[3];
                payload[0] = 0x75; // 命令：75
                payload[1] = getInt(value) == 1 ? (byte) 0x01 : (byte) 0x00;
                payload[2] = (byte) 0x1F; // 默认锁定所有 5 个按钮
            }

            default -> throw new IllegalArgumentException("未知的控制功能: " + m);
        }

        return Base64.getEncoder().encodeToString(payload);
    }

    /**
     * 构建温度载荷 (3字节): 命令(1) + 温度值*100(2, 小端序)
     * 例如 19°C -> 1900 -> 0x076C -> 字节序 6C 07
     */
    private static byte[] buildTempPayload(byte cmd, Object val) {
        int temp = (int) (Double.parseDouble(String.valueOf(val)) * 100);
        ByteBuffer buffer = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(cmd);
        buffer.putShort((short) temp);
        return buffer.array();
    }

    private static int getInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
    }
}