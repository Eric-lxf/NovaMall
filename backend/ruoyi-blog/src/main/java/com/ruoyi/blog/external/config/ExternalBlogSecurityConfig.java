package com.ruoyi.blog.external.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.ruoyi.blog.external.constant.BlogApiScopes;
import com.ruoyi.blog.external.service.BlogApiAuditService;
import com.ruoyi.blog.external.service.BlogApiClientAuthService;
import com.ruoyi.blog.external.service.BlogApiOpaqueTokenService;
import com.ruoyi.blog.external.service.BlogApiRateLimiter;
import com.ruoyi.blog.external.security.BlogApiAuthenticationFilter;
import com.ruoyi.blog.external.security.BlogApiErrorWriter;

@Configuration
public class ExternalBlogSecurityConfig
{
    @Bean
    @Order(1)
    SecurityFilterChain externalBlogApiSecurityFilterChain(HttpSecurity http,
            BlogExternalApiProperties properties,
            BlogApiOpaqueTokenService opaqueTokenService,
            BlogApiClientAuthService clientAuthService,
            BlogApiRateLimiter rateLimiter,
            BlogApiErrorWriter errorWriter,
            BlogApiAuditService auditService) throws Exception
    {
        BlogApiAuthenticationFilter authenticationFilter = new BlogApiAuthenticationFilter(properties,
                opaqueTokenService, clientAuthService, rateLimiter, errorWriter, auditService);
        return http
                .securityMatcher("/open-api/v1/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .headers(headers -> headers.cacheControl(cache -> {}))
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, error) -> errorWriter.write(request, response,
                                HttpStatus.UNAUTHORIZED, "invalid_token", "Authentication is required"))
                        .accessDeniedHandler((request, response, error) -> errorWriter.write(request, response,
                                HttpStatus.FORBIDDEN, "insufficient_scope", "The token lacks the required scope")))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/open-api/v1/oauth/token").permitAll()
                        .requestMatchers(HttpMethod.GET, "/open-api/v1/blog/categories", "/open-api/v1/blog/tags")
                            .hasAuthority(BlogApiScopes.authority(BlogApiScopes.TAXONOMY_READ))
                        .requestMatchers(HttpMethod.POST, "/open-api/v1/blog/articles")
                            .hasAuthority(BlogApiScopes.authority(BlogApiScopes.ARTICLE_CREATE))
                        .requestMatchers(HttpMethod.GET, "/open-api/v1/blog/articles", "/open-api/v1/blog/articles/*")
                            .hasAuthority(BlogApiScopes.authority(BlogApiScopes.ARTICLE_READ_OWN))
                        .anyRequest().denyAll())
                .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
