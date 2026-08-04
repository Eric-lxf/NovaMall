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
import com.ruoyi.history.domain.HistoryPlace;
import com.ruoyi.history.dto.HistoryPlacePageQuery;
import com.ruoyi.history.dto.HistoryPlaceSaveRequest;
import com.ruoyi.history.mapper.HistoryPlaceMapper;
import com.ruoyi.history.service.HistoryPlaceService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HistoryPlaceServiceImpl implements HistoryPlaceService
{
    private final HistoryPlaceMapper historyPlaceMapper;

    @Override
    public Page<HistoryPlace> page(HistoryPlacePageQuery query)
    {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : Math.min(query.getPageSize(), 100);
        LambdaQueryWrapper<HistoryPlace> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getName()))
        {
            wrapper.like(HistoryPlace::getName, query.getName().trim());
        }
        if (StringUtils.hasText(query.getAuditStatus()))
        {
            wrapper.eq(HistoryPlace::getAuditStatus, query.getAuditStatus().trim());
        }
        if (StringUtils.hasText(query.getStatus()))
        {
            wrapper.eq(HistoryPlace::getStatus, query.getStatus().trim());
        }
        wrapper.orderByAsc(HistoryPlace::getId);
        return historyPlaceMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public List<HistoryPlace> listActive()
    {
        return historyPlaceMapper.selectList(new LambdaQueryWrapper<HistoryPlace>()
                .eq(HistoryPlace::getStatus, HistoryConstants.STATUS_NORMAL)
                .orderByAsc(HistoryPlace::getId));
    }

    @Override
    public HistoryPlace getById(Long id)
    {
        return requirePlace(id);
    }

    @Override
    public Long create(HistoryPlaceSaveRequest request)
    {
        HistoryPlace place = new HistoryPlace();
        copyRequest(request, place);
        place.setCreateBy(SecurityUtils.getUsername());
        historyPlaceMapper.insert(place);
        return place.getId();
    }

    @Override
    public void update(HistoryPlaceSaveRequest request)
    {
        if (request.getId() == null)
        {
            throw new ServiceException("地点ID不能为空", HttpStatus.BAD_REQUEST);
        }
        requirePlace(request.getId());
        HistoryPlace place = new HistoryPlace();
        place.setId(request.getId());
        copyRequest(request, place);
        place.setUpdateBy(SecurityUtils.getUsername());
        historyPlaceMapper.updateById(place);
    }

    @Override
    public void delete(Long id)
    {
        requirePlace(id);
        historyPlaceMapper.deleteById(id);
    }

    private HistoryPlace requirePlace(Long id)
    {
        HistoryPlace place = historyPlaceMapper.selectById(id);
        if (place == null)
        {
            throw new ServiceException("历史地点不存在", HttpStatus.NOT_FOUND);
        }
        return place;
    }

    private void copyRequest(HistoryPlaceSaveRequest request, HistoryPlace place)
    {
        place.setName(request.getName());
        place.setAlias(request.getAlias());
        place.setModernName(request.getModernName());
        place.setRegion(request.getRegion());
        place.setLongitude(request.getLongitude());
        place.setLatitude(request.getLatitude());
        place.setSummary(request.getSummary());
        place.setAuditStatus(StringUtils.hasText(request.getAuditStatus())
                ? request.getAuditStatus() : HistoryConstants.AUDIT_DRAFT);
        place.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : HistoryConstants.STATUS_NORMAL);
        place.setRemark(request.getRemark());
    }
}
