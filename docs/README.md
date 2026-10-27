# 项目文档导航

| 目录 | 只放什么 | 从哪里开始 |
| --- | --- | --- |
| [`current/`](./current/README.md) | 当前需求、设计、架构和实施计划；固定文件名，不累积旧版本状态 | [现行索引](./current/README.md) |
| [`decisions/`](./decisions/README.md) | 按日期保存业务范围、权限和关键规则的决策过程 | [决策索引](./decisions/README.md) |
| [`validation/`](./validation/README.md) | 带环境与日期的实际执行、审查和验收证据 | [验证索引](./validation/README.md) |
| [`archive/`](./archive/README.md) | 被替代的全文版本、候选稿、历史验证材料和已完成专项计划 | [归档索引](./archive/README.md) |
| `superpowers/specs/` | 已批准的专项设计与功能范围约束 | [社区管理员工账号与角色管理设计](./superpowers/specs/2026-10-26-社区管理员工账号与角色管理设计.md) |
| [`superpowers/plans/`](./superpowers/plans/README.md) | 实施步骤与任务状态；完成结论以验证记录为准 | [计划索引](./superpowers/plans/README.md) |

阅读顺序：先看[现行索引](./current/README.md)，当前推进状态看[项目实施计划](./current/项目计划.md)；员工角色与护工工单验收看[批次 1 数据入口与授权边界记录](./validation/批次1-数据入口与授权边界验收记录_2026-10-27.md)，工程入口与首次种子保护看[批次 1 工程入口记录](./validation/批次1-工程入口与首装数据保护验收记录_2026-10-27.md)。此前 HTTP/Maven 主链见[系统隔离联调记录](./validation/系统隔离联调记录_2026-10-26.md)，注销、管理端看板和旧浏览器验收见[演示闭环验收记录](./validation/演示闭环验收记录_2026-10-26.md)；调查某条规则如何形成时，再看决策或归档。设计目标、代码存在、构建通过和实际业务验收分别陈述。

维护时直接改固定路径的现行规范。关键业务决定新增带日期的决策记录并同步现行规范；一次运行写一份验证记录。旧全文只留在归档，不把不同日期的“已完成/未开始”段落接在现行正文后面。
