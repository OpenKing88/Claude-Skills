---
name: "android-multi-flavor-google"
version: "1.2.0"
description: "Android Gradle 多渠道(productFlavors)配置 + Google 服务(Firebase、FCM、Google Play 好评)+ 归因分析(Adjust 默认/AppsFlyer 备选)按渠道隔离方案。v1.2 起归因逻辑抽象为 AttributionService 接口,支持 Adjust 与 AppsFlyer 双方案。搭建完成后会自动通过 keytool 创建测试签名文件(test-debug.jks)供 devTest/preProduct 渠道使用。当用户需要为 Android 项目搭建多个环境渠道(测试环境/生产域名预演/含 Google 服务的生产)、按 flavor 隔离 Google SDK 依赖、用 sourceSet 分离 Google 代码、按 task 名条件 apply google-services 与 firebase-crashlytics plugin、避免非 google 渠道引入任何 com.google.* 字节码时调用。触发关键词:productFlavors、buildVariant、build variant、多渠道、渠道隔离、flavor、Google 服务隔离、Firebase 按渠道、Adjust 集成、AppsFlyer 集成、FCM 按渠道、googleImplementation、sourceSet 隔离、按渠道打包、按渠道 apply plugin、按 flavor 配置依赖、google-services.json 隔离、按渠道 apply google plugin、归因分析、appsflyer、归因方案"
---

# Android 多渠道 + Google 服务按渠道隔离

为 Android 项目搭建 **一个维度 × 三个 flavor**(测试/预演/Google)的渠道矩阵,把 Firebase / 归因(Adjust 或 AppsFlyer) / FCM / Google Play 好评等 Google 服务**完全隔离在 google flavor 内**(其它 flavor 的 classpath 与 APK 字节码里零 Google 符号)。

## 适用前提

- **AGP 8.x / 9.x**(本 skill 以 AGP 9.1+ 实战验证;AGP 8 也能用,但 sourceSet 写法会更简单,不必 @Suppress("DEPRECATION"))
- **Kotlin 2.x**(因为 AGP 9 + Kotlin 2.x 把 `.kt` 源剥到独立的 `kotlin` sourceDirectorySet,有专门陷阱见后)
- **Gradle Version Catalog**(`gradle/libs.versions.toml`)
- **Application 模块**为 `com.android.application` plugin

## 整体方案

### Variant 矩阵

- **flavorDimension** = `"env"`(单一维度,3 个 flavor)
- **productFlavors**:
  - `dev`(或项目特定名,如 `devTest`)— 测试域名 + 无 Google
  - `preProduct` — **生产域名** + 无 Google(上线前真域名走查)
  - `google` — 生产域名 + Firebase/Adjust/FCM/好评(完整上线包)
- **buildTypes**:沿用 `debug` / `release`
- **启用的 variant**:`devDebug`、`devRelease`、`preProductRelease`、`googleRelease`
- **禁用的 variant**:`preProductDebug`、`googleDebug`(通过 `androidComponents.beforeVariants` 关闭,IDE Build Variants 面板可见但灰掉)

| Variant            | signing | minify | shrinkResources | ENABLE_LOG | sourceSet 隔离  |
|--------------------|---------|--------|-----------------|------------|---------------|
| devDebug           | release | false  | false           | true       | `noGoogle/`   |
| devRelease         | release | true   | true            | false      | `noGoogle/`   |
| preProductRelease  | release | true   | true            | false      | `noGoogle/`   |
| googleRelease      | release | true   | true            | false      | `google/`     |

> **devDebug 用正式签名但不混淆**:保留断点调试和符号信息。日常开发跑 devDebug 不会被 R8 拖慢。

### 源码隔离的 sourceSet 布局

- `src/main/java/.../google/`:**只放 facade 接口**(纯 Kotlin,绝不 import com.google.*/com.adjust.*)。文件名加 `<AppPrefix>` 前缀(如 `<AppPrefix>GoogleService.kt`)避免与其它模块冲突
- `src/noGoogle/java/.../google/<AppPrefix>GoogleServiceImpl.kt`:**dev + preProduct 共享**的 No-Op 实现(单类同时实现所有 facade 接口)
- `src/google/java/.../google/<AppPrefix>GoogleServiceImpl.kt`:**只在 google flavor 编译**的真实 SDK 实现骨架(同名同包,与 noGoogle 编译期二选一)
- `src/google/java/.../google/<AppPrefix>PushService.kt`:FCM `FirebaseMessagingService` 子类
- `src/google/AndroidManifest.xml`:声明 FCM `<service>` 静态注册 + (AppsFlyer 时)归因 Receiver

> Android 单平台不能用 Kotlin `expect/actual`,「**同名同包类不同 sourceSet**」是 AGP 官方推荐的 flavor 二选一手法。

### 归因分析方案（双方案可选）

skill 默认使用 **Adjust** 归因。仅当用户明确提及「AppsFlyer」「appsflyer」「自定义归因」等关键词时，才切换为 AppsFlyer 方案。

| 方案 | 触发条件 | 归因 SDK |
|------|----------|----------|
| Adjust（默认） | 无特殊关键词 | `com.adjust.sdk:adjust-android` |
| AppsFlyer | 用户提及 appsflyer/AppsFlyer | `com.appsflyer:af-android-sdk` |

两种方案共享同一套 `AttributionService` 接口，`GoogleService` / `GoogleServiceImpl` 签名不变，仅内部委托的实现类不同。归因数据统一写入 DataStore `attributionDataKey`，上层无感。

### 依赖与 plugin 隔离

- 依赖:Google SDK 全部用 `"googleImplementation"(...)` 注入(variant-aware configuration,只生效在 google flavor)
- Plugin:`com.google.gms.google-services` + `com.google.firebase.crashlytics` 在根 `build.gradle.kts` 用 `apply false` 进 classpath,在 `app/build.gradle.kts` 通过 `gradle.startParameter.taskNames` 是否含 `"Google"` 字符串**条件 apply**

---

## 目录结构

```
<repo>/
├── build.gradle.kts                          ← 根:plugin alias apply false
├── settings.gradle.kts                       ← 不动
├── gradle/libs.versions.toml                 ← Google alias 段
└── app/
    ├── build.gradle.kts                      ← flavor + sourceSet + 条件 apply + googleImplementation
    ├── release.jks                           ← 正式签名（手动管理）
    ├── test-debug.jks                        ← 测试签名（keytool 自动生成，供 devTest/preProduct 使用）
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml           ← 不动
        │   ├── java/.../<pkg>/
        │   │   ├── <App>.kt                  ← onCreate 调 GoogleServices.instance.init(...)
        │   │   ├── base/DataStore.kt         ← 新增 fcmTokenKey / googleAdIdKey
        │   │   └── google/                   ← facade 接口包
        │   │       ├── <AppPrefix>GoogleService.kt      ← 应用层接口(init + lifecycle)
        │   │       ├── <AppPrefix>GoogleReview.kt       ← 好评接口
        │   │       └── <AppPrefix>GoogleServices.kt     ← 单例入口
        │   └── res/
        │
        ├── noGoogle/                         ← dev + preProduct 共享
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
        ├── dev/                              ← AGP 默认 flavor 目录,通常不放文件
        ├── preProduct/                       ← 同上
        ├── debug/                            ← 现有
        └── release/                          ← 现有
```

**AGP sourceSet 合并规则**:

- `devDebug` ⇒ `main + dev + noGoogle + debug + devDebug`
- `devRelease` ⇒ `main + dev + noGoogle + release + devRelease`
- `preProductRelease` ⇒ `main + preProduct + noGoogle + release + preProductRelease`
- `googleRelease` ⇒ `main + google + release + googleRelease`

`GoogleServiceImpl` 同名同包(<AppPrefix>GoogleServiceImpl.kt 与 <AppPrefix>PushService.kt 同理)出现在 `noGoogle/` 与 `google/` 两份,但任何 variant 只合并其中一份,不冲突。

**文件命名规范(v1.1 新增):**

所有 Google 接口文件使用 `<AppPrefix>` 前缀,如 `<AppPrefix>GoogleService.kt`。`<AppPrefix>` 替换为项目简称的驼峰形式(如 `Cbah`、`Wf`、`App` 等),避免不同模块的同名冲突,也方便一眼看出这些文件属于项目自有的 Google 封装层而非 SDK 代码。

---

## 完整代码模板

### 1. `gradle/libs.versions.toml`

仅展示 Google 相关段(其他依赖 alias 按项目原状保留)。

```toml
[versions]
# ... 项目原有 versions
firebaseBom = "34.0.0"
adjustSdk = "5.5.0"
appsflyer = "6.15.0"                    # AppsFlyer 归因(备选方案,仅在用户提及 appsflyer 时启用)
installReferrer = "2.2"
playReview = "2.0.2"
playServicesAdsId = "18.0.1"
gmsGoogleServices = "4.4.2"
firebaseCrashlyticsPlugin = "3.0.5"

[libraries]
# ... 项目原有 libraries
# Google 服务库:仅 google flavor 通过 googleImplementation 引入,dev/preProduct 不会打进 APK
firebase-bom = { module = "com.google.firebase:firebase-bom", version.ref = "firebaseBom" }
firebase-analytics = { module = "com.google.firebase:firebase-analytics" }
firebase-crashlytics = { module = "com.google.firebase:firebase-crashlytics" }
firebase-crashlytics-ndk = { module = "com.google.firebase:firebase-crashlytics-ndk" }
firebase-messaging = { module = "com.google.firebase:firebase-messaging" }
# 归因方案 1:(默认)Adjust
adjust-sdk = { module = "com.adjust.sdk:adjust-android", version.ref = "adjustSdk" }
adjust-webbridge = { module = "com.adjust.sdk:adjust-android-webbridge", version.ref = "adjustSdk" }
# 归因方案 2:(备选)AppsFlyer — 仅在用户提及 appsflyer 时取消注释替换 Adjust
# appsflyer-sdk = { module = "com.appsflyer:af-android-sdk", version.ref = "appsflyer" }
installreferrer = { module = "com.android.installreferrer:installreferrer", version.ref = "installReferrer" }
play-review-ktx = { module = "com.google.android.play:review-ktx", version.ref = "playReview" }
play-services-ads-identifier = { module = "com.google.android.gms:play-services-ads-identifier", version.ref = "playServicesAdsId" }

[plugins]
# ... 项目原有 plugins
gms-google-services = { id = "com.google.gms.google-services", version.ref = "gmsGoogleServices" }
firebase-crashlytics = { id = "com.google.firebase.crashlytics", version.ref = "firebaseCrashlyticsPlugin" }
```

> 版本号建议跟项目最新需求对齐(Firebase BOM 自动管理 analytics/crashlytics/messaging/crashlytics-ndk 的版本)。

### 2. 根 `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    /** Google 服务 plugin:仅声明到 buildscript classpath,由 app/build.gradle.kts 按 google flavor 条件 apply */
    alias(libs.plugins.gms.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}
```

### 3. `app/build.gradle.kts`(核心代码段)

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // google-services / firebase-crashlytics plugin 由下方按 task name 条件 apply
}

android {
    namespace = "<your.namespace>"
    // ... compileSdk / defaultConfig / signingConfigs 按项目原状

    /** 唯一 flavor 维度:env */
    flavorDimensions += "env"

    productFlavors {
        create("dev") {
            dimension = "env"
            buildConfigField("String", "BASE_URL", "\"<测试域名>\"")
            buildConfigField("String", "WEB_URL", "\"<测试 H5 域名>\"")
            buildConfigField("String", "FLAVOR_ENV", "\"dev\"")
        }
        create("preProduct") {
            dimension = "env"
            /** 生产域名 + 不含 Google,用于上线前真域名走查 */
            buildConfigField("String", "BASE_URL", "\"<生产域名>\"")
            buildConfigField("String", "WEB_URL", "\"<生产 H5 域名>\"")
            buildConfigField("String", "FLAVOR_ENV", "\"preProduct\"")
        }
        create("google") {
            dimension = "env"
            /** 生产域名 + Firebase/Adjust/FCM/谷歌好评(完整上线包) */
            buildConfigField("String", "BASE_URL", "\"<生产域名>\"")
            buildConfigField("String", "WEB_URL", "\"<生产 H5 域名>\"")
            buildConfigField("String", "FLAVOR_ENV", "\"google\"")
        }
    }

    buildTypes {
        debug {
            /** devDebug 用正式签名但不混淆,保留断点调试与符号信息 */
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("Boolean", "ENABLE_LOG", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "ENABLE_LOG", "false")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    /** sourceSet 隔离:dev/preProduct 共享 src/noGoogle/;google 用 src/google/
     *  ⚠️ 关键陷阱:AGP 9 + Kotlin 2.x 把 .kt 源剥到独立的 kotlin sourceDirectorySet,
     *  必须同时在 kotlin.srcDirs 列出 noGoogle/java 路径(否则 Kotlin 编译器看不到)。
     *  srcDirs(varargs) 在 AGP 9 已 deprecate,等官方稳定的替代 API(directories mutable set)成熟再迁。 */
    @Suppress("DEPRECATION")
    sourceSets {
        getByName("dev") {
            java.srcDirs("src/dev/java", "src/noGoogle/java")
            kotlin.srcDirs("src/dev/java", "src/noGoogle/java")
            res.srcDirs("src/dev/res", "src/noGoogle/res")
        }
        getByName("preProduct") {
            java.srcDirs("src/preProduct/java", "src/noGoogle/java")
            kotlin.srcDirs("src/preProduct/java", "src/noGoogle/java")
            res.srcDirs("src/preProduct/res", "src/noGoogle/res")
        }
        getByName("google") {
            /** google flavor 默认就会扫 src/google/java 与 src/google/res,只显式声明 Manifest 路径 */
            manifest.srcFile("src/google/AndroidManifest.xml")
        }
    }
}

/** 关闭无意义的 variant:preProductDebug / googleDebug
 *  AGP 9 推荐用 androidComponents.beforeVariants,而非过时的 variantFilter */
androidComponents {
    beforeVariants(selector().withBuildType("debug")) { variant ->
        val flavor = variant.productFlavors.firstOrNull { it.first == "env" }?.second
        if (flavor == "preProduct" || flavor == "google") {
            variant.enable = false
        }
    }
}

/** Google plugin 按 task 名条件 apply
 *  - CLI 跑 `./gradlew :app:assembleGoogleRelease` 时 taskNames 含 "Google",apply 生效
 *  - IDE 显式打 google 包(Build APK / Generate Signed Bundle / Run)task 一定含 "Google",apply 生效
 *  - IDE Project Sync / 跑 dev/preProduct task 时不含 "Google",plugin 不 apply,
 *    dev/preProduct 编译不需要 google-services.json,避免缺资源报错
 *
 *  ⚠️ 风险场景:`./gradlew build` 或 `./gradlew assemble` 不指定 variant 时 taskNames 不含 "Google",
 *  google 渠道包会缺少 google-services 注入的资源,运行时 Firebase 初始化失败。
 *  打 google 包必须显式 `./gradlew :app:assembleGoogleRelease` / `bundleGoogleRelease`。
 *
 *  注意:BuildConfig 切换与本判断无关 —— AGP 为每个 variant 独立生成 BuildConfig.java,
 *  只要 variant 选对,BASE_URL/WEB_URL/FLAVOR_ENV/ENABLE_LOG 就一定切到对应 flavor。 */
val isGoogleBuild = gradle.startParameter.taskNames.any { taskName ->
    taskName.contains("Google", ignoreCase = true)
}
/** 控制台 print:每次 gradle 跑都能在日志开头看到 plugin apply 状态,便于排查 */
println("[App] taskNames=${gradle.startParameter.taskNames}, isGoogleBuild=$isGoogleBuild")
if (isGoogleBuild) {
    apply(plugin = libs.plugins.gms.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
}

dependencies {
    // ... 项目原有依赖(implementation 等)

    /** Google 依赖:只在 google flavor 打入 APK(dev/preProduct classpath 完全无 Google 字节码)
     *  variant-aware configuration 名必须用字符串字面量("google" + "Implementation" 拼接)
     *
     *  ═══════════════════════════════════════════════════════════════════
     *  归因方案依赖:默认 Adjust,用户提及 AppsFlyer 时替换为对应依赖
     *  ═══════════════════════════════════════════════════════════════════ */
    "googleImplementation"(platform(libs.firebase.bom))
    "googleImplementation"(libs.firebase.analytics)
    "googleImplementation"(libs.firebase.crashlytics)
    "googleImplementation"(libs.firebase.crashlytics.ndk)        // C/C++ 层崩溃捕获
    "googleImplementation"(libs.firebase.messaging)
    "googleImplementation"(libs.play.review.ktx)

    // [归因方案 1:默认] Adjust 归因
    "googleImplementation"(libs.adjust.sdk)
    "googleImplementation"(libs.adjust.webbridge)                // WebView 内归因事件桥接

    // [归因方案 2:备选] AppsFlyer 归因 — 仅在用户提及 appsflyer 时取消注释,同时注释掉上面 Adjust 依赖
    // "googleImplementation"(libs.appsflyer.sdk)

    // [共用] Install Referrer + Advertising ID,两个归因方案都需要
    "googleImplementation"(libs.installreferrer)
    "googleImplementation"(libs.play.services.ads.identifier)
}
```

---

## Google Facade 接口与实现

### `src/main/.../google/<AppPrefix>GoogleService.kt`(应用层接口)

```kotlin
package <pkg>.google

import android.app.Activity
import android.app.Application

/**
 * Google 应用层服务抽象(初始化 + Activity 生命周期)。
 * 由 Application.onCreate 调用,真实实现在 google flavor 完成 Adjust + Firebase 初始化。
 */
interface GoogleService {
    /**
     * Application.onCreate 调用一次。真实实现内部完成:
     *  1. Adjust 初始化(token + ENVIRONMENT_PRODUCTION + global callback 参数)
     *  2. Adjust.getGoogleAdId 异步回调 → [onGoogleAdIdReady]
     *  3. FirebaseApp.initializeApp(同时启用 Crashlytics 自动崩溃收集)
     *  4. 首次拉取 FCM token → [onFcmTokenReady]
     *  (后续 FCM Service.onNewToken 直接写 DataStore,不再回调)
     */
    fun init(
        application: Application,
        onGoogleAdIdReady: (String) -> Unit,
        onFcmTokenReady: (String) -> Unit,
    )
    /** Activity onResume 转发,真实实现调 Adjust.onResume() */
    fun onActivityResumed(activity: Activity)
    /** Activity onPause 转发,真实实现调 Adjust.onPause() */
    fun onActivityPaused(activity: Activity)
}
```

### `src/main/.../google/<AppPrefix>GoogleReview.kt`(好评接口)

```kotlin
package <pkg>.google

import android.app.Activity

/**
 * Google Play 应用内好评抽象。
 * dev/preProduct 直接 onComplete(false),google flavor 走 ReviewManagerFactory。
 */
interface GoogleReview {
    /**
     * @param onComplete success=true 表示弹窗已展示(无论是否打星);
     *                   false 表示因 Play Store 限流/非 Play 渠道安装等原因未展示。
     */
    fun requestReview(activity: Activity, onComplete: (success: Boolean) -> Unit)
}
```

### `src/main/.../google/<AppPrefix>GoogleServices.kt`(单例入口)

```kotlin
package <pkg>.google

/**
 * Google 服务入口。业务侧:
 *  - 应用层(Application、生命周期)用 [instance]
 *  - 好评启动用 [review]
 *  - 归因服务内部委托,不对外暴露
 *
 * [GoogleServiceImpl] 由 sourceSet 二选一提供(noGoogle/google),单类同时实现所有接口,
 * instance 与 review 指向同一实例,业务侧通过不同接口视图调用,职责清晰。
 * 归因逻辑封装在 [GoogleServiceImpl] 内部,通过 [AttributionService] 委托实现,
 * 归因数据写入 DataStore.attributionDataKey,网络层自动读取。
 */
object GoogleServices {
    private val impl: GoogleServiceImpl = GoogleServiceImpl()
    val instance: GoogleService = impl
    val review: GoogleReview = impl
}
```

### `src/main/.../google/<AppPrefix>AttributionService.kt`(归因服务抽象)

**⚠️ skill 默认使用 Adjust 方案。仅当用户明确提及「AppsFlyer」「appsflyer」「自定义归因」等关键词时，才替换为 AppsFlyer 实现。**

归因服务接口独立于 `GoogleService`，便于替换实现。`GoogleServiceImpl` 内部委托给 `AttributionService`，`GoogleService.init()` 签名不变。

```kotlin
package <pkg>.google

import android.app.Activity
import android.app.Application

/**
 * 归因服务抽象(运行在 google flavor)。
 * [init] 初始化归因 SDK,归因数据回调给上层写入 DataStore。
 */
interface AttributionService {
    /**
     * 初始化归因 SDK。
     * @param onGoogleAdIdReady GAID 获取成功回调(Adjust 内部获取 / AppsFlyer 手动获取)
     * @param onAttributionReady 首次归因结果回调(Map: af_status, media_source, campaign 等)
     */
    fun init(
        application: Application,
        onGoogleAdIdReady: (String) -> Unit,
        onAttributionReady: (Map<String, String>) -> Unit,
    )
    fun onActivityResumed(activity: Activity)
    fun onActivityPaused(activity: Activity)
}
```

### `src/noGoogle/.../google/<AppPrefix>GoogleServiceImpl.kt`(No-Op)

```kotlin
package <pkg>.google

import android.app.Activity
import android.app.Application
import <pkg>.tools.Alog   // 项目自己的日志工具

/**
 * dev / preProduct flavor 使用的 No-Op 实现。
 * 全部空实现,APK 内不会出现 com.google.* / com.adjust.* / com.appsflyer.* 任何符号。
 * 关键:本类与 src/google/.../GoogleServiceImpl 同名同包,编译期由 AGP 二选一。
 */
internal class GoogleServiceImpl : GoogleService, GoogleReview, AttributionService {
    override fun init(
        application: Application,
        onGoogleAdIdReady: (String) -> Unit,
        onFcmTokenReady: (String) -> Unit,
    ) {
        Alog.i("GoogleService", "[noop] init (dev/preProduct flavor)")
    }
    override fun onActivityResumed(activity: Activity) { /* no-op */ }
    override fun onActivityPaused(activity: Activity) { /* no-op */ }
    override fun requestReview(activity: Activity, onComplete: (Boolean) -> Unit) {
        Alog.i("GoogleReview", "[noop] requestReview")
        onComplete(false)
    }
    // AttributionService no-op
    override fun init(
        application: Application,
        onGoogleAdIdReady: (String) -> Unit,
        onAttributionReady: (Map<String, String>) -> Unit,
    ) { /* no-op */ }
}
```

### `src/google/.../google/<AppPrefix>GoogleServiceImpl.kt`(真实实现)

归因逻辑委托给 `AttributionService` 实现。**默认使用 `AdjustAttributionImpl`**，用户提及 AppsFlyer 时替换为 `AppsFlyerAttributionImpl`。

```kotlin
package <pkg>.google

import android.app.Activity
import android.app.Application
import <pkg>.BuildConfig
import <pkg>.base.attributionDataKey
import <pkg>.base.putValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * google flavor 使用的真实实现。
 *
 * 所有 SDK 调用直接写入可运行代码,不再注释。
 * 归因逻辑委托给 [AttributionService] 实现,通过构造函数注入,
 * 默认 [AdjustAttributionImpl],替换为 [AppsFlyerAttributionImpl] 仅需改一行 new。
 * 本类与 src/noGoogle/.../<AppPrefix>GoogleServiceImpl 同名同包,编译期由 AGP 二选一。
 */
internal class <AppPrefix>GoogleServiceImpl : GoogleService, GoogleReview {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 归因服务实现:默认 Adjust,替换为 AppsFlyerAttributionImpl() 即可切换 */
    private val attribution: AttributionService = <AppPrefix>AdjustAttributionImpl()

    override fun init(
        application: Application,
        onGoogleAdIdReady: (String) -> Unit,
        onFcmTokenReady: (String) -> Unit,
    ) {
        // ─── 归因初始化(Adjust 或 AppsFlyer,委托给 AttributionService) ──
        attribution.init(
            application,
            onGoogleAdIdReady = onGoogleAdIdReady,
            onAttributionReady = { data ->
                scope.launch { attributionDataKey.putValue(data.toJson()) }
            },
        )

        // ─── Firebase 初始化(同时启用 Crashlytics 自动崩溃收集) ──────────
        com.google.firebase.FirebaseApp.initializeApp(application)

        // ─── FCM token 首次拉取 ────────────────────────────────────────
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> token?.let(onFcmTokenReady) }
    }

    override fun onActivityResumed(activity: Activity) {
        attribution.onActivityResumed(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        attribution.onActivityPaused(activity)
    }

    override fun requestReview(activity: Activity, onComplete: (Boolean) -> Unit) {
        val manager = com.google.android.play.core.review.ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { req ->
            if (req.isSuccessful) {
                manager.launchReviewFlow(activity, req.result).addOnCompleteListener { onComplete(true) }
            } else onComplete(false)
        }
    }
}
```

### `src/google/.../google/<AppPrefix>AdjustAttributionImpl.kt`(默认方案)

Skill 默认的 Adjust 归因实现。`GoogleServiceImpl` 通过 `AttributionService` 接口委托调用。
若切换到 AppsFlyer,本文件不再需要,由 `AppsFlyerAttributionImpl` 替换。

```kotlin
package <pkg>.google

import android.app.Activity
import android.app.Application
import <pkg>.BuildConfig
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustConfig

/**
 * Adjust 归因实现(默认)。
 * 初始化 Adjust SDK → 获取 GAID → 回调 onGoogleAdIdReady / onAttributionReady。
 */
internal class <AppPrefix>AdjustAttributionImpl : AttributionService {

    override fun init(
        application: Application,
        onGoogleAdIdReady: (String) -> Unit,
        onAttributionReady: (Map<String, String>) -> Unit,
    ) {
        val config = AdjustConfig(
            application,
            "<adjust app token>",
            AdjustConfig.ENVIRONMENT_PRODUCTION
        )
        Adjust.addGlobalCallbackParameter(
            "custom_app_version", BuildConfig.VERSION_CODE.toString()
        )
        // 可选:添加自定义设备标识到归因回调参数
        // Adjust.addGlobalCallbackParameter("custom_device_id", deviceId)

        Adjust.initSdk(config)

        // 获取 Google Advertising ID,回调给上层写 DataStore
        Adjust.getGoogleAdId(application) { adId ->
            val data = mutableMapOf<String, String>()
            adId?.let { data["googleAdId"] = it; onGoogleAdIdReady(it) }
            data["attribution_sdk"] = "adjust"
            onAttributionReady(data)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        Adjust.onResume()
    }

    override fun onActivityPaused(activity: Activity) {
        Adjust.onPause()
    }
}
```

### `src/google/.../google/<AppPrefix>AppsFlyerAttributionImpl.kt`(备选方案)

**仅在用户明确提及「AppsFlyer」「appsflyer」「自定义归因」时使用。**
使用时将 `GoogleServiceImpl` 中的 `attribution` 字段 new 改为本类。

```kotlin
package <pkg>.google

import android.app.Activity
import android.app.Application
import android.util.Log
import <pkg>.BuildConfig
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * AppsFlyer 归因实现(备选)。
 * 初始化 AppsFlyer SDK → 手动获取 GAID → ConversionDataListener 回调归因数据。
 */
internal class <AppPrefix>AppsFlyerAttributionImpl : AttributionService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "AppsFlyerAttribution"
    }

    override fun init(
        application: Application,
        onGoogleAdIdReady: (String) -> Unit,
        onAttributionReady: (Map<String, String>) -> Unit,
    ) {
        // 1. 手动获取 Google Advertising ID(AppFlyer 不封装此逻辑)
        scope.launch {
            try {
                val adInfo = AdvertisingIdClient.getAdvertisingIdInfo(application)
                onGoogleAdIdReady(adInfo.id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get GAID: ${e.message}")
            }
        }

        // 2. 设置自定义归因参数(AppFlyer 用 setAdditionalData)
        AppsFlyerLib.getInstance().setAdditionalData(
            mapOf(
                "custom_app_version" to BuildConfig.VERSION_CODE.toString()
            )
        )

        // 3. 初始化 SDK 并注册归因回调
        AppsFlyerLib.getInstance().init(
            "<appsFlyer dev key>",
            object : AppsFlyerConversionListener {
                override fun onConversionDataSuccess(data: MutableMap<String, Any>) {
                    val result = data.mapValues { it.value.toString() }.toMutableMap()
                    result["attribution_sdk"] = "appsflyer"
                    onAttributionReady(result)
                }
                override fun onConversionDataFail(error: String) {
                    Log.w(TAG, "onConversionDataFail: $error")
                    onAttributionReady(mapOf("attribution_sdk" to "appsflyer", "error" to error))
                }
                override fun onAppOpenAttribution(data: MutableMap<String, String>?) {
                    // 深度链接归因数据,可选上报
                    data?.let { Log.i(TAG, "onAppOpenAttribution: $it") }
                }
                override fun onAttributionFailure(error: String) {
                    Log.w(TAG, "onAttributionFailure: $error")
                }
            },
            application
        )

        // 4. 启动 SDK 归因
        AppsFlyerLib.getInstance().start(application)
    }

    override fun onActivityResumed(activity: Activity) {
        // AppsFlyer SDK 6.x 通过 ContentProvider 自动注入生命周期,
        // 手动 start()/stop() 可选。此处显式调用以保证日志一致。
        AppsFlyerLib.getInstance().start(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        AppsFlyerLib.getInstance().stop()
    }
}
```

### `src/google/.../google/<AppPrefix>PushService.kt`(FCM Service)

```kotlin
package <pkg>.google

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import <pkg>.R
import <pkg>.base.fcmTokenKey
import <pkg>.base.putValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * FCM 推送 Service。仅在 google flavor 编译进 APK,由 src/google/AndroidManifest.xml 静态注册。
 *
 * 继承 [com.google.firebase.messaging.FirebaseMessagingService],直接响应 FCM MESSAGING_EVENT。
 * Token 处理:onNewToken 直接写 DataStore.fcmTokenKey,业务侧从 DataStore 订阅。
 * 通知图标:setSmallIcon(R.mipmap.ic_launcher),后续设计交付专用 notification icon 后替换。
 */
class <AppPrefix>PushService : com.google.firebase.messaging.FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: com.google.firebase.messaging.RemoteMessage) {
        message.notification?.let {
            runCatching { showNotification(it.title, it.body) }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch { fcmTokenKey.putValue(token) }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun showNotification(title: String?, body: String?) {
        val intent = Intent().apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) }
        val pendingFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        } else {
            PendingIntent.FLAG_ONE_SHOT
        }
        val channelId = "App"
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingFlag)
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title ?: "")
            .setContentText(body ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body ?: ""))
            .setAutoCancel(true)
            .setSound(soundUri)
            .setContentIntent(pendingIntent)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        manager.notify(0, notification)
    }
}
```

### `src/google/AndroidManifest.xml`(注册 FCM + 归因 Receiver)

**⚠️ 默认使用 Adjust 归因时不需要额外的 `<receiver>` 注册。仅在替换为 AppsFlyer 方案时需在 `<application>` 内添加 AppsFlyer 广播接收器。**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- FCM 通知权限(Android 13+ 必须显式声明) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application>
        <!--
            FCM Service 静态注册。
            intent-filter 已启用,Service 直接响应 com.google.firebase.MESSAGING_EVENT。
        -->
        <service
            android:name="<pkg>.google.<AppPrefix>PushService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>

        <!--
            [仅 AppsFlyer 方案需要] 安装归因广播接收器。
            默认 Adjust 方案无需添加,Adjust 通过 InstallReferrerClient 自动获取。
            AppsFlyer 替换时取消注释以下两个 receiver。
        -->
        <!-- AppsFlyer:单一源安装归因 -->
        <!--
        <receiver
            android:name="com.appsflyer.SingleInstallBroadcastReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="com.android.vending.INSTALL_REFERRER" />
            </intent-filter>
        </receiver>
        -->
        <!-- AppsFlyer:多源安装归因(Samsung/Xiaomi/Huawei 等厂商商店) -->
        <!--
        <receiver
            android:name="com.appsflyer.MultipleInstallBroadcastReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="com.android.vending.INSTALL_REFERRER" />
            </intent-filter>
        </receiver>
        -->
    </application>
</manifest>
```

> Adjust SDK 通过 `InstallReferrerClient` 自行处理安装归因,无需 Manifest 静态注册 Receiver。AppsFlyer 依赖广播接收器接收系统安装归因广播。

---

## Application 集成

`src/main/.../<App>.kt`:

```kotlin
package <pkg>

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import <pkg>.base.fcmTokenKey
import <pkg>.base.googleAdIdKey
import <pkg>.base.putValue
import <pkg>.google.GoogleServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class <App> : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        context = this

        /** Google 服务初始化:dev/preProduct 是 No-Op,google 才连接真实 SDK */
        GoogleServices.instance.init(
            application = this,
            onGoogleAdIdReady = { adId -> appScope.launch { googleAdIdKey.putValue(adId) } },
            onFcmTokenReady = { token -> appScope.launch { fcmTokenKey.putValue(token) } },
        )

        /** Activity 生命周期转发(主要用于 Adjust.onResume/onPause)+ 当前 Activity 弱引用 */
        registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
    }

    /** 当前可见 Activity 弱引用(避免内存泄漏) */
    var currentActivity: WeakReference<Activity>? = null
        private set

    private val activityLifecycleCallbacks = object : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            currentActivity = WeakReference(activity)
        }
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityResumed(activity: Activity) {
            currentActivity = WeakReference(activity)
            GoogleServices.instance.onActivityResumed(activity)
        }
        override fun onActivityPaused(activity: Activity) {
            GoogleServices.instance.onActivityPaused(activity)
        }
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    companion object {
        lateinit var context: <App> private set
        val appContext: Context get() = context.applicationContext
    }
}
```

---

## DataStore 新增字段

`src/main/.../base/DataStore.kt`(末尾追加):

```kotlin
/** FCM 推送 token(由 FCM Service.onNewToken 写入,登录时上传给服务端) */
val fcmTokenKey = stringPreferencesKey("FcmToken")

/** Google Advertising ID(由归因 SDK 回调写入,网络层 header 用) */
val googleAdIdKey = stringPreferencesKey("GoogleAdId")

/** 归因数据 JSON(由 AttributionService 首次回调写入,包含 media_source / campaign / af_status 等) */
val attributionDataKey = stringPreferencesKey("AttributionData")
```

---

## ProGuard 规则

`app/proguard-rules.pro` 末尾追加(按实际归因方案启用对应规则):

```proguard
# ══════════════════════════════════════════════════════════
# 归因方案 1:(默认)Adjust — 默认启用
# ══════════════════════════════════════════════════════════
-keep public class com.adjust.sdk.** { *; }
-keep class com.adjust.sdk.** { *; }
-keep class com.google.android.gms.ads.identifier.** { *; }

# ══════════════════════════════════════════════════════════
# 归因方案 2:(备选)AppsFlyer — 仅在用户提及 appsflyer 时取消注释替换上面 Adjust 规则
# ══════════════════════════════════════════════════════════
# -keep class com.appsflyer.** { *; }
# -dontwarn com.appsflyer.**
# -keep class com.google.android.gms.ads.identifier.** { *; }
```

> Install Referrer 和 Advertising ID 的 keep rules 两种方案都需要,故共用部分已在 Adjust 规则中包含。

---

## 关键陷阱(实战踩过)

### 1. AGP 9 + Kotlin 2.x:必须同时配 `kotlin.srcDirs`

AGP 9 + Kotlin 2.x 把 `.kt` 源剥到独立的 `kotlin` sourceDirectorySet,**只配 `java.srcDirs` 不够**,Kotlin 编译器会找不到 `noGoogle/java/` 下的 .kt 文件,报 `Unresolved reference 'GoogleServiceImpl'`。

正确写法:`java.srcDirs(...)` 和 `kotlin.srcDirs(...)` 都列出 noGoogle 目录(参考"完整代码模板"section 3)。

### 2. `srcDirs(vararg)` 在 AGP 9 deprecated,但仍是 source of truth

AGP 9 提示 deprecation 警告说 "Use `directories` mutable set instead",但实测 `java.directories.add(...)` **不影响 kotlin 编译器输入**(它只是 SourceDirectorySet 的部分内部 field,kotlin compile 还是读 srcDirs)。

权宜方案:`@Suppress("DEPRECATION")` 包住 sourceSets 块,继续用 var-arg `srcDirs(...)`,等 AGP 推出稳定的替代 API 再迁。

### 3. Plugin 按 task name 条件 apply 的边界

`gradle.startParameter.taskNames.any { it.contains("Google", ignoreCase = true) }` 判断:
- ✅ IDE Build APK / Generate Signed Bundle / Run 真机 + 选 googleRelease → 触发的 task `:app:assembleGoogleRelease` 含 "Google",plugin apply
- ✅ CLI `./gradlew :app:assembleGoogleRelease` 含 "Google",plugin apply
- ❌ `./gradlew build` / `./gradlew assemble`(不指定 variant)taskNames 不含 "Google",plugin 不 apply,google 包会缺少 google-services 资源
- ⚠️ IDE Project Sync 阶段 taskNames 一般不含 "Google",plugin 不 apply。这**只影响 IDE 红线**(不影响打包),如果想消红线可以临时把 `if (isGoogleBuild)` 改成 `true` 跑一次 sync

**口诀:打 google 渠道包,命令一定要带 `Google` 字样**(`assembleGoogleRelease` / `bundleGoogleRelease` / `installGoogleRelease`)。

### 4. BuildConfig 与 plugin apply 完全独立

`buildConfigField` 是 AGP **配置阶段**为每个 variant 单独生成独立的 `BuildConfig.java`,跟 plugin 是否 apply 无关。只要 variant 选对,BASE_URL/WEB_URL/FLAVOR_ENV/ENABLE_LOG 就一定切到对应 flavor,不会和其它 flavor 串。

### 5. 第三方 AGP plugin 兼容性

AGP 9 删除了 `com.android.build.gradle.AppExtension` / `BaseExtension` / `LibraryExtension`,改用 `com.android.build.api.dsl.ApplicationExtension` 等。许多第三方 Gradle plugin(Sonar、AboutLibraries、Sentry、Hilt 老版本、`io.github.OpenKing88.verify` 等)apply 时报:
```
Extension of type 'AppExtension' does not exist
```
**解决**:升级到兼容 AGP 9 的 plugin 版本,或者临时降级 AGP 到 8.x,或者去掉这个 plugin。

### 6. flavor 名 vs sourceSet 路径

flavor 命名如果改了(比如 `dev` → `devTest`),记得同步:
- `sourceSets.getByName("devTest") { java.srcDirs(...) }` 里的 sourceSet name
- `productFlavors { create("devTest") { ... } }` 里的 flavor name
- `androidComponents.beforeVariants` 里的 flavor 判断字符串
- 物理目录 `src/<flavor>/...`(如果有自定义的话)

sourceSets 中 srcDirs 的路径(`src/dev/java` vs `src/devTest/java`)可以不存在 — AGP 只是声明 sourceSet 包含该目录,不存在就不扫描,**不会报错**。建议保持 flavor 名与目录名一致以减少混乱。

### 7. Adjust SDK 5.x vs 4.x 的 onResume/onPause

Adjust 5.x 内部自动注册 ActivityLifecycleCallbacks,`Adjust.onResume()`/`Adjust.onPause()` 已废弃。但 facade 接口形状不变,真实实现里这两个方法可以 no-op。

### 8. Adjust + Firebase 同时依赖 play-services-ads-identifier

不要忘了显式加 `play-services-ads-identifier`,否则 Adjust 获取 googleAdId 会失败(只会拿到 null)。

### 9. Firebase 通知图标

`setSmallIcon(R.mipmap.ic_launcher)` 在某些 Android 版本下被系统强制转为纯白方块。设计交付专用 notification icon 后替换为 `R.drawable.ic_notification`(透明背景白色单色)。

---

## 创建测试签名文件

渠道隔离搭建完成后，需为 devTest / preProduct 渠道创建一个测试签名文件供调试使用。在项目根目录执行以下命令：

```bash
keytool -genkeypair -v \
  -keystore app/test-debug.jks \
  -alias testdebug \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -storepass android \
  -keypass android \
  -dname "CN=Test Debug, OU=Dev, O=Test, L=Lima, ST=Lima, C=PE"
```

| 参数 | 值 | 说明 |
|------|-----|------|
| 文件路径 | `app/test-debug.jks` | 与 `release.jks` 同级 |
| alias | `testdebug` | 测试签名别名 |
| 密码 | `android` | store 和 key 密码相同 |
| 密钥算法 | RSA 2048 | 与正式签名一致 |
| 有效期 | 10000 天 | 约 27 年，测试环境无需更换 |
| 签发者 | CN=Test Debug, O=Test, C=PE | 便于与正式签名区分 |

> 此签名仅用于 devTest / preProduct 内部测试，**绝不用于 Google Play 上架**。建议将 `app/test-debug.jks` 加入 `.gitignore` 避免提交到仓库。

---

## 验证步骤

### 1. 三个 variant 都能编译

```bash
./gradlew :app:compileDevDebugKotlin
./gradlew :app:compileDevReleaseKotlin
./gradlew :app:compilePreProductReleaseKotlin
./gradlew :app:compileGoogleReleaseKotlin
```

预期:全部 `BUILD SUCCESSFUL`。

### 2. Plugin 按 task 名条件 apply 工作

```bash
# A) 非 google task:plugin 不应 apply
./gradlew :app:tasks --warning-mode none 2>&1 | grep "isGoogleBuild"
# 预期输出:isGoogleBuild=false

# B) google task --dry-run:plugin 应注册自己的 task
./gradlew :app:assembleGoogleRelease --dry-run 2>&1 | grep "isGoogleBuild"
# 预期输出:isGoogleBuild=true
# 同时 task graph 中应出现 :app:processGoogleReleaseGoogleServices
```

### 3. classpath 隔离契约

```bash
# google flavor 应含全套 Google 依赖
./gradlew :app:dependencies --configuration googleReleaseRuntimeClasspath 2>&1 \
    | grep -E "firebase|adjust|installreferrer|play-services-ads-identifier|com\.google\.android\.play:review"
# 预期:看到 firebase-bom / analytics / messaging / crashlytics / adjust-android / installreferrer / review-ktx 等

# dev/preProduct 必须空
./gradlew :app:dependencies --configuration devReleaseRuntimeClasspath 2>&1 \
    | grep -iE "firebase|adjust|installreferrer|play-services-ads-identifier|com\.google\.android\.play:review"
# 预期:无任何匹配

./gradlew :app:dependencies --configuration preProductReleaseRuntimeClasspath 2>&1 \
    | grep -iE "firebase|adjust|installreferrer|play-services-ads-identifier|com\.google\.android\.play:review"
# 预期:无任何匹配
```

### 4. variant filter 已生效

```bash
./gradlew tasks --all | grep -E "assemble(PreProduct|Google)Debug"
# 预期:无输出(preProductDebug / googleDebug 已禁用)
```

### 5.(可选)APK symbol scan

```bash
APKANALYZER=~/Library/Android/sdk/cmdline-tools/latest/bin/apkanalyzer

$APKANALYZER dex packages app/build/outputs/apk/devDebug/*.apk \
    | grep -E "com\.google\.firebase|com\.adjust|com\.google\.android\.play\.core\.review"
# dev/preProduct 包预期:无输出(干净)

# google 包接入 SDK 后预期:能搜到符号
```

---

## 后续接入真实 SDK 的步骤

代码已按完整可运行状态写入,接入时只需填充配置与替换占位符:

1. **`<AppPrefix>` 替换**:将所有文件名与类名中的 `<AppPrefix>` 替换为项目简称驼峰形式(如 `Cbah`、`Wf`、`App`),避免模块间同名冲突
2. **`gradle/libs.versions.toml`**:确认版本号与项目最新需求对齐
3. **`app/build.gradle.kts`**:确认 `"googleImplementation"(...)` 依赖列表与项目实际接入的 SDK 一致
4. **`app/src/google/google-services.json`**:从 Firebase Console 下载放入
5. **归因方案选择**:
   - **默认(Adjust)** → 在 `AdjustAttributionImpl.kt` 中替换 `<adjust app token>` 为真实 Adjust App Token
   - **AppsFlyer** → 取消 `libs.versions.toml` 中 `appsflyer-sdk` 注释,取消 `build.gradle.kts` 中 `appsflyer-sdk` 依赖注释并注释 Adjust 依赖,取消 `AndroidManifest.xml` 中 AppsFlyer Receiver 注释,将 `GoogleServiceImpl` 中 `attribution` 改为 `AppsFlyerAttributionImpl()`,在 `AppsFlyerAttributionImpl.kt` 中替换 `<appsFlyer dev key>` 为真实 Dev Key
6. 跑 `./gradlew :app:assembleGoogleRelease` 验证完整打包

### 归因方案切换检查清单

| 检查项 | Adjust(默认) | AppsFlyer |
|--------|-------------|-----------|
| `libs.versions.toml` 依赖 | `adjust-sdk` + `adjust-webbridge` | `appsflyer-sdk` |
| `build.gradle.kts` 依赖 | `libs.adjust.sdk` + `libs.adjust.webbridge` | `libs.appsflyer.sdk` |
| `= <AppPrefix>AttributionImpl` | `AdjustAttributionImpl()` | `AppsFlyerAttributionImpl()` |
| `AndroidManifest.xml` Receiver | 无需注册 | 取消注释 2 个 Receiver |
| SDK App Key | `<adjust app token>` | `<appsFlyer dev key>` |
| ProGuard keep rules | `com.adjust.sdk.**` | `com.appsflyer.**` |
| 共用依赖 | `installreferrer` + `play-services-ads-identifier` | 同左 |
| WebView 桥接 | `adjust-webbridge` 保留 | 移除(AppsFlyer 无需此依赖) |

---

## 不适用场景

- 单 flavor 项目(没有"测试/预演/生产"维度差异)
- 项目本身就在所有渠道都用 Google 服务,不需要隔离
- KMP(Kotlin Multiplatform)项目:用 `expect/actual` 更原生,不需要 sourceSet flavor 二选一
- AGP 7.x 或更早:本 skill 写法虽然多数兼容,但 sourceSet 可以用更老的 API(无 deprecation 警告);若沿用本写法没问题

---

## 备忘:相关命令清单

```bash
# 日常开发联调(测试域名 + 日志开 + 不混淆 + 不含 Google)
./gradlew :app:assembleDevDebug

# 测试环境验证混淆/签名
./gradlew :app:assembleDevRelease

# 上线前真域名走查(生产域名 + 不含 Google)
./gradlew :app:assemblePreProductRelease

# 最终上线包(生产域名 + Firebase/Adjust/FCM/谷歌好评)
./gradlew :app:assembleGoogleRelease
./gradlew :app:bundleGoogleRelease       # AAB 包

# 单元测试(默认跑 devDebug)
./gradlew :app:test
```
