package com.ruoyi.history.task;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.ruoyi.history.service.HistoryExtractService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class HistoryExtractWorker
{
    private final HistoryExtractService historyExtractService;

    @Async("historyTaskExecutor")
    public void enqueue(Long taskId)
    {
        try
        {
            historyExtractService.processExtract(taskId);
        }
        catch (Exception ex)
        {
            log.error("history extract worker failed taskId={}", taskId, ex);
        }
    }
}
