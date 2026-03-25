package org.liang.cmd;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

/**
 * Milesight WT303 风管机面板下行指令转换器
 * 已对照手册 7.5 节全面修订
 */
public class DuctlessAcCommandTransformer implements ICommandTransformer {

    @Override
    public String buildDownlinkData(String m, Object value) {
        byte[] payload = switch (m) {

            // 0x67 - 系统开关: ON=01, OFF=00
            case "power_state" -> new byte[]{
                    0x67,
                    toBool(value) ? (byte) 0x01 : (byte) 0x00
            };

            // 0x68 - 温控模式: 00=通风, 01=制热, 02=制冷
            case "work_mode" -> new byte[]{
                    0x68,
                    switch (String.valueOf(value).toUpperCase()) {
                        case "HEAT"        -> (byte) 0x01;
                        case "COOL"        -> (byte) 0x02;
                        default            -> (byte) 0x00; // VENTILATION
                    }
            };

            // 0x64 - 启用温控模式组合: 03=通风+制热, 05=通风+制冷, 07=通风+制热+制冷
            case "hvac_mode_enable" -> new byte[]{
                    0x64,
                    switch (String.valueOf(value).toUpperCase()) {
                        case "VENT_HEAT"      -> (byte) 0x03;
                        case "VENT_COOL"      -> (byte) 0x05;
                        case "VENT_HEAT_COOL" -> (byte) 0x07;
                        default -> throw new IllegalArgumentException("不支持的模式组合: " + value);
                    }
            };

            // 0x6b - 制热目标温度 (3 字节, 小端序, 值×100)
            case "target_temp_heat"              -> buildTempPayload((byte) 0x6b, value);

            // 0x6c - 制冷目标温度 (默认 target_temp 映射到制冷)
            case "target_temp", "target_temp_cool" -> buildTempPayload((byte) 0x6c, value);

            // 0x72 - 风机模式: 00=自动, 01=低速, 02=中速, 03=高速
            case "fan_speed_level" -> new byte[]{
                    0x72,
                    (byte) getInt(value)
            };

            // 0x75 - 童锁 (3 字节)
            // 启用: 75 01 1F (锁定全部5个按钮)
            // 禁用: 75 00 00 (手册示例 750000)
            case "child_lock" -> {
                boolean enable = toBool(value);
                yield new byte[]{
                        0x75,
                        enable ? (byte) 0x01 : (byte) 0x00,
                        enable ? (byte) 0x1F : (byte) 0x00  // BUG FIX: disable时byte2须为0x00
                };
            }

            // 0x66 - 屏幕显示: 00=禁用, 01 0F=启用全部
            case "screen_display" -> new byte[]{
                    0x66,
                    toBool(value) ? (byte) 0x01 : (byte) 0x00,
                    toBool(value) ? (byte) 0x0F : (byte) 0x00
            };

            // 0x62 - 上报周期 (4 字节): 单位 + 2字节时长(小端序)
            case "report_interval" -> buildIntervalPayload((byte) 0x62, value);

            // 0x60 - 采集间隔 (格式同上报周期)
            case "collection_interval" -> buildIntervalPayload((byte) 0x60, value);

            // 0x63 - 温度单位: 00=°C, 01=°F
            case "temp_unit" -> new byte[]{
                    0x63,
                    "F".equalsIgnoreCase(String.valueOf(value)) ? (byte) 0x01 : (byte) 0x00
            };

            // 0x90 - 继电器变化上报: 00=禁用, 01=启用
            // 0x90 - 继电器变化上报
            case "relay_report" -> new byte[]{
                    (byte) 0x90,
                    toBool(value) ? (byte) 0x01 : (byte) 0x00
            };

            // 单字节控制指令
            case "reboot"     -> new byte[]{ (byte) 0xBE };
            case "rejoin"     -> new byte[]{ (byte) 0xB6 };
            case "get_status" -> new byte[]{ (byte) 0xB9 };

            default -> throw new IllegalArgumentException("未知的控制功能: " + m);
        };

        return Base64.getEncoder().encodeToString(payload);
    }

    /**
     * 构建温度载荷 (3 字节): 命令(1) + 温度值×100 (2 字节小端序)
     * 例: 19°C → 1900 → 0x076C → 字节 [cmd, 6C, 07]
     */
    private static byte[] buildTempPayload(byte cmd, Object val) {
        int temp = (int) (Double.parseDouble(String.valueOf(val)) * 100);
        ByteBuffer buf = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(cmd);
        buf.putShort((short) temp);
        return buf.array();
    }

    /**
     * 构建间隔载荷 (4 字节): 命令(1) + 单位(1) + 时长(2 字节小端序)
     * value 格式: 数字(秒) 或 "20m"/"1200s" 字符串
     * 例: "20m" → 62 01 14 00
     */
    private static byte[] buildIntervalPayload(byte cmd, Object val) {
        String s = String.valueOf(val).trim().toLowerCase();
        byte unit;
        int duration;

        if (s.endsWith("m")) {
            unit = 0x01; // 分钟
            duration = Integer.parseInt(s.replace("m", ""));
        } else if (s.endsWith("s")) {
            unit = 0x00; // 秒
            duration = Integer.parseInt(s.replace("s", ""));
        } else {
            // 默认当作秒处理
            unit = 0x00;
            duration = Integer.parseInt(s);
        }

        ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(cmd);
        buf.put(unit);
        buf.putShort((short) duration);
        return buf.array();
    }

    private static boolean toBool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.intValue() != 0;
        String s = String.valueOf(o).trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "on".equalsIgnoreCase(s);
    }

    private static int getInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
    }
}