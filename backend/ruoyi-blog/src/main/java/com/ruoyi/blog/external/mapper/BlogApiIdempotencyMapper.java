package com.ruoyi.blog.external.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.blog.external.domain.BlogApiIdempotency;

@Mapper
public interface BlogApiIdempotencyMapper extends BaseMapper<BlogApiIdempotency>
{
    @Insert("""
            INSERT IGNORE INTO blog_api_idempotency
              (client_id, idempotency_key, request_hash, status, expire_time, create_time, update_time)
            VALUES
              (#{clientId}, #{idempotencyKey}, #{requestHash}, 0, #{expireTime}, NOW(), NOW())
            """)
    int insertPending(@Param("clientId") Long clientId, @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash, @Param("expireTime") LocalDateTime expireTime);

    @Delete("""
            DELETE FROM blog_api_idempotency
            WHERE client_id = #{clientId} AND idempotency_key = #{idempotencyKey}
              AND expire_time < #{now}
            """)
    int deleteExpiredKey(@Param("clientId") Long clientId, @Param("idempotencyKey") String idempotencyKey,
            @Param("now") LocalDateTime now);

    @Delete("DELETE FROM blog_api_idempotency WHERE expire_time < #{now} ORDER BY expire_time LIMIT #{limit}")
    int deleteExpiredBatch(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
