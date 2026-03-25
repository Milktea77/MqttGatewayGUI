package org.liang.get;

import java.util.LinkedHashMap;
import java.util.Map;

public class ParserFactory {
    // 预定义解析器映射，确保代码引用了这些类
    private static final Map<String, IDataParser> parsers = new LinkedHashMap<>();

    static {
        parsers.put("GenericSensorDecoder", new GenericSensorDecoder());
        parsers.put("AirSensor", new AirSensorDecoder());
        parsers.put("ContactSensor", new ContactSensorDecoder());
        parsers.put("PeopleSensor", new PeopleSensorDecoder());
        parsers.put("Switch", new SwitchDecoder());
        parsers.put("DuctlessAC", new DuctlessAcDecoder());
        parsers.put("OccupancySensorDecoder", new OccupancySensorDecoder());
        parsers.put("WS136SSPanel", new WS136SSPanel());
    }

    /**
     * 根据类型获取解析器，使用 Java 12+ 增强 switch 语法
     */
    public static IDataParser getParser(String type) {
        return switch (type) {
            case "AirSensor" -> parsers.get("AirSensor");
            case "ContactSensor" -> parsers.get("ContactSensor");
            case "PeopleSensor" -> parsers.get("PeopleSensor");
            case "DuctlessAcDecoder"  -> parsers.get("DuctlessAC");
            case "OccupancySensorDecoder" -> parsers.get("OccupancySensorDecoder");
            case "SwitchDecoder" -> parsers.get("SwitchDecoder");
            case "WS136SSPanel" -> parsers.get("WS136SSPanel");
            default -> parsers.get("GenericSensorDecoder");
        };
    }

//     提供给 GUI 下拉框使用，避免硬编码
    public static String[] getParserNames() {
        return parsers.keySet().toArray(new String[0]);
    }
}