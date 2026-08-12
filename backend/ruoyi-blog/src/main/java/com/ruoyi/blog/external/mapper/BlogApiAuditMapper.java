package com.ruoyi.blog.external.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.blog.external.domain.BlogApiAudit;

@Mapper
public interface BlogApiAuditMapper extends BaseMapper<BlogApiAudit>
{
    @Delete("DELETE FROM blog_api_audit WHERE create_time < #{before} ORDER BY create_time LIMIT #{limit}")
    int deleteBeforeBatch(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
