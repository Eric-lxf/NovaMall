package com.ruoyi.history.vo;

import lombok.Data;

@Data
public class HistoryCountryPeriodVO
{
    private Long id;
    private String name;
    private String alias;
    private Integer startYear;
    private Integer endYear;
    private String summary;
    private Integer sort;
    private Integer eventCount;
}
