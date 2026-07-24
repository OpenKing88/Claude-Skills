# 多渠道隔离架构

## 适用前提

- **AGP 8.x / 9.x**(本 skill 以 AGP 9.1+ 实战验证;AGP 8 也能用,但 sourceSet 写法会更简单,不必 @Suppress("DEPRECATION"))
- **Kotlin 2.x**(因为 AGP 9 + Kotlin 2.x 把 `.kt` 源剥到独立的 `kotlin` sourceDirectorySet,有专门陷阱见后)
- **Gradle Version Catalog**(`gradle/libs.versions.toml`)
- **Application 模块**为 `com.android.application` plugin

## 整体方案

### Variant 矩阵

- **flavorDimension** = `"env"`(单一维度,3 个 flavor)
- **productFlavors**:
  - `devTest` — 测试域名 + 无 Google
  - `preProduct` — **生产域名** + 无 Google(上线前真域名走查)
  - `google` — 生产域名 + Firebase/Adjust/FCM/好评(完整上线包，可自定义名称)
- **buildTypes**:沿用 `debug` / `release`
- **启用的 variant**:`devTestDebug`、`devTestRelease`、`preProductRelease`、`googleRelease`
- **禁用的 variant**:`preProductDebug`、`googleDebug`(通过 `androidComponents.beforeVariants` 关闭,IDE Build Variants 面板可见但灰掉)

## Variant 矩阵

| Variant            | signing         | minify | shrinkResources | ENABLE_LOG | sourceSet 隔离  |
|--------------------|-----------------|--------|-----------------|------------|---------------|
| devTestDebug       | testRelease     | false  | false           | true       | `noGoogle/`   |
| devTestRelease     | testRelease     | true   | true            | false      | `noGoogle/`   |
| preProductRelease  | testRelease     | true   | true            | false      | `noGoogle/`   |
| googleRelease      | googleRelease   | true   | true            | false      | `google/`     |

> **devTestDebug 用测试签名但不混淆**:保留断点调试和符号信息。日常开发跑 devTestDebug 不会被 R8 拖慢。签名凭据通过 `secret()` 从 `gradle.properties` 读取，不硬编码在 build 文件中。

## sourceSet 隔离布局

- `src/main/java/.../google/`:**只放 facade 接口**(纯 Kotlin,绝不 import com.google.*/com.adjust.*)。文件名加 `<AppPrefix>` 前缀(如 `<AppPrefix>GoogleService.kt`)避免与其它模块冲突
- `src/noGoogle/java/.../google/<AppPrefix>GoogleServiceImpl.kt`:**devTest + preProduct 共享**的 No-Op 实现(单类同时实现所有 facade 接口)
- `src/google/java/.../google/<AppPrefix>GoogleServiceImpl.kt`:**只在 google flavor 编译**的真实 SDK 实现骨架(同名同包,与 noGoogle 编译期二选一)
- `src/google/java/.../google/<AppPrefix>PushService.kt`:FCM `FirebaseMessagingService` 子类
- `src/google/AndroidManifest.xml`:声明 FCM `<service>` 静态注册 + (AppsFlyer 时)归因 Receiver

> Android 单平台不能用 Kotlin `expect/actual`,「**同名同包类不同 sourceSet**」是 AGP 官方推荐的 flavor 二选一手法。

## 归因分析方案（双方案可选）

skill 默认使用 **Adjust** 归因。仅当用户明确提及「AppsFlyer」「appsflyer」「自定义归因」等关键词时，才切换为 AppsFlyer 方案。

| 方案 | 触发条件 | 归因 SDK |
|------|----------|----------|
| Adjust（默认） | 无特殊关键词 | `com.adjust.sdk:adjust-android` |
| AppsFlyer | 用户提及 appsflyer/AppsFlyer | `com.appsflyer:af-android-sdk` |

两种方案共享同一套 `AttributionService` 接口，`GoogleService` / `GoogleServiceImpl` 签名不变，仅内部委托的实现类不同。归因数据统一写入 DataStore `attributionDataKey`，上层无感。

**归因服务抽象（v1.2 新增）**

归因服务接口独立于 `GoogleService`，便于替换实现。`GoogleServiceImpl` 内部委托给 `AttributionService`。生命周期方法由 `GoogleService` 统一声明，`AttributionService` 不重复定义。

```kotlin
interface AttributionService {
    /**
     * 初始化归因 SDK。
     * @param onGoogleAdIdReady GAID 获取成功回调
     * @param onAttributionReady 首次归因结果回调
     *        attributionDeviceId: 归因工具设备 ID
     *          - Adjust: Adjust.getAdid() — 32 位 hex
     *          - AppsFlyer: AppsFlyerLib.getAppsFlyerUID()
     *        attributionData: 归因数据 JSON 字符串,可空
     *          - Adjust: null(不需要归因数据)
     *          - AppsFlyer: 归因字段 JSON
     */
    fun init(
        application: Application,
        onGoogleAdIdReady: (String) -> Unit,
        onAttributionReady: (attributionDeviceId: String, attributionData: String?) -> Unit,
    )
}
```

> **⚠️ skill 默认使用 Adjust 方案。仅当用户明确提及「AppsFlyer」「appsflyer」「自定义归因」等关键词时，才替换为 AppsFlyer 实现。**

## 依赖与 Plugin 隔离

- 依赖:Google SDK 全部用 `"googleImplementation"(...)` 注入(variant-aware configuration,只生效在 google flavor)
- Plugin:`com.google.gms.google-services` + `com.google.firebase.crashlytics` 在根 `build.gradle.kts` 用 `apply false` 进 classpath,在 `app/build.gradle.kts` 通过 `gradle.startParameter.taskNames` 是否含 `"Google"` 字符串**条件 apply**

## 目录结构

```
<repo>/
├── build.gradle.kts                          ← 根:plugin alias apply false
├── settings.gradle.kts                       ← 不动
├── gradle/libs.versions.toml                 ← Google alias 段
└── app/
    ├── build.gradle.kts                      ← flavor + sourceSet + 条件 apply + googleImplementation + signing
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml           ← 不动
        │   ├── java/.../<pkg>/
        │   │   ├── <App>.kt                  ← onCreate 调 GoogleServices.instance.init(...)
        │   │   ├── base/DataStore.kt         ← 新增 fcmTokenKey / googleAdIdKey
        │   │   └── google/                   ← facade 接口包
        │   │       ├── <AppPrefix>GoogleService.kt      ← 应用层接口(init + requestReview + lifecycle)
        │   │       └── <AppPrefix>GoogleServices.kt     ← 单例入口
        │   └── res/
        │
        ├── noGoogle/                         ← devTest + preProduct 共享
        │   └── java/.../<pkg>/google/
        │       └── <AppPrefix>GoogleServiceImpl.kt      ← No-Op 实现两个接口
        │
        ├── google/                           ← 仅 google flavor
        │   ├── AndroidManifest.xml           ← 注册 FCM <service>
        │   ├── google-services.json          ← 接入 Firebase 时放
        │   └── java/.../<pkg>/google/
        │       ├── <AppPrefix>GoogleServiceImpl.kt      ← 真实骨架(实现两个接口)
        │       └── <AppPrefix>PushService.kt       ← FCM Service
        │
        ├── devTest/                           ← AGP 默认 flavor 目录,通常不放文件
        ├── preProduct/                       ← 同上
        ├── debug/                            ← 现有
        └── release/                          ← 现有
```

## AGP sourceSet 合并规则

- `devTestDebug` ⇒ `main + devTest + noGoogle + debug + devTestDebug`
- `devTestRelease` ⇒ `main + devTest + noGoogle + release + devTestRelease`
- `preProductRelease` ⇒ `main + preProduct + noGoogle + release + preProductRelease`
- `googleRelease` ⇒ `main + google + release + googleRelease`

`GoogleServiceImpl` 同名同包(<AppPrefix>GoogleServiceImpl.kt 与 <AppPrefix>PushService.kt 同理)出现在 `noGoogle/` 与 `google/` 两份,但任何 variant 只合并其中一份,不冲突。

## 文件命名规范

所有 Google 接口文件使用 `<AppPrefix>` 前缀,如 `<AppPrefix>GoogleService.kt`。`<AppPrefix>` 替换为项目简称的驼峰形式(如 `Cbah`、`Wf`、`App` 等),避免不同模块的同名冲突,也方便一眼看出这些文件属于项目自有的 Google 封装层而非 SDK 代码。

## preProduct 渠道的定位与价值

**preProduct 渠道是整个矩阵中容易被忽视但实战价值很高的一个 flavor**,它的核心定义是:**生产域名 + 无 Google**。

### 为什么需要 preProduct

在典型的开发流程中,`devTest` 渠道连接测试域名(API 返回模拟数据或测试环境数据),而 `google` 渠道连接生产域名但引入了 Google SDK。两者之间缺少一个「在生产域名上验证功能,但不受 Google SDK 干扰」的环节——这就是 preProduct 的定位。

### 核心价值

1. **真域名、真数据验证**：preProduct 直接使用生产域名(Base URL、Web URL),连接真实后端 API,可以提前发现生产环境特有的问题(如协议差异、证书、重定向等),而不需要走到最终的 google 打包步骤。

2. **排除 Google SDK 变量**：当线上问题排查时,preProductRelease 包不含任何 Firebase / Adjust / FCM 字节码。如果 bug 在 preProduct 上能复现但 google 上不能(或反之),就可以快速定位问题是出在业务代码还是 Google 集成层——这是「对照组」思维。

3. **CI 预发布验证**：CI 可以在合并到主分支前跑 `:app:assemblePreProductRelease`,验证生产环境 API 连通性和签名配置,无需等待 Google 相关 plugin 执行,构建速度更快,失败归因更清晰。

4. **外部演示/内测**：给非技术团队成员做上线前演示时,preProductRelease 体积比 googleRelease 小(没有 Google SDK 依赖),安装快,且不会触发 Adjust 归因事件或 Firebase 崩溃上报,避免污染生产归因数据。

### 与 devTest / google 的对比

| 维度 | devTest | preProduct | google |
|------|-----|------------|--------|
| Base URL | 测试域名 | 生产域名 | 生产域名 |
| Google SDK | 无 | 无 | 完整(Firebase + Adjust + FCM) |
| minify + shrink | devTestDebug:否 / devTestRelease:是 | 是 | 是 |
| 主要用途 | 日常开发调试 | 上线前真域名走查 | 正式发布到 Google Play |
| 归因数据 | 不产生 | 不产生 | 写入生产归因系统 |

preProduct 是 devTest 到 google 之间的一道「安全检查门」,确保在正式发布前,业务代码在生产域名下能正常工作,且 Google SDK 的引入不会引入额外的问题变量。

## 不适用场景

- 单 flavor 项目(没有"测试/预演/生产"维度差异)
- 项目本身就在所有渠道都用 Google 服务,不需要隔离
- KMP(Kotlin Multiplatform)项目:用 `expect/actual` 更原生,不需要 sourceSet flavor 二选一
- AGP 7.x 或更早:本 skill 写法虽然多数兼容,但 sourceSet 可以用更老的 API(无 deprecation 警告);若沿用本写法没问题

## 附录：常用命令速查

```bash
# 日常开发联调(测试域名 + 日志开 + 不混淆 + 不含 Google + 测试签名)
./gradlew :app:assembleDevTestDebug

# 测试环境验证混淆/签名
./gradlew :app:assembleDevTestRelease

# 上线前真域名走查(生产域名 + 不含 Google)
./gradlew :app:assemblePreProductRelease

# 最终上线包(生产域名 + Firebase/Adjust/FCM/谷歌好评)
./gradlew :app:assembleGoogleRelease
./gradlew :app:bundleGoogleRelease       # AAB 包

# 单元测试(默认跑 devTestDebug)
./gradlew :app:test
```

## 版本更新日志

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| v1.0 | 初始版本 | 基础多渠道隔离方案：productFlavors 单一维度(dev/preProduct/google)、sourceSet 二选一(noGoogle/google)、variant-aware 依赖隔离、条件 plugin apply、FCM PushService、Google Review 好评接口、No-Op 实现占位。 |
| v1.1 | 命名规范 | 新增 `<AppPrefix>` 文件前缀规范，所有 Google facade 接口与实现文件统一使用项目简称驼峰前缀，避免多模块同名冲突。 |
| v1.2 | 归因抽象 | 新增 `AttributionService` 接口，将归因逻辑与 `GoogleService` 解耦；支持 Adjust(默认)与 AppsFlyer(备选)双方案；归因数据统一写入 DataStore `attributionDataKey`；配套 ProGuard 规则与版本目录依赖。 |
| v1.3 | 文档重构 | 交互式询问模式（渠道命名→归因方案→版本确认）；单体 SKILL.md 拆分为 skill.yaml + SKILL.md(dispatcher, ~100行) + 5 references + 16 templates；新增 Setup/Verify/Troubleshoot 三模式系统；占位符统一为 {{mustache}} 风格；模板代码修复 12 项（通知去重/Firebase ProGuard/Adjust 废弃 API 等）。 |
| v1.4 | 签名升级 | 双签名方案：`signingConfigs` 拆为 `testRelease` + `{{flavorName}}Release`；凭据通过 `secret()` 从 `gradle.properties` 读取而非硬编码；签名按 flavor 在 `beforeVariants` 中分发；移除 `test-debug.jks` keytool 生成步骤。 |
