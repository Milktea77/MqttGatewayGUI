package org.liang.get;

import com.alibaba.fastjson2.JSONObject;

public interface IDataParser {
    /**
     * 执行具体的解析逻辑
     * @param input 原始数据的 JSON 对象
     * @param now 当前时间戳
     * @return 返回解析后的设备数据项（devs 数组中的一个元素）
     */
    JSONObject parse(JSONObject input, long now);
}