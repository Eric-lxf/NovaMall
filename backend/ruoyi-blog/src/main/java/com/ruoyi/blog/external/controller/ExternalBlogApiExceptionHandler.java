package com.ruoyi.blog.external.controller;

import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.ruoyi.blog.external.exception.BlogApiException;
import com.ruoyi.blog.external.security.BlogApiRequestContext;
import com.ruoyi.blog.external.vo.ExternalApiErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.ruoyi.blog.external.controller")
public class ExternalBlogApiExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(ExternalBlogApiExceptionHandler.class);

    @ExceptionHandler(BlogApiException.class)
    public ResponseEntity<ExternalApiErrorResponse> handleBlogApiException(BlogApiException e,
            HttpServletRequest request)
    {
        HttpHeaders headers = new HttpHeaders();
        if (e.getRetryAfterSeconds() != null)
        {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()));
        }
        return response(request, e.getStatus(), e.getErrorCode(), e.getMessage(), headers);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
            ConstraintViolationException.class, MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ExternalApiErrorResponse> handleInvalidRequest(Exception e, HttpServletRequest request)
    {
        String message = "Request validation failed";
        if (e instanceof MethodArgumentNotValidException validation && validation.getFieldError() != null)
        {
            message = validation.getFieldError().getDefaultMessage();
        }
        else if (e instanceof BindException bind && bind.getFieldError() != null)
        {
            message = bind.getFieldError().getDefaultMessage();
        }
        else if (e instanceof ConstraintViolationException violation && !violation.getConstraintViolations().isEmpty())
        {
            message = violation.getConstraintViolations().iterator().next().getMessage();
        }
        return response(request, HttpStatus.BAD_REQUEST, "invalid_request", message, new HttpHeaders());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ExternalApiErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e,
            HttpServletRequest request)
    {
        return response(request, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type",
                "Unsupported Content-Type", new HttpHeaders());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ExternalApiErrorResponse> handleDuplicate(DuplicateKeyException e,
            HttpServletRequest request)
    {
        return response(request, HttpStatus.CONFLICT, "resource_conflict",
                "The request conflicts with an existing resource", new HttpHeaders());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExternalApiErrorResponse> handleUnexpected(Exception e, HttpServletRequest request)
    {
        log.error("External blog API controller failed, requestId={}", BlogApiRequestContext.requestId(request), e);
        return response(request, HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                "An internal error occurred", new HttpHeaders());
    }

    private ResponseEntity<ExternalApiErrorResponse> response(HttpServletRequest request, HttpStatus status,
            String code, String message, HttpHeaders headers)
    {
        request.setAttribute(BlogApiRequestContext.ATTR_ERROR_CODE, code);
        if (request.getAttribute(BlogApiRequestContext.ATTR_AUTH_RESULT) == null)
        {
            request.setAttribute(BlogApiRequestContext.ATTR_AUTH_RESULT, "DENIED");
        }
        String requestId = BlogApiRequestContext.requestId(request);
        if (requestId != null)
        {
            headers.set(BlogApiRequestContext.REQUEST_ID_HEADER, requestId);
        }
        if (status == HttpStatus.UNAUTHORIZED && "invalid_client".equals(code))
        {
            headers.set(HttpHeaders.WWW_AUTHENTICATE,
                    "Basic realm=\"NovaMall External Blog API\", charset=\"UTF-8\"");
        }
        else if (status == HttpStatus.UNAUTHORIZED)
        {
            headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"" + code + "\"");
        }
        else if (status == HttpStatus.FORBIDDEN)
        {
            headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\"");
        }
        ExternalApiErrorResponse body = new ExternalApiErrorResponse(OffsetDateTime.now(), requestId,
                status.value(), code, code, message, request.getRequestURI());
        return new ResponseEntity<>(body, headers, status);
    }
}
