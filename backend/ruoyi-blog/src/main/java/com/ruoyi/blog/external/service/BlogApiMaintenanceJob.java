package com.ruoyi.blog.external.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ruoyi.blog.external.config.BlogExternalApiProperties;
import com.ruoyi.blog.external.mapper.BlogApiAuditMapper;
import com.ruoyi.blog.external.mapper.BlogApiIdempotencyMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "blog.external-api", name = "schema-ready", havingValue = "true")
public class BlogApiMaintenanceJob
{
    private static final Logger log = LoggerFactory.getLogger(BlogApiMaintenanceJob.class);
    private static final int BATCH_SIZE = 1_000;
    private static final int MAX_BATCHES_PER_RUN = 10;

    private final BlogApiIdempotencyMapper idempotencyMapper;
    private final BlogApiAuditMapper auditMapper;
    private final BlogExternalApiProperties properties;

    @Scheduled(initialDelayString = "${blog.external-api.cleanup-initial-delay-ms:300000}",
            fixedDelayString = "${blog.external-api.cleanup-delay-ms:3600000}")
    public void cleanup()
    {
        int idempotencyDeleted = deleteIdempotency(LocalDateTime.now());
        int auditDeleted = deleteAudit(LocalDateTime.now().minusDays(Math.max(1, properties.getAuditRetentionDays())));
        if (idempotencyDeleted > 0 || auditDeleted > 0)
        {
            log.info("External blog API cleanup completed: idempotency={}, audit={}",
                    idempotencyDeleted, auditDeleted);
        }
    }

    private int deleteIdempotency(LocalDateTime now)
    {
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++)
        {
            int deleted = idempotencyMapper.deleteExpiredBatch(now, BATCH_SIZE);
            total += deleted;
            if (deleted < BATCH_SIZE)
            {
                break;
            }
        }
        return total;
    }

    private int deleteAudit(LocalDateTime before)
    {
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++)
        {
            int deleted = auditMapper.deleteBeforeBatch(before, BATCH_SIZE);
            total += deleted;
            if (deleted < BATCH_SIZE)
            {
                break;
            }
        }
        return total;
    }
}
