package com.ruoyi.history.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.history.constant.HistoryConstants;
import com.ruoyi.history.domain.HistoryAiTask;
import com.ruoyi.history.domain.HistorySourceDocument;
import com.ruoyi.history.domain.HistorySourceFragment;
import com.ruoyi.history.dto.HistoryAiTaskPageQuery;
import com.ruoyi.history.dto.HistorySourceImportRequest;
import com.ruoyi.history.dto.HistorySourceImportResult;
import com.ruoyi.history.dto.HistorySourcePageQuery;
import com.ruoyi.history.mapper.HistoryAiTaskMapper;
import com.ruoyi.history.mapper.HistorySourceDocumentMapper;
import com.ruoyi.history.mapper.HistorySourceFragmentMapper;
import com.ruoyi.history.service.HistorySourceService;
import com.ruoyi.history.task.HistoryImportParseWorker;
import com.ruoyi.history.vo.HistorySourceDocumentVO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HistorySourceServiceImpl implements HistorySourceService
{
    private final HistorySourceDocumentMapper historySourceDocumentMapper;
    private final HistorySourceFragmentMapper historySourceFragmentMapper;
    private final HistoryAiTaskMapper historyAiTaskMapper;
    private final HistoryImportParseWorker historyImportParseWorker;

    @Override
    public Page<HistorySourceDocument> pageDocuments(HistorySourcePageQuery query)
    {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : Math.min(query.getPageSize(), 100);
        LambdaQueryWrapper<HistorySourceDocument> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getTitle()))
        {
            wrapper.like(HistorySourceDocument::getTitle, query.getTitle().trim());
        }
        if (StringUtils.hasText(query.getParseStatus()))
        {
            wrapper.eq(HistorySourceDocument::getParseStatus, query.getParseStatus().trim());
        }
        wrapper.orderByDesc(HistorySourceDocument::getId);
        return historySourceDocumentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public HistorySourceDocumentVO getDocumentDetail(Long id, boolean includeFragments)
    {
        HistorySourceDocument document = requireDocument(id);
        HistorySourceDocumentVO vo = new HistorySourceDocumentVO();
        BeanUtils.copyProperties(document, vo);
        Long count = historySourceFragmentMapper.selectCount(new LambdaQueryWrapper<HistorySourceFragment>()
                .eq(HistorySourceFragment::getDocumentId, id));
        vo.setFragmentCount(count == null ? 0 : count.intValue());
        if (includeFragments)
        {
            vo.setFragments(listFragments(id));
        }
        return vo;
    }

    /**
     * 仅创建文档与异步任务记录并返回 taskId，不在请求线程中解析 PDF / 调用模型。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public HistorySourceImportResult importSource(HistorySourceImportRequest request)
    {
        String fileType = request.getFileType().trim().toUpperCase();
        validateImportRequest(fileType, request);

        String username = SecurityUtils.getUsername();
        HistorySourceDocument document = new HistorySourceDocument();
        document.setTitle(request.getTitle());
        document.setFileType(fileType);
        document.setFileUrl(request.getFileUrl());
        document.setFileName(request.getFileName());
        document.setFileSize(request.getFileSize());
        document.setContentText(request.getContentText());
        document.setSourceDesc(request.getSourceDesc());
        document.setParseStatus(HistoryConstants.TASK_QUEUED);
        document.setRemark(request.getRemark());
        document.setCreateBy(username);
        historySourceDocumentMapper.insert(document);

        HistoryAiTask task = new HistoryAiTask();
        task.setTaskType(HistoryConstants.TASK_TYPE_IMPORT_PARSE);
        task.setDocumentId(document.getId());
        task.setStatus(HistoryConstants.TASK_QUEUED);
        task.setInputPayload(JSON.toJSONString(request));
        task.setRetryCount(0);
        task.setCreateBy(username);
        historyAiTaskMapper.insert(task);

        enqueueAfterCommit(task.getId());

        HistorySourceImportResult result = new HistorySourceImportResult();
        result.setDocumentId(document.getId());
        result.setTaskId(task.getId());
        result.setStatus(task.getStatus());
        return result;
    }

    @Override
    public Page<HistoryAiTask> pageTasks(HistoryAiTaskPageQuery query)
    {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : Math.min(query.getPageSize(), 100);
        LambdaQueryWrapper<HistoryAiTask> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getTaskType()))
        {
            wrapper.eq(HistoryAiTask::getTaskType, query.getTaskType().trim());
        }
        if (StringUtils.hasText(query.getStatus()))
        {
            wrapper.eq(HistoryAiTask::getStatus, query.getStatus().trim());
        }
        if (query.getDocumentId() != null)
        {
            wrapper.eq(HistoryAiTask::getDocumentId, query.getDocumentId());
        }
        wrapper.orderByDesc(HistoryAiTask::getId);
        return historyAiTaskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public HistoryAiTask getTask(Long taskId)
    {
        HistoryAiTask task = historyAiTaskMapper.selectById(taskId);
        if (task == null)
        {
            throw new ServiceException("AI 任务不存在", HttpStatus.NOT_FOUND);
        }
        return task;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HistorySourceImportResult retryTask(Long taskId)
    {
        HistoryAiTask task = getTask(taskId);
        if (!HistoryConstants.TASK_TYPE_IMPORT_PARSE.equals(task.getTaskType()))
        {
            throw new ServiceException("仅支持重试资料解析任务", HttpStatus.BAD_REQUEST);
        }
        if (!HistoryConstants.TASK_FAILED.equals(task.getStatus())
                && !HistoryConstants.TASK_RETRYING.equals(task.getStatus()))
        {
            throw new ServiceException("仅失败状态的任务可重试", HttpStatus.BAD_REQUEST);
        }
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        if (retryCount >= HistoryConstants.TASK_MAX_RETRY)
        {
            throw new ServiceException("已超过最大重试次数（" + HistoryConstants.TASK_MAX_RETRY + "）", HttpStatus.BAD_REQUEST);
        }

        requireDocument(task.getDocumentId());
        historySourceFragmentMapper.delete(new LambdaQueryWrapper<HistorySourceFragment>()
                .eq(HistorySourceFragment::getDocumentId, task.getDocumentId()));

        LocalDateTime now = LocalDateTime.now();
        HistoryAiTask taskUpdate = new HistoryAiTask();
        taskUpdate.setId(task.getId());
        taskUpdate.setStatus(HistoryConstants.TASK_RETRYING);
        taskUpdate.setRetryCount(retryCount + 1);
        taskUpdate.setErrorMessage(null);
        taskUpdate.setOutputPayload(null);
        taskUpdate.setStartedAt(null);
        taskUpdate.setFinishedAt(null);
        taskUpdate.setUpdateBy(SecurityUtils.getUsername());
        taskUpdate.setUpdateTime(now);
        historyAiTaskMapper.updateById(taskUpdate);

        HistorySourceDocument docUpdate = new HistorySourceDocument();
        docUpdate.setId(task.getDocumentId());
        docUpdate.setParseStatus(HistoryConstants.TASK_RETRYING);
        docUpdate.setUpdateBy(SecurityUtils.getUsername());
        docUpdate.setUpdateTime(now);
        historySourceDocumentMapper.updateById(docUpdate);

        enqueueAfterCommit(task.getId());

        HistorySourceImportResult result = new HistorySourceImportResult();
        result.setDocumentId(task.getDocumentId());
        result.setTaskId(task.getId());
        result.setStatus(HistoryConstants.TASK_RETRYING);
        return result;
    }

    @Override
    public List<HistorySourceFragment> listFragments(Long documentId)
    {
        requireDocument(documentId);
        return historySourceFragmentMapper.selectList(new LambdaQueryWrapper<HistorySourceFragment>()
                .eq(HistorySourceFragment::getDocumentId, documentId)
                .orderByAsc(HistorySourceFragment::getSeqNo)
                .orderByAsc(HistorySourceFragment::getId));
    }

    @Override
    public HistorySourceFragment getFragment(Long fragmentId)
    {
        HistorySourceFragment fragment = historySourceFragmentMapper.selectById(fragmentId);
        if (fragment == null)
        {
            throw new ServiceException("资料片段不存在", HttpStatus.NOT_FOUND);
        }
        return fragment;
    }

    private void validateImportRequest(String fileType, HistorySourceImportRequest request)
    {
        if (!HistoryConstants.FILE_TYPE_TEXT.equals(fileType)
                && !HistoryConstants.FILE_TYPE_MARKDOWN.equals(fileType)
                && !HistoryConstants.FILE_TYPE_PDF.equals(fileType))
        {
            throw new ServiceException("不支持的文件类型，仅支持 TEXT/MARKDOWN/PDF", HttpStatus.BAD_REQUEST);
        }
        if (HistoryConstants.FILE_TYPE_PDF.equals(fileType))
        {
            if (!StringUtils.hasText(request.getFileUrl()) && !StringUtils.hasText(request.getContentText()))
            {
                throw new ServiceException("PDF 资料需提供 fileUrl，或粘贴可解析的备用正文", HttpStatus.BAD_REQUEST);
            }
            return;
        }
        if (!StringUtils.hasText(request.getContentText()) && !StringUtils.hasText(request.getFileUrl()))
        {
            throw new ServiceException("文本类资料需提供 contentText 或 fileUrl", HttpStatus.BAD_REQUEST);
        }
    }

    private HistorySourceDocument requireDocument(Long id)
    {
        HistorySourceDocument document = historySourceDocumentMapper.selectById(id);
        if (document == null)
        {
            throw new ServiceException("资料文档不存在", HttpStatus.NOT_FOUND);
        }
        return document;
    }

    private void enqueueAfterCommit(Long taskId)
    {
        if (TransactionSynchronizationManager.isSynchronizationActive())
        {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override
                public void afterCommit()
                {
                    historyImportParseWorker.enqueue(taskId);
                }
            });
        }
        else
        {
            historyImportParseWorker.enqueue(taskId);
        }
    }
}
