package com.ruoyi.history.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.history.domain.HistoryPerson;
import com.ruoyi.history.dto.HistoryPersonPageQuery;
import com.ruoyi.history.dto.HistoryPersonSaveRequest;

public interface HistoryPersonService
{
    Page<HistoryPerson> page(HistoryPersonPageQuery query);

    HistoryPerson getById(Long id);

    Long create(HistoryPersonSaveRequest request);

    void update(HistoryPersonSaveRequest request);

    void delete(Long id);
}
