package com.ruoyi.history.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.history.domain.HistoryPlace;
import com.ruoyi.history.dto.HistoryPlacePageQuery;
import com.ruoyi.history.dto.HistoryPlaceSaveRequest;

import java.util.List;

public interface HistoryPlaceService
{
    Page<HistoryPlace> page(HistoryPlacePageQuery query);

    List<HistoryPlace> listActive();

    HistoryPlace getById(Long id);

    Long create(HistoryPlaceSaveRequest request);

    void update(HistoryPlaceSaveRequest request);

    void delete(Long id);
}
