package com.ruoyi.history.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HistorySourceImportRequest
{
    @NotBlank(message = "资料标题不能为空")
    @Size(max = 255, message = "资料标题长度不能超过255")
    private String title;

    /** TEXT / MARKDOWN / PDF */
    @NotBlank(message = "文件类型不能为空")
    private String fileType;

    private String fileUrl;
    private String fileName;
    private Long fileSize;

    /** 纯文本或 Markdown 正文；PDF 阶段可后解析 */
    private String contentText;

    @Size(max = 1000, message = "来源说明长度不能超过1000")
    private String sourceDesc;

    private String remark;
}
