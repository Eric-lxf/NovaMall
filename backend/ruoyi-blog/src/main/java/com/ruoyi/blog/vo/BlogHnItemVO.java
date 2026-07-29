package com.ruoyi.blog.vo;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class BlogHnItemVO
{

    private Long id;
    private Long hnId;
    private String itemType;
    private String titleEn;
    private String titleZh;
    private String url;
    private String hnUrl;
    private String textEn;
    private String textZh;
    private String summaryZh;
    private Integer score;
    private String author;
    private Integer commentCount;
    private LocalDateTime hnTime;
    private LocalDateTime fetchedAt;
    private LocalDateTime translatedAt;
    private String translateStatus;
    private Integer status;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String remark;
}
