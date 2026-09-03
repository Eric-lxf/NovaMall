package com.ruoyi.exam.service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.exam.domain.ExamTask;
import com.ruoyi.exam.repository.*;
import com.ruoyi.exam.security.*;
import com.ruoyi.exam.support.*;
import org.springframework.dao.DuplicateKeyException;
import static com.ruoyi.exam.repository.ExamDataStore.*;

/** Durable submission, bounded admission and lease-fenced result transactions shared by business workers. */
public class ExamJobs {
    private final ExamDataStore db; private final ExamTaskRepository tasks;
    public ExamJobs(ExamDataStore db,ExamTaskRepository tasks) { this.db=db; this.tasks=tasks; }
    public ExamTask existing(ExamActor actor,String kind,String key,JsonNode request) {
        ExamJson.require(key!=null && key.matches("[A-Za-z0-9_-]{16,100}"),"请提供 16–100 位幂等键");
        var old=tasks.findByKey(actor.userId(),kind,key);
        if(old.isEmpty()) return null;
        if(!old.get().requestHash().equals(ExamJson.hash(request))) throw ExamException.conflict(); return old.get();
    }
    public ExamTask submit(ExamActor actor,String kind,String title,String key,JsonNode request,JsonNode input,List<String> slots) {
        var old=existing(actor,kind,key,request); if(old!=null) return old;
        try { return db.tx(()->{
            // Global admission serializes only the tiny enqueue transaction, never document/model work.
            db.update("insert ignore into exam_owner_lock (owner_user_id) values (0)");
            db.one("select * from exam_owner_lock where owner_user_id=0 for update");
            var repeated=existing(actor,kind,key,request); if(repeated!=null) return repeated;
            ExamJson.require(db.count("select count(*) from exam_task where status in ('QUEUED','RUNNING','CANCEL_REQUESTED')")<100,"任务队列已满，请稍后重试");
            ExamJson.require(db.count("select count(*) from exam_task where owner_user_id=? and status in ('QUEUED','RUNNING','CANCEL_REQUESTED')",actor.userId())<3,"每人最多 3 个活动任务");
            long id=db.insert("exam_task",values("owner_user_id",actor.userId(),"kind",kind,"title",title,"idempotency_key",key,
                    "request_hash",ExamJson.hash(request),"status","QUEUED","attempt_no",0,"revision",0,"created_at",db.now(),"updated_at",db.now()));
            db.update("insert into exam_job (task_id,input_json,result_json,progress_json,calls_reserved,tokens_reserved) values (?,?,?, ?,0,0)",id,input.toString(),"{}","{}");
            for(String slot:slots) db.insert("exam_task_item",values("task_id",id,"slot_id",slot,"status","QUEUED"));
            return tasks.find(id).orElseThrow();
        }); } catch(DuplicateKeyException race) { var found=existing(actor,kind,key,request); if(found!=null) return found; throw race; }
    }
    public JsonNode input(long taskId) { return ExamJson.parse(string(db.one("select * from exam_job where task_id=?",taskId),"input_json")); }
    public ObjectNode detail(ExamActor actor,long id) {
        var task=tasks.find(id).orElseThrow(ExamException::notFound); ExamAccessPolicy.requireOwner(actor,task.ownerUserId());
        var result=ExamJson.object().put("taskId",Long.toString(id)).put("status",task.status());
        var job=db.one("select * from exam_job where task_id=?",id);
        result.put("callsReserved",number(job,"calls_reserved")).put("tokensReserved",number(job,"tokens_reserved"));
        result.set("result",ExamJson.parse(string(job,"result_json"))); result.set("progress",ExamJson.parse(string(job,"progress_json")));
        var items=result.putArray("items"); db.rows("select * from exam_task_item where task_id=? order by id",id).forEach(row->items.add(ExamJson.object()
                .put("slotId",string(row,"slot_id")).put("status",string(row,"status")).put("questionVersionId",string(row,"result_version_id")).put("errorCode",string(row,"error_code"))));
        var calls=result.putArray("calls"); db.rows("select * from exam_ai_call where task_id=? order by call_no",id).forEach(row->{
            var call=ExamJson.object().put("callNo",number(row,"call_no")).put("model",string(row,"model_name")).put("status",string(row,"status"))
                    .put("requestId",string(row,"request_id")).put("finishReason",string(row,"finish_reason")).put("errorCode",string(row,"error_code"));
            call.set("usage",ExamJson.parse(string(row,"usage_json"))); calls.add(call);
        }); return result;
    }
    public Lease lease(ExamTask task,int attempt,String worker) { return new Lease(task.id(),attempt,worker); }
    public final class Lease {
        private final long id; private final int attempt; private final String worker;
        private Lease(long id,int attempt,String worker) { this.id=id; this.attempt=attempt; this.worker=worker; }
        public <T> T commit(Supplier<T> write) {
            return db.tx(()->{
                var row=db.lock("exam_task",id);
                var until=row.get("lease_until"); LocalDateTime leaseUntil=until instanceof java.sql.Timestamp t?t.toLocalDateTime():(LocalDateTime)until;
                if(!"RUNNING".equals(string(row,"status")) || number(row,"attempt_no")!=attempt || !worker.equals(string(row,"lease_owner"))
                        || leaseUntil==null || !leaseUntil.isAfter(db.now())) throw new ExamException("EXAM_LEASE_LOST","任务已取消或执行租约失效");
                return write.get();
            });
        }
        public void check() { commit(()->null); }
    }
}
