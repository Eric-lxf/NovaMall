package com.ruoyi.history.service;

import com.ruoyi.history.domain.HistoryLearningRecord;
import com.ruoyi.history.dto.HistoryProgressSaveRequest;

public interface HistoryProgressService
{
    HistoryLearningRecord saveProgress(HistoryProgressSaveRequest request);
}
