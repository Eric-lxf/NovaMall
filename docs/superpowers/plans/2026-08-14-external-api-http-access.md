# External API HTTP Access Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 允许生产环境通过受信反向代理以公网 HTTP 调用外部博客 API，同时完整保留现有 Scope 权限控制。

**Architecture:** 删除应用层 HTTPS 强制校验，并将 Nginx 转发协议固定为 `http`。受信代理 IP、Client Credentials、Bearer Token、Scope、限流和自有文章隔离保持不变；不引入数据库迁移。

**Tech Stack:** Java 17、Spring Boot 3 / Spring Security、JUnit 5、Mockito、Docker Compose、Nginx、GitHub Actions YAML。

## Global Constraints

- 生产公网 HTTP 会明文传输 Client Secret 和 Bearer Token；该风险已明确接受。
- 请求仍须经过 `BLOG_EXTERNAL_API_TRUSTED_PROXY_IP` 指定的负载均衡或反向代理。
- `blog.article.create`、`blog.article.read.own`、`blog.taxonomy.read` 的签发与路由校验保持不变。
- 不修改数据库 Schema，不迁移现有客户端，不改变 Token 请求与响应结构。
- 只修改 HTTPS 强制相关代码、部署配置和对应文档。

---

## File Map

- `backend/ruoyi-blog/src/main/java/com/ruoyi/blog/external/security/BlogApiAuthenticationFilter.java`：删除 HTTP 请求的拒绝逻辑。
- `backend/ruoyi-blog/src/main/java/com/ruoyi/blog/external/config/BlogExternalApiProperties.java`：删除不再使用的 `requireHttps` 配置。
- `backend/ruoyi-blog/src/test/java/com/ruoyi/blog/external/security/BlogApiAuthenticationFilterTest.java`：验证 HTTP Token 请求进入现有处理链。
- `backend/ruoyi-admin/src/main/resources/application.yml`：删除默认 HTTPS 开关。
- `backend/ruoyi-admin/src/main/resources/application-docker.yml`：删除 Docker HTTPS 开关绑定。
- `frontend/nginx.conf`：固定向后端传递 `X-Forwarded-Proto: http`。
- `frontend/Dockerfile`、`frontend/Dockerfile.release`：前端镜像默认 `BLOG_EXTERNAL_API_TRUSTED_PROXY_IP`，与 Nginx 模板一致。
- `docker-compose.yml`、`docker-compose.prod.yml`：删除 HTTPS 开关和可变转发协议。
- `.env.example`：只保留外部 API 开关、Schema 就绪状态和受信代理 IP。
- `.github/workflows/deploy-ecs.yml`：删除 HTTPS 协议变量、校验和落盘。
- `docs/blog-external-api.md`：将部署、调用示例及安全说明更新为 HTTP，并保留 Scope 文档。

### Task 1: 删除后端 HTTPS 强制校验

**Files:**
- Create: `backend/ruoyi-blog/src/test/java/com/ruoyi/blog/external/security/BlogApiAuthenticationFilterTest.java`
- Modify: `backend/ruoyi-blog/src/main/java/com/ruoyi/blog/external/security/BlogApiAuthenticationFilter.java:61-103`
- Modify: `backend/ruoyi-blog/src/main/java/com/ruoyi/blog/external/config/BlogExternalApiProperties.java:20-25`
- Modify: `backend/ruoyi-admin/src/main/resources/application.yml:163-169`
- Modify: `backend/ruoyi-admin/src/main/resources/application-docker.yml:38-42`

**Interfaces:**
- Consumes: `POST /open-api/v1/oauth/token`、`BlogExternalApiProperties.enabled`。
- Produces: HTTP 请求不再因协议返回 `426 https_required`；其余认证链签名不变。

- [ ] **Step 1: 写出会失败的 HTTP 回归测试**

创建测试，并临时显式启用现有 `requireHttps`，证明当前实现会阻止 HTTP：

```java
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
        properties.setRequireHttps(true);
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
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```bash
cd backend && mvn -B -pl ruoyi-blog -am -Dtest=BlogApiAuthenticationFilterTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL；`FilterChain#doFilter` 未被调用，响应由 `https_required` 分支处理。

- [ ] **Step 3: 删除过滤器中的 HTTPS 判断**

从 `doFilterInternal` 删除：

```java
if (properties.isRequireHttps() && !isHttps(request))
{
    throw new BlogApiException(HttpStatus.UPGRADE_REQUIRED, "https_required",
            "HTTPS is required for the external API");
}
```

同时删除整个 `isHttps(HttpServletRequest request)` 方法。保留 `StringUtils` import，因为 Bearer Token 校验仍使用它。

- [ ] **Step 4: 运行测试并确认通过**

Run:

```bash
cd backend && mvn -B -pl ruoyi-blog -am -Dtest=BlogApiAuthenticationFilterTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS，1 test completed。

- [ ] **Step 5: 删除不再使用的配置项并调整最终测试**

从 `BlogExternalApiProperties` 删除：

```java
/** Docker/生产环境要求入口已经完成 TLS 终止。 */
private boolean requireHttps = false;
```

从测试删除：

```java
properties.setRequireHttps(true);
```

从两个 Spring YAML 文件删除 `require-https`：

```yaml
require-https: ${BLOG_EXTERNAL_API_REQUIRE_HTTPS:false}
```

以及：

```yaml
require-https: false
```

- [ ] **Step 6: 再次运行测试与编译**

Run:

```bash
cd backend && mvn -B -pl ruoyi-blog -am -Dtest=BlogApiAuthenticationFilterTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS，且不存在 `requireHttps` 编译引用。

- [ ] **Step 7: 提交后端变更**

```bash
git add backend/ruoyi-blog/src/main/java/com/ruoyi/blog/external/security/BlogApiAuthenticationFilter.java \
  backend/ruoyi-blog/src/main/java/com/ruoyi/blog/external/config/BlogExternalApiProperties.java \
  backend/ruoyi-blog/src/test/java/com/ruoyi/blog/external/security/BlogApiAuthenticationFilterTest.java \
  backend/ruoyi-admin/src/main/resources/application.yml \
  backend/ruoyi-admin/src/main/resources/application-docker.yml
git commit -m "fix: allow external API requests over HTTP"
```

### Task 2: 固定生产代理使用 HTTP

**Files:**
- Modify: `frontend/nginx.conf:64-110`
- Modify: `docker-compose.yml:96-142`
- Modify: `docker-compose.prod.yml:9-64`
- Modify: `.env.example:16-24`
- Modify: `.github/workflows/deploy-ecs.yml:193-206,234-266,295-298`

**Interfaces:**
- Consumes: `BLOG_EXTERNAL_API_TRUSTED_PROXY_IP`。
- Produces: Nginx 固定发送 `X-Forwarded-Proto: http`；部署不再接收或校验协议变量。

- [ ] **Step 1: 固定 Nginx 转发协议**

将两个开放接口 location 中的：

```nginx
proxy_set_header X-Forwarded-Proto ${BLOG_EXTERNAL_API_FORWARDED_PROTO};
```

替换为：

```nginx
proxy_set_header X-Forwarded-Proto http;
```

不要修改通用 `/prod-api/` location 的 `$scheme`，它不属于开放接口专用配置。

- [ ] **Step 2: 删除 Compose 中的 HTTPS 配置**

从 `docker-compose.prod.yml` 后端环境删除：

```yaml
BLOG_EXTERNAL_API_REQUIRE_HTTPS: "true"
```

从 `docker-compose.yml` 后端环境删除：

```yaml
BLOG_EXTERNAL_API_REQUIRE_HTTPS: ${BLOG_EXTERNAL_API_REQUIRE_HTTPS:-false}
```

从两个 Compose 文件的前端环境删除：

```yaml
BLOG_EXTERNAL_API_FORWARDED_PROTO: ${BLOG_EXTERNAL_API_FORWARDED_PROTO:-http}
```

保留 `BLOG_EXTERNAL_API_TRUSTED_PROXY_IP`。

- [ ] **Step 3: 简化环境变量示例**

从 `.env.example` 删除：

```dotenv
# 本地 HTTP 调试保持 false；生产 Compose 固定为 true
BLOG_EXTERNAL_API_REQUIRE_HTTPS=false
BLOG_EXTERNAL_API_FORWARDED_PROTO=http
```

保留并改写代理注释：

```dotenv
# 外部 API 只接受该反向代理的直接连接；填写 LB/反向代理的固定私网地址
BLOG_EXTERNAL_API_TRUSTED_PROXY_IP=127.0.0.1
```

- [ ] **Step 4: 删除部署工作流中的协议变量和 HTTPS 门禁**

从工作流级 SSH 环境、`envs` 列表、脚本变量初始化和 `.env` 输出中删除所有 `BLOG_EXTERNAL_API_FORWARDED_PROTO`。

删除以下门禁：

```bash
if [ "$BLOG_EXTERNAL_API_FORWARDED_PROTO" != "https" ]; then
  echo "External API production traffic must terminate TLS before Nginx" >&2
  exit 1
fi
```

保留 `BLOG_EXTERNAL_API_ENABLED`、`BLOG_EXTERNAL_API_SCHEMA_READY` 和受信代理 IPv4 校验。

- [ ] **Step 5: 静态验证已删除的配置**

Run:

```bash
rg "BLOG_EXTERNAL_API_REQUIRE_HTTPS|BLOG_EXTERNAL_API_FORWARDED_PROTO|require-https|https_required" \
  .env.example docker-compose.yml docker-compose.prod.yml frontend/nginx.conf \
  .github/workflows/deploy-ecs.yml backend/ruoyi-admin/src/main/resources \
  backend/ruoyi-blog/src/main/java
```

Expected: no matches。

- [ ] **Step 6: 验证 Compose 配置可解析**

Run:

```bash
TOKEN_SECRET=test MYSQL_HOST=mysql MYSQL_PORT=3306 MYSQL_DATABASE=nova_mall MYSQL_USER=root \
MYSQL_PASSWORD=test REDIS_HOST=redis REDIS_PORT=6379 REDIS_PASSWORD=test \
NOVAMALL_OSS_ENABLED=false BACKEND_IMAGE=test/frontend FRONTEND_IMAGE=test/backend \
BACKEND_PORT=8080 FRONTEND_PORT=80 RUOYI_LOG_PATH=/tmp/ruoyi-logs \
docker compose -f docker-compose.prod.yml config >/tmp/novamall-compose-config.yml
docker compose -f docker-compose.yml config >/tmp/novamall-compose-dev-config.yml
```

Expected: 两条命令退出码均为 0。

- [ ] **Step 7: 提交部署配置变更**

```bash
git add frontend/nginx.conf docker-compose.yml docker-compose.prod.yml .env.example \
  .github/workflows/deploy-ecs.yml
git commit -m "chore: configure external API proxy for HTTP"
```

### Task 3: 更新外部 API 接入文档

**Files:**
- Modify: `docs/blog-external-api.md:57-68,91-104,185-194`

**Interfaces:**
- Consumes: Task 1 和 Task 2 的最终配置名称。
- Produces: 与实际 HTTP 部署方式一致的管理员步骤和调用示例。

- [ ] **Step 1: 更新管理员配置步骤**

将 TLS 代理步骤改为：

```markdown
7. 配置负载均衡或反向代理的固定私网地址 `BLOG_EXTERNAL_API_TRUSTED_PROXY_IP`。外部 API location 会按原始对端地址拒绝绕过该代理的直连请求。
```

删除 `BLOG_EXTERNAL_API_FORWARDED_PROTO=https` 说明。

- [ ] **Step 2: 更新 Token 调用示例**

将 PowerShell 示例 URI 改为：

```powershell
-Uri 'http://example.com/open-api/v1/oauth/token' `
```

Scope 请求、响应和三个权限表保持原样。

- [ ] **Step 3: 更新安全约束并明确风险**

用以下内容替换生产 TLS 约束：

```markdown
- 生产通过指定负载均衡/反向代理的 HTTP 入口开放 `/open-api/`，Nginx 按原始对端地址拒绝绕过代理的直连。公网 HTTP 会明文传输 Client Secret 和 Bearer Token，链路监听者可能获取完整接口权限。
```

其余后端端口收口、Request ID、日志、审计和保留期约束保持不变。

- [ ] **Step 4: 检查文档与配置一致性**

Run:

```bash
rg "BLOG_EXTERNAL_API_REQUIRE_HTTPS|BLOG_EXTERNAL_API_FORWARDED_PROTO|生产只通过受信 TLS|https://example.com/open-api" \
  docs/blog-external-api.md .env.example docker-compose.yml docker-compose.prod.yml \
  frontend/nginx.conf .github/workflows/deploy-ecs.yml
```

Expected: no matches。

Run:

```bash
rg "blog.article.create|blog.article.read.own|blog.taxonomy.read" \
  docs/blog-external-api.md \
  backend/ruoyi-blog/src/main/java/com/ruoyi/blog/external/constant/BlogApiScopes.java \
  backend/ruoyi-blog/src/main/java/com/ruoyi/blog/external/config/ExternalBlogSecurityConfig.java
```

Expected: 三个文件继续包含现有 Scope 定义或引用。

- [ ] **Step 5: 提交文档变更**

```bash
git add docs/blog-external-api.md
git commit -m "docs: describe external API HTTP deployment"
```

### Task 4: 全量验证

**Files:**
- Verify only; no planned code changes.

**Interfaces:**
- Consumes: Tasks 1-3 的全部提交。
- Produces: 可审查的构建、配置和范围证据。

- [ ] **Step 1: 运行后端模块测试**

Run:

```bash
cd backend && mvn -B -pl ruoyi-blog -am test
```

Expected: BUILD SUCCESS。

- [ ] **Step 2: 构建后端启动模块**

Run:

```bash
cd backend && mvn -B -DskipTests package -pl ruoyi-admin -am
```

Expected: BUILD SUCCESS。

- [ ] **Step 3: 执行最终范围检查**

Run:

```bash
git diff --check master...HEAD
git status --short
git diff --stat master...HEAD
```

Expected: `git diff --check` 无输出；工作区干净；差异仅包含设计、计划、后端 HTTPS 校验、部署 HTTP 配置、测试和外部 API 文档。

- [ ] **Step 4: 确认 Scope 实现未被修改**

Run:

```bash
git diff --exit-code master...HEAD -- \
  backend/ruoyi-blog/src/main/java/com/ruoyi/blog/external/constant/BlogApiScopes.java \
  backend/ruoyi-blog/src/main/java/com/ruoyi/blog/external/config/ExternalBlogSecurityConfig.java \
  backend/ruoyi-blog/src/main/java/com/ruoyi/blog/external/service/BlogApiClientAuthService.java \
  frontend/src/views/blog/external-api/client/index.vue
```

Expected: 退出码 0，无差异。
