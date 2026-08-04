package com.ruoyi.history.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.history.domain.HistoryLearningPath;
import com.ruoyi.history.dto.HistoryPathPageQuery;
import com.ruoyi.history.dto.HistoryPathSaveRequest;
import com.ruoyi.history.vo.HistoryPathVO;

public interface HistoryPathService
{
    Page<HistoryLearningPath> page(HistoryPathPageQuery query);

    HistoryPathVO getDetail(Long id);

    Long create(HistoryPathSaveRequest request);

    void update(HistoryPathSaveRequest request);

    void delete(Long id);

    void publish(Long id);

    List<HistoryPathVO> listPublished(Integer limit);

    HistoryPathVO getPublishedDetail(Long id, Long userId);
}
