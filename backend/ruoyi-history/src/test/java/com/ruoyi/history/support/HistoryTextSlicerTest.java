package com.ruoyi.history.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class HistoryTextSlicerTest
{
    @Test
    void slicesParagraphsAndKeepsLocator()
    {
        String text = "第一段内容。\n\n第二段内容也在这里。\n\n第三段继续。";
        List<HistoryTextSlicer.Slice> slices = HistoryTextSlicer.slice(text);
        assertFalse(slices.isEmpty());
        assertEquals("seg:1", slices.get(0).locator());
        assertTrue(slices.get(0).content().contains("第一段"));
    }

    @Test
    void hardSplitsLongParagraph()
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 400; i++)
        {
            sb.append("这是一句用来撑长度的测试文本。");
        }
        List<HistoryTextSlicer.Slice> slices = HistoryTextSlicer.slice(sb.toString());
        assertTrue(slices.size() > 1);
        for (HistoryTextSlicer.Slice slice : slices)
        {
            assertTrue(slice.content().length() <= 1500);
        }
    }
}
