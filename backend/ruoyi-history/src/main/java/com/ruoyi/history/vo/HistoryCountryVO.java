package com.ruoyi.history.vo;

import java.util.List;

import lombok.Data;

@Data
public class HistoryCountryVO
{
    private Long id;
    private String name;
    private String alias;
    private String region;
    private String periodLabel;
    private String summary;
    private Integer sort;
    private Integer periodCount;
    private Integer eventCount;
    private List<HistoryCountryPeriodVO> periods;
}
