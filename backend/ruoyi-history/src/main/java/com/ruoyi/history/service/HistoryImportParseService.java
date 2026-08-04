package com.ruoyi.history.service;

/**
 * 资料导入解析：在异步线程中执行，写入片段并更新任务状态。
 */
public interface HistoryImportParseService
{
    void processImportParse(Long taskId);
}
