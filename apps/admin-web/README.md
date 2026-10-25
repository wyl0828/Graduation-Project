# 社区管理后台

这里是 Vue 3 + TypeScript 社区工作台工程，基于 Vue 官方 `create-vue` 脚手架建立，已锁定 Element Plus 与 ECharts 依赖。当前只有入口占位页，没有社区业务页面或接口调用。

```powershell
cd apps/admin-web
npm ci
npm run dev
npm run build
```

构建输出位于被 Git 忽略的 `dist/`。准确版本以 `package-lock.json` 为准；后续页面按模块接入路由、状态和图表。
