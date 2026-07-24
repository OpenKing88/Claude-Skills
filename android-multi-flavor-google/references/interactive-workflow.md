# 交互式搭建流程

## 概述
当 skill 以 Setup Mode 激活时，AI 必须按以下交互式询问流程执行。**先收集全部答案，再一次性批量生成所有文件**，不要在问题之间增量生成。

## 问题 1：生产渠道命名

**默认值**: `google`

**约束规则**:
- ✅ **仅生产渠道名可自定义**，`devTest` 和 `preProduct` 为固定名，不可自定义
- ✅ **noGoogle 强制自动对称同步**：当用户自定义正式渠道名为 `X`，`noGoogle` 自动变为 `noX`（首字母大写对称规则：`google`→`noGoogle`, `playStore`→`noPlayStore`）

**AI 询问模板**:
```
在开始搭建之前，我需要确认几个配置：

1️⃣ 正式渠道命名：包含 Google 服务的生产渠道叫什么名字？
   默认: google
   你可以输入自定义名称（如 playStore、production、release 等）
```
如果用户提供了名称则使用它，如果用户确认默认值或未输入则使用 `google`。

**命名转换规则** (for `{{flavorName}}` = user input, e.g., "playStore"):
| 占位符 | 转换 | 示例值 |
|--------|------|--------|
| `{{flavorName}}` | 原样（小写开头） | `playStore` |
| `{{FlavorName}}` | 首字母大写 | `PlayStore` |
| `{{noFlavorName}}` | `no` + 首字母大写 | `noPlayStore` |

**同步点矩阵** — 每个包含 flavor 名称的文件必须使用对应的占位符：
| 文件 | 使用占位符 | 示例 |
|------|-----------|------|
| build.gradle.kts (productFlavors) | `{{flavorName}}` | `create("playStore")` |
| build.gradle.kts (sourceSets) | `{{flavorName}}`, `{{noFlavorName}}` | `getByName("playStore")`, `src/noPlayStore/java` |
| build.gradle.kts (beforeVariants) | `{{flavorName}}` | `flavor == "playStore"` |
| build.gradle.kts (isGoogleBuild) | `{{FlavorName}}` | `taskName.contains("PlayStore")` |
| build.gradle.kts (dependencies) | `{{flavorName}}` | `"playStoreImplementation"` |
| AndroidManifest.google.xml (path comment) | `{{flavorName}}` | `src/playStore/AndroidManifest.xml` |

## 问题 2：归因方案选择

**默认值**: `Adjust`
**选项**: `Adjust` | `AppsFlyer`

**AI 询问模板**:
```
2️⃣ 归因分析方案：使用哪个归因 SDK？
   默认: Adjust（推荐）
   备选: AppsFlyer
```
如果用户在触发词中提及了 `AppsFlyer`、`appsflyer` 或 `自定义归因`，则将 AppsFlyer 作为首选/默认选项。

**决策影响表** — 每种方案对应的文件变化：
| 影响的文件 | Adjust 方案 | AppsFlyer 方案 |
|-----------|------------|---------------|
| libs.versions.toml | 启用 adjust-sdk, adjust-webbridge | 启用 appsflyer-sdk |
| build.gradle.kts (dependencies) | `libs.adjust.sdk`, `libs.adjust.webbridge` | `libs.appsflyer.sdk` |
| GoogleServiceImpl.real.kt | `attribution = {{appPrefix}}AdjustAttributionImpl()` | `attribution = {{appPrefix}}AppsFlyerAttributionImpl()` |
| 生成文件 | AdjustAttributionImpl.kt | AppsFlyerAttributionImpl.kt |
| AndroidManifest.google.xml | FCM service only, no receiver needed | FCM service + uncomment 2 receiver blocks |
| proguard-rules.pro | Keep rules for com.adjust.sdk.** | Keep rules for com.appsflyer.** |
| 共用依赖 | installreferrer + play-services-ads-identifier (both) | 同左 |

## 问题 3：依赖版本确认

**AI 询问模板**:
```
3️⃣ 依赖版本确认：以下是当前默认版本，是否需要调整？

| 依赖 | 当前版本 | 说明 |
|------|---------|------|
| Firebase BOM | 34.0.0 | 管理所有 Firebase SDK 版本 |
| Adjust SDK | 5.5.0 | Adjust 归因 Android SDK |
| AppsFlyer SDK | 6.15.0 | AppsFlyer 归因 Android SDK（仅 AppsFlyer 方案）|
| Google Play Review | 2.0.2 | 应用内好评 API |
| Play Services Ads ID | 18.0.1 | Google Advertising ID |
| Install Referrer | 2.2 | 安装来源检测 |
| GMS Google Services Plugin | 4.4.2 | google-services Gradle plugin |
| Firebase Crashlytics Plugin | 3.0.5 | crashlytics Gradle plugin |

输入"确认"使用默认版本，或告诉我需要修改哪些版本号。
```
用户可确认所有默认值，或逐个修改特定版本号。AI 存储最终版本号用于模板替换。

## 问题 4：生命周期管理

**默认值**: `不需要`
**选项**: `不需要` | `需要（手动控制）`

**AI 询问模板**:
```
4️⃣ 是否需要归因 SDK 手动处理 Activity 生命周期？

   默认: 不需要（Adjust 5.x+ 和 AppsFlyer 6.x+ 均通过 ContentProvider 自动管理生命周期）
   
   如果项目使用的 SDK 版本较旧，或需要显式调用 onResume/onPause，
   选择"需要"。这会在 GoogleService 接口和实现中生成对应的生命周期方法。
```
如果用户选择"需要"，设置 `lifecycle.manual = true`，生成时保留所有 `// BEGIN lifecycle.manual` / `// END lifecycle.manual` 块内的代码。
如果选择"不需要"（默认），删除这些条件块。

## 文件生成阶段

三个问题全部回答完毕后，AI 进入文件生成阶段：

### 3.1 收集用户回答
AI 内部记录如下结构：
```yaml
answers:
  flavorName: "google"  # 或用户提供的值
  attribution: "adjust"  # 或 "appsflyer"
  versions:
    firebaseBom: "34.0.0"
    adjustSdk: "5.5.0"
    appsflyer: "6.15.0"
    installReferrer: "2.2"
    playReview: "2.0.2"
    playServicesAdsId: "18.0.1"
    gmsGoogleServices: "4.4.2"
    firebaseCrashlyticsPlugin: "3.0.5"
```

### 3.2 占位符替换表
| 占位符 | 来源 | 示例值 |
|--------|------|--------|
| `{{appPrefix}}` | 从项目名推导（驼峰形式） | `Cbah` |
| `{{packageName}}` | 从项目 AndroidManifest 扫描 | `com.example.app` |
| `{{appClass}}` | 从项目 Application 类扫描 | `CbahApp` |
| `{{namespace}}` | 从 build.gradle.kts 扫描 | `com.example.app` |
| `{{devBaseUrl}}` | 从用户获取或项目现有配置 | `https://dev.example.com` |
| `{{prodBaseUrl}}` | 从用户获取或项目现有配置 | `https://api.example.com` |
| `{{devWebUrl}}` | 从用户获取 | `https://dev-h5.example.com` |
| `{{prodWebUrl}}` | 从用户获取 | `https://h5.example.com` |
| `{{flavorName}}` | 问题 1 回答 | `google` |
| `{{FlavorName}}` | `{{flavorName}}` 首字母大写 | `Google` |
| `{{noFlavorName}}` | `no` + `{{FlavorName}}` | `noGoogle` |
| `{{flavorNameUpper}}` | `{{flavorName}}` 全大写 | `GOOGLE` |
| `{{flavorName}}Release` | `{{flavorName}}` + `Release`（signingConfig 名） | `googleRelease` |
| `{{adjustToken}}` | Adjust App Token（让用户提供或标记 TODO） | `your-adjust-token` |
| `{{appsFlyerDevKey}}` | AppsFlyer Dev Key（让用户提供或标记 TODO） | `your-appsflyer-key` |
| `{{loggerImport}}` | 默认 `import android.util.Log` | `import android.util.Log` |
| `{{firebaseBom}}` | 问题 3 确认的版本 | `34.0.0` |
| (etc. for all version placeholders) | 问题 3 | |

### 3.3 归因条件块处理
对于包含归因相关内容的文件，使用注释分隔的标记块：
```
// BEGIN attribution.adjust
... Adjust 代码 ...
// END attribution.adjust
// BEGIN attribution.appsflyer
... AppsFlyer 代码 ...
// END attribution.appsflyer
```

生成 Adjust 方案时：保留 `// BEGIN attribution.adjust` 块，删除 `// BEGIN attribution.appsflyer` 块。
生成 AppsFlyer 方案时：保留 `// BEGIN attribution.appsflyer` 块，删除 `// BEGIN attribution.adjust` 块。

### 3.4 生成顺序
1. 配置文件: libs.versions.toml, build.gradle.kts (root + app), proguard-rules.pro
2. Facade 接口: GoogleService.kt, GoogleServices.kt, AttributionService.kt
3. 实现类: GoogleServiceImpl.noop.kt, GoogleServiceImpl.real.kt
4. 归因实现: AdjustAttributionImpl.kt 或 AppsFlyerAttributionImpl.kt（仅生成选中的那个）
5. 平台文件: PushService.kt, AndroidManifest.google.xml, Application.kt, DataStore.kt

### 3.5 生成后输出
AI 必须输出汇总信息：
- 生成文件数 + 文件列表
- Flavor 矩阵配置
- 归因方案选择
- 剩余手动步骤（下载 google-services.json、填入 SDK token 等）

## 生成后手动步骤
文件生成后，告知用户需要手动完成以下操作：
1. 从 Firebase Console 下载 `google-services.json` → 放入 `src/{{flavorName}}/` 目录
2. 替换 `{{adjustToken}}` 或 `{{appsFlyerDevKey}}` 为真实 SDK key
3. 在 `gradle.properties` 中配置 `TEST_*` 和 `GOOGLE_*` 签名凭据（参考 `references/verify.md`）
4. 将所有 `*.jks` 和 `*.keystore` 加入 `.gitignore`，确保签名凭据不入 git
5. 运行 `./gradlew :app:assemble{{FlavorName}}Release` 验证构建通过
