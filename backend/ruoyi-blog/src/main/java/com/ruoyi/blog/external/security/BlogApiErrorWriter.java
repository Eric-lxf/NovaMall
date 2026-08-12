package com.ruoyi.blog.external.security;

import java.io.IOException;
import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.blog.external.vo.ExternalApiErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BlogApiErrorWriter
{
    private final ObjectMapper objectMapper;

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
            String code, String message) throws IOException
    {
        if (response.isCommitted())
        {
            return;
        }
        request.setAttribute(BlogApiRequestContext.ATTR_ERROR_CODE, code);
        if (request.getAttribute(BlogApiRequestContext.ATTR_AUTH_RESULT) == null)
        {
            request.setAttribute(BlogApiRequestContext.ATTR_AUTH_RESULT, "DENIED");
        }
        response.setStatus(status.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (status == HttpStatus.UNAUTHORIZED && "invalid_client".equals(code))
        {
            response.setHeader("WWW-Authenticate", "Basic realm=\"NovaMall External Blog API\", charset=\"UTF-8\"");
        }
        else if (status == HttpStatus.UNAUTHORIZED)
        {
            response.setHeader("WWW-Authenticate", "Bearer error=\"" + code + "\"");
        }
        else if (status == HttpStatus.FORBIDDEN)
        {
            response.setHeader("WWW-Authenticate", "Bearer error=\"insufficient_scope\"");
        }
        String requestId = BlogApiRequestContext.requestId(request);
        if (requestId != null)
        {
            response.setHeader(BlogApiRequestContext.REQUEST_ID_HEADER, requestId);
        }
        ExternalApiErrorResponse body = new ExternalApiErrorResponse(
                OffsetDateTime.now(), requestId, status.value(), code, code, message,
                request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
