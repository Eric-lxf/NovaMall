package com.ruoyi.exam.parser;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.exam.support.ExamException;
import com.ruoyi.exam.support.ExamJson;

public final class ExamTextParser {
    public record Fragment(String text, ObjectNode locator) { }
    public record Result(List<Fragment> fragments, List<String> warnings) { }
    public Result parse(byte[] bytes) {
        ExamJson.require(bytes.length > 0 && bytes.length <= 10 * 1024 * 1024, "文件须为 1 字节至 10 MB");
        String text;
        try { text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (Exception error) { throw new ExamException("EXAM_FILE_ENCODING", "仅支持有效 UTF-8 文本，请转换编码后重试"); }
        text = text.replace("\r\n", "\n").replace('\r','\n').replace("\uFEFF", "");
        ExamJson.require(!text.isBlank() && text.length() <= 200000 && !text.contains("\u0000"), "正文为空、超出 20 万字符或不是纯文本");
        var result = new ArrayList<Fragment>(); String[] lines = text.split("\n", -1);
        for (int line = 0; line < lines.length; line++) {
            String value = lines[line].strip(); if (value.isBlank()) continue;
            for (int offset = 0; offset < value.length();) {
                int end=Math.min(offset+1500,value.length());
                if(end<value.length() && Character.isHighSurrogate(value.charAt(end-1))) end--;
                result.add(new Fragment(value.substring(offset, end),
                        ExamJson.object().put("kind", "TEXT").put("lineStart", line + 1).put("lineEnd", line + 1).put("characterOffset", offset)));
                offset=end;
            }
        }
        ExamJson.require(result.size() <= 2000, "资料片段过多，请拆分文件");
        return new Result(result, List.of());
    }
}
