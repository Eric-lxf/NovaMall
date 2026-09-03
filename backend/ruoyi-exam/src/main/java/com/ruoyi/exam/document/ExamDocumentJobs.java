package com.ruoyi.exam.document;

import java.util.*;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.exam.domain.ExamTask;
import com.ruoyi.exam.parser.ExamTextParser;
import com.ruoyi.exam.repository.*;
import com.ruoyi.exam.security.*;
import com.ruoyi.exam.service.*;
import com.ruoyi.exam.support.*;
import com.ruoyi.exam.task.ExamJobHandler;
import static com.ruoyi.exam.repository.ExamDataStore.*;

public class ExamDocumentJobs implements ExamJobHandler {
    public static final String PARSE="SOURCE_PARSE", EXPORT="PAPER_EXPORT";
    private final ExamDataStore db; private final ExamJobs jobs; private final ExamDocumentRunner runner; private final ExamExecutionAuthorizer authorizer;
    private final ExamSources sources; private final ExamFiles files; private final ExamPapers papers;
    public ExamDocumentJobs(ExamDataStore db,ExamJobs jobs,ExamDocumentRunner runner,ExamExecutionAuthorizer authorizer,ExamSources sources,ExamFiles files,ExamPapers papers) {
        this.db=db; this.jobs=jobs; this.runner=runner; this.authorizer=authorizer; this.sources=sources; this.files=files; this.papers=papers;
    }
    public boolean configured() { return runner.configured(); }
    public void validateRetry(ExamActor actor,String kind,JsonNode input) {
        ExamJson.require(supports(kind),"不支持的文档任务");
        requirePermission(actor.userId(),PARSE.equals(kind)?"exam:source:edit":"exam:paper:export");
        if(!"XLSX".equals(input.path("format").asText()) && !runner.configured()) throw new ExamException("EXAM_DOCUMENT_WORKER_NOT_READY","隔离文档工作进程未配置");
        if(PARSE.equals(kind)) {
            files.read(actor,ExamJson.id(input,"fileId"));
            if(input.has("sourceId")) {
                var source=sources.detail(actor,ExamJson.id(input,"sourceId"));
                ExamJson.require(source.path("revision").asLong()==input.path("expectedRevision").asLong(-1),"原资料已变更，请重新上传新版本");
            }
        } else {
            if("TEACHER".equals(input.path("audience").asText())) requirePermission(actor.userId(),"exam:paper:answers");
            papers.requireExportable(actor,ExamJson.id(input,"paperVersionId"));
        }
    }
    public ExamTask retry(ExamActor actor,String kind,JsonNode original,String key,JsonNode request) {
        validateRetry(actor,kind,original);
        var input=((ObjectNode)original).deepCopy().put("retryOfTaskId",request.path("retryOfTaskId").asText());
        var task=jobs.submit(actor,kind,PARSE.equals(kind)?"重试解析："+input.path("title").asText():"重试试卷导出："+input.path("format").asText(),key,request,input,List.of(PARSE.equals(kind)?"parse":"export"));
        if(EXPORT.equals(kind)) db.insert("exam_export",values("owner_user_id",actor.userId(),"paper_version_id",ExamJson.id(input,"paperVersionId"),"format",input.path("format").asText(),
                "audience",input.path("audience").asText(),"task_id",task.id(),"status","QUEUED","created_at",db.now()));
        return task;
    }
    public ExamTask parse(ExamActor actor,String key,String title,String filename,byte[] bytes,Long sourceId,Long revision) {
        ExamJson.require(title!=null && !title.isBlank() && title.length()<=160,"资料标题无效");
        ExamJson.require(filename!=null && filename.matches("(?i).+\\.(docx|pdf)$"),"只接收 DOCX 或文本 PDF");
        ExamJson.require(bytes.length>0 && bytes.length<=10*1024*1024,"文件大小超出 10 MB");
        if(!runner.configured()) throw new ExamException("EXAM_DOCUMENT_WORKER_NOT_READY","隔离文档工作进程未配置");
        String format=filename.toLowerCase(Locale.ROOT).endsWith(".docx")?"DOCX":"PDF";
        var request=ExamJson.object().put("title",title).put("filename",filename).put("hash",ExamJson.hash(bytes)).put("format",format);
        if(sourceId!=null) { var source=sources.detail(actor,sourceId); ExamJson.require(revision!=null && source.path("revision").asLong()==revision,"资料版本已变化"); request.put("sourceId",Long.toString(sourceId)).put("expectedRevision",revision); }
        var old=jobs.existing(actor,PARSE,key,request); if(old!=null) return old;
        return files.withQuotaLock(()->{
            // Recheck inside the shared file lock so concurrent idempotent uploads never leave duplicate blobs.
            var repeated=jobs.existing(actor,PARSE,key,request); if(repeated!=null) return repeated;
            return files.create(actor,bytes,filename,mime(format),"PENDING_SOURCE",fileId->{
                var input=request.deepCopy().put("fileId",Long.toString(fileId)); return jobs.submit(actor,PARSE,"解析："+title,key,request,input,List.of("parse"));
            });
        });
    }
    public ExamTask export(ExamActor actor,String key,JsonNode request) {
        var old=jobs.existing(actor,EXPORT,key,request); if(old!=null) return old;
        long versionId=ExamJson.id(request,"paperVersionId"); String format=ExamJson.text(request,"format",12),audience=ExamJson.text(request,"audience",12);
        ExamJson.require(Set.of("DOCX","PDF","XLSX").contains(format) && Set.of("STUDENT","TEACHER").contains(audience),"导出格式/版本无效");
        ExamJson.require(!format.equals("XLSX") || audience.equals("TEACHER"),"题库 XLSX 含答案，仅能导出教师版");
        if(!format.equals("XLSX") && !runner.configured()) throw new ExamException("EXAM_DOCUMENT_WORKER_NOT_READY","隔离导出进程未配置");
        papers.requireExportable(actor,versionId);
        return db.tx(()->{
            var task=jobs.submit(actor,EXPORT,"试卷导出："+format+" / "+audience,key,request,request,List.of("export"));
            if(db.count("select count(*) from exam_export where task_id=?",task.id())==0) db.insert("exam_export",values("owner_user_id",actor.userId(),"paper_version_id",versionId,"format",format,"audience",audience,"task_id",task.id(),"status","QUEUED","created_at",db.now()));
            return task;
        });
    }
    public com.fasterxml.jackson.databind.node.ArrayNode exports(ExamActor actor) {
        return exports(actor,null);
    }
    public com.fasterxml.jackson.databind.node.ArrayNode exports(ExamActor actor,Long beforeId) {
        var result=ExamJson.array();
        for(var row:db.rows("select e.*,t.status task_status,t.error_code from exam_export e join exam_task t on e.task_id=t.id where e.owner_user_id=? and e.id<? order by e.id desc limit 200",actor.userId(),ExamJson.cursor(beforeId))) {
            result.add(ExamJson.object().put("id",Long.toString(number(row,"id"))).put("taskId",Long.toString(number(row,"task_id"))).put("paperVersionId",Long.toString(number(row,"paper_version_id")))
                    .put("format",string(row,"format")).put("audience",string(row,"audience")).put("status",string(row,"task_status")).put("errorCode",string(row,"error_code")));
        } return result;
    }
    public ExamFiles.Download download(ExamActor actor,long id) {
        var row=db.entity("exam_export",id); ExamAccessPolicy.requireOwner(actor,number(row,"owner_user_id"));
        requirePermission(actor.userId(),"exam:paper:export"); if(string(row,"audience").equals("TEACHER")) requirePermission(actor.userId(),"exam:paper:answers");
        ExamJson.require(string(row,"status").equals("SUCCEEDED") && row.get("file_id")!=null,"导出文件尚未就绪");
        ExamJson.require("SUCCEEDED".equals(string(db.entity("exam_task",number(row,"task_id")),"status")),"导出任务尚未成功完成或已经取消");
        papers.requireExportable(actor,number(row,"paper_version_id")); return files.read(actor,number(row,"file_id"));
    }
    public boolean supports(String kind) { return PARSE.equals(kind) || EXPORT.equals(kind); }
    private void requirePermission(long owner,String permission) {
        if(!authorizer.mayExecute(owner,permission)) throw new ExamException("EXAM_PERMISSION_REVOKED","任务操作权限已撤销");
    }
    public Outcome execute(ExamTask task,int attempt,String worker) {
        var actor=new ExamActor(task.ownerUserId(),false); var lease=jobs.lease(task,attempt,worker); var input=jobs.input(task.id());
        try {
            lease.check(); requirePermission(actor.userId(),PARSE.equals(task.kind())?"exam:source:edit":"exam:paper:export");
            if(PARSE.equals(task.kind())) {
                var file=files.read(actor,ExamJson.id(input,"fileId"));
                var parsed=runner.run(ExamJson.object().put("mode","PARSE").put("format",input.path("format").asText()).put("data",Base64.getEncoder().encodeToString(file.bytes())));
                var fragments=new ArrayList<ExamTextParser.Fragment>(); int length=0;
                for(var part:ExamJson.list(parsed.get("fragments"),1,2000,"解析片段")) {
                    String text=ExamJson.text(part,"text",1500); length+=text.length(); ExamJson.require(length<=200000 && part.path("locator").isObject(),"解析输出无效");
                    fragments.add(new ExamTextParser.Fragment(text,(ObjectNode)part.path("locator")));
                }
                var warnings=new ArrayList<String>(); for(var warning:ExamJson.list(parsed.get("warnings"),0,100,"解析告警")) { ExamJson.require(warning.isTextual() && warning.asText().length()<=1000,"告警无效"); warnings.add(warning.asText()); }
                lease.commit(()->{
                    requirePermission(actor.userId(),"exam:source:edit");
                    var saved=sources.importStored(actor,input.path("title").asText(),file.bytes(),input.has("sourceId")?ExamJson.id(input,"sourceId"):null,
                            input.has("expectedRevision")?input.path("expectedRevision").asLong():null,new ExamTextParser.Result(fragments,warnings),ExamJson.id(input,"fileId"));
                    db.update("update exam_job set result_json=? where task_id=?",ExamJson.object().put("sourceId",saved.path("id").asText()).toString(),task.id());
                    db.update("update exam_task_item set status='SUCCEEDED' where task_id=?",task.id()); return null;
                });
                return new Outcome("SUCCEEDED",null,"资料已解析并保存为待确认版本，请对照原文核对片段");
            }
            long versionId=ExamJson.id(input,"paperVersionId"); String format=input.path("format").asText(),audience=input.path("audience").asText();
            if(audience.equals("TEACHER")) requirePermission(actor.userId(),"exam:paper:answers");
            papers.requireExportable(actor,versionId); var snapshot=papers.snapshot(actor,versionId,audience.equals("STUDENT"));
            byte[] bytes;
            if(format.equals("XLSX")) bytes=new ExamXlsxWriter().write(snapshot);
            else {
                var request=ExamJson.object().put("mode","EXPORT").put("format",format).put("audience",audience); request.set("snapshot",snapshot);
                var result=runner.run(request); bytes=Base64.getDecoder().decode(result.path("data").asText());
            }
            String name=snapshot.path("title").asText().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_")+"-"+(audience.equals("STUDENT")?"学生卷":"教师卷")+"."+format.toLowerCase(Locale.ROOT);
            lease.commit(()->{
                requirePermission(actor.userId(),"exam:paper:export"); if(audience.equals("TEACHER")) requirePermission(actor.userId(),"exam:paper:answers");
                return files.withQuotaLock(()->{
                    papers.lockSources(versionId); papers.requireExportable(actor,versionId);
                    return files.create(actor,bytes,name,mime(format),"EXPORT",fileId->{
                        db.update("update exam_export set status='SUCCEEDED',file_id=? where task_id=?",fileId,task.id());
                        db.update("update exam_task_item set status='SUCCEEDED' where task_id=?",task.id()); return fileId;
                    });
                });
            }); return new Outcome("SUCCEEDED",null,"导出文件已保存在私有目录，请从导出中心下载");
        } catch(ExamException failure) { return new Outcome("FAILED",failure.getErrorCode(),"文档任务失败；请检查输入和工作进程配置"); }
    }
    private String mime(String format) { return switch(format) {
        case "DOCX" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        case "XLSX" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        default -> "application/pdf";
    }; }
}
