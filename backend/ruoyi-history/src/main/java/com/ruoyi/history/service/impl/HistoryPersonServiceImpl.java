package com.ruoyi.history.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.history.constant.HistoryConstants;
import com.ruoyi.history.domain.HistoryPerson;
import com.ruoyi.history.dto.HistoryPersonPageQuery;
import com.ruoyi.history.dto.HistoryPersonSaveRequest;
import com.ruoyi.history.mapper.HistoryPersonMapper;
import com.ruoyi.history.service.HistoryPersonService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HistoryPersonServiceImpl implements HistoryPersonService
{
    private final HistoryPersonMapper historyPersonMapper;

    @Override
    public Page<HistoryPerson> page(HistoryPersonPageQuery query)
    {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : Math.min(query.getPageSize(), 100);
        LambdaQueryWrapper<HistoryPerson> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getName()))
        {
            wrapper.like(HistoryPerson::getName, query.getName().trim());
        }
        if (query.getPeriodId() != null)
        {
            wrapper.eq(HistoryPerson::getPeriodId, query.getPeriodId());
        }
        if (StringUtils.hasText(query.getAuditStatus()))
        {
            wrapper.eq(HistoryPerson::getAuditStatus, query.getAuditStatus().trim());
        }
        if (StringUtils.hasText(query.getStatus()))
        {
            wrapper.eq(HistoryPerson::getStatus, query.getStatus().trim());
        }
        wrapper.orderByAsc(HistoryPerson::getBirthYear).orderByAsc(HistoryPerson::getId);
        return historyPersonMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public HistoryPerson getById(Long id)
    {
        return requirePerson(id);
    }

    @Override
    public Long create(HistoryPersonSaveRequest request)
    {
        HistoryPerson person = new HistoryPerson();
        copyRequest(request, person);
        person.setCreateBy(SecurityUtils.getUsername());
        historyPersonMapper.insert(person);
        return person.getId();
    }

    @Override
    public void update(HistoryPersonSaveRequest request)
    {
        if (request.getId() == null)
        {
            throw new ServiceException("人物ID不能为空", HttpStatus.BAD_REQUEST);
        }
        requirePerson(request.getId());
        HistoryPerson person = new HistoryPerson();
        person.setId(request.getId());
        copyRequest(request, person);
        person.setUpdateBy(SecurityUtils.getUsername());
        historyPersonMapper.updateById(person);
    }

    @Override
    public void delete(Long id)
    {
        requirePerson(id);
        historyPersonMapper.deleteById(id);
    }

    private HistoryPerson requirePerson(Long id)
    {
        HistoryPerson person = historyPersonMapper.selectById(id);
        if (person == null)
        {
            throw new ServiceException("历史人物不存在", HttpStatus.NOT_FOUND);
        }
        return person;
    }

    private void copyRequest(HistoryPersonSaveRequest request, HistoryPerson person)
    {
        person.setName(request.getName());
        person.setAlias(request.getAlias());
        person.setPeriodId(request.getPeriodId());
        person.setBirthYear(request.getBirthYear());
        person.setDeathYear(request.getDeathYear());
        person.setDatePrecision(StringUtils.hasText(request.getDatePrecision())
                ? request.getDatePrecision() : HistoryConstants.DATE_PRECISION_YEAR);
        person.setOriginalDateText(request.getOriginalDateText());
        person.setCalendarType(request.getCalendarType());
        person.setIsApproximate(Boolean.TRUE.equals(request.getIsApproximate()));
        person.setBirthPlaceId(request.getBirthPlaceId());
        person.setSummary(request.getSummary());
        person.setUncertaintyNote(request.getUncertaintyNote());
        person.setAuditStatus(StringUtils.hasText(request.getAuditStatus())
                ? request.getAuditStatus() : HistoryConstants.AUDIT_DRAFT);
        person.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : HistoryConstants.STATUS_NORMAL);
        person.setRemark(request.getRemark());
    }
}
