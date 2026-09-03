package com.ruoyi.exam;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import com.ruoyi.exam.domain.ExamTask;
import com.ruoyi.exam.security.ExamActor;
import com.ruoyi.exam.service.ExamReadiness;
import com.ruoyi.exam.service.ExamTaskService;
import com.ruoyi.exam.support.ExamException;
import com.ruoyi.exam.task.ExamTaskWorker;

@Timeout(15)
class ExamTaskIntegrationTest {
    private ExamTestDatabase db;
    private ExamTaskService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final ExamActor alice = new ExamActor(20, false);
    private final ExamActor bob = new ExamActor(21, false);
    private final String key = "request_0123456789";
    private LocalDateTime now() { return LocalDateTime.now(clock); }

    @BeforeEach void prepare() throws Exception {
        db = new ExamTestDatabase();
        service = new ExamTaskService(db.repository, clock);
    }

    private ExamTask create() { return service.createCheck(alice, "测试任务", key); }

    @Test void sameKeyAndNormalizedParametersReturnSameTask() {
        long id = create().id();
        assertEquals(id, service.createCheck(alice, " 测试任务 ", key).id());
        assertEquals(1, db.repository.count(alice.userId(), null));
    }

    @Test void sameKeyDifferentParametersFails() {
        create();
        assertEquals("EXAM_IDEMPOTENCY_CONFLICT", assertThrows(ExamException.class,
                () -> service.createCheck(alice, "不同参数", key)).getErrorCode());
    }

    @Test void concurrentSubmissionUsesDatabaseUniqueness() throws Exception {
        var pool = Executors.newFixedThreadPool(6);
        try {
            var calls = new ArrayList<Callable<Long>>();
            for (int i = 0; i < 12; i++) calls.add(() -> create().id());
            var futures = pool.invokeAll(calls);
            long id = futures.get(0).get();
            for (var future : futures) assertEquals(id, future.get());
            assertEquals(1, db.repository.count(alice.userId(), null));
        } finally { pool.shutdownNow(); }
    }

    @Test void keysAreScopedPerOwnerAndCaseSensitive() {
        long a = create().id();
        long b = service.createCheck(bob, "测试任务", key).id();
        long c = service.createCheck(alice, "测试任务", key.toUpperCase()).id();
        assertNotEquals(a, b); assertNotEquals(a, c);
    }

    @Test void rejectsInvalidKeysAndTitles() {
        assertThrows(ExamException.class, () -> service.createCheck(alice, "标题", "short"));
        assertThrows(ExamException.class, () -> service.createCheck(alice, " ", key));
        assertThrows(ExamException.class, () -> service.createCheck(alice, "字".repeat(81), key));
    }

    @Test void crossOwnerDetailCancelAndRetryAreIndistinguishableFromMissing() {
        var task = create();
        for (Runnable action : new Runnable[]{() -> service.get(bob, task.id()),
                () -> service.cancel(bob, task.id(), 0), () -> service.retry(bob, task.id(), 0),
                () -> service.get(bob, 9999)}) {
            assertEquals("EXAM_RESOURCE_NOT_FOUND", assertThrows(ExamException.class, action::run).getErrorCode());
        }
        assertEquals("QUEUED", service.get(alice, task.id()).status());
    }

    @Test void ownerListDoesNotIncludeOtherUsersTasks() {
        create(); service.createCheck(bob, "另一用户", key);
        assertEquals(1, db.repository.count(alice.userId(), null));
        assertTrue(db.repository.page(alice.userId(), null, 0, 10).stream().allMatch(t -> t.ownerUserId() == alice.userId()));
    }

    @Test void explicitAdministratorCanAccessIndividualTask() {
        var task = create();
        assertEquals(task.id(), service.get(new ExamActor(30, true), task.id()).id());
    }

    @Test void cancelledQueuedTaskCannotBeClaimed() {
        var task = create();
        assertEquals("CANCELLED", service.cancel(alice, task.id(), task.revision()).status());
        assertFalse(db.repository.claim(task, "worker", now()));
    }

    @Test void onlyOneWorkerCanClaimVersion() {
        var task = create();
        assertTrue(db.repository.claim(task, "first", now()));
        assertFalse(db.repository.claim(task, "second", now()));
    }

    @Test void runningCancellationRejectsLateSuccessAndIsAcknowledged() {
        var task = create();
        db.repository.claim(task, "worker", now());
        task = service.get(alice, task.id());
        assertEquals("CANCEL_REQUESTED", service.cancel(alice, task.id(), task.revision()).status());
        assertFalse(db.repository.finish(task.id(), 1, "worker", "SUCCEEDED", null, "late", now()));
        db.repository.acknowledgeCancel(task.id(), 1, "worker", now());
        assertEquals("CANCELLED", service.get(alice, task.id()).status());
    }

    @Test void optimisticRevisionPreventsStaleCancel() {
        var task = create(); db.repository.claim(task, "worker", now());
        assertEquals("EXAM_VERSION_CONFLICT", assertThrows(ExamException.class,
                () -> service.cancel(alice, task.id(), task.revision())).getErrorCode());
    }

    @Test void expiredProbeCanBeRecoveredButOldWorkerCannotWrite() {
        var task = create(); db.repository.claim(task, "old", now());
        db.repository.recoverExpired(now().plusSeconds(61));
        var recovered = service.get(alice, task.id());
        assertEquals("QUEUED", recovered.status());
        assertTrue(db.repository.claim(recovered, "new", now().plusSeconds(62)));
        assertFalse(db.repository.finish(task.id(), 1, "old", "SUCCEEDED", null, "late", now().plusSeconds(63)));
        assertTrue(db.repository.finish(task.id(), 2, "new", "SUCCEEDED", null, "ok", now().plusSeconds(63)));
    }

    @Test void expiredUnknownTaskIsNotAutomaticallyRedispatched() {
        var task = create();
        db.jdbc.update("update exam_task set kind='AI_GENERATE' where id=?", task.id());
        db.repository.claim(service.get(alice, task.id()), "old", now());
        db.repository.recoverExpired(now().plusSeconds(61));
        assertEquals("NEEDS_CONFIRMATION", service.get(alice, task.id()).status());
    }

    @Test void expiredCancelledTaskIsNotRequeued() {
        var task = create(); db.repository.claim(task, "old", now());
        service.cancel(alice, task.id(), service.get(alice, task.id()).revision());
        db.repository.recoverExpired(now().plusSeconds(61));
        assertEquals("CANCELLED", service.get(alice, task.id()).status());
    }

    @Test void heartbeatExtendsOnlyCurrentLiveLease() {
        var task = create(); db.repository.claim(task, "current", now());
        assertFalse(db.repository.heartbeat(task.id(), 1, "stale", now().plusSeconds(20)));
        assertTrue(db.repository.heartbeat(task.id(), 1, "current", now().plusSeconds(20)));
        db.repository.recoverExpired(now().plusSeconds(61));
        assertEquals("RUNNING", service.get(alice, task.id()).status());
        assertFalse(db.repository.heartbeat(task.id(), 1, "current", now().plusSeconds(81)));
    }

    @Test void retryIsBoundedAndSameRevisionCannotBeAppliedTwice() {
        var task = create();
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertTrue(db.repository.claim(task, "worker", now()));
            assertTrue(db.repository.finish(task.id(), attempt, "worker", "FAILED", "TEST_FAILURE", null, now()));
            var failed = service.get(alice, task.id());
            if (attempt < 3) {
                task = service.retry(alice, failed.id(), failed.revision());
                assertThrows(ExamException.class, () -> service.retry(alice, failed.id(), failed.revision()));
            } else {
                assertEquals("EXAM_RETRY_NOT_ALLOWED", assertThrows(ExamException.class,
                        () -> service.retry(alice, failed.id(), failed.revision())).getErrorCode());
            }
        }
    }

    @Test void persistedTaskSurvivesServiceRecreation() {
        var task = create();
        var recreated = new ExamTaskService(db.repository, clock);
        assertEquals(task.id(), recreated.get(alice, task.id()).id());
        assertEquals(task.id(), recreated.createCheck(alice, "测试任务", key).id());
    }

    @Test void workerExecutesProbeWithoutAnyAiDependency() {
        var task = create();
        try (var worker = new ExamTaskWorker(db.repository, owner -> owner == alice.userId(), new ExamReadiness(db.repository), clock)) {
            worker.executeOne(task);
        }
        assertEquals("SUCCEEDED", service.get(alice, task.id()).status());
        assertTrue(service.get(alice, task.id()).resultSummary().contains("未调用 AI"));
    }

    @Test void workerRejectsRevokedPermission() {
        var task = create();
        try (var worker = new ExamTaskWorker(db.repository, owner -> false, new ExamReadiness(db.repository), clock)) {
            worker.executeOne(task);
        }
        assertEquals("FAILED", service.get(alice, task.id()).status());
        assertEquals("EXAM_PERMISSION_REVOKED", service.get(alice, task.id()).errorCode());
    }

    @Test void schemaProbeDetectsMissingTable() {
        assertTrue(new ExamReadiness(db.repository).isReady());
        db.jdbc.execute("alter table exam_task drop constraint uk_exam_task_request");
        assertFalse(new ExamReadiness(db.repository).isReady());
        db.jdbc.execute("drop table exam_task"); // Test-owned in-memory table only.
        assertFalse(new ExamReadiness(db.repository).isReady());
    }
}
