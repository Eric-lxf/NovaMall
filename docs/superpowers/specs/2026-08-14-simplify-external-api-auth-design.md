# 外部博客 API 鉴权简化设计

## 目标

将外部博客 API 从按 Scope 授权简化为仅校验客户端 Token。任何有效且未停用客户端签发的 Bearer Token，均可访问全部已开放接口。

生产环境允许公网 HTTP。请求仍须经过指定的负载均衡或反向代理，再以 HTTP 转发到 NovaMall Nginx。

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

## 鉴权行为

### Token 签发

`POST /open-api/v1/oauth/token` 继续使用 HTTP Basic Client Credentials。

`scope` 请求参数继续接受以兼容现有调用方，但服务端忽略其内容。Token 不再携带或依赖权限范围。

### 业务接口

分类、标签、草稿创建、自有文章列表和详情接口统一要求：

1. Bearer Token 存在且有效；
2. 客户端处于启用状态；
3. Token 的客户端和 Secret 版本仍匹配；
4. 请求未超过限流。

通过以上校验后即可访问全部开放接口，不再执行 Scope 权限判断。

### 现有客户端与 Token

- 数据库现有 `scopes` 字段和数据保留，但不再参与运行时鉴权；
- 不执行数据库迁移；
- 已签发且仍有效的 Token 立即获得全部开放接口权限；
- 管理接口不再要求调用方选择 Scope。

## HTTP 与代理

- 删除后端 `require-https` 校验；
- 删除生产 Compose 中强制启用 HTTPS 的配置；
- 删除部署流程中 `BLOG_EXTERNAL_API_FORWARDED_PROTO=https` 的强制检查；
- 反向代理固定向后端传递 `X-Forwarded-Proto: http`；
- 保留 `BLOG_EXTERNAL_API_TRUSTED_PROXY_IP`，只有指定代理的直接连接可以进入开放接口；
- 后端与 Nginx 端口收口规则保持不变。

## 管理页面

外部 API 客户端页面移除：

- 创建客户端时的 Scope 多选项；
- 客户端列表中的 Scope 展示。

继续保留客户端名称、状态、每分钟限额、Token 有效期和 Secret 轮换。

## 兼容性

- 旧调用方仍可提交 `scope`，请求不会失败；
- 现有客户端记录无需迁移；
- Token 响应保持现有 JSON 结构，`scope` 返回空字符串；
- 外部接口路径、请求体和业务响应不变。

## 错误处理

- 无效客户端凭证继续返回 `401 invalid_client`；
- 无效或过期 Token 继续返回 `401 invalid_token`；
- 不再产生 `403 insufficient_scope`；
- 代理来源不匹配、限流、路径和方法错误保持现有行为。

## 验证

- 后端测试验证请求 Scope 被忽略、无 Scope Token 可通过全部开放路由、旧 Token Scope 不影响校验；
- 前端生产构建验证管理页面删除 Scope 后可正常创建客户端；
- Compose 和部署工作流静态检查验证 HTTP 配置可用；
- Maven 构建验证后端模块编译通过。
