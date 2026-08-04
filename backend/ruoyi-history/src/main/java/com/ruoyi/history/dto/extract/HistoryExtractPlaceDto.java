package com.ruoyi.history.dto.extract;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class HistoryExtractPlaceDto
{
    private String tempId;
    private String name;
    private String alias;
    private String modernName;
    private String region;
    private String summary;
    private List<Integer> fragmentSeqNos = new ArrayList<>();
}
