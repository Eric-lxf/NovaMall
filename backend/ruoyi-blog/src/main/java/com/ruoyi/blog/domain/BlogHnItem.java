package com.ruoyi.blog.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("blog_hn_item")
public class BlogHnItem
{

    @TableId(type = IdType.AUTO)
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
