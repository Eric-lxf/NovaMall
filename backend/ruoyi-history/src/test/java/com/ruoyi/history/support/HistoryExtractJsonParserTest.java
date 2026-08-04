package com.ruoyi.history.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ruoyi.history.dto.extract.HistoryExtractResultDto;

class HistoryExtractJsonParserTest
{
    @Test
    void parsesFencedJson()
    {
        String raw = "```json\n{\"events\":[{\"title\":\"安史之乱爆发\",\"tempId\":\"e1\",\"fragmentSeqNos\":[1]}],\"persons\":[],\"places\":[],\"relations\":[]}\n```";
        HistoryExtractResultDto dto = HistoryExtractJsonParser.parse(raw);
        assertEquals(1, dto.getEvents().size());
        assertEquals("安史之乱爆发", dto.getEvents().get(0).getTitle());
        assertTrue(dto.getPersons().isEmpty());
    }
}
