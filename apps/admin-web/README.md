# 社区管理后台（Web Admin）

Vue 3、TypeScript、Element Plus 与 ECharts 工作台，供社区管理员和网格员使用。

截至 2026-10-26，当前未提交工作区已接入真实 API。隔离临时库联调中，管理员和网格员均完成浏览器登录并读取服务端页面；预约派单、网格员履约照片提交、管理员审核结案的写入链由真实 HTTP 脚本验证。浏览器写入和失败分支不在本轮验收范围。详见[系统隔离联调记录](../../docs/validation/系统隔离联调记录_2026-10-26.md)。

## 常用命令

```powershell
cd apps/admin-web
npm ci
npm run type-check
npm run dev
npm run build
```

开发服务器默认使用 `http://localhost:5173/`，通过 Vite 代理访问本机 API。`npm run build` 在本轮退出码为 0；Vite 有单个 bundle 超过 500 kB 的提示。

## 代码入口

- `src/api/adminApi.ts`、`src/api/http.ts`：服务端接口与请求封装。
- `src/router/index.ts`、`src/layout/AdminLayout.vue`：登录守卫、角色路由与工作台布局。
- `src/views/`：档案、亲情与更正审批、告警与仿真、用药、工单、看板、审计页面。
- `src/mock/` 与 `src/components/AdminMockSwitcher.vue`：保留的历史原型代码；当前业务页面不以 Mock 状态作为服务端操作成功依据。

亲情关系冻结、解冻、解绑因缺少可校验的关系 ID/列表契约而暂时禁用；看板统计口径与社区隔离尚待专项验收。构建通过不等于所有页面业务验收通过。
