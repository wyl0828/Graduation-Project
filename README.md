# 社区居家养老服务系统

## 从这里开始

- [项目文档导航](./docs/README.md)：现行规范、业务决策、验证记录与历史快照分别存放。
- [现行工作文档](./docs/current/README.md)：需求、总体设计、跨模块设计、代码架构和计划。
- [项目实施计划](./docs/current/项目计划.md)：当前推进顺序与完成门槛。
- [本地运行指南](./docs/current/运行指南.md)：建库、配置、启动 API 和两个前端。
- [D1 验收记录](./docs/validation/D1工程基座验收记录_2026-10-25.md)：本机与干净检出的实际命令、结果及适用边界。

## 当前进度

> **2026-10-27 本机共享工作区验证**：最终隔离临时 MySQL 的 Maven 全套 **176 项（172 通过、0 失败、0 错误、4 项按环境跳过）**，员工与角色、家庭角色范围、护工本人分配工单和既有业务 HTTP 矩阵全部通过。最终浏览器 smoke 覆盖管理员创建/自撤权、护工接单至上传提交、网格员访问员工管理路由被拦截；临时服务、夹具和数据目录已清理。详见[批次 1 数据入口与授权边界验收记录](./docs/validation/批次1-数据入口与授权边界验收记录_2026-10-27.md)。结论对应当前未提交工作区，不代表确定提交、干净检出、线上或真机结果。

1. **工程基座**：历史 D1 验收见[原记录](./docs/validation/D1工程基座验收记录_2026-10-25.md)。本轮隔离实例实测 Flyway V1–V3.3、合成种子、API 启动和跨角色 HTTP 主链；另一次隔离守卫验证了启动/测试/演示修复入口的缺失配置、错误目标拒绝，以及首次种子装载的回滚和并发保护，见[工程入口验收](./docs/validation/批次1-工程入口与首装数据保护验收记录_2026-10-27.md)。
2. **后端**：已完成社区范围员工账号/角色 API、审计和角色变更会话撤销；`CARE_WORKER` 仅能处理本人当前分配工单，乐观锁冲突不会再写入成功操作历史。Maven 176 项中 4 个 `AuthDatabaseIntegrationTest` 用例因 `AUTH_TEST_DB_URL` 环境门控跳过；共享幂等响应重放、并发竞态全覆盖及文件资产校验仍未全部完成。
3. **前端**：管理端增加“工作人员与角色”页面，护工登录进入本人分配工单工作台；最终浏览器 smoke 通过管理员建号/自撤权、护工履约和网格员路由拒绝。生产构建通过并有 JS chunk 体积提示；微信开发者工具及真机页面仍未测。
4. **待完成**：完整会话期限、失权、亲情状态、跨对象/社区权限矩阵和 4 个环境门控认证数据库用例仍需补验；无短信服务，手机号找回、子女注册及老人首次激活安全关闭；共享 `Idempotency-Key` 的原响应重放和跨重启持久化、全部文件证据资产权限、其他并发与审计完整性，以及使用中演示库的安全重置和数据库/文件联合备份恢复仍需继续验证与完善。看板首次接单响应时长尚未按 Q09-04/AT-37 实现，近 30 天/年度趋势后端也未实现，当前页面只展示近 7 天。不能据本轮主链通过宣称批次 1 或全模块交付。

## 本地工程

```powershell
./ops/windows/check-dev-env.ps1
./ops/windows/init-local.ps1
# 在本机 MySQL 创建 elderly_care_demo 和 elderly_app，再配置被忽略的 .env.local
./ops/windows/check-demo-db.ps1
# 已配置的本机演示库测试入口（会加载 .env.local）
./ops/windows/run-test.ps1
./ops/windows/start-api.ps1
# 启动完成 Flyway 迁移后核验
./ops/windows/check-demo-db.ps1 -RequireMigration
# 仅在目标库确认空白、应用写入静默时首次加载合成种子
./ops/windows/load-demo-seed.ps1
# 独立临时 MySQL + Flyway + seed + 全套测试 + 跨角色 HTTP 验收；不触碰当前演示库
./ops/windows/tests/Test-SystemHttpTempDb.ps1
# 工程入口配置/目标拒绝 + 首次种子事务与并发保护；使用并清理独立临时 MySQL
./ops/windows/tests/Test-DemoDbGuard.ps1
```

两个前端分别在 `apps/elder-family/`、`apps/admin-web/` 执行 `npm ci`；前者在被忽略的 `src/manifest.json` 中填写自己的 `mp-weixin.appid` 后用 `npm run build:mp-weixin`，后者用 `npm run build`。`init-local.ps1` 会从无凭据的模板创建该本机文件。微信开发者工具导入 `apps/elder-family/dist/build/mp-weixin/`。
