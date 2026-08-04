package com.ruoyi.history.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.history.constant.HistoryConstants;
import com.ruoyi.history.domain.HistoryCountry;
import com.ruoyi.history.domain.HistoryEvent;
import com.ruoyi.history.domain.HistoryPeriod;
import com.ruoyi.history.dto.HistoryCountryPageQuery;
import com.ruoyi.history.dto.HistoryCountrySaveRequest;
import com.ruoyi.history.mapper.HistoryCountryMapper;
import com.ruoyi.history.mapper.HistoryEventMapper;
import com.ruoyi.history.mapper.HistoryPeriodMapper;
import com.ruoyi.history.service.HistoryCountryService;
import com.ruoyi.history.vo.HistoryCountryPeriodVO;
import com.ruoyi.history.vo.HistoryCountryVO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HistoryCountryServiceImpl implements HistoryCountryService
{
    private final HistoryCountryMapper historyCountryMapper;
    private final HistoryPeriodMapper historyPeriodMapper;
    private final HistoryEventMapper historyEventMapper;

    @Override
    public Page<HistoryCountry> page(HistoryCountryPageQuery query)
    {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : Math.min(query.getPageSize(), 100);
        LambdaQueryWrapper<HistoryCountry> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getName()))
        {
            wrapper.like(HistoryCountry::getName, query.getName().trim());
        }
        if (StringUtils.hasText(query.getRegion()))
        {
            wrapper.eq(HistoryCountry::getRegion, query.getRegion().trim());
        }
        if (StringUtils.hasText(query.getStatus()))
        {
            wrapper.eq(HistoryCountry::getStatus, query.getStatus().trim());
        }
        wrapper.orderByAsc(HistoryCountry::getSort).orderByAsc(HistoryCountry::getId);
        return historyCountryMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public List<HistoryCountry> listActive()
    {
        return historyCountryMapper.selectList(new LambdaQueryWrapper<HistoryCountry>()
                .eq(HistoryCountry::getStatus, HistoryConstants.STATUS_NORMAL)
                .orderByAsc(HistoryCountry::getSort)
                .orderByAsc(HistoryCountry::getId));
    }

    @Override
    public HistoryCountry getById(Long id)
    {
        return requireCountry(id);
    }

    @Override
    public Long create(HistoryCountrySaveRequest request)
    {
        ensureNameUnique(request.getName(), null);
        HistoryCountry country = new HistoryCountry();
        copyRequest(request, country);
        country.setCreateBy(SecurityUtils.getUsername());
        historyCountryMapper.insert(country);
        return country.getId();
    }

    @Override
    public void update(HistoryCountrySaveRequest request)
    {
        if (request.getId() == null)
        {
            throw new ServiceException("国家ID不能为空", HttpStatus.BAD_REQUEST);
        }
        requireCountry(request.getId());
        ensureNameUnique(request.getName(), request.getId());
        HistoryCountry country = new HistoryCountry();
        country.setId(request.getId());
        copyRequest(request, country);
        country.setUpdateBy(SecurityUtils.getUsername());
        historyCountryMapper.updateById(country);
    }

    @Override
    public void delete(Long id)
    {
        requireCountry(id);
        Long periodCount = historyPeriodMapper.selectCount(new LambdaQueryWrapper<HistoryPeriod>()
                .eq(HistoryPeriod::getCountryId, id));
        if (periodCount != null && periodCount > 0)
        {
            throw new ServiceException("该国家下仍有时期，请先调整或删除时期", HttpStatus.BAD_REQUEST);
        }
        historyCountryMapper.deleteById(id);
    }

    @Override
    public List<HistoryCountryVO> listPublic()
    {
        List<HistoryCountry> countries = listActive();
        if (countries.isEmpty())
        {
            return List.of();
        }
        List<Long> countryIds = countries.stream().map(HistoryCountry::getId).collect(Collectors.toList());
        List<HistoryPeriod> periods = historyPeriodMapper.selectList(new LambdaQueryWrapper<HistoryPeriod>()
                .eq(HistoryPeriod::getStatus, HistoryConstants.STATUS_NORMAL)
                .in(HistoryPeriod::getCountryId, countryIds));
        Map<Long, List<HistoryPeriod>> periodsByCountry = periods.stream()
                .collect(Collectors.groupingBy(HistoryPeriod::getCountryId));
        Map<Long, Integer> eventCountByPeriod = countPublishedEventsByPeriod(
                periods.stream().map(HistoryPeriod::getId).collect(Collectors.toSet()));

        List<HistoryCountryVO> result = new ArrayList<>(countries.size());
        for (HistoryCountry country : countries)
        {
            HistoryCountryVO vo = toBaseVo(country);
            List<HistoryPeriod> countryPeriods = periodsByCountry.getOrDefault(country.getId(), List.of());
            vo.setPeriodCount(countryPeriods.size());
            int eventCount = 0;
            for (HistoryPeriod period : countryPeriods)
            {
                eventCount += eventCountByPeriod.getOrDefault(period.getId(), 0);
            }
            vo.setEventCount(eventCount);
            result.add(vo);
        }
        return result;
    }

    @Override
    public HistoryCountryVO getPublicDetail(Long id)
    {
        HistoryCountry country = requireCountry(id);
        if (!HistoryConstants.STATUS_NORMAL.equals(country.getStatus()))
        {
            throw new ServiceException("国家不存在或已停用", HttpStatus.NOT_FOUND);
        }
        List<HistoryPeriod> periods = historyPeriodMapper.selectList(new LambdaQueryWrapper<HistoryPeriod>()
                .eq(HistoryPeriod::getStatus, HistoryConstants.STATUS_NORMAL)
                .eq(HistoryPeriod::getCountryId, id)
                .orderByAsc(HistoryPeriod::getSort)
                .orderByAsc(HistoryPeriod::getStartYear)
                .orderByAsc(HistoryPeriod::getId));
        Map<Long, Integer> eventCountByPeriod = countPublishedEventsByPeriod(
                periods.stream().map(HistoryPeriod::getId).collect(Collectors.toSet()));

        HistoryCountryVO vo = toBaseVo(country);
        List<HistoryCountryPeriodVO> periodVos = new ArrayList<>(periods.size());
        int totalEvents = 0;
        for (HistoryPeriod period : periods)
        {
            HistoryCountryPeriodVO periodVo = new HistoryCountryPeriodVO();
            periodVo.setId(period.getId());
            periodVo.setName(period.getName());
            periodVo.setAlias(period.getAlias());
            periodVo.setStartYear(period.getStartYear());
            periodVo.setEndYear(period.getEndYear());
            periodVo.setSummary(period.getSummary());
            periodVo.setSort(period.getSort());
            int eventCount = eventCountByPeriod.getOrDefault(period.getId(), 0);
            periodVo.setEventCount(eventCount);
            totalEvents += eventCount;
            periodVos.add(periodVo);
        }
        vo.setPeriods(periodVos);
        vo.setPeriodCount(periodVos.size());
        vo.setEventCount(totalEvents);
        return vo;
    }

    private Map<Long, Integer> countPublishedEventsByPeriod(Set<Long> periodIds)
    {
        Map<Long, Integer> result = new HashMap<>();
        if (periodIds == null || periodIds.isEmpty())
        {
            return result;
        }
        List<HistoryEvent> events = historyEventMapper.selectList(new LambdaQueryWrapper<HistoryEvent>()
                .select(HistoryEvent::getId, HistoryEvent::getPeriodId)
                .eq(HistoryEvent::getStatus, HistoryConstants.STATUS_NORMAL)
                .eq(HistoryEvent::getAuditStatus, HistoryConstants.AUDIT_PUBLISHED)
                .in(HistoryEvent::getPeriodId, periodIds));
        for (HistoryEvent event : events)
        {
            if (event.getPeriodId() == null)
            {
                continue;
            }
            result.merge(event.getPeriodId(), 1, Integer::sum);
        }
        return result;
    }

    private HistoryCountryVO toBaseVo(HistoryCountry country)
    {
        HistoryCountryVO vo = new HistoryCountryVO();
        vo.setId(country.getId());
        vo.setName(country.getName());
        vo.setAlias(country.getAlias());
        vo.setRegion(country.getRegion());
        vo.setPeriodLabel(country.getPeriodLabel());
        vo.setSummary(country.getSummary());
        vo.setSort(country.getSort());
        return vo;
    }

    private void ensureNameUnique(String name, Long excludeId)
    {
        LambdaQueryWrapper<HistoryCountry> wrapper = new LambdaQueryWrapper<HistoryCountry>()
                .eq(HistoryCountry::getName, name.trim());
        if (excludeId != null)
        {
            wrapper.ne(HistoryCountry::getId, excludeId);
        }
        Long count = historyCountryMapper.selectCount(wrapper);
        if (count != null && count > 0)
        {
            throw new ServiceException("国家名称已存在", HttpStatus.BAD_REQUEST);
        }
    }

    private HistoryCountry requireCountry(Long id)
    {
        HistoryCountry country = historyCountryMapper.selectById(id);
        if (country == null)
        {
            throw new ServiceException("国家不存在", HttpStatus.NOT_FOUND);
        }
        return country;
    }

    private void copyRequest(HistoryCountrySaveRequest request, HistoryCountry country)
    {
        country.setName(request.getName().trim());
        country.setAlias(request.getAlias());
        country.setRegion(request.getRegion());
        country.setPeriodLabel(StringUtils.hasText(request.getPeriodLabel()) ? request.getPeriodLabel().trim() : "时期");
        country.setSummary(request.getSummary());
        country.setSort(request.getSort() == null ? 0 : request.getSort());
        country.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : HistoryConstants.STATUS_NORMAL);
        country.setRemark(request.getRemark());
    }
}
