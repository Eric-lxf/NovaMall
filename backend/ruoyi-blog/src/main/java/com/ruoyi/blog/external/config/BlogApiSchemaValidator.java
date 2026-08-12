package com.ruoyi.blog.external.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 外部 API 被标记为已迁移时，以只读方式核对运行所需的表、列和唯一索引。
 * 校验失败会阻止应用进入健康状态，避免功能开关先于 V2.7 迁移上线。
 */
@Component
@RequiredArgsConstructor
public class BlogApiSchemaValidator implements ApplicationRunner
{
    private final BlogExternalApiProperties properties;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args)
    {
        if (properties.isEnabled() && !properties.isSchemaReady())
        {
            throw new IllegalStateException(
                    "BLOG_EXTERNAL_API_ENABLED requires BLOG_EXTERNAL_API_SCHEMA_READY=true after V2.7 migration");
        }
        if (!properties.isSchemaReady())
        {
            return;
        }
        int tableCount = count("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('blog_api_client', 'blog_api_idempotency', 'blog_api_audit')
                """);
        int columnCount = count("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'blog_article'
                  AND column_name IN ('source_type', 'source_client_id', 'external_id')
                """);
        int uniqueIndexCount = count("""
                SELECT COUNT(DISTINCT CONCAT(table_name, ':', index_name))
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND non_unique = 0
                  AND ((table_name = 'blog_api_client' AND index_name = 'uk_blog_api_client_client_id')
                    OR (table_name = 'blog_api_idempotency' AND index_name = 'uk_blog_api_idempotency_client_key')
                    OR (table_name = 'blog_article' AND index_name = 'uk_blog_article_source_external'))
                """);

        if (tableCount != 3 || columnCount != 3 || uniqueIndexCount != 3)
        {
            throw new IllegalStateException(
                    "External blog API schema is incomplete; execute sql/V2.7.0__blog_external_write_api.sql first");
        }
    }

    private int count(String sql)
    {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
