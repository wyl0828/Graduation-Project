# 老人端与子女端 (Uni-app 小程序)

这里是老人和子女共用的 Uni-app + Vue 3 工程。骨架来自 DCloud 官方 Vue 3/Vite TypeScript 模板；实际版本以 `package-lock.json` 为准。

截至 2026-10-26，当前未提交工作区已将登录、本人档案、老人 SOS/服药、子女关系与预约等主要入口接入真实 API。隔离临时库上三条 HTTP 主链已通过，老人和子女的主要页面已在 H5 浏览器读取服务端记录；浏览器写入分支、微信开发者工具及真机尚未在本轮验收。详见[系统隔离联调记录](../../docs/validation/系统隔离联调记录_2026-10-26.md)。无短信服务期间，注册、首次激活和密码找回安全关闭。

## 常用命令

```powershell
cd apps/elder-family
npm ci
npm run type-check       # Vue 3 / TypeScript 类型检查
npm run dev:h5           # 启动本地 H5 实时开发服务 (可直观审查页面交互)
npm run build:h5         # H5 生产打包构建
npm run build:mp-weixin  # 微信小程序打包构建
```

## API 地址

H5 开发模式通过 Vite 将 `/api/v1` 代理到本机 `http://127.0.0.1:8080`。微信小程序构建前必须配置设备能够访问的 API 根地址，例如在本地环境文件设置 `VITE_API_BASE_URL=https://你的测试域名/api/v1`。未配置时小程序请求会明确报错；设备上的 `127.0.0.1` 不能指向开发电脑。API 所在域名及 HTTPS 配置还需在微信开发者工具和小程序平台核验。

前端类型检查或构建通过，只能证明产物可编译；真实账号、网络和业务流程以隔离数据库上的 HTTP 冒烟及后续真机验证为准。

## 页面与组件结构

```text
src/
├── uni.scss                    # 适老化 Design Tokens (暖橙 #EA580C, 医疗青 #0284C7, 护眼米白 #FAF8F5)
├── pages/
│   ├── auth/role-select.vue    # 当前会话授权角色切换
│   ├── auth/login.vue          # 统一登录
│   ├── auth/forgot-password.vue# 无核验渠道时不可用提示
│   ├── elder/activate.vue      # 无核验渠道时不可用提示
│   ├── elder/profile.vue       # 老人本人联系资料
│   ├── family/register.vue     # 无核验渠道时不可用提示
│   ├── family/bind-apply.vue   # 亲情绑定申请
│   ├── family/dashboard.vue    # 当前亲情关系列表与授权老人切换
│   └── family/correction-apply.vue # 档案更正申请
├── components/
│   ├── common/MockSwitcher.vue # 历史原型组件，真实业务链不应使用
│   ├── elder/ElderKeypad.vue   # 3×4 超大防误触半屏数字键盘 (>=60px单键, 触感振动反馈)
│   ├── elder/VoiceGuideBar.vue # 52px 适老语音引导大按钮
│   └── family/ElderContextSwitcher.vue # 顶部多长辈横向药丸切换栏
└── mock/mockState.ts           # 历史原型数据，不能作为真实业务状态
```

## 微信小程序开发者工具导入

微信小程序构建输出位于 `dist/build/mp-weixin/`，该目录被 Git 忽略，也是微信开发者工具的导入目录；不要选择本工程的源码根目录。首次检出后，复制 `src/manifest.example.json` 为 `src/manifest.json`，在后者的 `mp-weixin.appid` 填写自己的微信 AppID，再运行小程序构建。`src/manifest.json` 是本机配置，已被 Git 忽略；不要将 AppID 或 AppSecret 写入文档及其他已跟踪文件。
页面与适老化规范详见[现行小程序代码架构](../../docs/current/小程序代码架构.md)与[M01/M02原型与交互设计说明书](../../docs/current/原型与交互设计_M01_M02.md)。
