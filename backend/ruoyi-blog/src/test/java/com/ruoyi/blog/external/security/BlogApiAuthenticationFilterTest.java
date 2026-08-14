package com.ruoyi.blog.external.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.ruoyi.blog.external.config.BlogExternalApiProperties;
import com.ruoyi.blog.external.service.BlogApiAuditService;
import com.ruoyi.blog.external.service.BlogApiClientAuthService;
import com.ruoyi.blog.external.service.BlogApiOpaqueTokenService;
import com.ruoyi.blog.external.service.BlogApiRateLimiter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class BlogApiAuthenticationFilterTest
{
    @Test
    void allowsHttpTokenRequest() throws Exception
    {
        BlogExternalApiProperties properties = new BlogExternalApiProperties();
        properties.setEnabled(true);
        BlogApiErrorWriter errorWriter = mock(BlogApiErrorWriter.class);
        BlogApiAuthenticationFilter filter = new BlogApiAuthenticationFilter(properties,
                mock(BlogApiOpaqueTokenService.class), mock(BlogApiClientAuthService.class),
                mock(BlogApiRateLimiter.class), errorWriter, mock(BlogApiAuditService.class));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/open-api/v1/oauth/token");
        request.setServletPath("/open-api/v1/oauth/token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(errorWriter, never()).write(any(HttpServletRequest.class), any(HttpServletResponse.class),
                any(), any(), any());
    }
}
