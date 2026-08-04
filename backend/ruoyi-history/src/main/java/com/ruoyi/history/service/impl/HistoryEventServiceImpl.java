package com.ruoyi.history.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import com.ruoyi.history.domain.HistoryEvent;
import com.ruoyi.history.domain.HistoryPeriod;
import com.ruoyi.history.dto.HistoryEventPageQuery;
import com.ruoyi.history.dto.HistoryEventSaveRequest;
import com.ruoyi.history.dto.HistoryTimelineQuery;
import com.ruoyi.history.mapper.HistoryCountryMapper;
import com.ruoyi.history.mapper.HistoryEventMapper;
import com.ruoyi.history.mapper.HistoryPeriodMapper;
import com.ruoyi.history.mapper.HistoryPlaceMapper;
import com.ruoyi.history.service.HistoryEventService;
import com.ruoyi.history.vo.HistoryTimelineItemVO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HistoryEventServiceImpl implements HistoryEventService
{
    private final HistoryEventMapper historyEventMapper;
    private final HistoryPeriodMapper historyPeriodMapper;
    private final HistoryPlaceMapper historyPlaceMapper;
    private final HistoryCountryMapper historyCountryMapper;

    @Override
    public Page<HistoryEvent> page(HistoryEventPageQuery query)
    {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : Math.min(query.getPageSize(), 100);
        LambdaQueryWrapper<HistoryEvent> wrapper = buildQueryWrapper(query);
        wrapper.orderByAsc(HistoryEvent::getStartYear).orderByAsc(HistoryEvent::getId);
        return historyEventMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public HistoryEvent getById(Long id)
    {
        return requireEvent(id);
    }

    @Override
    public Long create(HistoryEventSaveRequest request)
    {
        HistoryEvent event = new HistoryEvent();
        copyRequest(request, event);
        event.setCreateBy(SecurityUtils.getUsername());
        historyEventMapper.insert(event);
        return event.getId();
    }

    @Override
    public void update(HistoryEventSaveRequest request)
    {
        if (request.getId() == null)
        {
            throw new ServiceException("事件ID不能为空", HttpStatus.BAD_REQUEST);
        }
        requireEvent(request.getId());
        HistoryEvent event = new HistoryEvent();
        event.setId(request.getId());
        copyRequest(request, event);
        event.setUpdateBy(SecurityUtils.getUsername());
        historyEventMapper.updateById(event);
    }

    @Override
    public void delete(Long id)
    {
        requireEvent(id);
        historyEventMapper.deleteById(id);
    }

    @Override
    public void publish(Long id)
    {
        HistoryEvent existing = requireEvent(id);
        HistoryEvent event = new HistoryEvent();
        event.setId(existing.getId());
        event.setAuditStatus(HistoryConstants.AUDIT_PUBLISHED);
        event.setUpdateBy(SecurityUtils.getUsername());
        historyEventMapper.updateById(event);
    }

    @Override
    public List<HistoryTimelineItemVO> timeline(HistoryTimelineQuery query)
    {
        int limit = query.getLimit() == null ? 100 : Math.min(query.getLimit(), 500);
        LambdaQueryWrapper<HistoryEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HistoryEvent::getStatus, HistoryConstants.STATUS_NORMAL)
                .eq(HistoryEvent::getAuditStatus, HistoryConstants.AUDIT_PUBLISHED);
        if (query.getPeriodId() != null)
        {
            wrapper.eq(HistoryEvent::getPeriodId, query.getPeriodId());
        }
        else if (query.getCountryId() != null)
        {
            List<HistoryPeriod> periods = historyPeriodMapper.selectList(new LambdaQueryWrapper<HistoryPeriod>()
                    .select(HistoryPeriod::getId)
                    .eq(HistoryPeriod::getCountryId, query.getCountryId())
                    .eq(HistoryPeriod::getStatus, HistoryConstants.STATUS_NORMAL));
            if (periods.isEmpty())
            {
                return List.of();
            }
            List<Long> periodIds = periods.stream().map(HistoryPeriod::getId).collect(Collectors.toList());
            wrapper.in(HistoryEvent::getPeriodId, periodIds);
        }
        if (query.getYearFrom() != null)
        {
            wrapper.ge(HistoryEvent::getStartYear, query.getYearFrom());
        }
        if (query.getYearTo() != null)
        {
            wrapper.le(HistoryEvent::getStartYear, query.getYearTo());
        }
        wrapper.orderByAsc(HistoryEvent::getStartYear).orderByAsc(HistoryEvent::getId);
        Page<HistoryEvent> page = historyEventMapper.selectPage(new Page<>(1, limit), wrapper);
        return toTimelineItems(page.getRecords());
    }

    private List<HistoryTimelineItemVO> toTimelineItems(List<HistoryEvent> events)
    {
        if (events.isEmpty())
        {
            return List.of();
        }
        Set<Long> periodIds = events.stream().map(HistoryEvent::getPeriodId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> placeIds = events.stream().map(HistoryEvent::getPlaceId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, HistoryPeriod> periodMap = new HashMap<>();
        if (!periodIds.isEmpty())
        {
            historyPeriodMapper.selectBatchIds(periodIds).forEach(p -> periodMap.put(p.getId(), p));
        }
        Set<Long> countryIds = periodMap.values().stream()
                .map(HistoryPeriod::getCountryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> countryNames = new HashMap<>();
        if (!countryIds.isEmpty())
        {
            historyCountryMapper.selectBatchIds(countryIds).forEach(c -> countryNames.put(c.getId(), c.getName()));
        }
        Map<Long, String> placeNames = new HashMap<>();
        if (!placeIds.isEmpty())
        {
            historyPlaceMapper.selectBatchIds(placeIds).forEach(p -> placeNames.put(p.getId(), p.getName()));
        }
        List<HistoryTimelineItemVO> items = new ArrayList<>(events.size());
        for (HistoryEvent event : events)
        {
            HistoryTimelineItemVO item = new HistoryTimelineItemVO();
            item.setId(event.getId());
            item.setTitle(event.getTitle());
            item.setPeriodId(event.getPeriodId());
            HistoryPeriod period = event.getPeriodId() == null ? null : periodMap.get(event.getPeriodId());
            item.setPeriodName(period == null ? null : period.getName());
            if (period != null && period.getCountryId() != null)
            {
                item.setCountryId(period.getCountryId());
                item.setCountryName(countryNames.get(period.getCountryId()));
            }
            item.setPlaceId(event.getPlaceId());
            item.setPlaceName(event.getPlaceId() == null ? null : placeNames.get(event.getPlaceId()));
            item.setStartYear(event.getStartYear());
            item.setEndYear(event.getEndYear());
            item.setDatePrecision(event.getDatePrecision());
            item.setOriginalDateText(event.getOriginalDateText());
            item.setIsApproximate(event.getIsApproximate());
            item.setSummary(event.getSummary());
            item.setUncertaintyNote(event.getUncertaintyNote());
            items.add(item);
        }
        return items;
    }

    private LambdaQueryWrapper<HistoryEvent> buildQueryWrapper(HistoryEventPageQuery query)
    {
        LambdaQueryWrapper<HistoryEvent> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getTitle()))
        {
            wrapper.like(HistoryEvent::getTitle, query.getTitle().trim());
        }
        if (query.getPeriodId() != null)
        {
            wrapper.eq(HistoryEvent::getPeriodId, query.getPeriodId());
        }
        if (StringUtils.hasText(query.getAuditStatus()))
        {
            wrapper.eq(HistoryEvent::getAuditStatus, query.getAuditStatus().trim());
        }
        if (StringUtils.hasText(query.getStatus()))
        {
            wrapper.eq(HistoryEvent::getStatus, query.getStatus().trim());
        }
        if (query.getYearFrom() != null)
        {
            wrapper.ge(HistoryEvent::getStartYear, query.getYearFrom());
        }
        if (query.getYearTo() != null)
        {
            wrapper.le(HistoryEvent::getStartYear, query.getYearTo());
        }
        return wrapper;
    }

    private HistoryEvent requireEvent(Long id)
    {
        HistoryEvent event = historyEventMapper.selectById(id);
        if (event == null)
        {
            throw new ServiceException("历史事件不存在", HttpStatus.NOT_FOUND);
        }
        return event;
    }

    private void copyRequest(HistoryEventSaveRequest request, HistoryEvent event)
    {
        event.setTitle(request.getTitle());
        event.setPeriodId(request.getPeriodId());
        event.setPlaceId(request.getPlaceId());
        event.setStartYear(request.getStartYear());
        event.setEndYear(request.getEndYear());
        event.setDatePrecision(StringUtils.hasText(request.getDatePrecision())
                ? request.getDatePrecision() : HistoryConstants.DATE_PRECISION_YEAR);
        event.setOriginalDateText(request.getOriginalDateText());
        event.setCalendarType(request.getCalendarType());
        event.setIsApproximate(Boolean.TRUE.equals(request.getIsApproximate()));
        event.setSummary(request.getSummary());
        event.setBackground(request.getBackground());
        event.setProcess(request.getProcess());
        event.setCauseAnalysis(request.getCauseAnalysis());
        event.setImpact(request.getImpact());
        event.setUncertaintyNote(request.getUncertaintyNote());
        event.setAuditStatus(StringUtils.hasText(request.getAuditStatus())
                ? request.getAuditStatus() : HistoryConstants.AUDIT_DRAFT);
        event.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : HistoryConstants.STATUS_NORMAL);
        event.setRemark(request.getRemark());
    }
}
