package com.ruoyi.blog.external.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BlogTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") int expiresIn,
        String scope)
{
}
