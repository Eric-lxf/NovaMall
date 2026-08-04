package com.ruoyi.history.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.history.constant.HistoryConstants;
import com.ruoyi.history.domain.HistoryLearningPath;
import com.ruoyi.history.domain.HistoryLearningPathUnit;
import com.ruoyi.history.domain.HistoryLearningRecord;
import com.ruoyi.history.domain.HistoryLearningUnit;
import com.ruoyi.history.dto.HistoryPathPageQuery;
import com.ruoyi.history.dto.HistoryPathSaveRequest;
import com.ruoyi.history.mapper.HistoryLearningPathMapper;
import com.ruoyi.history.mapper.HistoryLearningPathUnitMapper;
import com.ruoyi.history.mapper.HistoryLearningRecordMapper;
import com.ruoyi.history.mapper.HistoryLearningUnitMapper;
import com.ruoyi.history.service.HistoryPathService;
import com.ruoyi.history.vo.HistoryPathVO;
import com.ruoyi.history.vo.HistoryUnitVO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HistoryPathServiceImpl implements HistoryPathService
{
    private final HistoryLearningPathMapper historyLearningPathMapper;
    private final HistoryLearningPathUnitMapper historyLearningPathUnitMapper;
    private final HistoryLearningUnitMapper historyLearningUnitMapper;
    private final HistoryLearningRecordMapper historyLearningRecordMapper;

    @Override
    public Page<HistoryLearningPath> page(HistoryPathPageQuery query)
    {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : Math.min(query.getPageSize(), 100);
        LambdaQueryWrapper<HistoryLearningPath> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getTitle()))
        {
            wrapper.like(HistoryLearningPath::getTitle, query.getTitle().trim());
        }
        if (query.getPeriodId() != null)
        {
            wrapper.eq(HistoryLearningPath::getPeriodId, query.getPeriodId());
        }
        if (StringUtils.hasText(query.getAuditStatus()))
        {
            wrapper.eq(HistoryLearningPath::getAuditStatus, query.getAuditStatus());
        }
        if (StringUtils.hasText(query.getStatus()))
        {
            wrapper.eq(HistoryLearningPath::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(HistoryLearningPath::getId);
        return historyLearningPathMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public HistoryPathVO getDetail(Long id)
    {
        return toVo(requirePath(id), null, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(HistoryPathSaveRequest request)
    {
        HistoryLearningPath path = new HistoryLearningPath();
        copyRequest(request, path);
        if (!StringUtils.hasText(path.getAuditStatus()))
        {
            path.setAuditStatus(HistoryConstants.AUDIT_DRAFT);
        }
        if (!StringUtils.hasText(path.getStatus()))
        {
            path.setStatus(HistoryConstants.STATUS_NORMAL);
        }
        if (!StringUtils.hasText(path.getDifficulty()))
        {
            path.setDifficulty("BEGINNER");
        }
        path.setCreateBy(SecurityUtils.getUsername());
        historyLearningPathMapper.insert(path);
        replacePathUnits(path.getId(), request.getUnitIds());
        return path.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(HistoryPathSaveRequest request)
    {
        if (request.getId() == null)
        {
            throw new ServiceException("路径ID不能为空", HttpStatus.BAD_REQUEST);
        }
        requirePath(request.getId());
        HistoryLearningPath path = new HistoryLearningPath();
        path.setId(request.getId());
        copyRequest(request, path);
        path.setUpdateBy(SecurityUtils.getUsername());
        historyLearningPathMapper.updateById(path);
        replacePathUnits(request.getId(), request.getUnitIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id)
    {
        requirePath(id);
        historyLearningPathUnitMapper.delete(new LambdaQueryWrapper<HistoryLearningPathUnit>()
                .eq(HistoryLearningPathUnit::getPathId, id));
        historyLearningPathMapper.deleteById(id);
    }

    @Override
    public void publish(Long id)
    {
        HistoryLearningPath existing = requirePath(id);
        List<HistoryLearningPathUnit> links = listPathUnits(id);
        if (links.isEmpty())
        {
            throw new ServiceException("请先为路径添加学习单元再发布", HttpStatus.BAD_REQUEST);
        }
        for (HistoryLearningPathUnit link : links)
        {
            HistoryLearningUnit unit = historyLearningUnitMapper.selectById(link.getUnitId());
            if (unit == null || !HistoryConstants.AUDIT_PUBLISHED.equals(unit.getAuditStatus()))
            {
                throw new ServiceException("路径中存在未发布的学习单元，无法发布", HttpStatus.BAD_REQUEST);
            }
        }
        HistoryLearningPath path = new HistoryLearningPath();
        path.setId(existing.getId());
        path.setAuditStatus(HistoryConstants.AUDIT_PUBLISHED);
        path.setUpdateBy(SecurityUtils.getUsername());
        historyLearningPathMapper.updateById(path);
    }

    @Override
    public List<HistoryPathVO> listPublished(Integer limit)
    {
        int size = limit == null ? 50 : Math.min(limit, 200);
        Page<HistoryLearningPath> page = historyLearningPathMapper.selectPage(new Page<>(1, size),
                new LambdaQueryWrapper<HistoryLearningPath>()
                        .eq(HistoryLearningPath::getStatus, HistoryConstants.STATUS_NORMAL)
                        .eq(HistoryLearningPath::getAuditStatus, HistoryConstants.AUDIT_PUBLISHED)
                        .orderByDesc(HistoryLearningPath::getId));
        List<HistoryPathVO> result = new ArrayList<>(page.getRecords().size());
        for (HistoryLearningPath path : page.getRecords())
        {
            result.add(toVo(path, null, false));
        }
        return result;
    }

    @Override
    public HistoryPathVO getPublishedDetail(Long id, Long userId)
    {
        HistoryLearningPath path = requirePath(id);
        if (!HistoryConstants.STATUS_NORMAL.equals(path.getStatus())
                || !HistoryConstants.AUDIT_PUBLISHED.equals(path.getAuditStatus()))
        {
            throw new ServiceException("学习路径不存在或未发布", HttpStatus.NOT_FOUND);
        }
        return toVo(path, userId, true);
    }

    private void replacePathUnits(Long pathId, List<Long> unitIds)
    {
        historyLearningPathUnitMapper.delete(new LambdaQueryWrapper<HistoryLearningPathUnit>()
                .eq(HistoryLearningPathUnit::getPathId, pathId));
        if (CollectionUtils.isEmpty(unitIds))
        {
            return;
        }
        Set<Long> seen = new HashSet<>();
        int sort = 0;
        for (Long unitId : unitIds)
        {
            if (unitId == null || !seen.add(unitId))
            {
                continue;
            }
            HistoryLearningUnit unit = historyLearningUnitMapper.selectById(unitId);
            if (unit == null)
            {
                throw new ServiceException("学习单元不存在：" + unitId, HttpStatus.BAD_REQUEST);
            }
            HistoryLearningPathUnit link = new HistoryLearningPathUnit();
            link.setPathId(pathId);
            link.setUnitId(unitId);
            link.setSort(sort++);
            historyLearningPathUnitMapper.insert(link);
        }
    }

    private List<HistoryLearningPathUnit> listPathUnits(Long pathId)
    {
        return historyLearningPathUnitMapper.selectList(new LambdaQueryWrapper<HistoryLearningPathUnit>()
                .eq(HistoryLearningPathUnit::getPathId, pathId)
                .orderByAsc(HistoryLearningPathUnit::getSort)
                .orderByAsc(HistoryLearningPathUnit::getId));
    }

    private HistoryPathVO toVo(HistoryLearningPath path, Long userId, boolean includeUnits)
    {
        HistoryPathVO vo = new HistoryPathVO();
        vo.setId(path.getId());
        vo.setTitle(path.getTitle());
        vo.setSummary(path.getSummary());
        vo.setPeriodId(path.getPeriodId());
        vo.setDifficulty(path.getDifficulty());
        vo.setEstimatedDays(path.getEstimatedDays());
        vo.setAuditStatus(path.getAuditStatus());
        vo.setStatus(path.getStatus());
        vo.setRemark(path.getRemark());
        vo.setCreateTime(path.getCreateTime());
        vo.setUpdateTime(path.getUpdateTime());

        List<HistoryLearningPathUnit> links = listPathUnits(path.getId());
        List<Long> unitIds = links.stream().map(HistoryLearningPathUnit::getUnitId).collect(Collectors.toList());
        vo.setUnitIds(unitIds);
        vo.setTotalUnitCount(unitIds.size());

        if (!includeUnits)
        {
            return vo;
        }

        Map<Long, HistoryLearningRecord> recordMap = new HashMap<>();
        if (userId != null && !unitIds.isEmpty())
        {
            List<HistoryLearningRecord> records = historyLearningRecordMapper.selectList(
                    new LambdaQueryWrapper<HistoryLearningRecord>()
                            .eq(HistoryLearningRecord::getUserId, userId)
                            .in(HistoryLearningRecord::getUnitId, unitIds));
            for (HistoryLearningRecord record : records)
            {
                recordMap.put(record.getUnitId(), record);
            }
        }

        List<HistoryUnitVO> units = new ArrayList<>();
        int completed = 0;
        int progressSum = 0;
        for (HistoryLearningPathUnit link : links)
        {
            HistoryLearningUnit unit = historyLearningUnitMapper.selectById(link.getUnitId());
            if (unit == null)
            {
                continue;
            }
            HistoryUnitVO unitVo = HistoryUnitServiceImpl.toVo(unit);
            unitVo.setSort(link.getSort());
            HistoryLearningRecord record = recordMap.get(unit.getId());
            if (record != null)
            {
                unitVo.setProgress(record.getProgress());
                unitVo.setCompleted(Boolean.TRUE.equals(record.getCompleted()));
                if (Boolean.TRUE.equals(record.getCompleted()))
                {
                    completed++;
                }
                progressSum += record.getProgress() == null ? 0 : record.getProgress();
            }
            else
            {
                unitVo.setProgress(0);
                unitVo.setCompleted(false);
            }
            units.add(unitVo);
        }
        vo.setUnits(units);
        vo.setCompletedUnitCount(completed);
        if (!units.isEmpty() && userId != null)
        {
            vo.setPathProgress(progressSum / units.size());
        }
        return vo;
    }

    private void copyRequest(HistoryPathSaveRequest request, HistoryLearningPath path)
    {
        path.setTitle(request.getTitle());
        path.setSummary(request.getSummary());
        path.setPeriodId(request.getPeriodId());
        if (StringUtils.hasText(request.getDifficulty()))
        {
            path.setDifficulty(request.getDifficulty());
        }
        path.setEstimatedDays(request.getEstimatedDays());
        if (StringUtils.hasText(request.getAuditStatus()))
        {
            path.setAuditStatus(request.getAuditStatus());
        }
        if (StringUtils.hasText(request.getStatus()))
        {
            path.setStatus(request.getStatus());
        }
        path.setRemark(request.getRemark());
    }

    private HistoryLearningPath requirePath(Long id)
    {
        HistoryLearningPath path = historyLearningPathMapper.selectById(id);
        if (path == null)
        {
            throw new ServiceException("学习路径不存在", HttpStatus.NOT_FOUND);
        }
        return path;
    }
}
