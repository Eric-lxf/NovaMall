package com.ruoyi.blog.external.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class BlogApiException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String errorCode;
    private final Integer retryAfterSeconds;

    public BlogApiException(HttpStatus status, String errorCode, String message)
    {
        this(status, errorCode, message, null);
    }

    public BlogApiException(HttpStatus status, String errorCode, String message, Integer retryAfterSeconds)
    {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
