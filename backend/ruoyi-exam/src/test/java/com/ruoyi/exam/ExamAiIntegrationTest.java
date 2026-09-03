package com.ruoyi.exam;

import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.exam.ai.*;
import com.ruoyi.exam.domain.ExamTask;
import com.ruoyi.exam.service.*;
import com.ruoyi.exam.support.*;
import com.ruoyi.exam.task.ExamTaskWorker;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ExamAiIntegrationTest extends ExamWorkflowIntegrationTest {
    ExamJobs jobs; ExamAiJobs ai; FakeModel model; boolean permitted=true;
    @BeforeEach void aiSetup() {
        jobs=new ExamJobs(db,test.repository); model=new FakeModel();
        ai=new ExamAiJobs(db,jobs,model,user->permitted,sources,knowledge,blueprints,questions);
        var confirm=ExamJson.object().put("expectedRevision",1).put("externalAllowed",true); confirm.putArray("fragmentIds").add(fragmentId);
        sources.confirm(owner,Long.parseLong(sourceVersionId),confirm);
    }
    ObjectNode request(String... slots) {
        var req=ExamJson.object().put("blueprintId",blueprint.path("id").asText()).put("externalConsent",true).put("maxCalls",12).put("maxTokens",200000)
                .put("generationFingerprint","generation").put("verificationFingerprint","verification");
        var array=req.putArray("slotIds"); for(String slot:slots) array.add(slot); return req;
    }
    ExamTask run(ObjectNode req) {
        var task=ai.submit(owner,ExamAiJobs.GENERATE,UUID.randomUUID().toString(),req); execute(task); return test.repository.find(task.id()).orElseThrow();
    }
    void execute(ExamTask task) {
        try(var worker=new ExamTaskWorker(test.repository,user->true,new ExamReadiness(test.repository),java.time.Clock.systemUTC(),List.of(ai))) { worker.executeOne(task); }
    }
    @Test void generationHasIndependentAnswerChecksAndNeverAutoApproves() {
        var task=run(request("q1","q2","q3","q4")); assertEquals("SUCCEEDED",task.status()); assertEquals(12,model.calls);
        assertEquals(4,db.count("select count(*) from exam_question_version where review_state='DRAFT' and origin='AI'"));
        assertEquals(4,db.count("select count(*) from exam_question_check where check_kind='AI_VERIFY' and passed=1"));
        assertEquals(12,jobs.detail(owner,task.id()).path("calls").size()); assertFalse(model.solverSawAnswers);
    }
    @Test void identicalIdempotencyKeyDoesNotCreateDuplicatePaidJobs() {
        String key=UUID.randomUUID().toString(); var req=request("q1");
        var a=ai.submit(owner,ExamAiJobs.GENERATE,key,req); var b=ai.submit(owner,ExamAiJobs.GENERATE,key,req);
        assertEquals(a.id(),b.id()); assertEquals(0,model.calls);
        assertThrows(ExamException.class,()->ai.submit(owner,ExamAiJobs.GENERATE,key,request("q2")));
    }
    @Test void timeoutIsUncertainAndIsNotAutomaticallyRetried() {
        model.timeout=true; var task=run(request("q1","q2"));
        assertEquals("NEEDS_CONFIRMATION",task.status()); assertEquals(1,model.calls); assertEquals(0,db.count("select count(*) from exam_question"));
        test.repository.recoverExpired(db.now().plusHours(1)); assertEquals("NEEDS_CONFIRMATION",test.repository.find(task.id()).orElseThrow().status());
    }
    @Test void cancellationDuringModelCallCannotCommitQuestions() {
        model.afterCall=()->{ var task=test.repository.queued(1); var row=db.one("select id from exam_task where status='RUNNING'");
            var current=test.repository.find(ExamDataStoreId(row)).orElseThrow(); test.repository.cancel(current,db.now()); };
        var task=run(request("q1")); assertEquals("CANCELLED",task.status()); assertEquals(0,db.count("select count(*) from exam_question")); assertEquals(1,model.calls);
    }
    long ExamDataStoreId(Map<String,Object> row) { return com.ruoyi.exam.repository.ExamDataStore.number(row,"id"); }
    @Test void permissionOrSourceConsentRevocationPreventsDispatch() {
        var task=ai.submit(owner,ExamAiJobs.GENERATE,UUID.randomUUID().toString(),request("q1")); permitted=false; execute(task);
        assertEquals(0,model.calls); assertEquals("NEEDS_CONFIRMATION",test.repository.find(task.id()).orElseThrow().status());
        permitted=true; var task2=ai.submit(owner,ExamAiJobs.GENERATE,UUID.randomUUID().toString(),request("q1"));
        var confirm=ExamJson.object().put("expectedRevision",2).put("externalAllowed",false); confirm.putArray("fragmentIds").add(fragmentId); sources.confirm(owner,Long.parseLong(sourceVersionId),confirm);
        execute(task2); assertEquals(0,model.calls);
    }
    @Test void budgetStopsBeforeSecondCallAndKeepsAudit() {
        var task=run(request("q1").put("maxCalls",1)); assertEquals("NEEDS_CONFIRMATION",task.status()); assertEquals(1,model.calls);
        assertEquals(1,jobs.detail(owner,task.id()).path("callsReserved").asInt()); assertEquals(0,db.count("select count(*) from exam_question"));
    }
    @Test void malformedSlotProducesPartialSuccessWithoutDiscardingOtherQuestions() {
        model.badSlot="q1"; var task=run(request("q1","q2")); assertEquals("PARTIAL_SUCCESS",task.status());
        assertEquals(1,db.count("select count(*) from exam_question")); assertEquals(1,db.count("select count(*) from exam_task_item where status='FAILED'"));
    }
    @Test void liveLeaseGuardRejectsExpiredWorkerWrites() {
        var task=ai.submit(owner,ExamAiJobs.GENERATE,UUID.randomUUID().toString(),request("q1")); assertTrue(test.repository.claim(task,"worker",db.now()));
        db.update("update exam_task set lease_until=? where id=?",db.now().minusSeconds(1),task.id());
        assertThrows(ExamException.class,()->jobs.lease(task,1,"worker").commit(()->db.update("update exam_job set result_json='{}' where task_id=?",task.id())));
    }
    @Test void malformedJsonGetsOnlyOneBudgetedRepair() {
        model.invalidJsonCalls=1; var task=run(request("q1"));
        assertEquals("SUCCEEDED",task.status()); assertEquals(4,model.calls);
    }
    @Test void secondMalformedResponseFailsSlotWithoutInfiniteRepairs() {
        model.invalidJsonCalls=2; var task=run(request("q1"));
        assertEquals("FAILED",task.status()); assertEquals(2,model.calls); assertEquals(0,db.count("select count(*) from exam_question"));
    }
    @Test void longKnowledgeSourcesAreAllBatchedAndTrackPartialFailure() {
        var large=sources.importText(owner,ExamJson.object().put("title","长资料").put("text","请检查设备并在故障时停止作业。".repeat(1200)),null);
        long version=large.path("currentVersionId").asLong(); var confirmation=ExamJson.object().put("expectedRevision",0).put("externalAllowed",true);
        var ids=confirmation.putArray("fragmentIds"); sources.fragments(owner,version).forEach(f->ids.add(f.path("id").asText())); sources.confirm(owner,version,confirmation);
        var task=ai.submit(owner,ExamAiJobs.KNOWLEDGE,UUID.randomUUID().toString(),request().put("sourceVersionId",Long.toString(version)));
        var input=jobs.input(task.id()); assertTrue(input.path("batches").size()>1);
        var included=new HashSet<String>(); input.path("batches").forEach(batch->batch.forEach(id->assertTrue(included.add(id.asText())))); assertEquals(ids.size(),included.size());
        model.badKnowledgeCall=2; execute(task);
        assertEquals("PARTIAL_SUCCESS",test.repository.find(task.id()).orElseThrow().status());
        assertEquals(1,db.count("select count(*) from exam_task_item where task_id=? and status='FAILED'",task.id()));
        assertEquals(1,jobs.detail(owner,task.id()).path("result").path("knowledgeIds").size());
    }
    class FakeModel implements ExamModelGateway {
        int calls,invalidJsonCalls,badKnowledgeCall; boolean timeout,solverSawAnswers; String badSlot=""; Runnable afterCall;
        public ObjectNode describe(String module) { return ExamJson.object().put("providerId","1").put("model","unit-test-only").put("configFingerprint",module.equals("exam_generate")?"generation":"verification"); }
        public Result complete(String module,JsonNode expected,String system,JsonNode input,int maxOutputTokens) {
            calls++; if(afterCall!=null) { var action=afterCall; afterCall=null; action.run(); }
            if(timeout) throw new ExamException("EXAM_RESULT_UNCERTAIN","test timeout"); JsonNode output;
            if(calls<=invalidJsonCalls) return new Result("{",ExamJson.object(),"test-"+calls,"stop",10);
            if(input.has("slot")) {
                int index=Integer.parseInt(input.path("slot").path("slotId").asText().substring(1))-1;
                output=badSlot.equals(input.path("slot").path("slotId").asText())?ExamJson.object().put("unsupported",true):content(index);
            } else if(input.has("independentAnswer")) output=ExamJson.object().put("passed",true).put("reason","测试评分核对通过");
            else if(!input.has("question")) {
                var point=ExamJson.object().put("name","本批安全知识").put("description","测试候选"); var evidence=input.path("evidence").get(0);
                point.putArray("sourceRefs").add(ExamJson.object().put("fragmentId",evidence.path("fragmentId").asText()).put("quote",evidence.path("text").asText().substring(0,10)));
                output=calls==badKnowledgeCall?ExamJson.object().put("unsupported",true):ExamJson.object().set("points",ExamJson.array().add(point));
            }
            else {
                var q=input.path("question"); solverSawAnswers|=q.has("analysis") || q.has("correctOptionIds") || q.has("referenceAnswer") || q.has("answerBoolean");
                var answer=ExamJson.object(); String type=q.path("type").asText();
                if(type.endsWith("CHOICE")) { var values=answer.putArray("correctOptionIds").add("A"); if(type.equals("MULTIPLE_CHOICE")) values.add("B"); }
                else if(type.equals("TRUE_FALSE")) answer.put("answerBoolean",true); else answer.put("referenceAnswer","检查设备，故障停工并报告。");
                output=ExamJson.object().put("supported",true).put("ambiguous",false).put("reason","测试依据充分").set("answer",answer);
            }
            return new Result(output.toString(),ExamJson.object().put("total_tokens",100),"test-"+calls,"stop",10);
        }
    }
}
