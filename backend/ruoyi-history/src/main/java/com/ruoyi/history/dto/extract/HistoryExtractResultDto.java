package com.ruoyi.history.dto.extract;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * AI 抽取结果根对象。
 */
@Data
public class HistoryExtractResultDto
{
    private List<HistoryExtractEventDto> events = new ArrayList<>();
    private List<HistoryExtractPersonDto> persons = new ArrayList<>();
    private List<HistoryExtractPlaceDto> places = new ArrayList<>();
    private List<HistoryExtractRelationDto> relations = new ArrayList<>();
}
