package com.ruoyi.history.support;

import org.springframework.util.StringUtils;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.history.dto.extract.HistoryExtractResultDto;

/**
 * 从模型输出中剥离代码围栏并解析 JSON。
 */
public final class HistoryExtractJsonParser
{
    private HistoryExtractJsonParser()
    {
    }

    public static HistoryExtractResultDto parse(String raw)
    {
        if (!StringUtils.hasText(raw))
        {
            throw new ServiceException("模型返回为空", HttpStatus.ERROR);
        }
        String json = stripFence(raw.trim());
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start)
        {
            throw new ServiceException("模型返回不是合法 JSON 对象", HttpStatus.ERROR);
        }
        json = json.substring(start, end + 1);
        try
        {
            HistoryExtractResultDto dto = JSON.parseObject(json, HistoryExtractResultDto.class);
            if (dto == null)
            {
                throw new ServiceException("无法解析抽取结果", HttpStatus.ERROR);
            }
            if (dto.getEvents() == null)
            {
                dto.setEvents(java.util.List.of());
            }
            if (dto.getPersons() == null)
            {
                dto.setPersons(java.util.List.of());
            }
            if (dto.getPlaces() == null)
            {
                dto.setPlaces(java.util.List.of());
            }
            if (dto.getRelations() == null)
            {
                dto.setRelations(java.util.List.of());
            }
            return dto;
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new ServiceException("解析抽取 JSON 失败：" + ex.getMessage(), HttpStatus.ERROR);
        }
    }

    static String stripFence(String raw)
    {
        String text = raw;
        if (text.startsWith("```"))
        {
            int firstNl = text.indexOf('\n');
            if (firstNl > 0)
            {
                text = text.substring(firstNl + 1);
            }
            int fence = text.lastIndexOf("```");
            if (fence >= 0)
            {
                text = text.substring(0, fence);
            }
        }
        return text.trim();
    }
}
