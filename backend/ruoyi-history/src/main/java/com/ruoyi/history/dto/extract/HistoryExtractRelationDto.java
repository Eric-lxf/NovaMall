package com.ruoyi.history.dto.extract;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class HistoryExtractRelationDto
{
    private String fromTempId;
    private String toTempId;
    private String relationType;
    private String description;
    private List<Integer> fragmentSeqNos = new ArrayList<>();
}
