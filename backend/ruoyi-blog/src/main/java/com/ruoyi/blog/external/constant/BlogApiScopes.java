package com.ruoyi.blog.external.constant;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.util.StringUtils;

/** 外部博客 API 支持的最小权限集合。 */
public final class BlogApiScopes
{
    public static final String ARTICLE_CREATE = "blog.article.create";
    public static final String ARTICLE_READ_OWN = "blog.article.read.own";
    public static final String TAXONOMY_READ = "blog.taxonomy.read";

    public static final Set<String> SUPPORTED = Set.of(ARTICLE_CREATE, ARTICLE_READ_OWN, TAXONOMY_READ);
    public static final List<String> DEFAULT = List.of(ARTICLE_CREATE, ARTICLE_READ_OWN, TAXONOMY_READ);

    private BlogApiScopes()
    {
    }

    public static List<String> parse(String value)
    {
        if (!StringUtils.hasText(value))
        {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String item : value.split("[,\\s]+"))
        {
            if (StringUtils.hasText(item))
            {
                result.add(item.trim());
            }
        }
        return List.copyOf(result);
    }

    public static String serialize(List<String> scopes)
    {
        return String.join(",", scopes == null ? List.of() : scopes);
    }

    public static String authority(String scope)
    {
        return "SCOPE_" + scope;
    }
}
