package com.ld.poetry.utils;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.*;

public class JsonUtils {

    static final JsonMapper MAPPER = JsonMapper.builder().build();

    public static String toJsonString(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static <T> T parseObject(String j, Class<T> c) {
        try {
            return MAPPER.readValue(j, c);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static JsonObj parseObject(String j) {
        try {
            JsonNode n = MAPPER.readTree(j);
            if (n == null || !n.isObject()) return null;
            return new JsonObj((ObjectNode) n);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static <T> List<T> parseArray(String j, Class<T> c) {
        try {
            return MAPPER.readValue(j, new TypeReference<List<T>>() {});
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static JsonArr parseArray(String j) {
        try {
            JsonNode n = MAPPER.readTree(j);
            return n != null && n.isArray() ? new JsonArr((ArrayNode) n) : null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static ObjectNode createObjectNode() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode createArrayNode() {
        return MAPPER.createArrayNode();
    }

    public static JsonMapper getMapper() {
        return MAPPER;
    }

    // ========================================
    // JsonObj
    // ========================================
    public static class JsonObj {
        final ObjectNode node;

        public JsonObj() {
            this(MAPPER.createObjectNode());
        }

        public JsonObj(boolean ordered) {
            this(MAPPER.createObjectNode());
        }

        JsonObj(ObjectNode n) {
            this.node = n;
        }

        public String getString(String k) {
            JsonNode c = node.path(k);
            return c.isMissingNode() ? null : c.asText(null);
        }

        public Integer getInteger(String k) {
            JsonNode c = node.path(k);
            return c.isMissingNode() ? null : c.asInt();
        }

        public int getIntValue(String k) {
            return node.path(k).asInt();
        }

        public Boolean getBoolean(String k) {
            JsonNode c = node.path(k);
            return c.isMissingNode() ? null : c.asBoolean();
        }

        public boolean getBooleanValue(String k) {
            return node.path(k).asBoolean();
        }

        public BigDecimal getBigDecimal(String k) {
            JsonNode c = node.path(k);
            return c.isMissingNode() || !c.isNumber() ? null : c.decimalValue();
        }

        public boolean containsKey(String k) {
            return node.has(k);
        }

        public boolean isEmpty() {
            return node.isEmpty();
        }

        public Object get(String k) {
            JsonNode c = node.get(k);
            if (c == null || c.isNull()) return null;
            if (c.isTextual()) return c.asText();
            if (c.isInt()) return c.asInt();
            if (c.isLong()) return c.asLong();
            if (c.isDouble()) return c.asDouble();
            if (c.isBoolean()) return c.asBoolean();
            if (c.isObject()) return new JsonObj((ObjectNode) c);
            if (c.isArray()) return new JsonArr((ArrayNode) c);
            return c;
        }

        public Set<String> keySet() {
            Set<String> keys = new LinkedHashSet<>();
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                keys.add(entry.getKey());
            }
            return keys;
        }

        public JsonObj getJSONObject(String k) {
            JsonNode c = node.get(k);
            return c != null && c.isObject() ? new JsonObj((ObjectNode) c) : null;
        }

        public JsonArr getJSONArray(String k) {
            JsonNode c = node.get(k);
            return c != null && c.isArray() ? new JsonArr((ArrayNode) c) : null;
        }

        public JsonObj put(String k, Object v) {
            if (v == null) {
                node.putNull(k);
            } else if (v instanceof String s) {
                node.put(k, s);
            } else if (v instanceof Integer i) {
                node.put(k, i);
            } else if (v instanceof Long l) {
                node.put(k, l);
            } else if (v instanceof Double d) {
                node.put(k, d);
            } else if (v instanceof Boolean b) {
                node.put(k, b);
            } else if (v instanceof JsonObj jo) {
                node.set(k, jo.node);
            } else if (v instanceof JsonArr ja) {
                node.set(k, ja.array);
            } else {
                node.set(k, MAPPER.valueToTree(v));
            }
            return this;
        }

        public ObjectNode unwrap() {
            return node;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                m.put(entry.getKey(), valueToObject(entry.getValue()));
            }
            return m;
        }

        private static Object valueToObject(JsonNode v) {
            if (v == null || v.isNull()) return null;
            if (v.isTextual()) return v.asText();
            if (v.isInt()) return v.asInt();
            if (v.isLong()) return v.asLong();
            if (v.isDouble()) return v.asDouble();
            if (v.isBoolean()) return v.asBoolean();
            if (v.isObject()) {
                Map<String, Object> map = new LinkedHashMap<>();
                for (Map.Entry<String, JsonNode> entry : v.properties()) {
                    map.put(entry.getKey(), valueToObject(entry.getValue()));
                }
                return map;
            }
            if (v.isArray()) {
                List<Object> list = new ArrayList<>();
                for (JsonNode item : v) {
                    list.add(valueToObject(item));
                }
                return list;
            }
            return v;
        }

        public String toJSONString() {
            return node.toString();
        }

        @Override
        public String toString() {
            return node.toString();
        }
    }

    // ========================================
    // JsonArr
    // ========================================
    public static class JsonArr implements Iterable<Object> {
        final ArrayNode array;

        public JsonArr() {
            this(MAPPER.createArrayNode());
        }

        JsonArr(ArrayNode a) {
            this.array = a;
        }

        public JsonObj getJSONObject(int i) {
            JsonNode c = array.get(i);
            return c != null && c.isObject() ? new JsonObj((ObjectNode) c) : null;
        }

        public String getString(int i) {
            JsonNode c = array.get(i);
            return c != null && c.isTextual() ? c.asText() : null;
        }

        public int size() {
            return array.size();
        }

        public boolean isEmpty() {
            return array.isEmpty();
        }

        public JsonNode get(int i) {
            return array.get(i);
        }

        @Override
        public Iterator<Object> iterator() {
            return new Iterator<Object>() {
                private int idx = 0;

                @Override
                public boolean hasNext() {
                    return idx < array.size();
                }

                @Override
                public Object next() {
                    JsonNode n = array.get(idx++);
                    if (n == null || n.isNull()) return null;
                    if (n.isTextual()) return n.asText();
                    if (n.isInt()) return n.asInt();
                    if (n.isLong()) return n.asLong();
                    if (n.isDouble()) return n.asDouble();
                    if (n.isBoolean()) return n.asBoolean();
                    if (n.isObject()) return new JsonObj((ObjectNode) n);
                    if (n.isArray()) return new JsonArr((ArrayNode) n);
                    return n;
                }
            };
        }

        public JsonArr add(Object v) {
            if (v == null) {
                array.addNull();
            } else if (v instanceof String s) {
                array.add(s);
            } else if (v instanceof Integer i) {
                array.add(i);
            } else if (v instanceof Long l) {
                array.add(l);
            } else if (v instanceof Double d) {
                array.add(d);
            } else if (v instanceof Boolean b) {
                array.add(b);
            } else if (v instanceof JsonObj jo) {
                array.add(jo.node);
            } else if (v instanceof JsonArr ja) {
                array.add(ja.array);
            } else {
                array.add(MAPPER.valueToTree(v));
            }
            return this;
        }

        @SuppressWarnings("unchecked")
        public <T> List<T> toJavaList(Class<T> elementType) {
            List<T> list = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                JsonNode n = array.get(i);
                if (n == null || n.isNull()) continue;
                if (elementType == String.class && n.isTextual()) {
                    list.add((T) n.asText());
                } else if (elementType == Object.class) {
                    list.add((T) valueToObject(n));
                } else {
                    list.add((T) n);
                }
            }
            return list;
        }

        private static Object valueToObject(JsonNode v) {
            if (v == null || v.isNull()) return null;
            if (v.isTextual()) return v.asText();
            if (v.isInt()) return v.asInt();
            if (v.isLong()) return v.asLong();
            if (v.isDouble()) return v.asDouble();
            if (v.isBoolean()) return v.asBoolean();
            return v;
        }

        public ArrayNode unwrap() {
            return array;
        }

        public String toJSONString() {
            return array.toString();
        }

        @Override
        public String toString() {
            return array.toString();
        }
    }
}