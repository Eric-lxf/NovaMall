package com.ruoyi.exam.service;

import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.exam.repository.ExamDataStore;
import com.ruoyi.exam.security.*;
import com.ruoyi.exam.support.*;
import static com.ruoyi.exam.repository.ExamDataStore.*;

public class ExamKnowledge {
    private final ExamDataStore db; private final ExamSources sources;
    public ExamKnowledge(ExamDataStore db,ExamSources sources) { this.db=db; this.sources=sources; }
    public ArrayNode list(ExamActor actor, Long versionId) {
        return list(actor,versionId,null);
    }
    public ArrayNode list(ExamActor actor,Long versionId,Long beforeId) {
        var result=ExamJson.array();
        var rows = versionId==null ? db.rows("select * from exam_knowledge_point where owner_user_id=? and id<? order by id desc limit 500",actor.userId(),ExamJson.cursor(beforeId))
                : db.rows("select * from exam_knowledge_point where owner_user_id=? and source_version_id=? and id<? order by id desc limit 500",actor.userId(),versionId,ExamJson.cursor(beforeId));
        rows.forEach(row -> result.add(view(row))); return result;
    }
    public ObjectNode save(ExamActor actor,Long id,JsonNode request) {
        long versionId=ExamJson.id(request,"sourceVersionId"); sources.version(actor,versionId,true);
        String name=ExamJson.text(request,"name",160), description=request.path("description").asText("");
        ExamJson.require(description.length()<=2000,"知识点描述过长");
        ArrayNode refs=ExamJson.array(); Set<Long> seen=new HashSet<>();
        for(var ref:ExamJson.list(request.get("sourceRefs"),1,8,"知识点依据")) {
            long fragmentId=ExamJson.id(ref,"fragmentId"); var fragment=sources.evidence(actor,fragmentId,true);
            String quote=ExamJson.text(ref,"quote",1000);
            ExamJson.require(number(fragment,"source_version_id")==versionId && string(fragment,"content").contains(quote) && seen.add(fragmentId),"知识点依据无效/重复");
            refs.add(ExamJson.object().put("fragmentId",Long.toString(fragmentId)).put("sourceVersionId",Long.toString(versionId)).put("quote",quote));
        }
        final String descriptionValue=description;
        return db.tx(() -> {
            long savedId;
            if(id==null) savedId=db.insert("exam_knowledge_point",values("owner_user_id",actor.userId(),"source_version_id",versionId,
                    "name",name,"description",descriptionValue,"refs_json",refs.toString(),"confirmed",0,"enabled",1,"revision",0,"created_at",db.now()));
            else {
                var old=db.lock("exam_knowledge_point",id); ExamAccessPolicy.requireOwner(actor,number(old,"owner_user_id"));
                if(number(old,"revision")!=ExamJson.revision(request)) throw ExamException.conflict();
                db.update("update exam_knowledge_point set name=?,description=?,refs_json=?,confirmed=0,revision=revision+1 where id=?",name,descriptionValue,refs.toString(),id);
                ExamJson.require(number(old,"source_version_id")==versionId,"知识点不能切换资料版本，请新建"); savedId=id;
            }
            return view(db.entity("exam_knowledge_point",savedId));
        });
    }
    public ObjectNode confirm(ExamActor actor,long id,JsonNode request) {
        return db.tx(() -> {
            var row=db.lock("exam_knowledge_point",id); ExamAccessPolicy.requireOwner(actor,number(row,"owner_user_id"));
            sources.version(actor,number(row,"source_version_id"),true); ExamJson.require(number(row,"enabled")==1,"已停用知识点不可确认");
            if(number(row,"revision")!=ExamJson.revision(request)) throw ExamException.conflict();
            for(var ref:ExamJson.parse(string(row,"refs_json"))) sources.evidence(actor,ExamJson.id(ref,"fragmentId"),true);
            db.update("update exam_knowledge_point set confirmed=1,revision=revision+1 where id=?",id);
            return view(db.entity("exam_knowledge_point",id));
        });
    }
    public ObjectNode toggle(ExamActor actor,long id,JsonNode request) {
        return db.tx(()->{
            var row=db.lock("exam_knowledge_point",id); ExamAccessPolicy.requireOwner(actor,number(row,"owner_user_id"));
            if(number(row,"revision")!=ExamJson.revision(request)) throw ExamException.conflict();
            ExamJson.require(request.path("enabled").isBoolean(),"启停状态无效");
            db.update("update exam_knowledge_point set enabled=?,confirmed=0,revision=revision+1 where id=?",request.path("enabled").asBoolean()?1:0,id);
            return view(db.entity("exam_knowledge_point",id));
        });
    }
    /** Merge creates a new unconfirmed candidate; archived originals remain for frozen blueprint provenance. */
    public ObjectNode merge(ExamActor actor,JsonNode request) {
        var revisions=new TreeMap<Long,Long>();
        for(var item:ExamJson.list(request.get("mergeFrom"),2,10,"合并来源")) {
            long id=ExamJson.id(item,"id"); ExamJson.require(revisions.put(id,ExamJson.revision(item))==null,"合并来源重复");
        }
        return db.tx(()->{
            long versionId=ExamJson.id(request,"sourceVersionId");
            for(var item:revisions.entrySet()) {
                var old=db.lock("exam_knowledge_point",item.getKey()); ExamAccessPolicy.requireOwner(actor,number(old,"owner_user_id"));
                if(number(old,"revision")!=item.getValue()) throw ExamException.conflict();
                ExamJson.require(number(old,"enabled")==1 && number(old,"source_version_id")==versionId,"只可合并同一资料版本的启用知识点");
            }
            var result=save(actor,null,request);
            for(long id:revisions.keySet()) db.update("update exam_knowledge_point set enabled=0,confirmed=0,revision=revision+1 where id=?",id);
            return result;
        });
    }
    public Map<String,Object> confirmed(ExamActor actor,long id) {
        var row=db.entity("exam_knowledge_point",id); ExamAccessPolicy.requireOwner(actor,number(row,"owner_user_id"));
        sources.version(actor,number(row,"source_version_id"),true);
        ExamJson.require(number(row,"confirmed")==1 && number(row,"enabled")==1,"知识点尚未确认或已停用"); return row;
    }
    private ObjectNode view(Map<String,Object> row) {
        var result=ExamJson.object().put("id",Long.toString(number(row,"id"))).put("sourceVersionId",Long.toString(number(row,"source_version_id")))
                .put("name",string(row,"name")).put("description",string(row,"description")).put("confirmed",number(row,"confirmed")==1)
                .put("enabled",number(row,"enabled")==1).put("revision",number(row,"revision"));
        result.set("sourceRefs",ExamJson.parse(string(row,"refs_json"))); return result;
    }
}
