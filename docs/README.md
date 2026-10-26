# 项目文档导航

| 目录 | 只放什么 | 从哪里开始 |
| --- | --- | --- |
| [`current/`](./current/README.md) | 当前需求、设计、架构和实施计划；固定文件名，不累积旧版本状态 | [现行索引](./current/README.md) |
| [`decisions/`](./decisions/README.md) | 按日期保存业务范围、权限和关键规则的决策过程 | [决策索引](./decisions/README.md) |
| [`validation/`](./validation/README.md) | 带环境与日期的实际执行、审查和验收证据 | [验证索引](./validation/README.md) |
| [`archive/`](./archive/README.md) | 被替代的全文版本、候选稿和整理前快照 | [归档索引](./archive/README.md) |
| [`superpowers/plans/`](./superpowers/plans/README.md) | 当前仍在使用的修复任务清单 | [计划索引](./superpowers/plans/README.md) |

阅读顺序：先看[现行索引](./current/README.md)，HTTP/Maven 主链看[系统隔离联调记录](./validation/系统隔离联调记录_2026-10-26.md)，近期页面、注销和管理端看板验收看[演示闭环验收记录](./validation/演示闭环验收记录_2026-10-26.md)；调查某条规则如何形成时，再看决策或归档。设计目标、代码存在、构建通过和实际业务验收分别陈述。

维护时直接改固定路径的现行规范。关键业务决定新增带日期的决策记录并同步现行规范；一次运行写一份验证记录。旧全文只留在归档，不把不同日期的“已完成/未开始”段落接在现行正文后面。
