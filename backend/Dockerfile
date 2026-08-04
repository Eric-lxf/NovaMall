FROM crpi-skinyl3l0124ry6m.cn-beijing.personal.cr.aliyuncs.com/lxf_ai/maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY backend/pom.xml .
COPY backend/ruoyi-admin ./ruoyi-admin
COPY backend/ruoyi-framework ./ruoyi-framework
COPY backend/ruoyi-system ./ruoyi-system
COPY backend/ruoyi-common ./ruoyi-common
COPY backend/ruoyi-quartz ./ruoyi-quartz
COPY backend/ruoyi-generator ./ruoyi-generator
COPY backend/ruoyi-blog ./ruoyi-blog
COPY backend/ruoyi-wechat ./ruoyi-wechat
COPY backend/ruoyi-mall-product ./ruoyi-mall-product
COPY backend/ruoyi-mall-trade ./ruoyi-mall-trade
COPY backend/ruoyi-mall-payment ./ruoyi-mall-payment
COPY backend/ruoyi-history ./ruoyi-history
RUN mvn -B -DskipTests package -pl ruoyi-admin -am

FROM crpi-skinyl3l0124ry6m.cn-beijing.personal.cr.aliyuncs.com/lxf_ai/maven:3.9-eclipse-temurin-17
WORKDIR /app
RUN apt-get update && apt-get install -y wget \
    && groupadd -r app && useradd -r -g app app \
    && mkdir -p /data/uploads \
    && chown -R app:app /data/uploads /app
COPY --from=build /build/ruoyi-admin/target/ruoyi-admin.jar app.jar
USER app
EXPOSE 8080
ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms256m -Xmx768m -Duser.timezone=Asia/Shanghai"
ENV SPRING_PROFILES_ACTIVE=druid,docker
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
