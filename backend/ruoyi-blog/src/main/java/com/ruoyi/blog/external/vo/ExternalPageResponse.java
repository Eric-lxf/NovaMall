package com.ruoyi.blog.external.vo;

import java.util.List;

public record ExternalPageResponse<T>(List<T> records, long total, long pageNum, long pageSize)
{
}
