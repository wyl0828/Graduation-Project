# 老人端与子女端

这里是老人和子女共用的 Uni-app + Vue 3 工程。骨架来自 DCloud 官方 Vue 3/Vite TypeScript 模板；实际版本以 `package-lock.json` 为准。当前只有入口占位页，没有登录、健康、用药、预约等业务实现。

```powershell
cd apps/elder-family
npm ci
npm run type-check
npm run dev:h5
npm run build:mp-weixin
```

微信小程序构建输出位于 `dist/build/mp-weixin/`，该目录被 Git 忽略。首期目标平台为微信小程序，微信 AppID 已写入 `src/manifest.json` 的 `mp-weixin.appid`；顶层 `appid` 是 Uni-app 自身的应用标识，仍为空。编译通过不等于微信开发者工具导入或真机验收。老人/子女双模式、页面、会话与请求边界见[现行小程序代码架构](../../docs/current/小程序代码架构.md)。
