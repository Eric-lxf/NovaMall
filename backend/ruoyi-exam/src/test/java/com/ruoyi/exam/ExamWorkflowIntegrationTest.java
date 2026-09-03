package com.ruoyi.exam;

import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.exam.config.ExamProperties;
import com.ruoyi.exam.repository.ExamDataStore;
import com.ruoyi.exam.security.ExamActor;
import com.ruoyi.exam.service.*;
import com.ruoyi.exam.support.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ExamWorkflowIntegrationTest {
    @TempDir Path temporary;
    ExamTestDatabase test; ExamDataStore db; ExamSources sources; ExamKnowledge knowledge;
    ExamBlueprints blueprints; ExamQuestions questions; ExamPapers papers;
    ExamActor owner=new ExamActor(7,false), stranger=new ExamActor(8,false), reviewer=new ExamActor(9,false,true);
    ObjectNode source,point,blueprint; String fragmentId,sourceVersionId;

    @BeforeEach void setup() throws Exception {
        test=new ExamTestDatabase();
        test.jdbc.execute(Files.readString(Path.of(System.getProperty("examWorkflowSchema"))).replaceAll("(?s)ENGINE=InnoDB.*?;",";"));
        db=new ExamDataStore(test.dataSource,Clock.systemUTC()); db.verifySchema();
        var props=new ExamProperties(); props.setPrivateRoot(temporary.resolve("private").toString());
        sources=new ExamSources(db,new ExamFiles(db,props,List.of(temporary.resolve("public"))));
        knowledge=new ExamKnowledge(db,sources); blueprints=new ExamBlueprints(db,sources,knowledge);
        questions=new ExamQuestions(db,sources,blueprints); papers=new ExamPapers(db,questions);
        source=sources.importText(owner,ExamJson.object().put("title","安全培训").put("text","作业前必须检查防护设备。发现故障应停止作业并报告主管。"),null);
        sourceVersionId=source.path("currentVersionId").asText(); fragmentId=sources.fragments(owner,Long.parseLong(sourceVersionId)).get(0).path("id").asText();
        var confirm=ExamJson.object().put("expectedRevision",0).put("externalAllowed",false); confirm.putArray("fragmentIds").add(fragmentId);
        sources.confirm(owner,Long.parseLong(sourceVersionId),confirm);
        var request=ExamJson.object().put("sourceVersionId",sourceVersionId).put("name","作业安全");
        request.putArray("sourceRefs").add(ExamJson.object().put("fragmentId",fragmentId).put("quote","作业前必须检查防护设备。"));
        point=knowledge.save(owner,null,request); point=knowledge.confirm(owner,id(point),revision(point));
        var plan=ExamJson.object().put("title","安全测试"); var settings=plan.putObject("settings").put("durationMinutes",30).put("totalScore",100);
        settings.putArray("sourceVersionIds").add(sourceVersionId);
        var slots=plan.putArray("slots"); int i=0;
        for(String type:List.of("SINGLE_CHOICE","MULTIPLE_CHOICE","TRUE_FALSE","SHORT_ANSWER")) {
            var slot=ExamJson.object().put("slotId","q"+(++i)).put("type",type).put("targetDifficulty","EASY").put("score",25);
            slot.putArray("knowledgePointIds").add(point.path("id").asText()); slot.putArray("sourceFragmentIds").add(fragmentId); slots.add(slot);
        }
        blueprint=blueprints.save(owner,null,plan);
        blueprint=blueprints.confirm(owner,id(blueprint),revision(blueprint).put("contentHash",blueprint.path("contentHash").asText()));
    }
    long id(JsonNode node) { return node.path("id").asLong(); }
    ObjectNode revision(JsonNode node) { return ExamJson.object().put("expectedRevision",node.path("revision").asLong()); }
    ObjectNode content(int index) {
        var slot=blueprint.path("slots").get(index); String type=slot.path("type").asText();
        var value=ExamJson.object().put("slotId",slot.path("slotId").asText()).put("type",type).put("stem","作业安全要求是什么？"+index).put("analysis","根据培训资料，作业前须检查防护设备，故障时停止作业并报告主管。");
        value.set("knowledgePointIds",slot.path("knowledgePointIds").deepCopy());
        value.putArray("sourceRefs").add(ExamJson.object().put("sourceVersionId",sourceVersionId).put("fragmentId",fragmentId).put("quote","作业前必须检查防护设备。"));
        if(index<2) {
            value.putArray("options").add(ExamJson.object().put("id","A").put("text","检查防护设备")).add(ExamJson.object().put("id","B").put("text","发现故障停止作业"));
            var correct=value.putArray("correctOptionIds").add("A"); if(index==1) correct.add("B");
        } else if(index==2) value.put("answerBoolean",true);
        else { value.put("referenceAnswer","检查设备，故障停工并报告。"); value.putArray("rubric").add(ExamJson.object().put("point","检查设备").put("weight",50)).add(ExamJson.object().put("point","停工并报告").put("weight",50)); }
        return value;
    }
    ObjectNode create(int index) { return questions.save(owner,null,ExamJson.object().put("blueprintId",blueprint.path("id").asText()).set("content",content(index))); }
    ObjectNode reviewRequest(ObjectNode question) {
        return revision(question).put("contentHash",question.path("versions").get(0).path("contentHash").asText());
    }
    ObjectNode approve(ObjectNode q) {
        q=questions.submit(owner,q.path("currentVersionId").asLong(),reviewRequest(q));
        return questions.review(reviewer,q.path("currentVersionId").asLong(),reviewRequest(q).put("decision","APPROVED").put("reason","逐项核对原文和答案").put("manualVerification",true));
    }
    ObjectNode paper(ObjectNode q) {
        var request=ExamJson.object().put("title","定版测试卷");
        request.putObject("draft").put("durationMinutes",30).put("totalScore",25).putArray("items")
                .add(ExamJson.object().put("questionVersionId",q.path("currentVersionId").asText()).put("score",25));
        var paper=papers.save(owner,null,request); return papers.finalizePaper(owner,id(paper),revision(paper));
    }
    @Test void allFourTypesPassManualReviewAndStudentSnapshotsNeverContainAnswers() {
        for(int i=0;i<4;i++) {
            var q=approve(create(i)); var paper=paper(q); long version=paper.path("currentVersionId").asLong();
            String student=papers.snapshot(owner,version,true).toString(); String teacher=papers.snapshot(owner,version,false).toString();
            for(String secret:List.of("correctOptionIds","answerBoolean","referenceAnswer","rubric","analysis","sourceRefs","knowledgePointIds","questionVersionId")) assertFalse(student.contains(secret),secret);
            assertTrue(teacher.contains("analysis")); assertEquals("FINALIZED",paper.path("status").asText());
        }
    }
    @Test void strangerCannotReadOrModifyPrivateResources() {
        assertThrows(ExamException.class,()->sources.detail(stranger,id(source)));
        assertThrows(ExamException.class,()->sources.download(stranger,Long.parseLong(sourceVersionId)));
        assertThrows(ExamException.class,()->sources.fragments(stranger,Long.parseLong(sourceVersionId)));
        assertThrows(ExamException.class,()->blueprints.detail(stranger,id(blueprint)));
        var q=approve(create(0)); assertThrows(ExamException.class,()->questions.detail(stranger,id(q)));
        var p=paper(q); assertThrows(ExamException.class,()->papers.snapshot(stranger,p.path("currentVersionId").asLong(),true));
        assertThrows(ExamException.class,()->sources.detail(reviewer,id(source))); // Review permission is not raw-file permission.
    }
    @Test void editingCreatesNewVersionAndCannotChangeFrozenPaper() {
        var q=approve(create(0)); var p=paper(q); long version=p.path("currentVersionId").asLong();
        String before=papers.snapshot(owner,version,false).toString();
        var changed=content(0).put("stem","新版本题干");
        q=questions.save(owner,id(q),revision(q).put("blueprintId",blueprint.path("id").asText()).set("content",changed));
        assertEquals(2,q.path("versions").size()); assertEquals("DRAFT",q.path("versions").get(0).path("reviewState").asText());
        assertEquals(before,papers.snapshot(owner,version,false).toString());
    }
    @Test void staleReviewCannotApproveEditedVersion() {
        var q=create(0); q=questions.submit(owner,q.path("currentVersionId").asLong(),reviewRequest(q));
        var request=reviewRequest(q).put("decision","APPROVED").put("reason","旧页面").put("manualVerification",true); long old=q.path("currentVersionId").asLong();
        questions.save(owner,id(q),revision(q).put("blueprintId",blueprint.path("id").asText()).set("content",content(0).put("stem","新题干")));
        assertThrows(ExamException.class,()->questions.review(reviewer,old,request));
        assertEquals(0,db.count("select count(*) from exam_question_review"));
    }
    @Test void sourceRevisionBlocksExportButDoesNotRewriteHistoricalSnapshot() {
        var p=paper(approve(create(0))); long version=p.path("currentVersionId").asLong(); String snapshot=papers.snapshot(owner,version,false).toString();
        sources.importText(owner,revision(source).put("title","新安全培训").put("text","新规定：不得使用旧规定。"),id(source));
        assertThrows(ExamException.class,()->papers.requireExportable(owner,version));
        assertEquals(snapshot,papers.snapshot(owner,version,false).toString());
    }
    @Test void withdrawnEvidenceBlocksReviewAndAssembly() {
        var q=create(0); q=questions.submit(owner,q.path("currentVersionId").asLong(),reviewRequest(q));
        sources.toggle(owner,id(source),revision(source).put("enabled",false));
        long version=q.path("currentVersionId").asLong(); var request=reviewRequest(q).put("decision","APPROVED").put("reason","检查").put("manualVerification",true);
        assertThrows(ExamException.class,()->questions.review(reviewer,version,request));
    }
    @Test void approvalCannotSilentlySkipIndependentVerification() {
        var q=create(0); q=questions.submit(owner,q.path("currentVersionId").asLong(),reviewRequest(q));
        long version=q.path("currentVersionId").asLong(); var request=reviewRequest(q).put("decision","APPROVED").put("reason","检查");
        assertThrows(ExamException.class,()->questions.review(reviewer,version,request));
        questions.addCheck(version,"AI_VERIFY",ExamJson.object().put("passed",false),false);
        assertThrows(ExamException.class,()->questions.review(reviewer,version,request.put("manualVerification",true)));
    }
    @Test void invalidModelOutputAndDuplicateJsonAreRejected() {
        assertThrows(ExamException.class,()->ExamJson.parse("{\"a\":1,\"a\":2}"));
        assertThrows(ExamException.class,()->ExamJson.parse("{}{}"));
        var q=content(0).put("reviewState","APPROVED"); assertThrows(ExamException.class,()->questions.validate(owner,id(blueprint),q));
        q.remove("reviewState"); ((ObjectNode)q.path("sourceRefs").get(0)).put("quote","不存在的引文");
        assertThrows(ExamException.class,()->questions.validate(owner,id(blueprint),q));
    }
    @Test void wrongCountsAndMissingEvidenceFailClosed() {
        var settings=blueprint.path("settings").deepCopy(); ((ObjectNode)settings).put("totalScore",99);
        assertThrows(ExamException.class,()->blueprints.validate(owner,settings,blueprint.path("slots")));
        var q=approve(create(0)); long version=q.path("currentVersionId").asLong();
        db.update("delete from exam_question_source where question_version_id=?",version);
        assertThrows(ExamException.class,()->questions.requireEvidenceAvailable(version));
    }
}
