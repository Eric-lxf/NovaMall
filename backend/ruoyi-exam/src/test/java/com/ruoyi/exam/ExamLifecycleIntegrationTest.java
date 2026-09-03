package com.ruoyi.exam;

import com.ruoyi.exam.support.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExamLifecycleIntegrationTest extends ExamWorkflowIntegrationTest {
    @Test void mergingKnowledgeCreatesCandidateAndPreservesArchivedOriginals() {
        var secondRequest=point.deepCopy().put("name","设备检查"); var second=knowledge.save(owner,null,secondRequest);
        var merge=point.deepCopy().put("name","合并知识点");
        merge.putArray("mergeFrom").add(revision(point).put("id",point.path("id").asText())).add(revision(second).put("id",second.path("id").asText()));
        assertThrows(ExamException.class,()->knowledge.merge(stranger,merge));
        var result=knowledge.merge(owner,merge); assertFalse(result.path("confirmed").asBoolean());
        assertEquals(3,knowledge.list(owner,null).size()); assertThrows(ExamException.class,()->knowledge.confirmed(owner,id(point)));
        assertThrows(ExamException.class,()->knowledge.merge(owner,merge));
    }
    @Test void disabledQuestionCannotBeUsedForNewPaperOrDownload() {
        var q=approve(create(0)); var p=paper(q); questions.toggle(owner,id(q),revision(q).put("enabled",false));
        assertThrows(ExamException.class,()->questions.approved(owner,q.path("currentVersionId").asLong()));
        assertThrows(ExamException.class,()->papers.requireExportable(owner,p.path("currentVersionId").asLong()));
        assertTrue(papers.snapshot(owner,p.path("currentVersionId").asLong(),true).has("items"));
    }
    @Test void questionListOnlyCarriesLatestVersionAndCursorIsStrictlyOlder() {
        var q=create(0); questions.save(owner,id(q),revision(q).put("blueprintId",blueprint.path("id").asText()).set("content",content(0)));
        var second=create(1); var listed=questions.list(owner,false); assertEquals(2,listed.size());
        assertEquals(1,listed.get(1).path("versions").size()); assertFalse(listed.get(1).path("versions").get(0).has("evidence"));
        var older=questions.list(owner,false,id(second)); assertEquals(1,older.size()); assertEquals(id(q),id(older.get(0)));
        assertEquals(2,questions.detail(owner,id(q)).path("versions").size());
    }
    @Test void textSplittingDoesNotCorruptSupplementaryCharacters() {
        String source="中".repeat(1499)+"😀"+"尾";
        var parts=new com.ruoyi.exam.parser.ExamTextParser().parse(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(source,parts.fragments().stream().map(com.ruoyi.exam.parser.ExamTextParser.Fragment::text).collect(java.util.stream.Collectors.joining()));
        assertFalse(Character.isHighSurrogate(parts.fragments().get(0).text().charAt(1498)));
    }
}
