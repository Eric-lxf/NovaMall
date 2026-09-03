package com.ruoyi.exam.service;

import java.math.BigDecimal;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.exam.repository.ExamDataStore;
import com.ruoyi.exam.security.*;
import com.ruoyi.exam.support.*;
import static com.ruoyi.exam.repository.ExamDataStore.*;

public class ExamPapers {
    private final ExamDataStore db; private final ExamQuestions questions;
    public ExamPapers(ExamDataStore db,ExamQuestions questions) { this.db=db; this.questions=questions; }
    public ArrayNode list(ExamActor actor) {
        return list(actor,null);
    }
    public ArrayNode list(ExamActor actor,Long beforeId) {
        var result=ExamJson.array(); db.rows("select * from exam_paper where owner_user_id=? and id<? order by id desc limit 200",actor.userId(),ExamJson.cursor(beforeId)).forEach(row->result.add(view(row))); return result;
    }
    public ObjectNode detail(ExamActor actor,long id) {
        var row=db.entity("exam_paper",id); ExamAccessPolicy.requireOwner(actor,number(row,"owner_user_id")); return view(row);
    }
    public ObjectNode save(ExamActor actor,Long id,JsonNode request) {
        String title=ExamJson.text(request,"title",160); var draft=validateDraft(actor,request.path("draft"));
        return db.tx(()->{
            long paperId;
            if(id==null) paperId=db.insert("exam_paper",values("owner_user_id",actor.userId(),"title",title,"status","DRAFT","revision",0,
                    "draft_json",draft.toString(),"created_at",db.now(),"updated_at",db.now()));
            else {
                var row=db.lock("exam_paper",id); ExamAccessPolicy.requireOwner(actor,number(row,"owner_user_id"));
                if(number(row,"revision")!=ExamJson.revision(request)) throw ExamException.conflict();
                ExamJson.require(string(row,"status").equals("DRAFT"),"已定版试卷不可修改，请创建新试卷");
                db.update("update exam_paper set title=?,draft_json=?,revision=revision+1,updated_at=? where id=?",title,draft.toString(),db.now(),id); paperId=id;
            }
            return detail(actor,paperId);
        });
    }
    public ObjectNode finalizePaper(ExamActor actor,long id,JsonNode request) {
        return db.tx(()->{
            var row=db.lock("exam_paper",id); ExamAccessPolicy.requireOwner(actor,number(row,"owner_user_id"));
            if(number(row,"revision")!=ExamJson.revision(request)) throw ExamException.conflict();
            ExamJson.require(string(row,"status").equals("DRAFT"),"试卷已经定版");
            JsonNode draft=ExamJson.parse(string(row,"draft_json"));
            var versionIds=new TreeSet<Long>(); draft.path("items").forEach(item->versionIds.add(ExamJson.id(item,"questionVersionId")));
            // All source locks precede question-version locks; source changes cannot race a successful finalization.
            var sourceIds=new TreeSet<Long>();
            for(long qid:versionIds) for(var ref:db.rows("select v.source_id from exam_question_source r join exam_source_version v on r.source_version_id=v.id where r.question_version_id=?",qid)) sourceIds.add(number(ref,"source_id"));
            sourceIds.forEach(sid->db.lock("exam_source",sid));
            var rootIds=new TreeSet<Long>(); versionIds.forEach(vid->rootIds.add(number(db.entity("exam_question_version",vid),"question_id")));
            rootIds.forEach(qid->db.lock("exam_question",qid)); versionIds.forEach(vid->db.lock("exam_question_version",vid));
            var validated=validateDraft(actor,draft);
            var snapshot=ExamJson.object().put("title",string(row,"title")).put("durationMinutes",validated.path("durationMinutes").asInt())
                    .put("totalScore",validated.path("totalScore").decimalValue()).put("templateVersion","exam-paper.v1");
            var items=snapshot.putArray("items"); int ordinal=0;
            for(var item:validated.path("items")) {
                var version=questions.approved(actor,ExamJson.id(item,"questionVersionId"));
                var frozen=ExamJson.object().put("ordinal",++ordinal).put("questionVersionId",Long.toString(number(version,"id"))).put("score",item.path("score").decimalValue());
                frozen.set("content",ExamJson.parse(string(version,"content_json"))); items.add(frozen);
            }
            long versionId=db.insert("exam_paper_version",values("paper_id",id,"version_no",1,"snapshot_json",snapshot.toString(),"content_hash",ExamJson.hash(snapshot),"created_at",db.now()));
            ordinal=0; for(var item:items) db.update("insert into exam_paper_item (paper_version_id,ordinal_no,question_version_id,score) values (?,?,?,?)",
                    versionId,++ordinal,ExamJson.id(item,"questionVersionId"),item.path("score").decimalValue());
            db.update("update exam_paper set current_version_id=?,status='FINALIZED',revision=revision+1,updated_at=? where id=?",versionId,db.now(),id);
            return detail(actor,id);
        });
    }
    public ObjectNode snapshot(ExamActor actor,long versionId,boolean student) {
        var version=db.entity("exam_paper_version",versionId); var paper=db.entity("exam_paper",number(version,"paper_id"));
        ExamAccessPolicy.requireOwner(actor,number(paper,"owner_user_id"));
        var snapshot=(ObjectNode)ExamJson.parse(string(version,"snapshot_json"));
        return student ? studentProjection(snapshot) : snapshot;
    }
    public void requireExportable(ExamActor actor,long versionId) {
        snapshot(actor,versionId,true);
        for(var item:db.rows("select question_version_id from exam_paper_item where paper_version_id=?",versionId)) questions.approved(actor,number(item,"question_version_id"));
    }
    public void lockSources(long versionId) {
        var ids=new TreeSet<Long>();
        for(var row:db.rows("select distinct v.source_id from exam_paper_item i join exam_question_source r on i.question_version_id=r.question_version_id join exam_source_version v on v.id=r.source_version_id where i.paper_version_id=?",versionId)) ids.add(number(row,"source_id"));
        ids.forEach(id->db.lock("exam_source",id));
        for(var row:db.rows("select distinct v.question_id from exam_paper_item i join exam_question_version v on i.question_version_id=v.id where i.paper_version_id=? order by v.question_id",versionId)) db.lock("exam_question",number(row,"question_id"));
    }
    public static ObjectNode studentProjection(JsonNode snapshot) {
        var result=ExamJson.object().put("title",snapshot.path("title").asText()).put("durationMinutes",snapshot.path("durationMinutes").asInt())
                .put("totalScore",snapshot.path("totalScore").decimalValue()).put("templateVersion",snapshot.path("templateVersion").asText());
        var items=result.putArray("items");
        for(var item:snapshot.path("items")) {
            var question=item.path("content"); var content=ExamJson.object().put("type",question.path("type").asText()).put("stem",question.path("stem").asText());
            if(question.has("options")) {
                var options=content.putArray("options"); question.path("options").forEach(option->options.add(ExamJson.object().put("id",option.path("id").asText()).put("text",option.path("text").asText())));
            }
            var output=ExamJson.object().put("ordinal",item.path("ordinal").asInt()).put("score",item.path("score").decimalValue()); output.set("content",content); items.add(output);
        }
        return result;
    }
    private ObjectNode validateDraft(ExamActor actor,JsonNode draft) {
        ExamJson.require(draft.path("durationMinutes").isIntegralNumber() && draft.path("durationMinutes").asInt()>0 && draft.path("durationMinutes").asInt()<=480,"考试时长无效");
        var result=ExamJson.object().put("durationMinutes",draft.path("durationMinutes").asInt()); var items=result.putArray("items");
        Set<Long> ids=new HashSet<>(); BigDecimal sum=BigDecimal.ZERO;
        for(var item:ExamJson.list(draft.get("items"),1,50,"试卷题目")) {
            long id=ExamJson.id(item,"questionVersionId"); ExamJson.require(ids.add(id),"试卷包含重复题目版本"); questions.approved(actor,id);
            BigDecimal score=ExamJson.score(item.get("score")); sum=sum.add(score); items.add(ExamJson.object().put("questionVersionId",Long.toString(id)).put("score",score));
        }
        ExamJson.require(sum.compareTo(ExamJson.score(draft.get("totalScore")))==0,"试卷总分与题目分值之和不符"); result.put("totalScore",sum); return result;
    }
    private ObjectNode view(Map<String,Object> row) {
        var result=ExamJson.object().put("id",Long.toString(number(row,"id"))).put("title",string(row,"title")).put("status",string(row,"status"))
                .put("revision",number(row,"revision")).put("currentVersionId",string(row,"current_version_id"));
        result.set("draft",ExamJson.parse(string(row,"draft_json"))); return result;
    }
}
