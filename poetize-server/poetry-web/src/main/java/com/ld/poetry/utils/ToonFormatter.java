package com.ld.poetry.utils;
import com.ld.poetry.utils.JsonUtils;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * TOON (Token-Oriented Object Notation) 编解码器
 * <p>
 * 基于 TOON v3.0 规范实现：https://github.com/toon-format/spec
 * <p>
 * TOON 是一种为 LLM 优化的数据序列化格式，通过缩进结构和最少标点符号
 * 实现 30-60% 的 token 节省。
 * <p>
 * 核心规则:
 * - 缩进式嵌套对象: "key: value" (原语), "key:" (嵌套对象，子字段缩进2空格)
 * - 原语数组: "key[N]: v1,v2,v3"
 * - 对象数组(表格式): "key[N]{f1,f2}: \n v1,v2\n v3,v4"
 * - 无花括号、方括号、引号噪音
 */
@Slf4j
public class ToonFormatter {

    private static final String INDENT = "  "; // 2空格缩进
    private static final String DELIMITER = ",";

    // ======================== 编码 (JSON → TOON) ========================

    /**
     * 将 Java Map 编码为 TOON 格式字符串
     *
     * @param data 要编码的数据（支持嵌套 Map, List, 原语类型）
     * @return TOON 格式字符串
     */
    public static String encode(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        encodeObject(data, sb, 0);
        return sb.toString().stripTrailing();
    }

    /**
     * 将 JsonUtils.JsonObj 编码为 TOON 格式
     */
    public static String encode(JsonUtils.JsonObj json) {
        if (json == null || json.isEmpty()) {
            return "";
        }
        Map<String, Object> map = json.toMap();
        return encode(map);
    }

    @SuppressWarnings("unchecked")
    private static void encodeObject(Map<String, Object> obj, StringBuilder sb, int depth) {
        String indent = INDENT.repeat(depth);

        for (Map.Entry<String, Object> entry : obj.entrySet()) {
            String key = encodeKey(entry.getKey());
            Object value = entry.getValue();

            if (value == null) {
                sb.append(indent).append(key).append(": null\n");
            } else if (value instanceof Map) {
                Map<String, Object> nested = (Map<String, Object>) value;
                if (nested.isEmpty()) {
                    sb.append(indent).append(key).append(":\n");
                } else {
                    sb.append(indent).append(key).append(":\n");
                    encodeObject(nested, sb, depth + 1);
                }
            } else if (value instanceof JsonUtils.JsonObj jsonObj) {
                if (jsonObj.isEmpty()) {
                    sb.append(indent).append(key).append(":\n");
                } else {
                    sb.append(indent).append(key).append(":\n");
                    encodeObject(jsonObj.toMap(), sb, depth + 1);
                }
            } else if (value instanceof List || value instanceof JsonUtils.JsonArr) {
                List<?> list = value instanceof JsonUtils.JsonArr
                        ? ((JsonUtils.JsonArr) value).toJavaList(Object.class)
                        : (List<?>) value;
                encodeArray(key, list, sb, depth);
            } else {
                // 原语值 (String, Number, Boolean)
                sb.append(indent).append(key).append(": ").append(encodeValue(value)).append("\n");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void encodeArray(String key, List<?> list, StringBuilder sb, int depth) {
        String indent = INDENT.repeat(depth);
        int length = list.size();

        if (list.isEmpty()) {
            sb.append(indent).append(key).append("[0]:\n");
            return;
        }

        // 判断是否所有元素都是原语 → inline array
        boolean allPrimitive = list.stream().allMatch(ToonFormatter::isPrimitive);
        if (allPrimitive) {
            sb.append(indent).append(key).append("[").append(length).append("]: ");
            StringJoiner joiner = new StringJoiner(DELIMITER);
            for (Object item : list) {
                joiner.add(encodeValue(item));
            }
            sb.append(joiner).append("\n");
            return;
        }

        // 判断是否所有元素都是相同结构的对象 → tabular form
        if (isUniformObjectArray(list)) {
            List<Map<String, Object>> objList = (List<Map<String, Object>>) (List<?>) list;
            List<String> fields = new ArrayList<>(objList.get(0).keySet());

            sb.append(indent).append(key).append("[").append(length).append("]{");
            sb.append(String.join(DELIMITER, fields));
            sb.append("}:\n");

            String rowIndent = INDENT.repeat(depth + 1);
            for (Map<String, Object> row : objList) {
                StringJoiner joiner = new StringJoiner(DELIMITER);
                for (String field : fields) {
                    joiner.add(encodeValue(row.get(field)));
                }
                sb.append(rowIndent).append(joiner).append("\n");
            }
            return;
        }

        // 混合数组 → expanded list items
        sb.append(indent).append(key).append("[").append(length).append("]:\n");
        String itemIndent = INDENT.repeat(depth + 1);
        for (Object item : list) {
            if (item instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) item;
                Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator();
                if (it.hasNext()) {
                    Map.Entry<String, Object> first = it.next();
                    sb.append(itemIndent).append("- ").append(encodeKey(first.getKey()))
                            .append(": ").append(encodeValue(first.getValue())).append("\n");
                    // 剩余字段在 depth+2
                    String moreIndent = INDENT.repeat(depth + 2);
                    while (it.hasNext()) {
                        Map.Entry<String, Object> e = it.next();
                        sb.append(moreIndent).append(encodeKey(e.getKey()))
                                .append(": ").append(encodeValue(e.getValue())).append("\n");
                    }
                }
            } else {
                sb.append(itemIndent).append("- ").append(encodeValue(item)).append("\n");
            }
        }
    }

    // ======================== 解码 (TOON → JSON) ========================

    /**
     * 将 TOON 格式字符串解码为 Map
     *
     * @param toon TOON 格式字符串
     * @return 解码后的 Map（保持键顺序）
     */
    public static Map<String, Object> decode(String toon) {
        if (toon == null || toon.isBlank()) {
            return new LinkedHashMap<>();
        }

        List<String> lines = new ArrayList<>();
        for (String line : toon.split("\n")) {
            // 保留有内容的行（包括纯空行用于分隔）
            if (!line.isBlank()) {
                lines.add(line);
            }
        }

        if (lines.isEmpty()) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        parseLines(lines, 0, lines.size(), 0, result);
        return result;
    }

    /**
     * 解析指定行范围内的 TOON 数据到 Map
     */
    private static void parseLines(List<String> lines, int start, int end, int expectedDepth,
            Map<String, Object> target) {
        int i = start;
        while (i < end) {
            String line = lines.get(i);
            int depth = getIndentDepth(line);

            if (depth != expectedDepth) {
                i++;
                continue;
            }

            String trimmed = line.strip();

            // 跳过列表项标记
            if (trimmed.startsWith("- ")) {
                i++;
                continue;
            }

            // 解析 key: value 或 key:
            int colonIdx = findKeyColon(trimmed);
            if (colonIdx < 0) {
                i++;
                continue;
            }

            String key = decodeKey(trimmed.substring(0, colonIdx).strip());
            String afterColon = trimmed.substring(colonIdx + 1).strip();

            // 检查是否为数组头: key[N] 或 key[N]{fields}
            if (key.contains("[") && key.endsWith("]") || key.contains("[") && key.contains("]{")) {
                parseArrayHeader(key, afterColon, lines, i, end, expectedDepth, target);
                // 跳过数组体的行
                i = findBlockEnd(lines, i + 1, end, expectedDepth);
                continue;
            }

            if (afterColon.isEmpty()) {
                // 嵌套对象: "key:"
                int blockEnd = findBlockEnd(lines, i + 1, end, expectedDepth);
                Map<String, Object> nested = new LinkedHashMap<>();
                parseLines(lines, i + 1, blockEnd, expectedDepth + 1, nested);
                target.put(key, nested);
                i = blockEnd;
            } else {
                // 原语: "key: value"
                target.put(key, parseValue(afterColon));
                i++;
            }
        }
    }

    /**
     * 解析数组头并解码数组内容
     */
    private static void parseArrayHeader(String rawKey, String afterColon, List<String> lines,
            int headerLine, int end, int depth, Map<String, Object> target) {
        // 解析 key[N]{fields}: 格式
        int bracketStart = rawKey.indexOf('[');
        String key = decodeKey(rawKey.substring(0, bracketStart).strip());

        // 提取数组长度和字段列表
        String header = rawKey.substring(bracketStart);
        int bracketEnd = header.indexOf(']');
        int count;
        try {
            count = Integer.parseInt(header.substring(1, bracketEnd).strip());
        } catch (NumberFormatException e) {
            count = -1; // 未知长度
        }

        // 检查是否有字段声明 {f1,f2,...}
        String fieldsStr = null;
        if (header.contains("{") && header.contains("}")) {
            int braceStart = header.indexOf('{');
            int braceEnd = header.indexOf('}');
            fieldsStr = header.substring(braceStart + 1, braceEnd);
        }

        if (fieldsStr != null) {
            // 表格式数组: key[N]{f1,f2,...}:
            String[] fields = fieldsStr.split(DELIMITER);
            List<Map<String, Object>> rows = new ArrayList<>();

            int blockEnd = findBlockEnd(lines, headerLine + 1, end, depth);
            for (int r = headerLine + 1; r < blockEnd; r++) {
                String rowLine = lines.get(r);
                if (getIndentDepth(rowLine) <= depth)
                    break;
                String rowTrimmed = rowLine.strip();
                if (rowTrimmed.isEmpty())
                    continue;

                String[] values = splitDelimited(rowTrimmed, DELIMITER.charAt(0));
                Map<String, Object> row = new LinkedHashMap<>();
                for (int f = 0; f < fields.length && f < values.length; f++) {
                    row.put(fields[f].strip(), parseValue(values[f].strip()));
                }
                rows.add(row);
            }
            target.put(key, rows);
        } else if (!afterColon.isEmpty()) {
            // 内联原语数组: key[N]: v1,v2,v3
            String[] values = splitDelimited(afterColon, DELIMITER.charAt(0));
            List<Object> list = new ArrayList<>();
            for (String v : values) {
                list.add(parseValue(v.strip()));
            }
            target.put(key, list);
        } else {
            // 扩展列表项
            List<Object> list = new ArrayList<>();
            int blockEnd = findBlockEnd(lines, headerLine + 1, end, depth);
            for (int r = headerLine + 1; r < blockEnd; r++) {
                String rowLine = lines.get(r);
                if (getIndentDepth(rowLine) <= depth)
                    break;
                String rowTrimmed = rowLine.strip();
                if (rowTrimmed.startsWith("- ")) {
                    String itemVal = rowTrimmed.substring(2).strip();
                    list.add(parseValue(itemVal));
                }
            }
            target.put(key, list);
        }
    }

    // ======================== 辅助方法 ========================

    /** 判断值是否为原语类型 */
    private static boolean isPrimitive(Object value) {
        return value == null || value instanceof String || value instanceof Number || value instanceof Boolean;
    }

    /** 判断列表是否为统一对象数组（所有元素都是 Map 且有相同的键集合） */
    @SuppressWarnings("unchecked")
    private static boolean isUniformObjectArray(List<?> list) {
        if (list.isEmpty())
            return false;
        Set<String> firstKeys = null;
        for (Object item : list) {
            if (!(item instanceof Map))
                return false;
            Map<String, Object> map = (Map<String, Object>) item;
            // 所有值都必须是原语
            if (!map.values().stream().allMatch(ToonFormatter::isPrimitive))
                return false;
            if (firstKeys == null) {
                firstKeys = new LinkedHashSet<>(map.keySet());
            } else if (!firstKeys.equals(map.keySet())) {
                return false;
            }
        }
        return true;
    }

    /** 编码 key — 如果包含特殊字符则加引号 */
    private static String encodeKey(String key) {
        if (key.contains(":") || key.contains(",") || key.contains("\"") ||
                key.contains("[") || key.contains("]") || key.contains("{") ||
                key.contains("}") || key.startsWith(" ") || key.endsWith(" ") ||
                key.contains("-") || key.contains("\n") || key.contains("\t")) {
            return "\"" + escapeString(key) + "\"";
        }
        return key;
    }

    /** 解码 key — 去除引号 */
    private static String decodeKey(String key) {
        if (key.startsWith("\"") && key.endsWith("\"")) {
            return unescapeString(key.substring(1, key.length() - 1));
        }
        return key;
    }

    /** 编码原语值 */
    private static String encodeValue(Object value) {
        if (value == null)
            return "null";
        if (value instanceof Boolean)
            return value.toString();
        if (value instanceof Number)
            return canonicalNumber((Number) value);
        String str = value.toString();
        // 需要引号：包含分隔符、冒号、换行，或看起来像数字/布尔
        if (needsQuoting(str)) {
            return "\"" + escapeString(str) + "\"";
        }
        return str;
    }

    /** 判断字符串值是否需要引号 */
    private static boolean needsQuoting(String str) {
        if (str.isEmpty())
            return true;
        if ("true".equals(str) || "false".equals(str) || "null".equals(str))
            return true;
        if (str.contains(",") || str.contains(":") || str.contains("\"") ||
                str.contains("\n") || str.contains("\r") || str.contains("\t")) {
            return true;
        }
        // 看起来像数字？
        try {
            Double.parseDouble(str);
            return true; // 需要引号避免歧义
        } catch (NumberFormatException ignored) {
        }
        return false;
    }

    /** 规范化数字：无前导零、无尾部零、无指数 */
    private static String canonicalNumber(Number num) {
        double d = num.doubleValue();
        if (d == Math.floor(d) && !Double.isInfinite(d)) {
            return String.valueOf((long) d);
        }
        // 去尾部零
        String s = String.valueOf(d);
        if (s.contains("E") || s.contains("e")) {
            // 展开指数
            return new java.math.BigDecimal(s).stripTrailingZeros().toPlainString();
        }
        return s;
    }

    /** 转义字符串（TOON 只支持 \\, \", \n, \r, \t） */
    private static String escapeString(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /** 反转义字符串 */
    private static String unescapeString(String str) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\\' && i + 1 < str.length()) {
                char next = str.charAt(i + 1);
                switch (next) {
                    case '\\' -> {
                        sb.append('\\');
                        i++;
                    }
                    case '"' -> {
                        sb.append('"');
                        i++;
                    }
                    case 'n' -> {
                        sb.append('\n');
                        i++;
                    }
                    case 'r' -> {
                        sb.append('\r');
                        i++;
                    }
                    case 't' -> {
                        sb.append('\t');
                        i++;
                    }
                    default -> sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 解析值 token → Java 对象 */
    private static Object parseValue(String token) {
        if (token.isEmpty())
            return "";

        // 引号包裹 → 字符串
        if (token.startsWith("\"") && token.endsWith("\"")) {
            return unescapeString(token.substring(1, token.length() - 1));
        }

        // 布尔和 null
        if ("true".equals(token))
            return true;
        if ("false".equals(token))
            return false;
        if ("null".equals(token))
            return null;

        // 禁止前导零的数字
        if (token.length() > 1 && token.startsWith("0") && !token.startsWith("0.") && !token.startsWith("0e")
                && !token.startsWith("0E")) {
            return token; // 作为字符串
        }
        if (token.length() > 2 && token.startsWith("-0") && !token.startsWith("-0.") && !token.startsWith("-0e")
                && !token.startsWith("-0E")) {
            return token; // 作为字符串
        }

        // 尝试解析数字
        try {
            if (token.contains(".") || token.contains("e") || token.contains("E")) {
                return Double.parseDouble(token);
            }
            long l = Long.parseLong(token);
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                return (int) l;
            }
            return l;
        } catch (NumberFormatException ignored) {
        }

        return token; // 普通字符串
    }

    /** 获取行的缩进深度（每2空格算1级） */
    private static int getIndentDepth(String line) {
        int spaces = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ')
                spaces++;
            else
                break;
        }
        return spaces / 2;
    }

    /** 找到 key-value 分隔的冒号位置（跳过引号内的冒号和数组头内的冒号） */
    private static int findKeyColon(String line) {
        boolean inQuote = false;
        int brackets = 0;
        int braces = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
            } else if (!inQuote) {
                if (c == '[')
                    brackets++;
                else if (c == ']')
                    brackets--;
                else if (c == '{')
                    braces++;
                else if (c == '}')
                    braces--;
                else if (c == ':' && brackets == 0 && braces == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** 找到当前块的结束行号 */
    private static int findBlockEnd(List<String> lines, int start, int end, int parentDepth) {
        for (int i = start; i < end; i++) {
            String line = lines.get(i);
            if (line.isBlank())
                continue;
            if (getIndentDepth(line) <= parentDepth) {
                return i;
            }
        }
        return end;
    }

    /** 按分隔符拆分值（尊重引号） */
    private static String[] splitDelimited(String line, char delimiter) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
                current.append(c);
            } else if (c == delimiter && !inQuote) {
                parts.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts.toArray(new String[0]);
    }
}
