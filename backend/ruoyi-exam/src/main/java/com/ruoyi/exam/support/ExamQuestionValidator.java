package com.ruoyi.exam.support;

import java.util.*;
import java.util.function.Function;
import com.fasterxml.jackson.databind.JsonNode;
import static com.ruoyi.exam.support.ExamJson.*;

/** The model supplies content, never resource ownership, score or approval state. */
public class ExamQuestionValidator {
    public record Evidence(String sourceVersionId,String text) { }
    public void validate(JsonNode q,JsonNode slot,Function<String,Evidence> evidence) {
        String type=text(q,"type",24); Set<String> required=new HashSet<>(Set.of("slotId","type","stem","analysis","knowledgePointIds","sourceRefs"));
        switch(type) {
            case "SINGLE_CHOICE","MULTIPLE_CHOICE" -> required.addAll(Set.of("options","correctOptionIds"));
            case "TRUE_FALSE" -> required.add("answerBoolean");
            case "SHORT_ANSWER" -> required.addAll(Set.of("referenceAnswer","rubric"));
            default -> throw new ExamException("EXAM_OUTPUT_INVALID","未知题型");
        }
        fields(q,required,Set.of()); require(type.equals(slot.path("type").asText()) && text(q,"slotId",64).equals(slot.path("slotId").asText()),"题型/槽位与蓝图不符");
        text(q,"stem",2000); text(q,"analysis",4000);
        Set<String> allowedKnowledge=strings(slot.path("knowledgePointIds")), selectedKnowledge=new HashSet<>();
        for(var id:list(q.get("knowledgePointIds"),1,8,"知识点")) require(id.isTextual() && allowedKnowledge.contains(id.asText()) && selectedKnowledge.add(id.asText()),"题目知识点越界/重复");
        Set<String> allowedFragments=strings(slot.path("sourceFragmentIds")), selectedRefs=new HashSet<>();
        for(var ref:list(q.get("sourceRefs"),1,8,"题目依据")) {
            fields(ref,Set.of("sourceVersionId","fragmentId","quote"),Set.of());
            String id=text(ref,"fragmentId",64), version=text(ref,"sourceVersionId",64); text(ref,"quote",1000); String quote=ref.path("quote").asText();
            require(allowedFragments.contains(id) && selectedRefs.add(id+":"+quote),"题目依据越界/重复");
            Evidence original=evidence.apply(id);
            require(original!=null && original.sourceVersionId().equals(version) && original.text().contains(quote),"题目引文与原文不符");
        }
        if(type.equals("SINGLE_CHOICE") || type.equals("MULTIPLE_CHOICE")) {
            var options=list(q.get("options"),2,6,"选项"); Set<String> ids=new HashSet<>(), texts=new HashSet<>(); int i=0;
            for(var option:options) {
                fields(option,Set.of("id","text"),Set.of()); String id=text(option,"id",1), content=text(option,"text",1000);
                require(id.equals(Character.toString('A'+i++)) && ids.add(id) && texts.add(content.replaceAll("\\s+"," ")),"选项编号须连续且内容不重复");
            }
            Set<String> answers=new HashSet<>();
            for(var answer:list(q.get("correctOptionIds"),type.equals("SINGLE_CHOICE")?1:2,type.equals("SINGLE_CHOICE")?1:options.size(),"正确选项"))
                require(answer.isTextual() && ids.contains(answer.asText()) && answers.add(answer.asText()),"正确选项不存在或重复");
        } else if(type.equals("TRUE_FALSE")) require(q.path("answerBoolean").isBoolean(),"判断题答案须为布尔值");
        else {
            text(q,"referenceAnswer",4000); int sum=0; Set<String> points=new HashSet<>();
            for(var point:list(q.get("rubric"),1,10,"评分点")) {
                fields(point,Set.of("point","weight"),Set.of()); require(points.add(text(point,"point",1000)),"评分点重复");
                require(point.path("weight").isIntegralNumber() && point.path("weight").asInt()>=1 && point.path("weight").asInt()<=100,"评分点权重无效"); sum+=point.path("weight").asInt();
            }
            require(sum==100,"评分点权重之和须为 100");
        }
    }
    private Set<String> strings(JsonNode node) { Set<String> result=new HashSet<>(); node.forEach(value->result.add(value.asText())); return result; }
}
