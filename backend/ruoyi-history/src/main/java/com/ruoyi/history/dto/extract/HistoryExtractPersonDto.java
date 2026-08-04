package com.ruoyi.history.dto.extract;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class HistoryExtractPersonDto
{
    private String tempId;
    private String name;
    private String alias;
    private Integer birthYear;
    private Integer deathYear;
    private String summary;
    private String uncertaintyNote;
    private List<Integer> fragmentSeqNos = new ArrayList<>();
}
