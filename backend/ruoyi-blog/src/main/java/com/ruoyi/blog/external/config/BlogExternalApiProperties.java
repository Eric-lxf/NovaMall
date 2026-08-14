package com.ruoyi.blog.external.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 外部博客 API 的安全与容量配置。
 *
 * <p>该功能默认关闭；生产环境必须在数据库迁移完成后显式开启。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "blog.external-api")
public class BlogExternalApiProperties
{
    private boolean enabled = false;

    /** V2.7 schema 已安装；与公网开关分离，关闭公开流量后仍可执行保留期清理。 */
    private boolean schemaReady = false;

    /** 默认访问令牌有效期（秒）。 */
    private int tokenTtlSeconds = 900;

    /** 幂等记录保留时间（小时）。 */
    private int idempotencyTtlHours = 72;

    /** 安全审计保留天数。 */
    private int auditRetentionDays = 90;

    /** 单次文章写入允许的 UTF-8 字节数。 */
    private int maxBodyBytes = 1_048_576;

    /** 未在客户端单独配置时的每分钟请求上限。 */
    private int defaultRateLimitPerMinute = 60;
}
