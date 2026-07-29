package com.ruoyi.blog.vo;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class BlogHnListItemVO
{

    private Long id;
    private Long hnId;
    private String titleEn;
    private String titleZh;
    private String summaryZh;
    private String url;
    private String hnUrl;
    private Integer score;
    private String author;
    private Integer commentCount;
    private LocalDateTime hnTime;
    private String translateStatus;
    private Integer status;
    /** 关联最新快照时才有 */
    private Integer rank;
}
