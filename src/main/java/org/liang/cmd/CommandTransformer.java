package org.liang.cmd;

import java.util.Base64;

public class CommandTransformer {
    public static String buildDownlinkData(String m, boolean value) {
        int changeBit = 0;
        int stateBit = 0;

        // 根据被控开关设置位：x 为变化位 (Bit 4-6)，y 为状态位 (Bit 0-2)
        if ("switch_1".equalsIgnoreCase(m)) {
            changeBit = 0x10; // Bit 4
            stateBit = value ? 0x01 : 0x00; // Bit 0
        } else if ("switch_2".equalsIgnoreCase(m)) {
            changeBit = 0x20; // Bit 5
            stateBit = value ? 0x02 : 0x00; // Bit 1
        } else if ("switch_3".equalsIgnoreCase(m)) {
            changeBit = 0x40; // Bit 6
            stateBit = value ? 0x04 : 0x00; // Bit 2
        }

        byte[] payload = new byte[3];
        payload[0] = (byte) 0xFF;
        payload[1] = (byte) 0x29;
        payload[2] = (byte) (changeBit | stateBit);

        return Base64.getEncoder().encodeToString(payload);
    }
}