package com.ruoyi.history.support;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.history.dto.HistoryUnitSaveRequest;
import com.ruoyi.history.vo.HistoryUnitContentVO;

/**
 * 学习单元 content_json 编解码。
 */
public final class HistoryUnitContentCodec
{
    private HistoryUnitContentCodec()
    {
    }

    public static String encode(HistoryUnitSaveRequest request)
    {
        HistoryUnitContentVO content = new HistoryUnitContentVO();
        content.setTimePlace(request.getTimePlace());
        content.setBackground(request.getBackground());
        content.setKeyPeople(request.getKeyPeople());
        content.setProcess(request.getProcess());
        content.setCauseAnalysis(request.getCauseAnalysis());
        content.setImpact(request.getImpact());
        content.setSourcesAndViews(request.getSourcesAndViews());
        content.setPracticeHint(request.getPracticeHint());
        content.setFurtherReading(request.getFurtherReading());
        return JSON.toJSONString(content);
    }

    public static HistoryUnitContentVO decode(String contentJson)
    {
        if (contentJson == null || contentJson.isBlank())
        {
            return new HistoryUnitContentVO();
        }
        HistoryUnitContentVO content = JSON.parseObject(contentJson, HistoryUnitContentVO.class);
        return content == null ? new HistoryUnitContentVO() : content;
    }

    public static void applyToRequest(HistoryUnitContentVO content, HistoryUnitSaveRequest request)
    {
        if (content == null)
        {
            return;
        }
        request.setTimePlace(content.getTimePlace());
        request.setBackground(content.getBackground());
        request.setKeyPeople(content.getKeyPeople());
        request.setProcess(content.getProcess());
        request.setCauseAnalysis(content.getCauseAnalysis());
        request.setImpact(content.getImpact());
        request.setSourcesAndViews(content.getSourcesAndViews());
        request.setPracticeHint(content.getPracticeHint());
        request.setFurtherReading(content.getFurtherReading());
    }
}
