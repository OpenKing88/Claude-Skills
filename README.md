# Claude Dev Skills

为 [Claude Code](https://claude.ai/code) 准备的 Android 开发专用 Skills 集合，涵盖 Compose UI、多渠道构建、登录注册、KYC 流程、订单还款、Swagger 代码生成等常见业务场景。

## 仓库结构

```
claude-dev-skills/
├── README.md
├── install.sh                        # 一键安装到目标项目
├── android-compose-expert/           # Compose UI 开发专家
├── android-multi-flavor-google/      # 多渠道 + Google 服务隔离
├── android-viewsystem-foundations/   # ViewSystem 基础
├── kyc-workflow/                     # KYC 认证工作流
├── login-register-flow/              # 登录注册流程
├── order-repay-extension-flow/       # 订单还款 + 展期
├── project-bootstrap/                # 项目初始化向导
├── swagger-to-kotlin/                # Swagger → Kotlin 代码生成
└── test-case-audit/                  # 测试用例走查
```

## Skills 列表

### 1. android-compose-expert

Android Compose UI 开发专家，覆盖状态管理、视图组合、动画、导航、性能优化、设计稿转代码、生产环境崩溃排查等完整知识体系。

- **触发词**: Compose, @Composable, LazyColumn, Modifier, Material3, recomposition, 重构, refactor
- **体积**: ~2.6MB（含 AndroidX 源码引用）

### 2. android-multi-flavor-google

Android Gradle 多渠道 (productFlavors) 配置 + Google 服务 (Firebase/FCM/Play 好评) + 归因分析 (Adjust/AppsFlyer) 按渠道隔离方案。

- **版本**: v1.2.0
- **触发词**: productFlavors, buildVariant, 多渠道, flavor, Firebase, Adjust, AppsFlyer, FCM

### 3. android-viewsystem-foundations

Android ViewSystem 基础知识，覆盖传统 View 体系的开发模式。

- **版本**: v0.1.0

### 4. kyc-workflow

Android/Kotlin 项目通用 KYC 进件流程技能。

- **版本**: v4.0.0
- **模式**: Setup Mode（7问交互式问询 → 逐步骤展示流程 → 适配项目生成代码）+ Verify Mode（6步检查清单审计存量实现）
- **能力**: 标准5-6步KYC流程、字典三级缓存、表单缓存序列化安全、图片压缩OOM保护、人脸双渠道+降级、银行卡双模式、通用UI组件封装
- **触发词**: kyc, 进件流程, 身份认证, 人脸识别, 搭建KYC, 验证KYC, verify kyc

### 5. login-register-flow

手机号 + 验证码登录注册全流程自动搭建方案。自动检测项目技术栈（Compose/View、Retrofit/Ktor、DataStore/SharedPreferences）并生成完整页面 UI 和导航链路。

- **版本**: v1.1.0
- **触发词**: 登录, 注册, login, register, OTP, 验证码, 手机号登录, auth flow

### 6. order-repay-extension-flow

信贷 App 核心页面完整实现方案，涵盖订单详情页、还款详情页、展期页面。自动扫描项目网络层匹配已有接口定义，优先复用。

- **版本**: v1.0.0
- **触发词**: 订单详情, 还款详情, 展期, order detail, repayment, extension

### 7. project-bootstrap

新项目/模块初始化向导，自动串联完整开发流水线：API 生成 → 网络框架 → UI 技术栈选择 → 业务场景搭建。

- **注意**: 作为第一个激活的 skill，决定后续 skill 的调用顺序

### 8. swagger-to-kotlin

根据 Swagger/OpenAPI 文档自动生成 Kotlin Retrofit API 接口和请求/响应 Bean 类。支持 Swagger 2.0 和 OpenAPI 3.x，自动检测项目结构。

- **触发词**: 创建接口, 生成API, Swagger, OpenAPI, Retrofit, 网络层代码生成

### 9. test-case-audit

测试用例功能走查，对代码变更进行测试覆盖度审查。

- **版本**: v0.2.0

## 安装方式

### 方式一：一键安装（推荐）

```bash
git clone https://github.com/OpenKing88/Claude-Skills.git
cd Claude-Skills
./install.sh /path/to/your/android/project
```

### 方式二：手动安装

将需要的 skill 目录复制到目标项目的 `.claude/skills/` 下：

```bash
cp -r <skill-name> /path/to/your/project/.claude/skills/
```

## 依赖要求

- Claude Code CLI（最新版本）
- 目标项目需有 `.claude/` 目录

## 贡献

欢迎提交 Issue 和 PR 来改进这些 skills。

## License

MIT
