package com.ruoyi.exam.service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.exam.parser.ExamTextParser;
import com.ruoyi.exam.repository.ExamDataStore;
import com.ruoyi.exam.security.*;
import com.ruoyi.exam.support.*;
import static com.ruoyi.exam.repository.ExamDataStore.*;

public class ExamSources {
    private final ExamDataStore db; private final ExamFiles files;
    public ExamSources(ExamDataStore db, ExamFiles files) { this.db = db; this.files = files; }

    public ObjectNode importText(ExamActor actor, JsonNode request, Long sourceId) {
        String title = ExamJson.text(request,"title",160), text = ExamJson.text(request,"text",200000);
        return importParsed(actor, title, title.replaceAll("[\\\\/:*?\"<>|]","_") + ".txt", "text/plain",
                text.getBytes(StandardCharsets.UTF_8), sourceId, sourceId == null ? null : ExamJson.revision(request),
                new ExamTextParser().parse(text.getBytes(StandardCharsets.UTF_8)));
    }
    public ObjectNode importTxt(ExamActor actor, String title, String filename, byte[] bytes, Long sourceId, Long revision) {
        ExamJson.require(title != null && !title.isBlank() && title.length() <= 160, "资料标题无效");
        ExamJson.require(filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".txt"), "此接口只接收 UTF-8 TXT");
        return importParsed(actor,title,filename,"text/plain",bytes,sourceId,revision,new ExamTextParser().parse(bytes));
    }
    public ObjectNode importParsed(ExamActor actor, String title, String name, String mime, byte[] bytes,
            Long sourceId, Long expectedRevision, ExamTextParser.Result parsed) {
        return files.create(actor,bytes,name,mime,"SOURCE",fileId -> persistParsed(actor,title,bytes,sourceId,expectedRevision,parsed,fileId));
    }
    public ObjectNode importStored(ExamActor actor,String title,byte[] bytes,Long sourceId,Long expectedRevision,ExamTextParser.Result parsed,long fileId) {
        var file=db.entity("exam_file",fileId); ExamAccessPolicy.requireOwner(actor,number(file,"owner_user_id"));
        ExamJson.require(string(file,"sha256").equals(ExamJson.hash(bytes)) && string(file,"purpose").equals("PENDING_SOURCE"),"原始文件与解析任务不一致");
        return db.tx(()->persistParsed(actor,title,bytes,sourceId,expectedRevision,parsed,fileId));
    }
    private ObjectNode persistParsed(ExamActor actor,String title,byte[] bytes,Long sourceId,Long expectedRevision,ExamTextParser.Result parsed,long fileId) {
            long id; int version = 1;
            if (sourceId == null) {
                id = db.insert("exam_source", values("owner_user_id",actor.userId(),"title",title,"enabled",1,"revision",0,
                        "created_at",db.now(),"updated_at",db.now()));
            } else {
                var source = db.lock("exam_source",sourceId); own(actor,source);
                if (expectedRevision == null || number(source,"revision") != expectedRevision) throw ExamException.conflict();
                id = sourceId;
                version = (int) db.count("select coalesce(max(version_no),0)+1 from exam_source_version where source_id=?",id);
            }
            long versionId = db.insert("exam_source_version",values("source_id",id,"version_no",version,"file_id",fileId,
                    "status","NEEDS_REVIEW","content_hash",ExamJson.hash(bytes),"warnings_json",ExamJson.MAPPER.valueToTree(parsed.warnings()).toString(),
                    "external_allowed",0,"revision",0,"created_at",db.now()));
            int ordinal = 0;
            for (var fragment : parsed.fragments()) db.insert("exam_source_fragment",values("source_version_id",versionId,
                    "ordinal_no",++ordinal,"content",fragment.text(),"locator_json",fragment.locator().toString(),"usable",0));
            db.update("update exam_source set current_version_id=?,title=?,revision=revision+1,updated_at=? where id=?",versionId,title,db.now(),id);
            db.update("update exam_file set purpose='SOURCE' where id=?",fileId);
            return detail(actor,id);
    }
    public ArrayNode list(ExamActor actor) {
        return list(actor,null);
    }
    public ArrayNode list(ExamActor actor,Long beforeId) {
        var result = ExamJson.array();
        db.rows("select * from exam_source where owner_user_id=? and id<? order by id desc limit 200",actor.userId(),ExamJson.cursor(beforeId)).forEach(row -> result.add(view(row)));
        return result;
    }
    public ObjectNode detail(ExamActor actor, long id) {
        var source = db.entity("exam_source",id); own(actor,source); var result = view(source);
        var versions = result.putArray("versions");
        for (var version : db.rows("select v.*,f.original_name from exam_source_version v join exam_file f on v.file_id=f.id where v.source_id=? order by v.version_no desc",id)) {
            var item = ExamJson.object().put("id",Long.toString(number(version,"id"))).put("versionNo",number(version,"version_no"))
                    .put("status",string(version,"status")).put("revision",number(version,"revision"))
                    .put("externalAllowed",number(version,"external_allowed") == 1).put("originalName",string(version,"original_name"));
            item.set("warnings",ExamJson.parse(string(version,"warnings_json"))); versions.add(item);
        }
        return result;
    }
    public ArrayNode fragments(ExamActor actor, long versionId) {
        var version = version(actor,versionId,false); var result = ExamJson.array();
        for (var row : db.rows("select * from exam_source_fragment where source_version_id=? order by ordinal_no",number(version,"id"))) {
            var item = ExamJson.object().put("id",Long.toString(number(row,"id"))).put("sourceVersionId",Long.toString(versionId))
                    .put("text",string(row,"content")).put("usable",number(row,"usable") == 1);
            item.set("locator",ExamJson.parse(string(row,"locator_json"))); result.add(item);
        }
        return result;
    }
    public ObjectNode confirm(ExamActor actor, long versionId, JsonNode request) {
        return db.tx(() -> {
            var current = version(actor,versionId,false); db.lock("exam_source",number(current,"source_id"));
            current = db.lock("exam_source_version",versionId);
            if (number(current,"revision") != ExamJson.revision(request)) throw ExamException.conflict();
            Set<Long> selected = new HashSet<>();
            for (var item : ExamJson.list(request.get("fragmentIds"),1,2000,"可用片段")) {
                long id = ExamJson.id(ExamJson.object().set("id",item),"id"); var fragment = db.entity("exam_source_fragment",id);
                ExamJson.require(number(fragment,"source_version_id") == versionId && selected.add(id),"片段不属于资料或重复");
            }
            ExamJson.require(request.path("externalAllowed").isBoolean(),"请明确资料是否允许发送给所选外部模型");
            // Reconfirmation changes usability/authorization, not immutable text; dependent operations revalidate this state.
            db.update("update exam_source_fragment set usable=0 where source_version_id=?",versionId);
            selected.forEach(id -> db.update("update exam_source_fragment set usable=1 where id=?",id));
            db.update("update exam_source_version set status='READY',external_allowed=?,revision=revision+1 where id=?",
                    request.path("externalAllowed").asBoolean()?1:0,versionId);
            return detail(actor,number(current,"source_id"));
        });
    }
    public ObjectNode toggle(ExamActor actor, long id, JsonNode request) {
        return db.tx(() -> {
            var source = db.lock("exam_source",id); own(actor,source);
            if (number(source,"revision") != ExamJson.revision(request)) throw ExamException.conflict();
            ExamJson.require(request.path("enabled").isBoolean(),"enabled 必须为布尔值");
            db.update("update exam_source set enabled=?,revision=revision+1,updated_at=? where id=?",request.path("enabled").asBoolean()?1:0,db.now(),id);
            return detail(actor,id);
        });
    }
    public Map<String,Object> version(ExamActor actor, long versionId, boolean requireReady) {
        var version = db.entity("exam_source_version",versionId); var source = db.entity("exam_source",number(version,"source_id")); own(actor,source);
        if (requireReady && (number(source,"enabled") != 1 || number(source,"current_version_id") != versionId || !string(version,"status").equals("READY"))) {
            throw new ExamException("EXAM_SOURCE_UNAVAILABLE","资料未确认、已停用或不是当前版本");
        }
        return version;
    }
    public Map<String,Object> evidence(ExamActor actor, long fragmentId, boolean ready) {
        var fragment = db.entity("exam_source_fragment",fragmentId); version(actor,number(fragment,"source_version_id"),ready);
        if (ready && number(fragment,"usable") != 1) throw new ExamException("EXAM_EVIDENCE_INVALID","引用片段未获确认");
        return fragment;
    }
    public ExamFiles.Download download(ExamActor actor,long versionId) {
        var version = version(actor,versionId,false); return files.read(actor,number(version,"file_id"));
    }
    private void own(ExamActor actor,Map<String,Object> row) { ExamAccessPolicy.requireOwner(actor,number(row,"owner_user_id")); }
    private ObjectNode view(Map<String,Object> row) {
        return ExamJson.object().put("id",Long.toString(number(row,"id"))).put("title",string(row,"title"))
                .put("currentVersionId",string(row,"current_version_id")).put("enabled",number(row,"enabled") == 1)
                .put("revision",number(row,"revision")).put("updatedAt",string(row,"updated_at"));
    }
}
