package com.ruoyi.exam.service;

import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.exam.repository.ExamDataStore;
import com.ruoyi.exam.security.*;
import com.ruoyi.exam.support.*;
import static com.ruoyi.exam.repository.ExamDataStore.*;

public class ExamQuestions {
    private final ExamDataStore db; private final ExamSources sources; private final ExamBlueprints blueprints;
    private final ExamQuestionValidator validator=new ExamQuestionValidator();
    public ExamQuestions(ExamDataStore db,ExamSources sources,ExamBlueprints blueprints) { this.db=db; this.sources=sources; this.blueprints=blueprints; }
    public ArrayNode list(ExamActor actor,boolean reviewQueue) {
        return list(actor,reviewQueue,null);
    }
    public ArrayNode list(ExamActor actor,boolean reviewQueue,Long beforeId) {
        if(reviewQueue) ExamJson.require(actor.reviewer() || actor.administrator(),"需要审核权限");
        var result=ExamJson.array(); var rows=reviewQueue
                ? db.rows("select q.* from exam_question q join exam_question_version v on q.current_version_id=v.id where v.review_state='PENDING_REVIEW' and q.enabled=1 and q.id<? order by q.id desc limit 200",ExamJson.cursor(beforeId))
                : db.rows("select * from exam_question where owner_user_id=? and id<? order by id desc limit 200",actor.userId(),ExamJson.cursor(beforeId));
        for(var row:rows) {
            var item=ExamJson.object().put("id",Long.toString(number(row,"id"))).put("revision",number(row,"revision"))
                    .put("enabled",number(row,"enabled")==1).put("currentVersionId",string(row,"current_version_id"));
            item.putArray("versions").add(versionSummary(db.entity("exam_question_version",number(row,"current_version_id")))); result.add(item);
        } return result;
    }
    public ObjectNode detail(ExamActor actor,long id) {
        var question=accessible(actor,id); var result=ExamJson.object().put("id",Long.toString(id)).put("revision",number(question,"revision"))
                .put("enabled",number(question,"enabled")==1).put("currentVersionId",string(question,"current_version_id"));
        var versions=result.putArray("versions");
        for(var version:db.rows("select * from exam_question_version where question_id=? order by version_no desc",id)) versions.add(versionView(version));
        return result;
    }
    public ObjectNode save(ExamActor actor,Long id,JsonNode request) {
        return saveContent(actor,id,ExamJson.id(request,"blueprintId"),request.path("content"),id==null?null:ExamJson.revision(request),"MANUAL");
    }
    public ObjectNode toggle(ExamActor actor,long id,JsonNode request) {
        return db.tx(()->{
            var initial=db.entity("exam_question",id); lockSources(number(initial,"current_version_id"));
            var row=db.lock("exam_question",id); ExamAccessPolicy.requireOwner(actor,number(row,"owner_user_id"));
            if(number(row,"revision")!=ExamJson.revision(request)) throw ExamException.conflict();
            ExamJson.require(request.path("enabled").isBoolean(),"启停状态无效");
            db.update("update exam_question set enabled=?,revision=revision+1 where id=?",request.path("enabled").asBoolean()?1:0,id); return detail(actor,id);
        });
    }
    public ObjectNode saveContent(ExamActor actor,Long id,long blueprintId,JsonNode content,Long expectedRevision,String origin) {
        validate(actor,blueprintId,content);
        String hash=ExamJson.hash(content); String slotId=ExamJson.text(content,"slotId",64);
        return db.tx(()->{
            long questionId; int versionNo=1;
            if(id==null) questionId=db.insert("exam_question",values("owner_user_id",actor.userId(),"enabled",1,"revision",0,"created_at",db.now()));
            else {
                var question=db.lock("exam_question",id); ExamAccessPolicy.requireOwner(actor,number(question,"owner_user_id"));
                if(expectedRevision==null || number(question,"revision")!=expectedRevision) throw ExamException.conflict();
                questionId=id; versionNo=(int)db.count("select coalesce(max(version_no),0)+1 from exam_question_version where question_id=?",id);
            }
            long versionId=db.insert("exam_question_version",values("question_id",questionId,"version_no",versionNo,"blueprint_id",blueprintId,"slot_id",slotId,
                    "content_json",content.toString(),"content_hash",hash,"review_state","DRAFT","origin",origin,"created_at",db.now()));
            for(var ref:content.path("sourceRefs")) db.update("insert into exam_question_source (question_version_id,source_version_id,fragment_id,quote_text,quote_hash) values (?,?,?,?,?)",
                    versionId,ExamJson.id(ref,"sourceVersionId"),ExamJson.id(ref,"fragmentId"),ref.path("quote").asText(),ExamJson.hash(ref.path("quote")));
            db.update("update exam_question set current_version_id=?,revision=revision+1 where id=?",versionId,questionId);
            addCheck(versionId,"RULES",ExamJson.object().put("passed",true).put("note","结构与来源校验通过；不代表答案正确"),true);
            return detail(actor,questionId);
        });
    }
    public void validate(ExamActor actor,long blueprintId,JsonNode content) {
        var slot=blueprints.slot(actor,blueprintId,ExamJson.text(content,"slotId",64));
        validator.validate(content,slot,id->{
            var fragment=sources.evidence(actor,Long.parseLong(id),true);
            return new ExamQuestionValidator.Evidence(Long.toString(number(fragment,"source_version_id")),string(fragment,"content"));
        });
    }
    public ObjectNode submit(ExamActor actor,long versionId,JsonNode request) {
        return db.tx(()->{
            lockSources(versionId);
            var initial=db.entity("exam_question_version",versionId); var question=db.lock("exam_question",number(initial,"question_id"));
            var version=db.lock("exam_question_version",versionId);
            ExamAccessPolicy.requireOwner(actor,number(question,"owner_user_id")); requireCurrent(question,versionId,version,request);
            ExamJson.require(number(question,"enabled")==1,"停用题目不可提交审核");
            ExamJson.require(Set.of("DRAFT","REJECTED").contains(string(version,"review_state")),"该版本当前不能提交审核");
            validate(actor,number(version,"blueprint_id"),ExamJson.parse(string(version,"content_json")));
            db.update("update exam_question_version set review_state='PENDING_REVIEW' where id=?",versionId);
            db.update("update exam_question set revision=revision+1 where id=?",number(question,"id")); return detail(actor,number(question,"id"));
        });
    }
    public ObjectNode review(ExamActor actor,long versionId,JsonNode request) {
        ExamJson.require(actor.reviewer() || actor.administrator(),"需要明确的人工审核权限");
        String decision=ExamJson.text(request,"decision",24), reason=ExamJson.text(request,"reason",2000);
        ExamJson.require(Set.of("APPROVED","REJECTED").contains(decision),"审核结论无效");
        return db.tx(()->{
            lockSources(versionId);
            var initial=db.entity("exam_question_version",versionId); var question=db.lock("exam_question",number(initial,"question_id"));
            var version=db.lock("exam_question_version",versionId); requireCurrent(question,versionId,version,request);
            ExamJson.require(string(version,"review_state").equals("PENDING_REVIEW"),"该版本不在待审状态");
            ExamJson.require(number(question,"enabled")==1,"停用题目不可审核");
            if(decision.equals("APPROVED")) {
                requireEvidenceAvailable(versionId);
                var checks=db.rows("select * from exam_question_check where question_version_id=? and check_kind='AI_VERIFY' order by id desc limit 1",versionId);
                if(!checks.isEmpty()) ExamJson.require(number(checks.get(0),"passed")==1,"自动复核存在问题，请先修改题目生成新版本");
                else ExamJson.require(request.path("manualVerification").isBoolean() && request.path("manualVerification").asBoolean(),"未运行 AI 独立复核，请明确确认已逐项人工核对答案与依据");
            }
            db.insert("exam_question_review",values("question_version_id",versionId,"content_hash",string(version,"content_hash"),"reviewer_id",actor.userId(),
                    "decision",decision,"reason",reason,"created_at",db.now()));
            db.update("update exam_question_version set review_state=? where id=?",decision,versionId);
            db.update("update exam_question set revision=revision+1 where id=?",number(question,"id")); return detail(actor,number(question,"id"));
        });
    }
    public void addCheck(long versionId,String kind,JsonNode result,boolean passed) {
        var version=db.entity("exam_question_version",versionId);
        db.insert("exam_question_check",values("question_version_id",versionId,"content_hash",string(version,"content_hash"),"check_kind",kind,
                "result_json",result.toString(),"passed",passed?1:0,"created_at",db.now()));
    }
    public Map<String,Object> accessible(ExamActor actor,long id) {
        var row=db.entity("exam_question",id);
        if(!actor.reviewer()) ExamAccessPolicy.requireOwner(actor,number(row,"owner_user_id"));
        return row;
    }
    public Map<String,Object> approved(ExamActor actor,long versionId) {
        var version=db.entity("exam_question_version",versionId); var question=db.entity("exam_question",number(version,"question_id"));
        ExamAccessPolicy.requireOwner(actor,number(question,"owner_user_id"));
        ExamJson.require(number(question,"enabled")==1 && string(version,"review_state").equals("APPROVED"),"试卷只允许使用已批准且未停用的明确题目版本");
        requireEvidenceAvailable(versionId); return version;
    }
    public void requireEvidenceAvailable(long versionId) {
        var refs=db.rows("select r.*,v.source_id,v.status,s.current_version_id,s.enabled,f.usable from exam_question_source r "
                + "join exam_source_version v on r.source_version_id=v.id join exam_source s on v.source_id=s.id join exam_source_fragment f on r.fragment_id=f.id and f.source_version_id=v.id where r.question_version_id=?",versionId);
        if(refs.isEmpty() || refs.size()!=db.count("select count(*) from exam_question_source where question_version_id=?",versionId))
            throw new ExamException("EXAM_SOURCE_UNAVAILABLE","题目引用依据缺失");
        for(var ref:refs) {
            if(number(ref,"enabled")!=1 || number(ref,"current_version_id")!=number(ref,"source_version_id") || number(ref,"usable")!=1 || !string(ref,"status").equals("READY"))
                throw new ExamException("EXAM_SOURCE_UNAVAILABLE","题目引用资料已更新、停用或撤销可用状态");
        }
    }
    public void lockSources(long versionId) {
        for(var row:db.rows("select distinct v.source_id from exam_question_source r join exam_source_version v on r.source_version_id=v.id where r.question_version_id=? order by v.source_id",versionId))
            db.lock("exam_source",number(row,"source_id"));
    }
    private void requireCurrent(Map<String,Object> question,long versionId,Map<String,Object> version,JsonNode request) {
        if(number(question,"current_version_id")!=versionId || number(question,"revision")!=ExamJson.revision(request)
                || !string(version,"content_hash").equals(ExamJson.text(request,"contentHash",64))) throw ExamException.conflict();
    }
    private ObjectNode versionSummary(Map<String,Object> version) {
        long id=number(version,"id"); var result=ExamJson.object().put("id",Long.toString(id)).put("versionNo",number(version,"version_no"))
                .put("blueprintId",Long.toString(number(version,"blueprint_id"))).put("reviewState",string(version,"review_state"))
                .put("contentHash",string(version,"content_hash")).put("origin",string(version,"origin"));
        result.set("content",ExamJson.parse(string(version,"content_json")));
        try { requireEvidenceAvailable(id); result.put("sourceAvailable",true); } catch(ExamException stale) { result.put("sourceAvailable",false); }
        return result;
    }
    private ObjectNode versionView(Map<String,Object> version) {
        long id=number(version,"id"); var result=versionSummary(version);
        var evidence=result.putArray("evidence");
        for(var row:db.rows("select r.fragment_id,r.source_version_id,r.quote_text,f.content,f.locator_json from exam_question_source r join exam_source_fragment f on r.fragment_id=f.id where r.question_version_id=?",id)) {
            var item=ExamJson.object().put("fragmentId",Long.toString(number(row,"fragment_id"))).put("sourceVersionId",Long.toString(number(row,"source_version_id")))
                    .put("text",string(row,"content")).put("quote",string(row,"quote_text")); item.set("locator",ExamJson.parse(string(row,"locator_json"))); evidence.add(item);
        }
        var checks=result.putArray("checks");
        db.rows("select * from exam_question_check where question_version_id=? order by id desc",id).forEach(row->{
            var check=ExamJson.object().put("kind",string(row,"check_kind")).put("passed",number(row,"passed")==1);
            check.set("result",ExamJson.parse(string(row,"result_json"))); checks.add(check);
        });
        var reviews=result.putArray("reviews");
        db.rows("select * from exam_question_review where question_version_id=? order by id desc",id).forEach(row->reviews.add(ExamJson.object()
                .put("reviewerId",Long.toString(number(row,"reviewer_id"))).put("decision",string(row,"decision")).put("reason",string(row,"reason")).put("createdAt",string(row,"created_at"))));
        return result;
    }
}
