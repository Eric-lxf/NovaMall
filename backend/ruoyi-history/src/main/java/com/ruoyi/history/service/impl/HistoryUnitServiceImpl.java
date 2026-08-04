package com.ruoyi.history.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.history.constant.HistoryConstants;
import com.ruoyi.history.domain.HistoryEvent;
import com.ruoyi.history.domain.HistoryLearningUnit;
import com.ruoyi.history.domain.HistoryPlace;
import com.ruoyi.history.dto.HistoryUnitGenerateRequest;
import com.ruoyi.history.dto.HistoryUnitPageQuery;
import com.ruoyi.history.dto.HistoryUnitSaveRequest;
import com.ruoyi.history.mapper.HistoryEventMapper;
import com.ruoyi.history.mapper.HistoryLearningUnitMapper;
import com.ruoyi.history.mapper.HistoryPlaceMapper;
import com.ruoyi.history.service.HistoryUnitService;
import com.ruoyi.history.support.HistoryUnitContentCodec;
import com.ruoyi.history.vo.HistoryUnitContentVO;
import com.ruoyi.history.vo.HistoryUnitVO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HistoryUnitServiceImpl implements HistoryUnitService
{
    private final HistoryLearningUnitMapper historyLearningUnitMapper;
    private final HistoryEventMapper historyEventMapper;
    private final HistoryPlaceMapper historyPlaceMapper;

    @Override
    public Page<HistoryLearningUnit> page(HistoryUnitPageQuery query)
    {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : Math.min(query.getPageSize(), 100);
        LambdaQueryWrapper<HistoryLearningUnit> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getTitle()))
        {
            wrapper.like(HistoryLearningUnit::getTitle, query.getTitle().trim());
        }
        if (query.getPeriodId() != null)
        {
            wrapper.eq(HistoryLearningUnit::getPeriodId, query.getPeriodId());
        }
        if (query.getEventId() != null)
        {
            wrapper.eq(HistoryLearningUnit::getEventId, query.getEventId());
        }
        if (StringUtils.hasText(query.getAuditStatus()))
        {
            wrapper.eq(HistoryLearningUnit::getAuditStatus, query.getAuditStatus());
        }
        if (StringUtils.hasText(query.getStatus()))
        {
            wrapper.eq(HistoryLearningUnit::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(HistoryLearningUnit::getId);
        return historyLearningUnitMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public HistoryUnitVO getDetail(Long id)
    {
        return toVo(requireUnit(id));
    }

    @Override
    public List<HistoryLearningUnit> listPublishedOptions()
    {
        return historyLearningUnitMapper.selectList(new LambdaQueryWrapper<HistoryLearningUnit>()
                .eq(HistoryLearningUnit::getStatus, HistoryConstants.STATUS_NORMAL)
                .eq(HistoryLearningUnit::getAuditStatus, HistoryConstants.AUDIT_PUBLISHED)
                .orderByAsc(HistoryLearningUnit::getId));
    }

    @Override
    public Long create(HistoryUnitSaveRequest request)
    {
        HistoryLearningUnit unit = new HistoryLearningUnit();
        copyRequest(request, unit);
        if (!StringUtils.hasText(unit.getAuditStatus()))
        {
            unit.setAuditStatus(HistoryConstants.AUDIT_DRAFT);
        }
        if (!StringUtils.hasText(unit.getStatus()))
        {
            unit.setStatus(HistoryConstants.STATUS_NORMAL);
        }
        unit.setCreateBy(SecurityUtils.getUsername());
        historyLearningUnitMapper.insert(unit);
        return unit.getId();
    }

    @Override
    public void update(HistoryUnitSaveRequest request)
    {
        if (request.getId() == null)
        {
            throw new ServiceException("单元ID不能为空", HttpStatus.BAD_REQUEST);
        }
        requireUnit(request.getId());
        HistoryLearningUnit unit = new HistoryLearningUnit();
        unit.setId(request.getId());
        copyRequest(request, unit);
        unit.setUpdateBy(SecurityUtils.getUsername());
        historyLearningUnitMapper.updateById(unit);
    }

    @Override
    public void delete(Long id)
    {
        requireUnit(id);
        historyLearningUnitMapper.deleteById(id);
    }

    @Override
    public void publish(Long id)
    {
        HistoryLearningUnit existing = requireUnit(id);
        HistoryLearningUnit unit = new HistoryLearningUnit();
        unit.setId(existing.getId());
        unit.setAuditStatus(HistoryConstants.AUDIT_PUBLISHED);
        unit.setUpdateBy(SecurityUtils.getUsername());
        historyLearningUnitMapper.updateById(unit);
    }

    @Override
    public Long generateFromEvent(HistoryUnitGenerateRequest request)
    {
        HistoryEvent event = historyEventMapper.selectById(request.getEventId());
        if (event == null)
        {
            throw new ServiceException("历史事件不存在", HttpStatus.NOT_FOUND);
        }
        if (!HistoryConstants.AUDIT_PUBLISHED.equals(event.getAuditStatus())
                || !HistoryConstants.STATUS_NORMAL.equals(event.getStatus()))
        {
            throw new ServiceException("仅已发布事件可生成学习单元", HttpStatus.BAD_REQUEST);
        }

        HistoryUnitSaveRequest save = new HistoryUnitSaveRequest();
        save.setTitle(event.getTitle());
        save.setEventId(event.getId());
        save.setPeriodId(event.getPeriodId());
        save.setOneLiner(event.getSummary());
        save.setObjectives("理解「" + event.getTitle() + "」的背景、过程与影响");
        save.setPrerequisites("建议先浏览同时期时间线与相关人物简介");
        save.setTimePlace(buildTimePlace(event));
        save.setBackground(event.getBackground());
        save.setKeyPeople("");
        save.setProcess(event.getProcess());
        save.setCauseAnalysis(event.getCauseAnalysis());
        save.setImpact(event.getImpact());
        save.setSourcesAndViews(StringUtils.hasText(event.getUncertaintyNote())
                ? "不确定性说明：" + event.getUncertaintyNote()
                : "请结合资料原文核对结论");
        save.setPracticeHint("完成本单元后可进入测验巩固关键时间与因果");
        save.setFurtherReading("可在时间线中查看同期事件与人物");
        save.setAuditStatus(HistoryConstants.AUDIT_DRAFT);
        save.setStatus(HistoryConstants.STATUS_NORMAL);
        save.setRemark("由事件 #" + event.getId() + " 自动生成");
        return create(save);
    }

    @Override
    public HistoryUnitVO getPublishedDetail(Long id)
    {
        HistoryLearningUnit unit = requireUnit(id);
        if (!HistoryConstants.STATUS_NORMAL.equals(unit.getStatus())
                || !HistoryConstants.AUDIT_PUBLISHED.equals(unit.getAuditStatus()))
        {
            throw new ServiceException("学习单元不存在或未发布", HttpStatus.NOT_FOUND);
        }
        return toVo(unit);
    }

    private String buildTimePlace(HistoryEvent event)
    {
        StringJoiner joiner = new StringJoiner(" · ");
        if (StringUtils.hasText(event.getOriginalDateText()))
        {
            joiner.add(event.getOriginalDateText());
        }
        else if (event.getStartYear() != null)
        {
            String year = String.valueOf(event.getStartYear());
            if (event.getEndYear() != null && !event.getEndYear().equals(event.getStartYear()))
            {
                year = event.getStartYear() + "~" + event.getEndYear();
            }
            joiner.add(year);
        }
        if (event.getPlaceId() != null)
        {
            HistoryPlace place = historyPlaceMapper.selectById(event.getPlaceId());
            if (place != null && StringUtils.hasText(place.getName()))
            {
                joiner.add(place.getName());
            }
        }
        return joiner.toString();
    }

    private void copyRequest(HistoryUnitSaveRequest request, HistoryLearningUnit unit)
    {
        unit.setTitle(request.getTitle());
        unit.setEventId(request.getEventId());
        unit.setPeriodId(request.getPeriodId());
        unit.setOneLiner(request.getOneLiner());
        unit.setObjectives(request.getObjectives());
        unit.setPrerequisites(request.getPrerequisites());
        unit.setContentJson(HistoryUnitContentCodec.encode(request));
        if (StringUtils.hasText(request.getAuditStatus()))
        {
            unit.setAuditStatus(request.getAuditStatus());
        }
        if (StringUtils.hasText(request.getStatus()))
        {
            unit.setStatus(request.getStatus());
        }
        unit.setRemark(request.getRemark());
    }

    private HistoryLearningUnit requireUnit(Long id)
    {
        HistoryLearningUnit unit = historyLearningUnitMapper.selectById(id);
        if (unit == null)
        {
            throw new ServiceException("学习单元不存在", HttpStatus.NOT_FOUND);
        }
        return unit;
    }

    public static HistoryUnitVO toVo(HistoryLearningUnit unit)
    {
        HistoryUnitVO vo = new HistoryUnitVO();
        vo.setId(unit.getId());
        vo.setTitle(unit.getTitle());
        vo.setEventId(unit.getEventId());
        vo.setPeriodId(unit.getPeriodId());
        vo.setOneLiner(unit.getOneLiner());
        vo.setObjectives(unit.getObjectives());
        vo.setPrerequisites(unit.getPrerequisites());
        HistoryUnitContentVO content = HistoryUnitContentCodec.decode(unit.getContentJson());
        vo.setContent(content);
        vo.setAuditStatus(unit.getAuditStatus());
        vo.setStatus(unit.getStatus());
        vo.setRemark(unit.getRemark());
        vo.setCreateTime(unit.getCreateTime());
        vo.setUpdateTime(unit.getUpdateTime());
        return vo;
    }

    public static List<HistoryUnitVO> toVoList(List<HistoryLearningUnit> units)
    {
        List<HistoryUnitVO> list = new ArrayList<>(units.size());
        for (HistoryLearningUnit unit : units)
        {
            list.add(toVo(unit));
        }
        return list;
    }
}
