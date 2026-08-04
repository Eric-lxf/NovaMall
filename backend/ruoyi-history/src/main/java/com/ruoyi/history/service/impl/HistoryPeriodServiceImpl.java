package com.ruoyi.history.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.history.constant.HistoryConstants;
import com.ruoyi.history.domain.HistoryPeriod;
import com.ruoyi.history.dto.HistoryPeriodPageQuery;
import com.ruoyi.history.dto.HistoryPeriodSaveRequest;
import com.ruoyi.history.mapper.HistoryPeriodMapper;
import com.ruoyi.history.service.HistoryPeriodService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HistoryPeriodServiceImpl implements HistoryPeriodService
{
    private final HistoryPeriodMapper historyPeriodMapper;

    @Override
    public Page<HistoryPeriod> page(HistoryPeriodPageQuery query)
    {
        int pageNum = normalizePageNum(query.getPageNum());
        int pageSize = normalizePageSize(query.getPageSize());
        LambdaQueryWrapper<HistoryPeriod> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getName()))
        {
            wrapper.like(HistoryPeriod::getName, query.getName().trim());
        }
        if (query.getCountryId() != null)
        {
            wrapper.eq(HistoryPeriod::getCountryId, query.getCountryId());
        }
        if (StringUtils.hasText(query.getStatus()))
        {
            wrapper.eq(HistoryPeriod::getStatus, query.getStatus().trim());
        }
        wrapper.orderByAsc(HistoryPeriod::getSort).orderByAsc(HistoryPeriod::getStartYear).orderByAsc(HistoryPeriod::getId);
        return historyPeriodMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public List<HistoryPeriod> listActive()
    {
        return historyPeriodMapper.selectList(new LambdaQueryWrapper<HistoryPeriod>()
                .eq(HistoryPeriod::getStatus, HistoryConstants.STATUS_NORMAL)
                .orderByAsc(HistoryPeriod::getSort)
                .orderByAsc(HistoryPeriod::getStartYear));
    }

    @Override
    public HistoryPeriod getById(Long id)
    {
        return requirePeriod(id);
    }

    @Override
    public Long create(HistoryPeriodSaveRequest request)
    {
        HistoryPeriod period = new HistoryPeriod();
        copyRequest(request, period);
        period.setCreateBy(SecurityUtils.getUsername());
        historyPeriodMapper.insert(period);
        return period.getId();
    }

    @Override
    public void update(HistoryPeriodSaveRequest request)
    {
        if (request.getId() == null)
        {
            throw new ServiceException("时期ID不能为空", HttpStatus.BAD_REQUEST);
        }
        requirePeriod(request.getId());
        HistoryPeriod period = new HistoryPeriod();
        period.setId(request.getId());
        copyRequest(request, period);
        period.setUpdateBy(SecurityUtils.getUsername());
        historyPeriodMapper.updateById(period);
    }

    @Override
    public void delete(Long id)
    {
        requirePeriod(id);
        historyPeriodMapper.deleteById(id);
    }

    private HistoryPeriod requirePeriod(Long id)
    {
        HistoryPeriod period = historyPeriodMapper.selectById(id);
        if (period == null)
        {
            throw new ServiceException("历史时期不存在", HttpStatus.NOT_FOUND);
        }
        return period;
    }

    private void copyRequest(HistoryPeriodSaveRequest request, HistoryPeriod period)
    {
        period.setName(request.getName());
        period.setAlias(request.getAlias());
        period.setCountryId(request.getCountryId());
        period.setStartYear(request.getStartYear());
        period.setEndYear(request.getEndYear());
        period.setDatePrecision(StringUtils.hasText(request.getDatePrecision())
                ? request.getDatePrecision() : HistoryConstants.DATE_PRECISION_YEAR);
        period.setOriginalDateText(request.getOriginalDateText());
        period.setCalendarType(request.getCalendarType());
        period.setIsApproximate(Boolean.TRUE.equals(request.getIsApproximate()));
        period.setSummary(request.getSummary());
        period.setSort(request.getSort() == null ? 0 : request.getSort());
        period.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : HistoryConstants.STATUS_NORMAL);
        period.setRemark(request.getRemark());
    }

    private static int normalizePageNum(Integer pageNum)
    {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private static int normalizePageSize(Integer pageSize)
    {
        return pageSize == null ? 10 : Math.min(pageSize, 100);
    }
}
