package com.ruoyi.history.dto.extract;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class HistoryExtractEventDto
{
    private String tempId;
    private String title;
    private Integer startYear;
    private Integer endYear;
    private String originalDateText;
    private String calendarType;
    private String summary;
    private String background;
    private String process;
    private String causeAnalysis;
    private String impact;
    private String uncertaintyNote;
    private String placeName;
    private List<Integer> fragmentSeqNos = new ArrayList<>();
}
