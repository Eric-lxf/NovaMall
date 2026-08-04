package com.ruoyi.history.service.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.history.constant.HistoryConstants;
import com.ruoyi.history.domain.HistoryLearningRecord;
import com.ruoyi.history.domain.HistoryLearningUnit;
import com.ruoyi.history.dto.HistoryProgressSaveRequest;
import com.ruoyi.history.mapper.HistoryLearningRecordMapper;
import com.ruoyi.history.mapper.HistoryLearningUnitMapper;
import com.ruoyi.history.service.HistoryProgressService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HistoryProgressServiceImpl implements HistoryProgressService
{
    private final HistoryLearningRecordMapper historyLearningRecordMapper;
    private final HistoryLearningUnitMapper historyLearningUnitMapper;

    @Override
    public HistoryLearningRecord saveProgress(HistoryProgressSaveRequest request)
    {
        Long userId = SecurityUtils.getUserId();
        HistoryLearningUnit unit = historyLearningUnitMapper.selectById(request.getUnitId());
        if (unit == null || !HistoryConstants.STATUS_NORMAL.equals(unit.getStatus())
                || !HistoryConstants.AUDIT_PUBLISHED.equals(unit.getAuditStatus()))
        {
            throw new ServiceException("学习单元不存在或未发布", HttpStatus.BAD_REQUEST);
        }

        int progress = request.getProgress() == null ? 0 : request.getProgress();
        boolean completed = Boolean.TRUE.equals(request.getCompleted()) || progress >= 100;
        if (completed)
        {
            progress = 100;
        }

        HistoryLearningRecord existing = historyLearningRecordMapper.selectOne(
                new LambdaQueryWrapper<HistoryLearningRecord>()
                        .eq(HistoryLearningRecord::getUserId, userId)
                        .eq(HistoryLearningRecord::getUnitId, request.getUnitId())
                        .last("limit 1"));

        LocalDateTime now = LocalDateTime.now();
        if (existing == null)
        {
            HistoryLearningRecord record = new HistoryLearningRecord();
            record.setUserId(userId);
            record.setPathId(request.getPathId());
            record.setUnitId(request.getUnitId());
            record.setProgress(progress);
            record.setCompleted(completed);
            record.setLastStudyTime(now);
            historyLearningRecordMapper.insert(record);
            return record;
        }

        HistoryLearningRecord update = new HistoryLearningRecord();
        update.setId(existing.getId());
        if (request.getPathId() != null)
        {
            update.setPathId(request.getPathId());
        }
        update.setProgress(Math.max(existing.getProgress() == null ? 0 : existing.getProgress(), progress));
        update.setCompleted(completed || Boolean.TRUE.equals(existing.getCompleted()));
        if (Boolean.TRUE.equals(update.getCompleted()))
        {
            update.setProgress(100);
        }
        update.setLastStudyTime(now);
        historyLearningRecordMapper.updateById(update);
        return historyLearningRecordMapper.selectById(existing.getId());
    }
}
