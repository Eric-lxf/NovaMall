package com.ruoyi.blog.constant;

import com.ruoyi.common.exception.ServiceException;

public enum HnBoard
{
    NEWS("news", "topstories", 500),
    PAST("past", "beststories", 500),
    SHOW("show", "showstories", 200),
    JOBS("jobs", "jobstories", 200);

    private final String code;
    private final String path;
    private final int cap;

    HnBoard(String code, String path, int cap)
    {
        this.code = code;
        this.path = path;
        this.cap = cap;
    }

    public String getCode()
    {
        return code;
    }

    public String getPath()
    {
        return path;
    }

    public int getCap()
    {
        return cap;
    }

    public static HnBoard fromCode(String code)
    {
        for (HnBoard board : values())
        {
            if (board.code.equals(code))
            {
                return board;
            }
        }
        throw new ServiceException("未知的 HN 榜单: " + code);
    }
}
