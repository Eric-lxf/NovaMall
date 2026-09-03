package com.ruoyi.exam.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public interface ExamModelGateway {
    ObjectNode describe(String module);
    Result complete(String module,JsonNode expected,String system,JsonNode input,ExamAiCallOptions options);
    record Result(String text,JsonNode usage,String requestId,String finishReason,long durationMs) { }
}
