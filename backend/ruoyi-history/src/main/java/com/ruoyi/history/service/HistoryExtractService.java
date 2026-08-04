package com.ruoyi.history.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.history.dto.HistoryClaimAuditRequest;
import com.ruoyi.history.dto.HistoryClaimPageQuery;
import com.ruoyi.history.dto.HistoryExtractRequest;
import com.ruoyi.history.dto.HistorySourceImportResult;
import com.ruoyi.history.vo.HistoryClaimVO;

public interface HistoryExtractService
{
    HistorySourceImportResult submitExtract(HistoryExtractRequest request);

    void processExtract(Long taskId);

    HistorySourceImportResult retryExtract(Long taskId);

    Page<HistoryClaimVO> pageClaims(HistoryClaimPageQuery query);

    void auditClaims(HistoryClaimAuditRequest request);
}
