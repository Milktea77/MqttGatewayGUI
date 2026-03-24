package org.liang.cmd;

/**
 * 下行指令转换器接口
 *
 * 新增设备支持只需两步：
 *   1. 新建一个类实现此接口（参考 SwitchCommandTransformer）
 *   2. 在 DownlinkPayloadTransformer 静态块中 register() 注册一行
 *
 * UI 下拉框会自动展示所有已注册的设备类型，无需其他改动。
 */
public interface ICommandTransformer {

    /**
     * 将业务指令编码为 Base64 下行载荷 (data 字段)
     *
     * @param m 指令名称，如 "power_state"、"switch_1"
     * @param v 指令值，可能是 Boolean、String 或 Number
     * @return Base64 编码的字符串，直接填入下行报文的 data 字段
     */
    String buildDownlinkData(String m, Object v);
}
