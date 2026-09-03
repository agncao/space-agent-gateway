# 迁移来源

本仓从 `space-aiagent` V2 架构设计拆分而来，来源提交固定为：

`6844b77d606b1524b20b4da996ebd1ab7bc4369f`

本仓使用全新 Git 历史，不反向修改来源仓库。Agent Gateway 只负责注册、协议策略、任务可靠投递、回调和审计，不拥有 WorkflowRun，也不生成 Todo/Plan。
