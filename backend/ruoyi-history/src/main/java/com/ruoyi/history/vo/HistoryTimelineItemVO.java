package com.ruoyi.history.vo;

import lombok.Data;

@Data
public class HistoryTimelineItemVO
{
    private Long id;
    private String title;
    private Long periodId;
    private String periodName;
    private Long countryId;
    private String countryName;
    private Long placeId;
    private String placeName;
    private Integer startYear;
    private Integer endYear;
    private String datePrecision;
    private String originalDateText;
    private Boolean isApproximate;
    private String summary;
    private String uncertaintyNote;
}
