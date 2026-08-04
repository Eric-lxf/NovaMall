package com.ruoyi.history.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.history.domain.HistoryEvent;
import com.ruoyi.history.dto.HistoryEventPageQuery;
import com.ruoyi.history.dto.HistoryEventSaveRequest;
import com.ruoyi.history.dto.HistoryTimelineQuery;
import com.ruoyi.history.vo.HistoryTimelineItemVO;

public interface HistoryEventService
{
    Page<HistoryEvent> page(HistoryEventPageQuery query);

    HistoryEvent getById(Long id);

    Long create(HistoryEventSaveRequest request);

    void update(HistoryEventSaveRequest request);

    void delete(Long id);

    void publish(Long id);

    List<HistoryTimelineItemVO> timeline(HistoryTimelineQuery query);
}
