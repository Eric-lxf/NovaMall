package com.ruoyi.exam.document;
import com.fasterxml.jackson.databind.JsonNode;
public interface ExamDocumentRunner {
    boolean configured();
    JsonNode run(JsonNode input);
}
