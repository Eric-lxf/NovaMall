package com.ruoyi.exam.service;

import java.math.BigDecimal;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.exam.repository.ExamDataStore;
import com.ruoyi.exam.security.*;
import com.ruoyi.exam.support.*;
import static com.ruoyi.exam.repository.ExamDataStore.*;

public class ExamBlueprints {
    public static final Set<String> TYPES=Set.of("SINGLE_CHOICE","MULTIPLE_CHOICE","TRUE_FALSE","SHORT_ANSWER");
    private final ExamDataStore db; private final ExamSources sources; private final ExamKnowledge knowledge;
    public ExamBlueprints(ExamDataStore db,ExamSources sources,ExamKnowledge knowledge) { this.db=db; this.sources=sources; this.knowledge=knowledge; }
    public ArrayNode list(ExamActor actor) {
        return list(actor,null);
    }
    public ArrayNode list(ExamActor actor,Long beforeId) {
        var result=ExamJson.array(); db.rows("select * from exam_blueprint where owner_user_id=? and id<? order by id desc limit 200",actor.userId(),ExamJson.cursor(beforeId)).forEach(row->result.add(view(row))); return result;
    }
    public ObjectNode detail(ExamActor actor,long id) { return view(owned(actor,id)); }
    public Map<String,Object> owned(ExamActor actor,long id) {
        var row=db.entity("exam_blueprint",id); ExamAccessPolicy.requireOwner(actor,number(row,"owner_user_id")); return row;
    }
    public ObjectNode save(ExamActor actor,Long id,JsonNode request) {
        String title=ExamJson.text(request,"title",160); var checked=validate(actor,request.path("settings"),request.path("slots"));
        String hash=ExamJson.hash(ExamJson.object().put("title",title).set("body",checked));
        return db.tx(()->{
            long savedId;
            if(id==null) savedId=db.insert("exam_blueprint",values("owner_user_id",actor.userId(),"title",title,"status","DRAFT","revision",0,
                    "settings_json",checked.get("settings").toString(),"slots_json",checked.get("slots").toString(),"content_hash",hash,"created_at",db.now(),"updated_at",db.now()));
            else {
                var old=db.lock("exam_blueprint",id); ExamAccessPolicy.requireOwner(actor,number(old,"owner_user_id"));
                if(number(old,"revision")!=ExamJson.revision(request)) throw ExamException.conflict();
                ExamJson.require(string(old,"status").equals("DRAFT"),"已确认蓝图不可原地修改，请复制新版本");
                db.update("update exam_blueprint set title=?,settings_json=?,slots_json=?,content_hash=?,revision=revision+1,updated_at=? where id=?",
                        title,checked.get("settings").toString(),checked.get("slots").toString(),hash,db.now(),id); savedId=id;
            }
            return detail(actor,savedId);
        });
    }
    public ObjectNode confirm(ExamActor actor,long id,JsonNode request) {
        return db.tx(()->{
            var row=db.lock("exam_blueprint",id); ExamAccessPolicy.requireOwner(actor,number(row,"owner_user_id"));
            if(number(row,"revision")!=ExamJson.revision(request) || !string(row,"content_hash").equals(ExamJson.text(request,"contentHash",64))) throw ExamException.conflict();
            ExamJson.require(string(row,"status").equals("DRAFT"),"蓝图已确认");
            var revalidated=validate(actor,ExamJson.parse(string(row,"settings_json")),ExamJson.parse(string(row,"slots_json")));
            if(!ExamJson.hash(ExamJson.object().put("title",string(row,"title")).set("body",revalidated)).equals(string(row,"content_hash")))
                throw new ExamException("EXAM_VERSION_CONFLICT","知识点发生变化，请重新保存并核对蓝图");
            db.update("update exam_blueprint set status='CONFIRMED',revision=revision+1,updated_at=? where id=?",db.now(),id); return detail(actor,id);
        });
    }
    public JsonNode slot(ExamActor actor,long id,String slotId) {
        var row=owned(actor,id); ExamJson.require(string(row,"status").equals("CONFIRMED"),"蓝图须先确认");
        for(var slot:ExamJson.parse(string(row,"slots_json"))) if(slot.path("slotId").asText().equals(slotId)) return slot;
        throw new ExamException("EXAM_INPUT_INVALID","题目槽位不属于蓝图");
    }
    public ObjectNode validate(ExamActor actor,JsonNode settings,JsonNode slots) {
        ExamJson.require(settings.isObject(),"命题设置无效");
        ExamJson.require(settings.path("durationMinutes").isIntegralNumber() && settings.path("durationMinutes").asInt()>=1
                && settings.path("durationMinutes").asInt()<=480,"考试时长须为 1–480 分钟");
        BigDecimal requiredTotal=ExamJson.score(settings.get("totalScore"));
        Set<Long> versionIds=new HashSet<>(); ArrayNode versions=ExamJson.array();
        for(var id:ExamJson.list(settings.get("sourceVersionIds"),1,10,"资料版本")) {
            long value=ExamJson.id(ExamJson.object().set("id",id),"id");
            ExamJson.require(versionIds.add(value),"资料版本重复"); sources.version(actor,value,true); versions.add(Long.toString(value));
        }
        Set<String> slotIds=new HashSet<>(); ArrayNode checkedSlots=ExamJson.array(), knowledgeSnapshot=ExamJson.array(); Set<Long> snapshotIds=new HashSet<>();
        BigDecimal total=BigDecimal.ZERO; Map<String,Integer> types=new HashMap<>(), difficulties=new HashMap<>();
        for(var slot:ExamJson.list(slots,1,50,"题目槽位")) {
            String slotId=ExamJson.text(slot,"slotId",64), type=ExamJson.text(slot,"type",24), difficulty=ExamJson.text(slot,"targetDifficulty",16);
            ExamJson.require(slotId.matches("[A-Za-z0-9_-]+") && slotIds.add(slotId),"槽位 ID 无效或重复");
            ExamJson.require(TYPES.contains(type) && Set.of("EASY","MEDIUM","HARD").contains(difficulty),"题型或目标难度无效");
            BigDecimal score=ExamJson.score(slot.get("score")); total=total.add(score);
            var fragments=ExamJson.array(); Set<Long> fragmentIds=new HashSet<>();
            for(var fragment:ExamJson.list(slot.get("sourceFragmentIds"),1,30,"槽位依据")) {
                long fid=ExamJson.id(ExamJson.object().set("id",fragment),"id"); var source=sources.evidence(actor,fid,true);
                ExamJson.require(versionIds.contains(number(source,"source_version_id")) && fragmentIds.add(fid),"槽位依据越界或重复"); fragments.add(Long.toString(fid));
            }
            var kids=ExamJson.array(); Set<Long> uniqueKnowledge=new HashSet<>();
            for(var kp:ExamJson.list(slot.get("knowledgePointIds"),1,8,"槽位知识点")) {
                long kid=ExamJson.id(ExamJson.object().set("id",kp),"id"); var k=knowledge.confirmed(actor,kid);
                ExamJson.require(versionIds.contains(number(k,"source_version_id")) && uniqueKnowledge.add(kid),"知识点越界或重复");
                boolean linked=false; for(var ref:ExamJson.parse(string(k,"refs_json"))) if(fragmentIds.contains(ExamJson.id(ref,"fragmentId"))) linked=true;
                ExamJson.require(linked,"槽位依据未覆盖选定知识点"); kids.add(Long.toString(kid));
                if(snapshotIds.add(kid)) knowledgeSnapshot.add(ExamJson.object().put("id",Long.toString(kid)).put("revision",number(k,"revision"))
                        .put("name",string(k,"name")).put("description",string(k,"description")));
            }
            var normalized=ExamJson.object().put("slotId",slotId).put("type",type).put("targetDifficulty",difficulty).put("score",score);
            String cognitive=slot.path("cognitiveLevel").asText("UNDERSTAND");
            ExamJson.require(Set.of("REMEMBER","UNDERSTAND","APPLY","ANALYZE").contains(cognitive),"认知层级无效"); normalized.put("cognitiveLevel",cognitive);
            normalized.set("knowledgePointIds",kids); normalized.set("sourceFragmentIds",fragments); checkedSlots.add(normalized);
            types.merge(type,1,Integer::sum); difficulties.merge(difficulty,1,Integer::sum);
        }
        ExamJson.require(total.compareTo(requiredTotal)==0,"槽位分值之和与设定总分不符");
        validateCounts(settings.get("typeCounts"),types,"题型数量"); validateCounts(settings.get("difficultyCounts"),difficulties,"难度数量");
        var normalizedSettings=ExamJson.object().put("durationMinutes",settings.path("durationMinutes").asInt()).put("totalScore",total)
                .put("audience",settings.path("audience").asText("一般培训"));
        ExamJson.require(normalizedSettings.path("audience").asText().length()<=160,"适用对象过长");
        for(String field:List.of("rawRequirements","analysisRequirements")) {
            String value=settings.path(field).asText(""); ExamJson.require(value.length()<=(field.equals("rawRequirements")?4000:2000),"命题要求过长"); normalizedSettings.put(field,value);
        }
        normalizedSettings.set("sourceVersionIds",versions); normalizedSettings.set("knowledgeSnapshot",knowledgeSnapshot);
        normalizedSettings.set("typeCounts",ExamJson.MAPPER.valueToTree(types)); normalizedSettings.set("difficultyCounts",ExamJson.MAPPER.valueToTree(difficulties));
        var result=ExamJson.object(); result.set("settings",normalizedSettings); result.set("slots",checkedSlots); return result;
    }
    private void validateCounts(JsonNode expected,Map<String,Integer> actual,String label) {
        if(expected==null || expected.isMissingNode()) return;
        ExamJson.require(expected.isObject(),label+" 无效"); var names=new HashSet<String>(); expected.fieldNames().forEachRemaining(names::add); names.addAll(actual.keySet());
        for(String name:names) ExamJson.require((!expected.has(name) || (expected.get(name).isIntegralNumber() && expected.get(name).asInt()>=0))
                && expected.path(name).asInt(0)==actual.getOrDefault(name,0),label+" 与槽位不一致");
    }
    private ObjectNode view(Map<String,Object> row) {
        var result=ExamJson.object().put("id",Long.toString(number(row,"id"))).put("title",string(row,"title")).put("status",string(row,"status"))
                .put("revision",number(row,"revision")).put("contentHash",string(row,"content_hash"));
        result.set("settings",ExamJson.parse(string(row,"settings_json"))); result.set("slots",ExamJson.parse(string(row,"slots_json"))); return result;
    }
}
