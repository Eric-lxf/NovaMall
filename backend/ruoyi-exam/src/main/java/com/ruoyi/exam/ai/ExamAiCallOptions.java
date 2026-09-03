package com.ruoyi.exam.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.exam.config.ExamProperties;
import com.ruoyi.exam.support.ExamJson;

/** 每次调用的输出上限同时参与请求、预算预留和配置快照，不能各自使用不同常量。 */
public record ExamAiCallOptions(int maxOutputTokens, String thinking, String reasoningEffort) {
    public ExamAiCallOptions {
        ExamJson.require(maxOutputTokens >= 1024 && maxOutputTokens <= 32768, "单次输出上限须为 1024–32768 token");
        ExamJson.require("enabled".equals(thinking) || "disabled".equals(thinking), "思考模式配置无效");
        ExamJson.require(java.util.Set.of("low", "high", "max").contains(reasoningEffort), "思考强度配置无效");
    }
    public ObjectNode json() {
        return ExamJson.object().put("maxOutputTokens", maxOutputTokens).put("thinking", thinking).put("reasoningEffort", reasoningEffort);
    }
    public static ExamAiCallOptions read(JsonNode descriptor, String operation) {
        var value = descriptor.path("callPolicies").path(operation);
        return new ExamAiCallOptions(value.path("maxOutputTokens").asInt(), value.path("thinking").asText(), value.path("reasoningEffort").asText());
    }
    public static ObjectNode policies(ExamProperties properties) {
        var result = ExamJson.object();
        result.set("extract", new ExamAiCallOptions(properties.getExtractMaxOutputTokens(), "disabled", "low").json());
        result.set("generate", new ExamAiCallOptions(properties.getGenerateMaxOutputTokens(), "enabled", properties.getGenerateReasoningEffort()).json());
        result.set("verify", new ExamAiCallOptions(properties.getVerifyMaxOutputTokens(), "enabled", properties.getVerifyReasoningEffort()).json());
        return result;
    }
}
