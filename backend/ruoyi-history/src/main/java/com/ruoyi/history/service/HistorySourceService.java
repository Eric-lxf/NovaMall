package com.ruoyi.history.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.history.domain.HistoryAiTask;
import com.ruoyi.history.domain.HistorySourceDocument;
import com.ruoyi.history.domain.HistorySourceFragment;
import com.ruoyi.history.dto.HistoryAiTaskPageQuery;
import com.ruoyi.history.dto.HistorySourceImportRequest;
import com.ruoyi.history.dto.HistorySourceImportResult;
import com.ruoyi.history.dto.HistorySourcePageQuery;
import com.ruoyi.history.vo.HistorySourceDocumentVO;

public interface HistorySourceService
{
    Page<HistorySourceDocument> pageDocuments(HistorySourcePageQuery query);

    HistorySourceDocumentVO getDocumentDetail(Long id, boolean includeFragments);

    HistorySourceImportResult importSource(HistorySourceImportRequest request);

    Page<HistoryAiTask> pageTasks(HistoryAiTaskPageQuery query);

    HistoryAiTask getTask(Long taskId);

    /** 失败任务重试：清空旧片段并重新排队解析 */
    HistorySourceImportResult retryTask(Long taskId);

    List<HistorySourceFragment> listFragments(Long documentId);

    HistorySourceFragment getFragment(Long fragmentId);
}
