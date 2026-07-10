# 关键陷阱速查

> 提取自原 v1.2 SKILL.md "关键陷阱(实战踩过)"章节，v1.3 起独立为单独文件，共 9 个陷阱。

## 症状速查表

| 症状 | 跳到 |
|------|------|
| `Unresolved reference 'GoogleServiceImpl'` | [陷阱 1](#陷阱-1-agp-9--kotlin-2x-必须同时配-kotlinsrcdirs) |
| `Extension of type 'AppExtension' does not exist` | [陷阱 5](#陷阱-5-第三方-agp-plugin-兼容性) |
| FCM 通知图标显示为白色方块 | [陷阱 9](#陷阱-9-firebase-通知图标问题) |
| `Adjust.getGoogleAdId` 返回 null | [陷阱 8](#陷阱-8-缺少-play-services-ads-identifier-依赖) |
| Kotlin 编译器找不到 noGoogle 下的 .kt 文件 | [陷阱 1](#陷阱-1-agp-9--kotlin-2x-必须同时配-kotlinsrcdirs) |
| Gradle deprecation 警告 "Use `directories` mutable set instead" | [陷阱 2](#陷阱-2-srcdirsvararg-在-agp-9-deprecated-但仍是-source-of-truth) |
| `./gradlew build` / `./gradlew assemble` 后 google 包缺资源 | [陷阱 3](#陷阱-3-plugin-按-task-name-条件-apply-的边界) |
| Adjust `onResume`/`onPause` deprecation 警告 | [陷阱 7](#陷阱-7-adjust-sdk-5x-的-onresumeonpause-已废弃) |
| flavor 改名后 sourceSet 不生效 | [陷阱 6](#陷阱-6-flavor-名-vs-sourceset-路径同步) |

---

### 陷阱 1: AGP 9 + Kotlin 2.x 必须同时配 kotlin.srcDirs

**症状**: Kotlin 编译器报 `Unresolved reference 'GoogleServiceImpl'`，noGoogle 下的 .kt 文件找不到。

**根因**: AGP 9 + Kotlin 2.x 把 `.kt` 源剥到独立的 `kotlin` sourceDirectorySet，**只配 `java.srcDirs` 不够**，Kotlin 编译器会找不到 `noGoogle/java/` 下的 .kt 文件。

**解决方案**: `java.srcDirs(...)` 和 `kotlin.srcDirs(...)` 都列出 noGoogle 目录（参考 `templates/build.gradle.kts.app` 中的 sourceSets 块）。

**验证**: 切换 flavor 到 noGoogle 后编译通过，`GoogleServiceImpl` 可正常引用。

---

### 陷阱 2: srcDirs(vararg) 在 AGP 9 deprecated 但仍是 source of truth

**症状**: AGP 9 提示 deprecation 警告 "Use `directories` mutable set instead"。

**根因**: `java.directories.add(...)` **不影响 kotlin 编译器输入**——它只是 SourceDirectorySet 的部分内部 field，kotlin compile 还是读 srcDirs。

**解决方案**: `@Suppress("DEPRECATION")` 包住 sourceSets 块，继续用 var-arg `srcDirs(...)`，等 AGP 推出稳定的替代 API 再迁。

**验证**: 编译通过且功能正常，虽然有 deprecation 警告但行为无误。

---

### 陷阱 3: Plugin 按 task name 条件 apply 的边界

**症状**:
- `./gradlew build` 或 `./gradlew assemble`（不指定 variant）时 taskNames 不含 "Google"，plugin 不 apply，google 包缺少 google-services 资源。
- IDE Project Sync 阶段 taskNames 一般不含 "Google"，IDE 显示红线（**只影响 IDE 红线**，不影响打包）。

**根因**: `gradle.startParameter.taskNames.any { it.contains("Google", ignoreCase = true) }` 判断只在 taskNames 含 "Google" 时 apply plugin。批量 build/assemble 不指定 variant 时不含 "Google"。

**解决方案**: 两种方式互补：
1. 打 google 渠道包时命令一定要带 `Google` 字样（`assembleGoogleRelease` / `bundleGoogleRelease` / `installGoogleRelease`）。
2. 新增兜底条件 `|| project.hasProperty("enableGooglePlugin")`，CI 或批量 build 时通过 `-PenableGooglePlugin` 主动触发（v1.3 改进）。
如果想消 IDE 红线，可以临时把 `if (isGoogleBuild)` 改成 `true` 跑一次 sync。

**验证**: 用 `./gradlew build -PenableGooglePlugin` 运行，google 包资源正常生成；不传此 property 时纯 noGoogle 渠道不受影响。

---

### 陷阱 4: BuildConfig 与 plugin apply 完全独立

**症状**: 误以为 BuildConfig 字段会和 plugin 绑定，担心不 apply plugin 时 BuildConfig 字段会串到其他 flavor。

**根因**: `buildConfigField` 是 AGP **配置阶段**为每个 variant 单独生成独立的 `BuildConfig.java`，跟 plugin 是否 apply 无关。

**解决方案**: 只要 variant 选对，`BASE_URL`/`WEB_URL`/`FLAVOR_ENV`/`ENABLE_LOG` 就一定切到对应 flavor，不会和其它 flavor 串。

**验证**: 分别打出 googleRelease 和 devTestRelease 两个 apk，解压查看各自 BuildConfig.class 确认字段值正确。

---

### 陷阱 5: 第三方 AGP plugin 兼容性

**症状**: 报错：
```
Extension of type 'AppExtension' does not exist
```

**根因**: AGP 9 删除了 `com.android.build.gradle.AppExtension` / `BaseExtension` / `LibraryExtension`，改用 `com.android.build.api.dsl.ApplicationExtension` 等。许多第三方 Gradle plugin（Sonar、AboutLibraries、Sentry、Hilt 老版本、`io.github.OpenKing88.verify` 等）还未适配。

**解决方案**: 升级到兼容 AGP 9 的 plugin 版本，或者临时降级 AGP 到 8.x，或者去掉这个 plugin。

**验证**: 升级后重新 build，`AppExtension` 相关报错消失。

---

### 陷阱 6: flavor 名 vs sourceSet 路径同步

**症状**: 修改 flavor 名后（如 `dev` → `devTest`）编译报错或 sourceSet 未生效。

**根因**: flavor 改名后漏同步以下任一位置：
- `sourceSets.getByName("devTest")` 里的 sourceSet name
- `productFlavors { create("devTest") { ... } }` 里的 flavor name
- `androidComponents.beforeVariants` 里的 flavor 判断字符串
- 物理目录 `src/<flavor>/...`（如果有自定义的话）

**解决方案**: 改 flavor 名时同步所有引用处。注意 `srcDirs` 路径可以不存在——AGP 只是声明 sourceSet 包含该目录，不存在就不扫描，**不会报错**。建议保持 flavor 名与目录名一致以减少混乱。

**验证**: 改名后所有 flavor 都能正常编译和运行。

---

### 陷阱 7: Adjust SDK 5.x 的 onResume/onPause 已废弃

**症状**: Adjust SDK 5.x 中调用 `Adjust.onResume()` / `Adjust.onPause()` 出现 deprecation 警告。

**根因**: Adjust 5.x 内部自动注册 `ActivityLifecycleCallbacks`，`Adjust.onResume()` / `Adjust.onPause()` 已废弃。但 facade 接口形状不变，真实实现里这两个方法已经是 no-op。

**解决方案**: 移除对 `Adjust.onResume()` / `Adjust.onPause()` 的手动调用，让 SDK 自动管理生命周期。

**验证**: 编译无 deprecation 警告，Adjust 功能正常（deeplink、session 统计等均不受影响）。

---

### 陷阱 8: 缺少 play-services-ads-identifier 依赖

**症状**: `Adjust.getGoogleAdId` 返回 null。

**根因**: Adjust 和 Firebase 都需要 `play-services-ads-identifier`，但未显式添加该依赖。

**解决方案**: 在 `build.gradle.kts` 的 `"{{flavorName}}Implementation"` 依赖块中确保包含：
```kotlin
"{{flavorName}}Implementation"(libs.play.services.ads.identifier)
```

**验证**: 添加后 `Adjust.getGoogleAdId` 正常返回广告 ID 而非 null。

---

### 陷阱 9: Firebase 通知图标问题

**症状**: FCM 通知图标显示为白色方块。

**根因**: `setSmallIcon(R.mipmap.ic_launcher)` 在某些 Android 版本下被系统强制转为纯白方块。

**解决方案**: 设计交付专用 notification icon 后替换为 `R.drawable.ic_notification`（透明背景白色单色）。

**验证**: 推送通知后状态栏显示正确的自定义图标而非白色方块。
