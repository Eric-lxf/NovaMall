package com.ruoyi.mall.product.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.ruoyi.mall.product.dto.MallSkuSpecItemRequest;

public final class MallSkuSpecKeyBuilder
{
    private MallSkuSpecKeyBuilder()
    {
    }

    public static String build(List<MallSkuSpecItemRequest> specs)
    {
        if (specs == null || specs.isEmpty())
        {
            return "";
        }
        List<MallSkuSpecItemRequest> sorted = new ArrayList<>(specs);
        sorted.sort(Comparator.comparing(MallSkuSpecItemRequest::getAttrId, Comparator.nullsLast(Long::compareTo)));
        StringBuilder sb = new StringBuilder();
        for (MallSkuSpecItemRequest item : sorted)
        {
            if (item.getAttrId() == null)
            {
                continue;
            }
            if (sb.length() > 0)
            {
                sb.append('|');
            }
            sb.append("attr:").append(item.getAttrId()).append('=');
            if (item.getOptionId() != null)
            {
                sb.append("option:").append(item.getOptionId());
            }
            else
            {
                sb.append("value:").append(Objects.toString(item.getValue(), "").trim());
            }
        }
        return sb.toString();
    }
}
