package com.ruoyi.exam.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;

import org.springframework.dao.DuplicateKeyException;

import com.ruoyi.exam.domain.ExamTask;
import com.ruoyi.exam.repository.ExamTaskRepository;
import com.ruoyi.exam.security.ExamAccessPolicy;
import com.ruoyi.exam.security.ExamActor;
import com.ruoyi.exam.support.ExamException;

public class ExamTaskService {
    private final ExamTaskRepository repository;
    private final Clock clock;

    public ExamTaskService(ExamTaskRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public ExamTask createCheck(ExamActor actor, String title, String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{16,64}")) {
            throw new ExamException("EXAM_INPUT_INVALID", "请提供 16–64 位有效幂等键");
        }
        if (title == null || title.isBlank() || title.length() > 80) {
            throw new ExamException("EXAM_INPUT_INVALID", "任务名称须为 1–80 字符");
        }
        String normalized = title.strip();
        String hash = hash("SYSTEM_CHECK\n" + normalized);
        var existing = repository.findByKey(actor.userId(), "SYSTEM_CHECK", key);
        if (existing.isPresent()) return sameRequest(existing.get(), hash);
        try {
            long id = repository.insert(actor.userId(), normalized, key, hash, LocalDateTime.now(clock));
            return repository.find(id).orElseThrow(ExamException::notFound);
        } catch (DuplicateKeyException conflict) {
            return sameRequest(repository.findByKey(actor.userId(), "SYSTEM_CHECK", key).orElseThrow(() -> conflict), hash);
        }
    }

    public ExamTask get(ExamActor actor, long id) {
        var task = repository.find(id).orElseThrow(ExamException::notFound);
        ExamAccessPolicy.requireOwner(actor, task.ownerUserId());
        return task;
    }

    public ExamTask cancel(ExamActor actor, long id, long expectedRevision) {
        var task = get(actor, id);
        if (task.revision() != expectedRevision) throw ExamException.conflict();
        if (task.status().equals("CANCELLED") || task.status().equals("CANCEL_REQUESTED")) return task;
        if (!repository.cancel(task, LocalDateTime.now(clock))) throw ExamException.conflict();
        return get(actor, id);
    }

    public ExamTask retry(ExamActor actor, long id, long expectedRevision) {
        var task = get(actor, id);
        if (task.revision() != expectedRevision) throw ExamException.conflict();
        if (!repository.retry(task, LocalDateTime.now(clock))) {
            throw new ExamException("EXAM_RETRY_NOT_ALLOWED", "仅失败的基础自检可重试，最多运行三次");
        }
        return get(actor, id);
    }

    private ExamTask sameRequest(ExamTask task, String hash) {
        if (!task.requestHash().equals(hash)) throw new ExamException("EXAM_IDEMPOTENCY_CONFLICT", "相同幂等键不能用于不同任务参数");
        return task;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
