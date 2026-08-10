# NovaMall 后端

Spring Boot 多模块后端（系统管理、博客/AI、微信运营、商城与历史学习）。启动模块：`ruoyi-admin`。

## 构建与启动

```bash
cd backend
mvn -B -DskipTests package -pl ruoyi-admin -am
# 启动前必须设置 TOKEN_SECRET；生产建议使用 `openssl rand -hex 64` 生成独立值
export TOKEN_SECRET="<random-secret>"
java -jar ruoyi-admin/target/ruoyi-admin.jar
```

首次初始化账号：`admin` / `admin123`。仅用于本地启动，登录后请立即修改。
