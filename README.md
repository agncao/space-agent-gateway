# space-agent-gateway

跨部门 Agent 控制面与可靠投递网关。它不是公司入口 Spring Cloud Gateway，也不生成 Todo/Plan；它负责逻辑 Worker 目录、协议验证、幂等受理、MySQL 事实表、Outbox、RocketMQ 投递、Nacos 服务发现及回调。

迁移来源设计基线：`space-aiagent` V2 commit `6844b77d606b1524b20b4da996ebd1ab7bc4369f`。本仓使用全新 Git 历史。

## 技术基线

- Java 21
- Spring Boot 3.5.0
- Spring Cloud 2025.0.0
- Spring Cloud Alibaba 2025.0.0.0
- RocketMQ 5.3.2、MySQL 8.0、Nacos 2.3.2

## 验证与运行

```bash
mvn test
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

开发默认端口 `8088`，依赖地址在 `application-dev.yml`；生产敏感配置使用 `MYSQL_PASSWORD`
等环境变量注入。数据库由 Flyway 自动迁移到独立 `space_agent_gateway` schema。

核心 API：`/api/v1/agents/*`、`/api/v1/tasks*`、`/api/v1/task-events`、`/api/v1/action-requests`。

## 契约依赖

Gateway 不定义自己的 wire DTO，直接编译和运行 `contracts-v0.1.0` 发布的 Java JAR。
在公司 Maven 制品库建立前，JAR 固定在 `vendor/`，`vendor/SHA256SUMS` 和 CI 防止静默替换；
上线内部制品库后只需将 Maven `systemPath` 换为普通版本依赖。
