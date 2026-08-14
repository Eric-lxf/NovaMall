package com.ruoyi.blog.external.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ruoyi.blog.external.config.BlogExternalApiProperties;
import com.ruoyi.blog.external.constant.BlogApiScopes;
import com.ruoyi.blog.external.exception.BlogApiException;
import com.ruoyi.blog.external.service.BlogApiAuditService;
import com.ruoyi.blog.external.service.BlogApiClientAuthService;
import com.ruoyi.blog.external.service.BlogApiOpaqueTokenService;
import com.ruoyi.blog.external.service.BlogApiRateLimiter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BlogApiAuthenticationFilter extends OncePerRequestFilter
{
    private static final String TOKEN_PATH = "/open-api/v1/oauth/token";

    private final BlogExternalApiProperties properties;
    private final BlogApiOpaqueTokenService opaqueTokenService;
    private final BlogApiClientAuthService clientAuthService;
    private final BlogApiRateLimiter rateLimiter;
    private final BlogApiErrorWriter errorWriter;
    private final BlogApiAuditService auditService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)
    {
        return !request.getServletPath().startsWith("/open-api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        request.setAttribute(BlogApiRequestContext.ATTR_STARTED_NANOS, System.nanoTime());
        String requestId = BlogApiRequestContext.ensureRequestId(request);
        response.setHeader(BlogApiRequestContext.REQUEST_ID_HEADER, requestId);

        if (!properties.isEnabled())
        {
            errorWriter.write(request, response, HttpStatus.NOT_FOUND, "api_disabled", "Resource not found");
            return;
        }

        try
        {
            if (!TOKEN_PATH.equals(request.getServletPath()))
            {
                authenticateBusinessRequest(request);
            }
            chain.doFilter(request, response);
        }
        catch (BlogApiException e)
        {
            if (e.getRetryAfterSeconds() != null)
            {
                response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()));
            }
            errorWriter.write(request, response, e.getStatus(), e.getErrorCode(), e.getMessage());
        }
        catch (Exception e)
        {
            logger.error("External blog API request failed, requestId=" + requestId, e);
            errorWriter.write(request, response, HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                    "An internal error occurred");
        }
        finally
        {
            auditService.record(request, response);
        }
    }

    private void authenticateBusinessRequest(HttpServletRequest request)
    {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer "))
        {
            throw new BlogApiException(HttpStatus.UNAUTHORIZED, "invalid_token", "A Bearer token is required");
        }
        String rawToken = authorization.substring(7).trim();
        BlogApiTokenSession session = opaqueTokenService.resolve(rawToken);
        if (session == null || session.getClientPk() == null)
        {
            throw new BlogApiException(HttpStatus.UNAUTHORIZED, "invalid_token", "Token is invalid or expired");
        }
        int sessionRateLimit = session.getRateLimitPerMinute() == null
                ? properties.getDefaultRateLimitPerMinute() : session.getRateLimitPerMinute();
        // 先用 Redis session 中的客户端信息挡住超额流量，再查库完成停用/密钥轮换的即时吊销校验。
        // 按 client + secretVersion 聚合所有同版本 Token，既保护 DB，又让轮换后的新版本不受旧 Token 干扰。
        rateLimiter.check("client-version:" + session.getClientPk() + ":" + session.getSecretVersion(),
                Math.max(1, sessionRateLimit), 60);
        BlogApiPrincipal principal = clientAuthService.validateSession(session);
        if (principal == null)
        {
            throw new BlogApiException(HttpStatus.UNAUTHORIZED, "invalid_token", "Token is invalid or expired");
        }

        List<SimpleGrantedAuthority> authorities = principal.scopes().stream()
                .map(BlogApiScopes::authority)
                .map(SimpleGrantedAuthority::new)
                .toList();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute(BlogApiRequestContext.ATTR_CLIENT_PK, principal.clientPk());
        request.setAttribute(BlogApiRequestContext.ATTR_SECRET_VERSION, session.getSecretVersion());
        request.setAttribute(BlogApiRequestContext.ATTR_AUTH_RESULT, "AUTHENTICATED");
        rateLimiter.check("client:" + principal.clientPk(), principal.rateLimitPerMinute(), 60);
    }

}
