package com.ruoyi.exam.task;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ruoyi.exam.domain.ExamTask;
import com.ruoyi.exam.repository.ExamTaskRepository;
import com.ruoyi.exam.security.ExamExecutionAuthorizer;
import com.ruoyi.exam.service.ExamReadiness;

/** Two bounded workers drain persistent tasks; business handlers fence every result write by the live lease. */
public class ExamTaskWorker implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ExamTaskWorker.class);
    private final ExamTaskRepository repository;
    private final ExamExecutionAuthorizer authorizer;
    private final ExamReadiness readiness;
    private final Clock clock;
    private final java.util.List<ExamJobHandler> handlers;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "exam-task-control"); thread.setDaemon(true); return thread;
    });
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new SynchronousQueue<>(), r -> {
                Thread thread = new Thread(r, "exam-task-worker"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public ExamTaskWorker(ExamTaskRepository repository, ExamExecutionAuthorizer authorizer,
            ExamReadiness readiness, Clock clock) {
        this(repository,authorizer,readiness,clock,java.util.List.of());
    }
    public ExamTaskWorker(ExamTaskRepository repository, ExamExecutionAuthorizer authorizer,
            ExamReadiness readiness, Clock clock,java.util.List<ExamJobHandler> handlers) {
        this.repository = repository;
        this.authorizer = authorizer;
        this.readiness = readiness;
        this.clock = clock;
        this.handlers=handlers;
    }

    public void start() { scheduler.scheduleWithFixedDelay(this::pollSafely, 3, 3, TimeUnit.SECONDS); }

    private void pollSafely() {
        try {
            if (!readiness.isReady()) return;
            repository.recoverExpired(now());
            for (var task : repository.queued(2)) {
                try { workers.execute(() -> executeSafely(task)); }
                catch (RejectedExecutionException full) { break; } // Still queued in DB; never run on request/control thread.
            }
        } catch (Exception failure) {
            log.warn("Exam task polling paused: {}", failure.getClass().getSimpleName());
        }
    }

    private void executeSafely(ExamTask queued) {
        try { executeOne(queued); }
        catch (Exception failure) {
            // Leave an uncertain write/DB outage to lease recovery; do not blindly dispatch again here.
            log.warn("Exam task {} interrupted: {}", queued.id(), failure.getClass().getSimpleName());
        }
    }

    public void executeOne(ExamTask queued) {
        String workerId = UUID.randomUUID().toString();
        if (!repository.claim(queued, workerId, now())) return;
        int attempt = queued.attemptNo() + 1;
        var heartbeat = scheduler.scheduleAtFixedRate(() -> {
            try { repository.heartbeat(queued.id(), attempt, workerId, now()); }
            catch (Exception unavailable) { log.warn("Exam heartbeat unavailable for task {}", queued.id()); }
        }, 15, 15, TimeUnit.SECONDS);
        try {
            if (!"SYSTEM_CHECK".equals(queued.kind())) {
                for(var handler:handlers) if(handler.supports(queued.kind())) {
                    var result=handler.execute(queued,attempt,workerId);
                    repository.finish(queued.id(),attempt,workerId,result.status(),result.error(),result.summary(),now());
                    return;
                }
                repository.finish(queued.id(), attempt, workerId, "NEEDS_CONFIRMATION", "EXAM_TASK_KIND_UNSUPPORTED", null, now());
                return;
            }
            if (!authorizer.mayExecute(queued.ownerUserId())) {
                repository.finish(queued.id(), attempt, workerId, "FAILED", "EXAM_PERMISSION_REVOKED", null, now());
                return;
            }
            // No model, no customer data, no files. A successful CAS verifies the persistent lifecycle.
            if (!authorizer.mayExecute(queued.ownerUserId())) {
                repository.finish(queued.id(), attempt, workerId, "FAILED", "EXAM_PERMISSION_REVOKED", null, now());
                return;
            }
            repository.finish(queued.id(), attempt, workerId, "SUCCEEDED", null,
                    "基础任务执行完成；未调用 AI、未解析文件。这不代表出题功能已完成。", now());
        } finally {
            heartbeat.cancel(false);
            repository.acknowledgeCancel(queued.id(), attempt, workerId, now());
        }
    }

    private LocalDateTime now() { return LocalDateTime.now(clock); }

    @Override public void close() {
        scheduler.shutdownNow();
        workers.shutdownNow();
        // Unfinished DB leases remain recoverable. Shutdown never marks unknown work as succeeded.
    }
}
