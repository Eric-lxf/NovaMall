package com.ruoyi.history.task;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.ruoyi.history.service.HistoryImportParseService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class HistoryImportParseWorker
{
    private final HistoryImportParseService historyImportParseService;

    @Async("historyTaskExecutor")
    public void enqueue(Long taskId)
    {
        try
        {
            historyImportParseService.processImportParse(taskId);
        }
        catch (Exception ex)
        {
            log.error("history import parse worker failed taskId={}", taskId, ex);
        }
    }
}
