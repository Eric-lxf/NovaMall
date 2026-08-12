package com.ruoyi.blog.external.security;

import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Redis 中保存的短期 opaque token 会话；Redis key 只使用 token 的 SHA-256。 */
@Data
@NoArgsConstructor
public class BlogApiTokenSession implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long clientPk;
    private String clientId;
    private String clientName;
    private List<String> scopes;
    private Integer secretVersion;
    private Integer rateLimitPerMinute;
    private Long issuedAtEpochSecond;
    private Long expiresAtEpochSecond;
}
