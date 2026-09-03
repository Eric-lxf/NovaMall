package com.ruoyi.exam;

import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.exam.config.ExamProperties;
import com.ruoyi.exam.document.*;
import com.ruoyi.exam.service.*;
import com.ruoyi.exam.support.*;
import com.ruoyi.exam.task.ExamTaskWorker;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExamDocumentIntegrationTest extends ExamWorkflowIntegrationTest {
    @Test void xlsxRoundTripContainsAllFourTypesAndLiteralFormulaLikeText() throws Exception {
        var request=ExamJson.object().put("title","安全培训测试卷（四题型验收样例）");
        var items=request.putObject("draft").put("durationMinutes",30).put("totalScore",100).putArray("items");
        for(int i=0;i<4;i++) {
            var q=approve(create(i)); items.add(ExamJson.object().put("questionVersionId",q.path("currentVersionId").asText()).put("score",25));
        }
        var paper=papers.save(owner,null,request); paper=papers.finalizePaper(owner,id(paper),revision(paper));
        long version=paper.path("currentVersionId").asLong(); var snapshot=papers.snapshot(owner,version,false);
        byte[] bytes=new ExamXlsxWriter().write(snapshot);
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet=workbook.getSheetAt(0); assertEquals(4,sheet.getLastRowNum());
            assertEquals("题干",sheet.getRow(0).getCell(3).getStringCellValue());
            for(int i=0;i<4;i++) { assertEquals(25,sheet.getRow(i+1).getCell(2).getNumericCellValue()); assertFalse(sheet.getRow(i+1).getCell(6).getStringCellValue().isBlank()); }
            assertTrue(sheet.getPaneInformation().isFreezePane());
        }
        String output=System.getProperty("examSampleOutput");
        if(output!=null) { var path=Path.of(output); Files.createDirectories(path); Files.write(path.resolve("question-bank.xlsx"),bytes);
            Files.writeString(path.resolve("paper-snapshot.json"),snapshot.toPrettyString()); }
        ((com.fasterxml.jackson.databind.node.ObjectNode)snapshot.path("items").get(0).path("content")).put("stem","=HYPERLINK(\"https://example.invalid\",\"test\")");
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(new ExamXlsxWriter().write(snapshot)))) {
            assertEquals(CellType.STRING,workbook.getSheetAt(0).getRow(1).getCell(3).getCellType());
            assertTrue(workbook.getSheetAt(0).getRow(1).getCell(3).getStringCellValue().startsWith("="));
        }
    }
    @Test void exportIsPrivateRechecksSourceAndRejectsStudentXlsx() {
        var p=paper(approve(create(0))); var docs=documents(input->{throw new AssertionError("XLSX does not use document parser");});
        var request=ExamJson.object().put("paperVersionId",p.path("currentVersionId").asText()).put("format","XLSX").put("audience","STUDENT");
        assertThrows(ExamException.class,()->docs.export(owner,UUID.randomUUID().toString(),request)); request.put("audience","TEACHER");
        var task=docs.export(owner,UUID.randomUUID().toString(),request);
        try(var worker=new ExamTaskWorker(test.repository,user->true,new ExamReadiness(test.repository),java.time.Clock.systemUTC(),List.of(docs))) { worker.executeOne(task); }
        long exportId=docs.exports(owner).get(0).path("id").asLong(); assertTrue(docs.download(owner,exportId).bytes().length>1000);
        assertThrows(ExamException.class,()->docs.download(stranger,exportId));
        sources.toggle(owner,id(source),revision(source).put("enabled",false)); assertThrows(ExamException.class,()->docs.download(owner,exportId));
    }
    @Test void studentPayloadSentToRendererNeverContainsAnswerData() {
        var p=paper(approve(create(0))); var seen=new ArrayList<JsonNode>();
        var docs=documents(input->{ seen.add(input); return ExamJson.object().put("data",Base64.getEncoder().encodeToString("test-renderer-only".getBytes())); });
        var task=docs.export(owner,UUID.randomUUID().toString(),ExamJson.object().put("paperVersionId",p.path("currentVersionId").asText()).put("format","DOCX").put("audience","STUDENT"));
        try(var worker=new ExamTaskWorker(test.repository,user->true,new ExamReadiness(test.repository),java.time.Clock.systemUTC(),List.of(docs))) { worker.executeOne(task); }
        assertEquals(1,seen.size()); assertFalse(seen.get(0).toString().contains("analysis")); assertFalse(seen.get(0).toString().contains("correctOptionIds"));
    }
    @Test void exportCommittedBeforeFinalCancellationCannotBeDownloaded() {
        var p=paper(approve(create(0))); var docs=documents(input->{throw new AssertionError();});
        var task=docs.export(owner,UUID.randomUUID().toString(),ExamJson.object().put("paperVersionId",p.path("currentVersionId").asText()).put("format","XLSX").put("audience","TEACHER"));
        assertTrue(test.repository.claim(task,"race-test",db.now()));
        assertEquals("SUCCEEDED",docs.execute(task,1,"race-test").status());
        long exportId=docs.exports(owner).get(0).path("id").asLong();
        assertThrows(ExamException.class,()->docs.download(owner,exportId));
        db.update("update exam_task set status='CANCELLED' where id=?",task.id());
        assertThrows(ExamException.class,()->docs.download(owner,exportId));
    }
    @Test void repeatedDocumentSubmissionKeepsOnePrivateFile() {
        var docs=documents(input->{throw new AssertionError();}); long before=db.count("select count(*) from exam_file");
        String key=UUID.randomUUID().toString(); byte[] bytes="fake parser fixture, never executed".getBytes();
        var first=docs.parse(owner,key,"文档","test.docx",bytes,null,null);
        var second=docs.parse(owner,key,"文档","test.docx",bytes,null,null);
        assertEquals(first.id(),second.id()); assertEquals(before+1,db.count("select count(*) from exam_file"));
    }
    @Test void outerTransactionRollbackRemovesOnlyNewBlob() throws Exception {
        var props=new ExamProperties(); props.setPrivateRoot(temporary.resolve("private").toString());
        var files=new ExamFiles(db,props,List.of(temporary.resolve("public")));
        long before=db.count("select count(*) from exam_file");
        long filesBefore; try(var paths=Files.walk(temporary.resolve("private"))) { filesBefore=paths.filter(Files::isRegularFile).count(); }
        assertThrows(IllegalStateException.class,()->db.tx(()->{
            files.create(owner,"test".getBytes(),"rollback.txt","text/plain","TEST",id->id);
            throw new IllegalStateException("rollback outer transaction");
        }));
        assertEquals(before,db.count("select count(*) from exam_file"));
        try(var paths=Files.walk(temporary.resolve("private"))) { assertEquals(filesBefore,paths.filter(Files::isRegularFile).count()); }
    }
    @Test void noDocumentWorkerMeansNoParseTaskAndNoFileWrite() {
        var props=new ExamProperties(); var runner=new DockerExamDocumentRunner(props); assertFalse(runner.configured());
        assertThrows(ExamException.class,()->runner.run(ExamJson.object()));
        props.setDocumentImage("evil --privileged"); assertFalse(runner.configured());
        props.setDocumentImage("novamall/exam-document:1"); assertTrue(runner.configured());
    }
    private ExamDocumentJobs documents(java.util.function.Function<JsonNode,JsonNode> handler) {
        var props=new ExamProperties(); props.setPrivateRoot(temporary.resolve("private").toString());
        var files=new ExamFiles(db,props,List.of(temporary.resolve("public")));
        var runner=new ExamDocumentRunner() { public boolean configured() { return true; } public JsonNode run(JsonNode input) { return handler.apply(input); } };
        return new ExamDocumentJobs(db,new ExamJobs(db,test.repository),runner,user->true,sources,files,papers);
    }
}
