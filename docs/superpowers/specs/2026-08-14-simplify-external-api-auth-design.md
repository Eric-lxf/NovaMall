# 外部博客 API HTTP 访问设计

## 目标

生产环境允许通过公网 HTTP 调用外部博客 API。请求仍须经过指定的负载均衡或反向代理，再以 HTTP 转发到 NovaMall Nginx。

现有 Scope 权限模型保持不变，本次不调整客户端权限、Token 授权或业务路由权限。

## 已确认的安全取舍

公网 HTTP 会明文传输 Client Secret 和 Bearer Token。客户端到负载均衡链路上的监听者可能获取完整接口权限。该风险已明确并被接受。

保留以下安全边界：

- 客户端 ID 与 Secret 校验；
- 短期 opaque Bearer Token；
- 客户端启停和 Secret 轮换后的即时失效；
- 代理来源 IP 校验；
- 网关 IP 限流和后端客户端限流；
- 客户端只能读取自己创建的文章；
- 未声明的路径和 HTTP 方法继续拒绝。

## Scope 权限

### Token 签发

`POST /open-api/v1/oauth/token` 继续使用 HTTP Basic Client Credentials。

客户端可请求其已配置 Scope 的全部或子集。服务端继续拒绝未配置或不受支持的 Scope。

### 业务接口

现有权限映射保持不变：

| Scope | 能力 |
|---|---|
| `blog.article.create` | 创建草稿 |
| `blog.article.read.own` | 查询该客户端自己创建的文章 |
| `blog.taxonomy.read` | 查询分类和标签 |

客户端配置、Token 会话和 Spring Security 路由继续校验 Scope。现有客户端和已签发 Token 的权限行为不变。

## HTTP 与代理

- 删除后端 `require-https` 校验；
- 删除生产 Compose 中强制启用 HTTPS 的配置；
- 删除部署流程中 `BLOG_EXTERNAL_API_FORWARDED_PROTO=https` 的强制检查；
- 反向代理固定向后端传递 `X-Forwarded-Proto: http`；
- 保留 `BLOG_EXTERNAL_API_TRUSTED_PROXY_IP`，只有指定代理的直接连接可以进入开放接口；
- 后端与 Nginx 端口收口规则保持不变。

## 管理页面

外部 API 客户端页面保持不变，继续支持 Scope 选择和展示，以及客户端启停、限流、Token 有效期和 Secret 轮换。

## 兼容性

- 现有客户端记录和 Scope 配置无需迁移；
- Token 请求与响应结构不变；
- 已签发 Token 的 Scope 权限不变；
- 外部接口路径、请求体和业务响应不变。

## 错误处理

- 无效客户端凭证继续返回 `401 invalid_client`；
- 无效或过期 Token 继续返回 `401 invalid_token`；
- Scope 不合法继续返回 `400 invalid_scope`；
- Token 缺少路由所需 Scope 继续返回 `403 insufficient_scope`；
- 代理来源不匹配、限流、路径和方法错误保持现有行为。

## 验证

- 后端测试验证现有 Scope 签发和路由授权行为未改变；
- Compose 和部署工作流静态检查验证 HTTP 配置可用；
- Maven 构建验证后端模块编译通过。
