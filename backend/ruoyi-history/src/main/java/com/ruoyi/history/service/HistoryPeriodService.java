package com.ruoyi.history.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.history.domain.HistoryPeriod;
import com.ruoyi.history.dto.HistoryPeriodPageQuery;
import com.ruoyi.history.dto.HistoryPeriodSaveRequest;

import java.util.List;

public interface HistoryPeriodService
{
    Page<HistoryPeriod> page(HistoryPeriodPageQuery query);

    List<HistoryPeriod> listActive();

    HistoryPeriod getById(Long id);

    Long create(HistoryPeriodSaveRequest request);

    void update(HistoryPeriodSaveRequest request);

    void delete(Long id);
}
