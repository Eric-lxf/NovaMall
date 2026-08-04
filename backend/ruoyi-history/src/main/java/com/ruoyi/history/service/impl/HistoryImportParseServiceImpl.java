package com.ruoyi.history.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.history.constant.HistoryConstants;
import com.ruoyi.history.domain.HistoryAiTask;
import com.ruoyi.history.domain.HistorySourceDocument;
import com.ruoyi.history.domain.HistorySourceFragment;
import com.ruoyi.history.mapper.HistoryAiTaskMapper;
import com.ruoyi.history.mapper.HistorySourceDocumentMapper;
import com.ruoyi.history.mapper.HistorySourceFragmentMapper;
import com.ruoyi.history.service.HistoryImportParseService;
import com.ruoyi.history.support.HistoryPdfTextExtractor;
import com.ruoyi.history.support.HistorySourceContentLoader;
import com.ruoyi.history.support.HistoryTextSlicer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryImportParseServiceImpl implements HistoryImportParseService
{
    private final HistoryAiTaskMapper historyAiTaskMapper;
    private final HistorySourceDocumentMapper historySourceDocumentMapper;
    private final HistorySourceFragmentMapper historySourceFragmentMapper;
    private final HistorySourceContentLoader historySourceContentLoader;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processImportParse(Long taskId)
    {
        HistoryAiTask task = historyAiTaskMapper.selectById(taskId);
        if (task == null)
        {
            log.warn("import parse skip, task not found: {}", taskId);
            return;
        }
        if (!HistoryConstants.TASK_TYPE_IMPORT_PARSE.equals(task.getTaskType()))
        {
            log.warn("import parse skip, unexpected type={} taskId={}", task.getTaskType(), taskId);
            return;
        }
        String status = task.getStatus();
        if (!HistoryConstants.TASK_QUEUED.equals(status)
                && !HistoryConstants.TASK_RETRYING.equals(status)
                && !HistoryConstants.TASK_FAILED.equals(status))
        {
            return;
        }

        markProcessing(task);
        HistorySourceDocument document = historySourceDocumentMapper.selectById(task.getDocumentId());
        if (document == null)
        {
            markFailed(task, null, "关联资料文档不存在");
            return;
        }

        try
        {
            historySourceFragmentMapper.delete(new LambdaQueryWrapper<HistorySourceFragment>()
                    .eq(HistorySourceFragment::getDocumentId, document.getId()));

            List<HistoryTextSlicer.Slice> slices;
            String fullText;
            if (historySourceContentLoader.isPdf(document))
            {
                byte[] pdfBytes = historySourceContentLoader.loadBytes(document);
                List<HistoryPdfTextExtractor.PageText> pages = HistoryPdfTextExtractor.extractPages(pdfBytes);
                slices = new ArrayList<>();
                StringBuilder all = new StringBuilder();
                int globalSeq = 1;
                for (HistoryPdfTextExtractor.PageText page : pages)
                {
                    if (StringUtils.hasText(page.text()))
                    {
                        if (all.length() > 0)
                        {
                            all.append("\n\n");
                        }
                        all.append(page.text().trim());
                    }
                    List<HistoryTextSlicer.Slice> pageSlices = HistoryTextSlicer.slice(page.text(), page.pageNo());
                    for (HistoryTextSlicer.Slice slice : pageSlices)
                    {
                        if (slices.size() >= HistoryConstants.FRAGMENT_MAX_COUNT)
                        {
                            break;
                        }
                        slices.add(new HistoryTextSlicer.Slice(
                                globalSeq++,
                                slice.pageNo(),
                                slice.locator(),
                                slice.content()));
                    }
                    if (slices.size() >= HistoryConstants.FRAGMENT_MAX_COUNT)
                    {
                        break;
                    }
                }
                fullText = all.toString();
                if (slices.isEmpty() && !StringUtils.hasText(fullText) && StringUtils.hasText(document.getContentText()))
                {
                    // PDF 无可抽取文字时，允许退回粘贴正文
                    fullText = document.getContentText();
                    slices = HistoryTextSlicer.slice(fullText);
                }
            }
            else
            {
                fullText = historySourceContentLoader.loadText(document);
                slices = HistoryTextSlicer.slice(fullText);
            }

            if (slices.isEmpty())
            {
                markFailed(task, document, "未能从资料中解析出有效文本片段");
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            for (HistoryTextSlicer.Slice slice : slices)
            {
                HistorySourceFragment fragment = new HistorySourceFragment();
                fragment.setDocumentId(document.getId());
                fragment.setSeqNo(slice.seqNo());
                fragment.setPageNo(slice.pageNo());
                fragment.setLocator(slice.locator());
                fragment.setContent(slice.content());
                fragment.setCreateTime(now);
                historySourceFragmentMapper.insert(fragment);
            }

            HistorySourceDocument docUpdate = new HistorySourceDocument();
            docUpdate.setId(document.getId());
            docUpdate.setContentText(truncateForStore(fullText));
            docUpdate.setParseStatus(HistoryConstants.TASK_SUCCEEDED);
            docUpdate.setUpdateTime(now);
            historySourceDocumentMapper.updateById(docUpdate);

            Map<String, Object> output = new HashMap<>();
            output.put("fragmentCount", slices.size());
            output.put("charCount", fullText == null ? 0 : fullText.length());
            output.put("fileType", document.getFileType());

            HistoryAiTask taskUpdate = new HistoryAiTask();
            taskUpdate.setId(task.getId());
            taskUpdate.setStatus(HistoryConstants.TASK_SUCCEEDED);
            taskUpdate.setOutputPayload(JSON.toJSONString(output));
            taskUpdate.setErrorMessage(null);
            taskUpdate.setFinishedAt(now);
            taskUpdate.setUpdateTime(now);
            historyAiTaskMapper.updateById(taskUpdate);
        }
        catch (Exception ex)
        {
            log.error("import parse failed taskId={} documentId={}", taskId, task.getDocumentId(), ex);
            markFailed(task, document, abbreviate(ex.getMessage(), 1800));
        }
    }

    private void markProcessing(HistoryAiTask task)
    {
        LocalDateTime now = LocalDateTime.now();
        HistoryAiTask update = new HistoryAiTask();
        update.setId(task.getId());
        update.setStatus(HistoryConstants.TASK_PROCESSING);
        update.setStartedAt(now);
        update.setErrorMessage(null);
        update.setUpdateTime(now);
        historyAiTaskMapper.updateById(update);

        if (task.getDocumentId() != null)
        {
            HistorySourceDocument doc = new HistorySourceDocument();
            doc.setId(task.getDocumentId());
            doc.setParseStatus(HistoryConstants.TASK_PROCESSING);
            doc.setUpdateTime(now);
            historySourceDocumentMapper.updateById(doc);
        }
    }

    private void markFailed(HistoryAiTask task, HistorySourceDocument document, String message)
    {
        LocalDateTime now = LocalDateTime.now();
        HistoryAiTask update = new HistoryAiTask();
        update.setId(task.getId());
        update.setStatus(HistoryConstants.TASK_FAILED);
        update.setErrorMessage(message);
        update.setFinishedAt(now);
        update.setUpdateTime(now);
        historyAiTaskMapper.updateById(update);

        Long documentId = document != null ? document.getId() : task.getDocumentId();
        if (documentId != null)
        {
            HistorySourceDocument doc = new HistorySourceDocument();
            doc.setId(documentId);
            doc.setParseStatus(HistoryConstants.TASK_FAILED);
            doc.setUpdateTime(now);
            historySourceDocumentMapper.updateById(doc);
        }
    }

    private static String truncateForStore(String text)
    {
        if (text == null)
        {
            return null;
        }
        // 避免超大 PDF 全文撑爆单行更新；完整证据以 fragment 为准
        int max = 200_000;
        if (text.length() <= max)
        {
            return text;
        }
        return text.substring(0, max);
    }

    private static String abbreviate(String message, int max)
    {
        if (message == null)
        {
            return "解析失败";
        }
        return message.length() <= max ? message : message.substring(0, max);
    }
}
