package com.ruoyi.blog.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.blog.domain.BlogHnRank;

@Mapper
public interface BlogHnRankMapper extends BaseMapper<BlogHnRank>
{

    @Select("SELECT MAX(snapshot_at) FROM blog_hn_rank WHERE board = #{board}")
    LocalDateTime selectMaxSnapshotAt(@Param("board") String board);
}
