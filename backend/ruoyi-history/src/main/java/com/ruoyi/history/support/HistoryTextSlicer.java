package com.ruoyi.history.support;

import java.util.ArrayList;
import java.util.List;

import org.springframework.util.StringUtils;

import com.ruoyi.history.constant.HistoryConstants;

/**
 * 将长文本切成可溯源片段：先按空行分段，再按目标长度合并/硬切。
 */
public final class HistoryTextSlicer
{
    private HistoryTextSlicer()
    {
    }

    public static List<Slice> slice(String text)
    {
        return slice(text, null);
    }

    public static List<Slice> slice(String text, Integer pageNo)
    {
        List<Slice> result = new ArrayList<>();
        if (!StringUtils.hasText(text))
        {
            return result;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty())
        {
            return result;
        }

        String[] paragraphs = normalized.split("\n\\s*\n+");
        StringBuilder buffer = new StringBuilder();
        int seq = 1;

        for (String raw : paragraphs)
        {
            String paragraph = raw.trim();
            if (paragraph.isEmpty())
            {
                continue;
            }
            if (buffer.length() == 0)
            {
                buffer.append(paragraph);
            }
            else if (buffer.length() + 1 + paragraph.length() <= HistoryConstants.FRAGMENT_TARGET_CHARS)
            {
                buffer.append('\n').append(paragraph);
            }
            else
            {
                seq = flushBuffer(result, buffer, pageNo, seq);
                if (paragraph.length() <= HistoryConstants.FRAGMENT_MAX_CHARS)
                {
                    buffer.append(paragraph);
                }
                else
                {
                    seq = hardSplit(result, paragraph, pageNo, seq);
                }
            }
            if (result.size() >= HistoryConstants.FRAGMENT_MAX_COUNT)
            {
                return result;
            }
        }
        if (buffer.length() > 0 && result.size() < HistoryConstants.FRAGMENT_MAX_COUNT)
        {
            flushBuffer(result, buffer, pageNo, seq);
        }
        return result;
    }

    private static int flushBuffer(List<Slice> result, StringBuilder buffer, Integer pageNo, int seq)
    {
        String content = buffer.toString().trim();
        buffer.setLength(0);
        if (content.isEmpty())
        {
            return seq;
        }
        if (content.length() <= HistoryConstants.FRAGMENT_MAX_CHARS)
        {
            result.add(new Slice(seq, pageNo, buildLocator(pageNo, seq), content));
            return seq + 1;
        }
        return hardSplit(result, content, pageNo, seq);
    }

    private static int hardSplit(List<Slice> result, String content, Integer pageNo, int seq)
    {
        int start = 0;
        while (start < content.length() && result.size() < HistoryConstants.FRAGMENT_MAX_COUNT)
        {
            int end = Math.min(start + HistoryConstants.FRAGMENT_MAX_CHARS, content.length());
            if (end < content.length())
            {
                int breakAt = findBreak(content, start, end);
                if (breakAt > start)
                {
                    end = breakAt;
                }
            }
            String piece = content.substring(start, end).trim();
            if (!piece.isEmpty())
            {
                result.add(new Slice(seq, pageNo, buildLocator(pageNo, seq), piece));
                seq++;
            }
            start = end;
            while (start < content.length() && Character.isWhitespace(content.charAt(start)))
            {
                start++;
            }
        }
        return seq;
    }

    private static int findBreak(String content, int start, int end)
    {
        for (int i = end; i > start + HistoryConstants.FRAGMENT_TARGET_CHARS / 2; i--)
        {
            char c = content.charAt(i - 1);
            if (c == '。' || c == '！' || c == '？' || c == '\n' || c == '.' || c == '!' || c == '?')
            {
                return i;
            }
        }
        return end;
    }

    private static String buildLocator(Integer pageNo, int seq)
    {
        if (pageNo == null)
        {
            return "seg:" + seq;
        }
        return "p" + pageNo + "#seg:" + seq;
    }

    public record Slice(int seqNo, Integer pageNo, String locator, String content)
    {
    }
}
