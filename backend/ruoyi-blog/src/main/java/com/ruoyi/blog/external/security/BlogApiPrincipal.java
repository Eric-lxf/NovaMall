package com.ruoyi.blog.external.security;

import java.io.Serializable;
import java.security.Principal;
import java.util.List;

/** 机器客户端身份，不与后台 {@code LoginUser/SysUser} 混用。 */
public record BlogApiPrincipal(Long clientPk, String clientId, String clientName, List<String> scopes,
        int rateLimitPerMinute) implements Principal, Serializable
{
    private static final long serialVersionUID = 1L;

    @Override
    public String getName()
    {
        return clientId;
    }
}
