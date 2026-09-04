# space-agent-gateway 开发说明

本文件适用于整个 `space-agent-gateway` 仓库。今后 Agent Gateway 的开发从本仓开始，不回到原 `space-aiagent-v2` 仓库修改。

## 开始工作前

先阅读：

- [README：职责、技术基线和运行方式](README.md)
- [整体架构：边界与部署拓扑](https://github.com/agncao/space-aiagent-platform/blob/codex/cross-department-agent-v1/docs/跨部门多智能体架构实施说明.md#1-边界与部署拓扑)
- [整体架构：控制权归属](https://github.com/agncao/space-aiagent-platform/blob/codex/cross-department-agent-v1/docs/跨部门多智能体架构实施说明.md#2-控制权归属)
- [整体架构：真实数据流](https://github.com/agncao/space-aiagent-platform/blob/codex/cross-department-agent-v1/docs/跨部门多智能体架构实施说明.md#3-分析它的光照数据真实数据流)
- [整体架构：恢复和幂等](https://github.com/agncao/space-aiagent-platform/blob/codex/cross-department-agent-v1/docs/跨部门多智能体架构实施说明.md#4-恢复和幂等)

整体架构文档由 `space-aiagent-platform` 维护，是跨仓边界的唯一事实源；本仓不复制另一份方案。

## 本仓职责

- 注册 Agent Manifest，维护逻辑能力目录和版本状态。
- 验证并幂等受理 `TaskSubmission`，分配 `execution_id`。
- 在 MySQL 中持久化 TaskDelivery、TaskEvent、Action、Command 和 Outbox。
- 通过 Outbox、RocketMQ、Nacos 和 HTTP 完成 Worker 可靠投递。
- 校验事件序号、Worker 所有权及 effects/invalidates/actions 权限，并可靠回调对应 requester。
- 支持多个 `requester_id`，但严格隔离各发起方的任务与回调。

## 不可破坏的边界

- 本服务不是公司 Spring Cloud Gateway。前者是 Agent 控制面和可靠投递层，后者是产品流量入口。
- 不识别用户意图，不生成 Todo/Plan，不持有 WorkflowRun，不插入依赖步骤，也不判断跨步骤业务是否完成。
- RocketMQ 仅属于 Gateway 的内部可靠投递实现，不得泄漏为 Orchestrator 或 Worker 的调用前提。
- 不直接操作 Cesium，不持有 SSE；Action 必须按协议回调 Orchestrator。
- 不定义私有 wire DTO。`vendor/space-agent-contracts-*.jar` 来自 platform 的不可变 Contracts Release，并由 `vendor/SHA256SUMS` 校验。
- `idempotency_key`、`execution_id`、`event_id`、`(execution_id,event_seq)`、`(execution_id,action_id)` 的数据库唯一性是正确性约束，不得用应用层“先查后写”替代。
- Task 与 Outbox 必须在同一数据库事务中写入；接收方未确认前不能把消息标记为已投递。
- Worker 上报的 effects、invalidates 和 actions 必须落在已注册 Manifest 的允许集合内。

## 代码导航

| 关注点 | 位置 |
| --- | --- |
| HTTP 接口与错误映射 | `src/main/java/io/github/agncao/spaceagent/gateway/api/` |
| 用例编排与持久服务 | `src/main/java/io/github/agncao/spaceagent/gateway/application/` |
| 任务账本和策略 | `src/main/java/io/github/agncao/spaceagent/gateway/domain/task/`、`domain/policy/` |
| RocketMQ 与 Nacos/HTTP | `src/main/java/io/github/agncao/spaceagent/gateway/infrastructure/rocketmq/`、`infrastructure/nacos/` |
| Flyway schema | `src/main/resources/db/migration/` |
| dev/prod 配置 | `src/main/resources/application*.yml` |
| 契约 JAR 与校验和 | `vendor/` |

## 跨仓变更规则

若要修改协议字段、事实、错误码或 Manifest 结构，先在 `space-aiagent-platform/space-agent-contracts` 修改、测试并发布新版本，然后更新本仓 JAR、版本和 SHA256。禁止直接修改或反编译 vendored JAR。

若修改回调或 Action 流程，必须同时核对整体架构第 3 节，以及 Orchestrator/Worker 对应消费者；Gateway 仍只做校验、路由、投递、审计和对账。

## 验证

```bash
shasum -a 256 -c vendor/SHA256SUMS
mvn test
mvn verify
git diff --check
```

`mvn verify` 包含 Testcontainers 集成测试，需要可用的 Docker。开发运行方式和端口见 [README 的验证与运行章节](README.md#验证与运行)。

不得提交密码、Token、本地 Nacos/MySQL/RocketMQ 数据、IDE 私有状态或 `target/`。新增配置必须同时定义 `dev`/`prod` 行为，生产敏感值只从环境变量或公司 Secret 注入。

## 完成标准

- 重复提交、乱序/重复事件、非法 effect/action、投递重试和重启恢复具有测试证据。
- 事务边界、唯一约束和投递确认语义未被削弱。
- 公共协议版本及校验和已固定，相关跨仓升级顺序有说明。
- README、整体架构与实现一致。
- 在 `codex/` feature 分支开发；除非用户明确要求，不自动合并到 `main`。
