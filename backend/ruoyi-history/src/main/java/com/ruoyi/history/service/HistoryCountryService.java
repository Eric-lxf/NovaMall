package com.ruoyi.history.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.history.domain.HistoryCountry;
import com.ruoyi.history.dto.HistoryCountryPageQuery;
import com.ruoyi.history.dto.HistoryCountrySaveRequest;
import com.ruoyi.history.vo.HistoryCountryVO;

public interface HistoryCountryService
{
    Page<HistoryCountry> page(HistoryCountryPageQuery query);

    List<HistoryCountry> listActive();

    HistoryCountry getById(Long id);

    Long create(HistoryCountrySaveRequest request);

    void update(HistoryCountrySaveRequest request);

    void delete(Long id);

    List<HistoryCountryVO> listPublic();

    HistoryCountryVO getPublicDetail(Long id);
}
