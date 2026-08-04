package com.ruoyi.history.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.history.domain.HistoryLearningUnit;
import com.ruoyi.history.dto.HistoryUnitGenerateRequest;
import com.ruoyi.history.dto.HistoryUnitPageQuery;
import com.ruoyi.history.dto.HistoryUnitSaveRequest;
import com.ruoyi.history.vo.HistoryUnitVO;

public interface HistoryUnitService
{
    Page<HistoryLearningUnit> page(HistoryUnitPageQuery query);

    HistoryUnitVO getDetail(Long id);

    List<HistoryLearningUnit> listPublishedOptions();

    Long create(HistoryUnitSaveRequest request);

    void update(HistoryUnitSaveRequest request);

    void delete(Long id);

    void publish(Long id);

    /** 从已发布事件生成学习单元草稿 */
    Long generateFromEvent(HistoryUnitGenerateRequest request);

    HistoryUnitVO getPublishedDetail(Long id);
}
