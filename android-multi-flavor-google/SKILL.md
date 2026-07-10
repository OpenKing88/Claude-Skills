---
name: android-multi-flavor-google
version: "1.3.0"
description: "Android multi-flavor Google service isolation skill with interactive setup mode"
---

# Android 多渠道 + Google 服务按渠道隔离

为 Android 项目搭建 **一个维度 × 三个 flavor**（devTest / preProduct / 正式渠道）的渠道矩阵，把 Firebase / 归因（Adjust 或 AppsFlyer）/ FCM / Google Play 好评等 Google 服务**完全隔离在生产渠道内**（其他渠道的 classpath 与 APK 字节码里零 Google 符号）。

v1.3 起采用**交互式询问模式**：搭建前先确认渠道命名、归因方案、依赖版本，然后批量生成所有文件。

## Mode Activation

| Mode | 触发关键词 | 说明 |
|------|-----------|------|
| **Setup Mode**（默认） | "搭建Google渠道", "配置多渠道", "setup google flavor", "渠道隔离" | 交互式询问 → 批量生成所有代码文件 |
| **Verify Mode** | "验证渠道隔离", "检查配置", "verify flavor isolation" | 对已有配置的存量项目跑 5 步编译/classpath/APK 验证 |
| **Troubleshoot Mode** | "报错", "编译失败", "crash", "build error", "Unresolved reference" | 根据症状诊断问题，匹配 9 个实战陷阱 |

## Reference Files

| 主题 | 文件 |
|------|------|
| **交互式搭建流程** — 4 个问题 + 决策树 + 占位符替换表 | `references/interactive-workflow.md` |
| **架构文档** — Variant 矩阵、sourceSet 布局、AGP 合并规则、命名规范 | `references/architecture.md` |
| **9 个关键陷阱** — 症状速查表 + 诊断/修复/验证 | `references/traps.md` |
| **验证步骤** — 5 步 shell 验证 + 测试签名创建 | `references/verify.md` |
| **集成清单** — SDK 接入步骤 + 归因方案切换 + CI/CD 建议 | `references/integration-checklist.md` |

## Templates

所有代码模板位于 `templates/` 目录，使用 `{{mustache}}` 风格占位符：

### 配置文件
- `templates/libs.versions.toml` — Version Catalog（Google + 归因依赖）
- `templates/build.gradle.kts.root` — 根 build.gradle.kts（plugin alias apply false）
- `templates/build.gradle.kts.app` — App build.gradle.kts（flavor × sourceSet × 条件 apply）
- `templates/proguard-rules.pro` — ProGuard 规则（Firebase + 归因）

### Facade 接口（src/main/）
- `templates/GoogleService.kt` — 应用层服务抽象（init + requestReview + 生命周期）
- `templates/GoogleServices.kt` — 单例入口
- `templates/AttributionService.kt` — 归因服务抽象（仅 init）

### 实现（sourceSet 二选一）
- `templates/GoogleServiceImpl.noop.kt` — No-Op（src/{{noFlavorName}}/）
- `templates/GoogleServiceImpl.real.kt` — 真实实现（src/{{flavorName}}/）
- `templates/AdjustAttributionImpl.kt` — Adjust 归因（默认）
- `templates/AppsFlyerAttributionImpl.kt` — AppsFlyer 归因（备选）

### 平台文件
- `templates/PushService.kt` — FCM 推送服务（src/{{flavorName}}/）
- `templates/AndroidManifest.google.xml` — FCM + 归因 Receiver 静态注册
- `templates/Application.kt` — Application 集成（统一回调处理 + 生命周期转发）
- `templates/DataStore.kt` — DataStore 新增字段（fcmToken / googleAdId / attributionData）

## Setup Mode 使用流程

激活 Setup Mode 后，AI 将依次询问 4 个问题：

```
1️⃣ 正式渠道命名？（默认: google）
   → devTest 和 preProduct 固定不可改
   → noGoogle sourceSet 名自动对称同步（google→noGoogle, playStore→noPlayStore）

2️⃣ 归因方案？（默认: Adjust / 备选: AppsFlyer）

3️⃣ 依赖版本确认？（列出 8 个默认版本，用户可逐个覆盖）

4️⃣ 是否需要手动生命周期？（默认: 不需要）
```

回答完毕后，AI 从 `templates/` 读取模板，完成占位符替换和归因条件块裁剪，一次性写入所有文件。

详细决策树和占位符替换表见 `references/interactive-workflow.md`。

## Verify Mode 使用流程

激活 Verify Mode 后，AI 按 `references/verify.md` 中的 5 步流程检查：
1. 所有 variant 能否编译
2. Plugin 条件 apply 是否正确
3. Classpath 隔离契约（google 有依赖，devTest/preProduct 零 Google 符号）
4. Variant filter 是否生效（preProductDebug / googleDebug 已禁用）
5. （可选）APK 字节码扫描

输出"通过/不通过"报告，失败项指向 `references/traps.md` 对应陷阱。

## Troubleshoot Mode 使用流程

当用户描述症状时，AI 先查 `references/traps.md` 顶部的**症状速查表**，定位到对应陷阱号，然后按陷阱的「解决方案」和「验证」步骤修复。常见症状速查：

| 症状 | 陷阱 |
|------|------|
| `Unresolved reference 'GoogleServiceImpl'` | 陷阱 1 |
| `Extension of type 'AppExtension' does not exist` | 陷阱 5 |
| FCM 通知图标显示为白色方块 | 陷阱 9 |
| `Adjust.getGoogleAdId` 返回 null | 陷阱 8 |
| `./gradlew build` 报缺少 google-services 资源 | 陷阱 3 |

## 适用前提

- AGP 8.x / 9.x（本 skill 以 AGP 9.1+ 验证）
- Kotlin 2.x
- Gradle Version Catalog（`gradle/libs.versions.toml`）
- Application 模块为 `com.android.application` plugin

## 不适用场景

- 单 flavor 项目（没有"测试/预演/生产"维度差异）
- 项目本身在所有渠道都用 Google 服务，不需要隔离
- KMP（Kotlin Multiplatform）项目：用 `expect/actual` 更原生
- AGP 7.x 或更早
