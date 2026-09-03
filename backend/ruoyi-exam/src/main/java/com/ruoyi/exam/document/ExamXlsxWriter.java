package com.ruoyi.exam.document;

import java.io.ByteArrayOutputStream;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.exam.support.ExamException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Write-only trusted snapshot export. Never loads an uploaded Office file or evaluates formulas. */
public class ExamXlsxWriter {
    private static final String[] HEADERS={"题序","题型","分值","题干","选项","参考答案","答案解析","评分点","资料依据","题目版本"};
    private static final int[] WIDTHS={9,23,10,55,55,48,60,48,64,22};
    public byte[] write(JsonNode snapshot) {
        try(var workbook=new XSSFWorkbook();var output=new ByteArrayOutputStream()) {
            var sheet=workbook.createSheet("题库"); sheet.createFreezePane(4,1); sheet.setDisplayGridlines(false);
            var font=workbook.createFont(); font.setFontName("微软雅黑"); font.setFontHeightInPoints((short)11);
            var headerFont=workbook.createFont(); headerFont.setFontName("微软雅黑"); headerFont.setBold(true); headerFont.setColor(IndexedColors.WHITE.getIndex());
            var headerStyle=workbook.createCellStyle(); headerStyle.setFont(headerFont); headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex()); headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var bodyStyle=workbook.createCellStyle(); bodyStyle.setFont(font); bodyStyle.setWrapText(true); bodyStyle.setVerticalAlignment(VerticalAlignment.TOP);
            var numericStyle=workbook.createCellStyle(); numericStyle.cloneStyleFrom(bodyStyle); numericStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
            var ordinalStyle=workbook.createCellStyle(); ordinalStyle.cloneStyleFrom(bodyStyle); ordinalStyle.setDataFormat(workbook.createDataFormat().getFormat("0"));
            var header=sheet.createRow(0); header.setHeightInPoints(28);
            for(int i=0;i<HEADERS.length;i++) { var cell=header.createCell(i,CellType.STRING); cell.setCellValue(HEADERS[i]); cell.setCellStyle(headerStyle); sheet.setColumnWidth(i,WIDTHS[i]*256); }
            int index=1;
            for(var item:snapshot.path("items")) {
                var q=item.path("content"); var row=sheet.createRow(index++);
                StringBuilder options=new StringBuilder(),rubric=new StringBuilder(),refs=new StringBuilder();
                q.path("options").forEach(option->options.append(option.path("id").asText()).append(". ").append(option.path("text").asText()).append('\n'));
                q.path("rubric").forEach(point->rubric.append(point.path("weight").asInt()).append("% ").append(point.path("point").asText()).append('\n'));
                q.path("sourceRefs").forEach(ref->refs.append("资料版本 ").append(ref.path("sourceVersionId").asText()).append(" / 片段 ").append(ref.path("fragmentId").asText()).append(": ").append(ref.path("quote").asText()).append('\n'));
                var choiceAnswers=new java.util.ArrayList<String>(); q.path("correctOptionIds").forEach(id->choiceAnswers.add(id.asText()));
                String answer=q.has("correctOptionIds")?String.join("、",choiceAnswers):q.has("answerBoolean")?(q.path("answerBoolean").asBoolean()?"正确":"错误"):q.path("referenceAnswer").asText();
                String type=switch(q.path("type").asText()) { case "SINGLE_CHOICE"->"单选题"; case "MULTIPLE_CHOICE"->"多选题"; case "TRUE_FALSE"->"判断题"; case "SHORT_ANSWER"->"简答题"; default->q.path("type").asText(); };
                String[] values={"",type,"",q.path("stem").asText(),options.toString(),answer,q.path("analysis").asText(),rubric.toString(),refs.toString(),item.path("questionVersionId").asText()};
                for(int i=0;i<values.length;i++) {
                    var cell=row.createCell(i,CellType.STRING); cell.setCellValue(values[i]); cell.setCellStyle(bodyStyle);
                    // setCellValue(String) creates a string cell even when content starts =, +, -, @; never setCellFormula.
                }
                row.getCell(0).setCellValue(item.path("ordinal").asInt()); row.getCell(0).setCellStyle(ordinalStyle);
                row.getCell(2).setCellValue(item.path("score").asDouble()); row.getCell(2).setCellStyle(numericStyle);
                int lines=1; for(int i=3;i<9;i++) { int count=0; for(String line:values[i].split("\n",-1)) count+=Math.max(1,(line.length()*2+WIDTHS[i]-1)/WIDTHS[i]); lines=Math.max(lines,count); }
                row.setHeightInPoints(Math.min(409,Math.max(44,lines*16+8)));
            }
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,index-1,0,HEADERS.length-1));
            sheet.getPrintSetup().setLandscape(true); sheet.setFitToPage(true); sheet.getPrintSetup().setFitWidth((short)1); sheet.getPrintSetup().setFitHeight((short)0);
            sheet.setRepeatingRows(new org.apache.poi.ss.util.CellRangeAddress(0,0,-1,-1));
            workbook.getProperties().getCoreProperties().setCreator("NovaMall"); workbook.getProperties().getCoreProperties().setTitle(snapshot.path("title").asText());
            workbook.write(output); return output.toByteArray();
        } catch(Exception failure) { throw new ExamException("EXAM_EXPORT_FAILED","Excel 导出失败"); }
    }
}
