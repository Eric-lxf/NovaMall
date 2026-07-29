package com.ruoyi.blog.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("blog_hn_rank")
public class BlogHnRank
{

    @TableId(type = IdType.AUTO)
    private Long id;
    private String board;
    private Long hnId;
    @TableField("`rank`")
    private Integer rank;
    private LocalDateTime snapshotAt;
}
