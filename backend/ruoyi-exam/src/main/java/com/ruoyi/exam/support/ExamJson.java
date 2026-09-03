package com.ruoyi.exam.support;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

public final class ExamJson {
    public static final ObjectMapper MAPPER = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(40).maxStringLength(400000).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private ExamJson() { }
    public static ObjectNode object() { return MAPPER.createObjectNode(); }
    public static ArrayNode array() { return MAPPER.createArrayNode(); }
    public static JsonNode parse(String json) {
        try {
            require(json != null && json.length() <= 2000000, "JSON 超出限制");
            JsonNode node = MAPPER.readTree(json);
            require(node != null, "JSON 为空");
            return node;
        } catch (Exception e) { throw new ExamException("EXAM_INPUT_INVALID", "JSON 格式无效或超出限制"); }
    }
    public static void require(boolean ok, String message) { if (!ok) throw new ExamException("EXAM_INPUT_INVALID", message); }
    public static String text(JsonNode node, String name, int max) {
        JsonNode field = node.get(name);
        require(field != null && field.isTextual() && !field.asText().isBlank() && field.asText().length() <= max, name + " 必须为非空文本，长度不超过 " + max);
        require(field.asText().codePoints().noneMatch(c -> Character.isISOControl(c) && c!='\n' && c!='\r' && c!='\t'),name + " 包含不支持的控制字符");
        return field.asText().strip();
    }
    public static long id(JsonNode node, String field) {
        try { long id = Long.parseLong(node.path(field).asText()); require(id > 0, "ID 无效"); return id; }
        catch (RuntimeException error) { throw new ExamException("EXAM_INPUT_INVALID", field + " 无效"); }
    }
    public static long revision(JsonNode node) {
        JsonNode field = node.get("expectedRevision");
        require(field != null && field.canConvertToLong() && field.isIntegralNumber() && field.asLong() >= 0, "缺少有效版本号");
        return field.asLong();
    }
    public static long cursor(Long beforeId) { require(beforeId==null || beforeId>0,"分页游标无效"); return beforeId==null?Long.MAX_VALUE:beforeId; }
    public static List<JsonNode> list(JsonNode node, int min, int max, String label) {
        require(node != null && node.isArray() && node.size() >= min && node.size() <= max, label + " 数量/类型无效");
        var result = new ArrayList<JsonNode>(); node.forEach(result::add); return result;
    }
    public static BigDecimal score(JsonNode node) {
        require(node != null && node.isNumber(), "分值必须为数值");
        BigDecimal value = node.decimalValue();
        require(value.signum() > 0 && value.compareTo(new BigDecimal("10000")) <= 0 && value.stripTrailingZeros().scale() <= 2, "分值须为正数且最多两位小数");
        return value;
    }
    public static String hash(JsonNode node) { return hash(sorted(node).toString().getBytes(StandardCharsets.UTF_8)); }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private static JsonNode sorted(JsonNode node) {
        if (node.isObject()) {
            var result = object(); var names = new TreeSet<String>(); node.fieldNames().forEachRemaining(names::add);
            names.forEach(name -> result.set(name, sorted(node.get(name)))); return result;
        }
        if (node.isArray()) { var result = array(); node.forEach(item -> result.add(sorted(item))); return result; }
        return node;
    }
    public static void fields(JsonNode node, Set<String> required, Set<String> optional) {
        require(node != null && node.isObject(), "必须为 JSON 对象");
        Set<String> names = new HashSet<>(); node.fieldNames().forEachRemaining(names::add);
        require(names.containsAll(required), "缺少必填字段"); names.removeAll(required); names.removeAll(optional);
        require(names.isEmpty(), "存在不允许的字段");
    }
}
