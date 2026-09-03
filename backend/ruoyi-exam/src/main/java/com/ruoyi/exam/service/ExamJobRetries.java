package com.ruoyi.exam.service;

import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.exam.ai.ExamAiJobs;
import com.ruoyi.exam.document.ExamDocumentJobs;
import com.ruoyi.exam.domain.ExamTask;
import com.ruoyi.exam.repository.*;
import com.ruoyi.exam.security.*;
import com.ruoyi.exam.support.*;
import static com.ruoyi.exam.repository.ExamDataStore.*;

/** 手动重试创建关联的新任务：保留旧审计，成功项不重放，每个原任务最多产生一个重试后继。 */
public class ExamJobRetries {
    private final ExamDataStore db;
    private final ExamTaskRepository tasks;
    private final ExamJobs jobs;
    private final ExamAiJobs ai;
    private final ExamDocumentJobs documents;
    private final ExamExecutionAuthorizer authorizer;
    public ExamJobRetries(ExamDataStore db,ExamTaskRepository tasks,ExamJobs jobs,ExamAiJobs ai,
            ExamDocumentJobs documents,ExamExecutionAuthorizer authorizer) {
        this.db=db; this.tasks=tasks; this.jobs=jobs; this.ai=ai; this.documents=documents; this.authorizer=authorizer;
    }
    private ExamTask owned(ExamActor actor,long id) {
        var task=tasks.find(id).orElseThrow(ExamException::notFound);
        // 管理员也不能将其他人的资料复制为自己的重试任务。
        if(task.ownerUserId()!=actor.userId()) throw ExamException.notFound();
        if(!authorizer.mayExecute(actor.userId(),"exam:task:retry")) throw new ExamException("EXAM_PERMISSION_REVOKED","没有手动重试权限");
        return task;
    }
    private List<String> remaining(ExamTask task) {
        ExamJson.require(Set.of("FAILED","PARTIAL_SUCCESS","NEEDS_CONFIRMATION").contains(task.status()),"只有失败、部分完成或需人工确认的任务可手动重试");
        ExamJson.require(ai.supports(task.kind()) || documents.supports(task.kind()),"该任务类型不支持业务重试");
        var result=new ArrayList<String>();
        for(var item:db.rows("select slot_id,status from exam_task_item where task_id=? order by id",task.id()))
            if(!"SUCCEEDED".equals(string(item,"status"))) result.add(string(item,"slot_id"));
        ExamJson.require(!result.isEmpty(),"所有子任务已完成，无需重试，请查看已有结果");
        return result;
    }
    private ObjectNode progress(long id) { return (ObjectNode)ExamJson.parse(string(db.one("select progress_json from exam_job where task_id=?",id),"progress_json")); }
    private void requireNoSuccessor(long id) {
        var progress=progress(id);
        if(progress.has("retryTaskId")) throw new ExamException("EXAM_RETRY_ALREADY_CREATED","已创建重试任务 "+progress.path("retryTaskId").asText()+"，请查看该任务，不要重复提交");
    }
    private boolean uncertain(ExamTask task) {
        return "NEEDS_CONFIRMATION".equals(task.status()) || db.count("select count(*) from exam_ai_call where task_id=? and status in ('DISPATCHING','UNCERTAIN')",task.id())>0;
    }
    public ObjectNode preview(ExamActor actor,long id) {
        var task=owned(actor,id); requireNoSuccessor(id); var remaining=remaining(task); var original=jobs.input(id);
        ObjectNode plan;
        if(ai.supports(task.kind())) plan=ai.prepareRetry(actor,task.kind(),original,remaining).preview();
        else {
            documents.validateRetry(actor,task.kind(),original);
            plan=ExamJson.object().put("ready",true).put("ai",false).put("itemCount",remaining.size()).put("planFingerprint",ExamJson.hash(original));
        }
        return plan.put("originalTaskId",Long.toString(id)).put("kind",task.kind()).put("expectedRevision",task.revision())
                .put("requiresUncertainAcknowledgement",uncertain(task));
    }
    public ExamTask retry(ExamActor actor,long id,String key,JsonNode body) {
        var task=owned(actor,id);
        ExamJson.fields(body,Set.of("expectedRevision","planFingerprint"),Set.of("externalConsent","acknowledgeUncertain","maxCalls","maxTokens","generationFingerprint","verificationFingerprint"));
        var request=((ObjectNode)body).deepCopy().put("retryOfTaskId",Long.toString(id));
        var repeated=jobs.existing(actor,task.kind(),key,request); if(repeated!=null) return repeated;
        return db.tx(()->{
            // 与普通提交使用相同的全局准入锁；不同请求键的双击也不能建立两个付费后继。
            db.update("insert ignore into exam_owner_lock (owner_user_id) values (0)");
            db.one("select * from exam_owner_lock where owner_user_id=0 for update");
            db.lock("exam_task",id);
            var again=jobs.existing(actor,task.kind(),key,request); if(again!=null) return again;
            var current=owned(actor,id); requireNoSuccessor(id);
            if(current.revision()!=ExamJson.revision(body)) throw ExamException.conflict();
            var remaining=remaining(current); var original=jobs.input(id);
            if(uncertain(current)) ExamJson.require(body.path("acknowledgeUncertain").isBoolean() && body.path("acknowledgeUncertain").asBoolean(),"原调用结果不确定，可能已计费；请单独确认再次调用风险");
            ExamTask created;
            if(ai.supports(current.kind())) {
                var prepared=ai.prepareRetry(actor,current.kind(),original,remaining);
                ExamJson.require(prepared.fingerprint().equals(body.path("planFingerprint").asText()),"重试计划、资料或模型配置已变化，请重新打开确认窗口");
                created=ai.enqueue(actor,key,request,prepared);
            } else {
                ExamJson.require(ExamJson.hash(original).equals(body.path("planFingerprint").asText()),"重试计划已变化，请重新确认");
                created=documents.retry(actor,current.kind(),original,key,request);
            }
            var progress=progress(id).put("retryTaskId",Long.toString(created.id()));
            db.update("update exam_job set progress_json=? where task_id=?",progress.toString(),id);
            db.update("update exam_task set revision=revision+1,updated_at=? where id=?",db.now(),id);
            return created;
        });
    }
}
