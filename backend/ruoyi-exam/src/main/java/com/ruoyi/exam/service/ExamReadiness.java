package com.ruoyi.exam.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

import com.ruoyi.exam.repository.ExamTaskRepository;
import com.ruoyi.exam.support.ExamException;

public class ExamReadiness {
    private static final Logger log = LoggerFactory.getLogger(ExamReadiness.class);
    private final ExamTaskRepository repository;
    private volatile boolean ready;
    private long lastCheckNanos;

    public ExamReadiness(ExamTaskRepository repository) { this.repository = repository; }

    public synchronized boolean isReady() {
        long now = System.nanoTime();
        if (lastCheckNanos != 0 && now - lastCheckNanos < 30_000_000_000L) return ready;
        lastCheckNanos = now;
        try {
            repository.verifySchema();
            ready = true;
        } catch (DataAccessException missingSchemaOrDatabase) {
            ready = false;
            log.warn("EXAM_NOT_READY: task schema or database unavailable; exam work remains paused");
        }
        return ready;
    }

    public void requireReady() {
        if (!isReady()) throw new ExamException("EXAM_NOT_READY", "命题任务基础尚未就绪，请先检查迁移与数据库连接");
    }
}
