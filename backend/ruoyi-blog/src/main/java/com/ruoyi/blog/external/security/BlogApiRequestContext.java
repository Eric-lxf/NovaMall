package com.ruoyi.blog.external.security;

import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

public final class BlogApiRequestContext
{
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    public static final String ATTR_REQUEST_ID = BlogApiRequestContext.class.getName() + ".requestId";
    public static final String ATTR_STARTED_NANOS = BlogApiRequestContext.class.getName() + ".startedNanos";
    public static final String ATTR_CLIENT_PK = BlogApiRequestContext.class.getName() + ".clientPk";
    public static final String ATTR_SECRET_VERSION = BlogApiRequestContext.class.getName() + ".secretVersion";
    public static final String ATTR_AUTH_RESULT = BlogApiRequestContext.class.getName() + ".authResult";
    public static final String ATTR_ERROR_CODE = BlogApiRequestContext.class.getName() + ".errorCode";
    public static final String ATTR_REQUEST_BODY_HASH = BlogApiRequestContext.class.getName() + ".bodyHash";
    public static final String ATTR_ARTICLE_ID = BlogApiRequestContext.class.getName() + ".articleId";

    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    private BlogApiRequestContext()
    {
    }

    public static String ensureRequestId(HttpServletRequest request)
    {
        Object current = request.getAttribute(ATTR_REQUEST_ID);
        if (current instanceof String value)
        {
            return value;
        }
        String supplied = request.getHeader(REQUEST_ID_HEADER);
        String requestId = supplied != null && REQUEST_ID_PATTERN.matcher(supplied).matches()
                ? supplied : UUID.randomUUID().toString().replace("-", "");
        request.setAttribute(ATTR_REQUEST_ID, requestId);
        return requestId;
    }

    public static String requestId(HttpServletRequest request)
    {
        Object value = request.getAttribute(ATTR_REQUEST_ID);
        return value instanceof String string ? string : null;
    }
}
