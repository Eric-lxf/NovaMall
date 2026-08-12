package com.ruoyi.blog.external.controller;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.blog.external.exception.BlogApiException;
import com.ruoyi.blog.external.security.BlogApiRequestContext;
import com.ruoyi.blog.external.service.BlogApiClientAuthService;
import com.ruoyi.blog.external.service.BlogApiClientAuthService.TokenGrant;
import com.ruoyi.blog.external.vo.BlogTokenResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/open-api/v1/oauth")
public class ExternalBlogTokenController
{
    private final BlogApiClientAuthService clientAuthService;

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BlogTokenResponse> token(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(name = "grant_type", required = false) String grantType,
            @RequestParam(name = "scope", required = false) String scope,
            HttpServletRequest request)
    {
        if (!"client_credentials".equals(grantType))
        {
            throw new BlogApiException(HttpStatus.BAD_REQUEST, "unsupported_grant_type",
                    "grant_type must be client_credentials");
        }
        ClientCredentials credentials = parseBasicCredentials(authorization);
        TokenGrant grant = clientAuthService.issueToken(credentials.clientId(), credentials.clientSecret(), scope,
                request);
        request.setAttribute(BlogApiRequestContext.ATTR_CLIENT_PK, grant.clientPk());
        request.setAttribute(BlogApiRequestContext.ATTR_SECRET_VERSION, grant.secretVersion());
        request.setAttribute(BlogApiRequestContext.ATTR_AUTH_RESULT, "TOKEN_ISSUED");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(grant.response());
    }

    private ClientCredentials parseBasicCredentials(String authorization)
    {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Basic "))
        {
            throw new BlogApiException(HttpStatus.UNAUTHORIZED, "invalid_client",
                    "HTTP Basic client credentials are required");
        }
        try
        {
            byte[] decoded = Base64.getDecoder().decode(authorization.substring(6).trim());
            String value = new String(decoded, StandardCharsets.ISO_8859_1);
            int separator = value.indexOf(':');
            if (separator <= 0 || separator == value.length() - 1)
            {
                throw new IllegalArgumentException("Malformed credentials");
            }
            return new ClientCredentials(value.substring(0, separator), value.substring(separator + 1));
        }
        catch (IllegalArgumentException e)
        {
            throw new BlogApiException(HttpStatus.UNAUTHORIZED, "invalid_client", "Invalid client credentials");
        }
    }

    private record ClientCredentials(String clientId, String clientSecret)
    {
    }
}
