package com.ruoyi.history.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.history.domain.HistoryEvent;

@Mapper
public interface HistoryEventMapper extends BaseMapper<HistoryEvent>
{
}
