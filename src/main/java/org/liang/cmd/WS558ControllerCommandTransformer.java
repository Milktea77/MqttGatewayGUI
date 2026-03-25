package org.liang.cmd;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

/**
 * Milesight WS558 智能灯控下行指令转换器
 * 通道 0x08: 2字节开关控制
 *   字节1: 控制掩码 (bit=1 表示控制该路)
 *   字节2: 状态掩码 (bit=1 表示打开该路)
 * 通道 0xff: 系统配置子命令
 * 支持的指令 m 值:
 *   switch_1 ~ switch_8      单路开关控制
 *   switch_all               全部线路同时控制
 *   report_interval          上报周期 (秒), 如 "1200" 或 "20m"
 *   add_schedule             延时任务, value 格式: "delaySeconds:switchIndex:state"
 *                            例: "60:6:false" = 1分钟后关闭线路6
 *   delete_schedule          删除延时任务 (无需 value)
 *   energy_report            电量统计上报: true=启用, false=禁用
 *   reset_energy             重置电量统计 (无需 value)
 *   query_energy             查询电参信息 (无需 value)
 *   reboot                   重启设备 (无需 value)
 */
public class WS558ControllerCommandTransformer implements ICommandTransformer {

    @Override
    public String buildDownlinkData(String m, Object value) {
        byte[] payload = switch (m) {

            // ── 单路开关 switch_1 ~ switch_8 ──────────────────────────────
            case "switch_1" -> buildSingleSwitch(0, value);
            case "switch_2" -> buildSingleSwitch(1, value);
            case "switch_3" -> buildSingleSwitch(2, value);
            case "switch_4" -> buildSingleSwitch(3, value);
            case "switch_5" -> buildSingleSwitch(4, value);
            case "switch_6" -> buildSingleSwitch(5, value);
            case "switch_7" -> buildSingleSwitch(6, value);
            case "switch_8" -> buildSingleSwitch(7, value);

            // ── 全部线路同时控制 ───────────────────────────────────────────
            // value: true=全开, false=全关
            case "switch_all" -> {
                byte state = toBool(value) ? (byte) 0xFF : (byte) 0x00;
                yield new byte[]{ 0x08, (byte) 0xFF, state };
            }

            // ── 上报周期 ff 03 [2字节小端, 单位:秒] ──────────────────────
            // value: 纯数字(秒) 或 "20m"(分钟) 或 "1200s"(秒)
            case "report_interval" -> {
                int seconds = parseSeconds(value);
                ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                buf.put((byte) 0xff);
                buf.put((byte) 0x03);
                buf.putShort((short) seconds);
                yield buf.array();
            }

            // ── 延时任务 ff 32 [5字节] ────────────────────────────────────
            // value 格式: "delaySeconds:switchIndex:on"
            //   例: "60:6:false" = 60秒后关闭第6路
            //   字节1: 00 (固定)
            //   字节2-3: 延迟秒数, 小端序
            //   字节4: 控制掩码
            //   字节5: 状态掩码
            case "add_schedule" -> {
                String[] parts = String.valueOf(value).split(":");
                if (parts.length != 3)
                    throw new IllegalArgumentException("add_schedule 格式错误，应为 \"秒数:线路号:true/false\"");
                int delaySec   = Integer.parseInt(parts[0].trim());
                int switchIdx  = Integer.parseInt(parts[1].trim()) - 1; // 转为0-based
                boolean on     = toBool(parts[2].trim());
                byte mask      = (byte) (1 << switchIdx);
                ByteBuffer buf = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN);
                buf.put((byte) 0xff);
                buf.put((byte) 0x32);
                buf.put((byte) 0x00);                    // 字节1: 固定00
                buf.putShort((short) delaySec);          // 字节2-3: 延迟时间
                buf.put(mask);                           // 字节4: 控制掩码
                buf.put(on ? mask : (byte) 0x00);        // 字节5: 状态掩码
                yield buf.array();
            }

            // ── 删除延时任务 ff 23 00 ff ──────────────────────────────────
            case "delete_schedule" -> new byte[]{ (byte) 0xff, 0x23, 0x00, (byte) 0xff };

            // ── 电量统计开关 ff 26 [00/01] ────────────────────────────────
            case "energy_report" -> new byte[]{
                    (byte) 0xff, 0x26,
                    toBool(value) ? (byte) 0x01 : (byte) 0x00
            };

            // ── 重置电量统计 ff 27 ff ─────────────────────────────────────
            case "reset_energy" -> new byte[]{ (byte) 0xff, 0x27, (byte) 0xff };

            // ── 查询电参信息 ff 28 ff ─────────────────────────────────────
            case "query_energy" -> new byte[]{ (byte) 0xff, 0x28, (byte) 0xff };

            // ── 重启设备 ff 10 ff ─────────────────────────────────────────
            case "reboot" -> new byte[]{ (byte) 0xff, 0x10, (byte) 0xff };

            default -> throw new IllegalArgumentException("WS558 不支持的指令: " + m);
        };

        return Base64.getEncoder().encodeToString(payload);
    }

    /**
     * 构建单路开关控制载荷 (3字节)
     * 格式: 08 [控制掩码] [状态掩码]
     * @param bitIndex 0-based 线路索引 (switch_1 → 0, switch_8 → 7)
     * @param value    开关状态
     */
    private static byte[] buildSingleSwitch(int bitIndex, Object value) {
        byte mask  = (byte) (1 << bitIndex);
        byte state = toBool(value) ? mask : (byte) 0x00;
        return new byte[]{ 0x08, mask, state };
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