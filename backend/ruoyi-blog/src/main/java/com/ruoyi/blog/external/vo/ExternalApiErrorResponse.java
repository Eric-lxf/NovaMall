package com.ruoyi.blog.external.vo;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExternalApiErrorResponse(OffsetDateTime timestamp, String requestId, int status, String error,
        String code, String message, String path)
{
}
