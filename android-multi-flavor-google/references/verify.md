# 验证步骤

## AI 执行指引

验证模式激活后，AI 按以下流程对存量项目做渠道隔离健康检查。

### 0. 前置扫描（必须最先执行）

```bash
# 从 build.gradle.kts 提取生产渠道 flavor 名
# 查找 create("xxx") 中注释含 "Firebase" 或 "归因" 或 "完整上线包" 的 flavor
grep -B1 -A3 'Firebase\|归因\|完整上线包' app/build.gradle.kts | grep 'create('
```

从输出中提取 flavor 名（如 `google`、`playStore`），计算：
- `flavorName` — 原样（如 `playStore`）
- `FlavorName` — 首字母大写（如 `PlayStore`）
- 后续所有命令中的 `google`/`Google` 替换为实际值

如果未找到生产渠道 flavor 配置，告知用户「未检测到多渠道隔离配置，请先运行 Setup Mode 搭建」，然后停止。

### 0.5 检测归因方案

```bash
# 检查 src/google/ 下存在哪个归因实现
ls src/{{flavorName}}/java/**/google/*AttributionImpl.kt 2>/dev/null
```

- 如果文件名含 `Adjust` → 归因方案为 Adjust
- 如果文件名含 `AppsFlyer` → 归因方案为 AppsFlyer
- 如果都不存在 → 标记「归因实现缺失」

如果项目存在 `gradle/libs.versions.toml`，也可交叉验证：
```bash
grep -E 'adjust-sdk|appsflyer-sdk' gradle/libs.versions.toml
```

### 0.6 源码集成检查

> 以下检查用 `find`/`grep` 完成，不需要 Gradle，几秒内完成。

#### 目录结构

```bash
echo "=== sourceSet 目录 ==="
ls -d src/{{flavorName}}/ 2>/dev/null && echo "✅ src/{{flavorName}}/ 存在" || echo "❌ 缺少 src/{{flavorName}}/"
ls -d src/{{noFlavorName}}/ 2>/dev/null && echo "✅ src/{{noFlavorName}}/ 存在" || echo "❌ 缺少 src/{{noFlavorName}}/"
ls -d src/main/java/*/google/ 2>/dev/null && echo "✅ src/main/.../google/ 存在" || echo "❌ 缺少 facade 接口目录"
```

#### Facade 接口文件

```bash
echo "=== facade 接口 ==="
for f in GoogleService GoogleServices AttributionService; do
  ls src/main/java/**/google/*$f.kt 2>/dev/null && echo "✅ $f.kt" || echo "❌ 缺少 $f.kt"
done
```

#### sourceSet 二选一实现

```bash
echo "=== GoogleServiceImpl 二选一 ==="
ls src/{{flavorName}}/java/**/google/*GoogleServiceImpl.kt 2>/dev/null && echo "✅ {{flavorName}} 侧" || echo "❌ 缺少 {{flavorName}} 侧"
ls src/{{noFlavorName}}/java/**/google/*GoogleServiceImpl.kt 2>/dev/null && echo "✅ {{noFlavorName}} 侧" || echo "❌ 缺少 {{noFlavorName}} 侧"
```

注意：两边的类必须**同名同包**，否则编译期 AGP 无法二选一。
```bash
echo "=== 同名同包验证 ==="
class1=$(grep -rh 'internal class.*GoogleServiceImpl' src/{{flavorName}}/java/ 2>/dev/null | head -1)
class2=$(grep -rh 'internal class.*GoogleServiceImpl' src/{{noFlavorName}}/java/ 2>/dev/null | head -1)
[ "$class1" = "$class2" ] && echo "✅ 类名一致" || echo "⚠️ 类名不一致,请检查"
```

#### FCM 注册

```bash
echo "=== AndroidManifest ==="
grep -q 'FirebaseMessagingService\|MESSAGING_EVENT' src/{{flavorName}}/AndroidManifest.xml 2>/dev/null && echo "✅ FCM Service 已注册" || echo "❌ 缺少 FCM Service 注册"
grep -q 'POST_NOTIFICATIONS' src/{{flavorName}}/AndroidManifest.xml 2>/dev/null && echo "✅ 通知权限已声明" || echo "⚠️ 缺少 POST_NOTIFICATIONS 权限"
```

#### google-services.json

```bash
ls src/{{flavorName}}/google-services.json 2>/dev/null && echo "✅ google-services.json" || echo "⚠️ google-services.json 缺失(从 Firebase Console 下载)"
```

#### Application 集成

```bash
echo "=== Application 集成 ==="
grep -q 'GoogleServices.*\.init(' app/src/main/java/**/Application.kt 2>/dev/null && echo "✅ Application.onCreate 调用了 init()" || echo "❌ 未找到 GoogleServices.init() 调用"
```

检查 `init()` 调用中是否包含 3 个必备回调：
```bash
grep -A10 'GoogleServices.*\.init(' app/src/main/java/**/Application.kt 2>/dev/null | grep -q 'onGoogleAdIdReady' && echo "✅ onGoogleAdIdReady" || echo "❌ 缺少"
grep -A10 'GoogleServices.*\.init(' app/src/main/java/**/Application.kt 2>/dev/null | grep -q 'onFcmTokenReady' && echo "✅ onFcmTokenReady" || echo "❌ 缺少"
grep -A10 'GoogleServices.*\.init(' app/src/main/java/**/Application.kt 2>/dev/null | grep -q 'onAttributionReady' && echo "✅ onAttributionReady" || echo "❌ 缺少"
```

#### DataStore 字段

```bash
echo "=== DataStore 字段 ==="
for key in fcmTokenKey googleAdIdKey attributionDataKey attributionDeviceIdKey; do
  grep -q "$key" app/src/main/java/**/DataStore.kt 2>/dev/null && echo "✅ $key" || echo "❌ 缺少 $key"
done
```

#### 归因集成检查（按检测到的方案执行）

**通用**（Adjust 和 AppsFlyer 都需要）：
```bash
echo "=== 归因通用检查 ==="
# GoogleServiceImpl.real 应持有 AttributionService 委托
grep -q 'AttributionService' src/{{flavorName}}/java/**/google/*GoogleServiceImpl.kt 2>/dev/null && echo "✅ 委托 AttributionService" || echo "⚠️ 未委托"
# GoogleServiceImpl.noop 不应 import 任何归因 SDK
grep -qE 'com\.adjust|com\.appsflyer' src/{{noFlavorName}}/java/**/google/*GoogleServiceImpl.kt 2>/dev/null && echo "❌ noop 不应引入归因 SDK" || echo "✅ noop 无归因 SDK import"
```

**如果方案为 Adjust**：
```bash
echo "=== Adjust 集成检查 ==="
impl="src/{{flavorName}}/java/**/google/*AdjustAttributionImpl.kt"
# 核心 API 调用
grep -q 'AdjustConfig(' $impl 2>/dev/null && echo "✅ AdjustConfig" || echo "❌ 缺少 AdjustConfig"
grep -q 'Adjust.initSdk' $impl 2>/dev/null && echo "✅ Adjust.initSdk" || echo "❌ 缺少 initSdk"
grep -q 'Adjust.getAdid()' $impl 2>/dev/null && echo "✅ Adjust.getAdid" || echo "❌ 缺少 getAdid"
grep -q 'Adjust.getGoogleAdId' $impl 2>/dev/null && echo "✅ Adjust.getGoogleAdId" || echo "❌ 缺少 getGoogleAdId"
# 回调: onAttributionReady(adid, null) — Adjust 不需要归因数据
grep -q 'onAttributionReady.*null' $impl 2>/dev/null && echo "✅ onAttributionReady(adid, null)" || echo "⚠️ 回调参数异常"
```

**如果方案为 AppsFlyer**：
```bash
echo "=== AppsFlyer 集成检查 ==="
impl="src/{{flavorName}}/java/**/google/*AppsFlyerAttributionImpl.kt"
# 核心 API 调用
grep -q 'AppsFlyerLib.getInstance().init(' $impl 2>/dev/null && echo "✅ AppsFlyerLib.init" || echo "❌ 缺少 init"
grep -q 'AppsFlyerConversionListener' $impl 2>/dev/null && echo "✅ ConversionListener" || echo "❌ 缺少 Listener"
grep -q 'onConversionDataSuccess' $impl 2>/dev/null && echo "✅ onConversionDataSuccess" || echo "❌ 缺少"
grep -q 'getAppsFlyerUID' $impl 2>/dev/null && echo "✅ getAppsFlyerUID" || echo "❌ 缺少"
grep -q 'AppsFlyerLib.getInstance().start(' $impl 2>/dev/null && echo "✅ start(application)" || echo "❌ 缺少 start"
# 回调: onAttributionReady(deviceId, json) — json 不可为 null
grep -q 'onAttributionReady.*JSONObject' $impl 2>/dev/null && echo "✅ onAttributionReady(deviceId, json)" || echo "⚠️ 回调参数异常"
# Manifest 中应有 AppsFlyer Receiver
grep -q 'SingleInstallBroadcastReceiver\|MultipleInstallBroadcastReceiver' src/{{flavorName}}/AndroidManifest.xml 2>/dev/null && echo "✅ Manifest Receiver" || echo "❌ 缺少 AppsFlyer Receiver"
```

#### 调用链路完整性

逐层验证从 Application 到归因 SDK 的调用链：

```
Application.onCreate
  → GoogleServices.instance.init()
    → GoogleServiceImpl.init()
      → AttributionService.init()          ← 归因委托
      → FirebaseApp.initializeApp()        ← Firebase
      → FirebaseMessaging.token            ← FCM
```

```bash
echo "=== 调用链完整性 ==="
# 1. Application → GoogleServices
grep -q 'GoogleServices.*\.init(' app/src/main/java/**/Application.kt 2>/dev/null && echo "✅ 1. Application → GoogleServices" || echo "❌ 1."

# 2. GoogleServices → GoogleServiceImpl
grep -q 'GoogleServiceImpl()' src/main/java/**/google/*GoogleServices.kt 2>/dev/null && echo "✅ 2. GoogleServices → GoogleServiceImpl" || echo "❌ 2."

# 3. GoogleServiceImpl.real → AttributionService
grep -q 'AttributionService' src/{{flavorName}}/java/**/google/*GoogleServiceImpl.kt 2>/dev/null && echo "✅ 3. GoogleServiceImpl → AttributionService" || echo "❌ 3."

# 4. GoogleServiceImpl.real → Firebase
grep -q 'FirebaseApp.initializeApp\|FirebaseMessaging' src/{{flavorName}}/java/**/google/*GoogleServiceImpl.kt 2>/dev/null && echo "✅ 4. Firebase + FCM 初始化" || echo "❌ 4."

# 5. 回调 → Application → DataStore
grep -q 'attributionDeviceIdKey' app/src/main/java/**/Application.kt 2>/dev/null && echo "✅ 5. Application 写 DataStore" || echo "❌ 5."

# 6. PushService → DataStore (FCM token 更新)
grep -q 'fcmTokenKey' src/{{flavorName}}/java/**/google/*PushService.kt 2>/dev/null && echo "✅ 6. PushService → DataStore" || echo "❌ 6."
```

### 源码检查输出格式

```
## 源码集成检查

| 类别 | 检查项 | 状态 |
|------|--------|------|
| 目录 | src/{{flavorName}}/ | ✅ |
| 目录 | src/{{noFlavorName}}/ | ✅ |
| 接口 | GoogleService.kt | ✅ |
| 接口 | GoogleServices.kt | ✅ |
| 接口 | AttributionService.kt | ✅ |
| 实现 | GoogleServiceImpl ({{flavorName}}) | ✅ |
| 实现 | GoogleServiceImpl ({{noFlavorName}}) | ✅ |
| 同名 | 类名一致性 | ✅ |
| FCM | Service 注册 | ✅ |
| FCM | POST_NOTIFICATIONS | ✅ |
| Firebase | google-services.json | ⚠️ 需手动下载 |
| Application | init() 调用 | ✅ |
| Application | 3 个回调完整 | ✅ |
| DataStore | 4 个 key | ✅ |
| 归因 | {{attributionScheme}} 核心 API | ✅ |
| 归因 | noop 无 SDK import | ✅ |
| 调用链 | App→GoogleServices→Impl→SDK→DataStore | 6/6 ✅ |
```

### 1. 执行规则

- 5 步按顺序执行，一步失败标记后继续下一步
- 每条命令输出：原始命令 + 关键结果行（截取前 10 行）+ ✅/❌ 判定
- 使用 `Bash` 工具执行，`description` 参数写明当前步骤
- 超时命令加 `timeout` 参数（如 APK 构建 300000ms）

### 2. 最终输出格式

```
## 渠道隔离验证报告

| 步骤 | 状态 | 关键信息 |
|------|------|---------|
| 1. 编译检查 | ✅ | 4/4 variant 编译通过 |
| 2. Plugin apply | ✅ | isGoogleBuild=false(non) / true(google) |
| 3. Classpath 隔离 | ✅ | google 含依赖，devTest/preProduct 干净 |
| 4. Variant filter | ✅ | preProductDebug / googleDebug 已禁用 |
| 5. APK 符号扫描 | ⏭️ | 跳过（未构建 APK） |

总结: 5/5 通过，渠道隔离配置正确。
```

如有失败项，在报告下方列出失败详情 + 指向 `references/traps.md` 对应陷阱。

---

## 1. 编译检查

```bash
./gradlew :app:compileDevTestDebugKotlin
./gradlew :app:compileDevTestReleaseKotlin
./gradlew :app:compilePreProductReleaseKotlin
./gradlew :app:compile{{FlavorName}}ReleaseKotlin
```

**预期**: 全部 `BUILD SUCCESSFUL`。

**失败时**: 检查编译错误信息。`Unresolved reference 'GoogleServiceImpl'` → 陷阱 1。

---

## 2. Plugin 条件 apply 验证

```bash
# A) 非 google task — plugin 不应 apply
./gradlew :app:tasks --warning-mode none 2>&1 | grep "isGoogleBuild"
# 预期: isGoogleBuild=false

# B) google task --dry-run — plugin 应注册自己的 task
./gradlew :app:assemble{{FlavorName}}Release --dry-run 2>&1 | grep "isGoogleBuild"
# 预期: isGoogleBuild=true
# 同时 task graph 中应出现 :app:process{{FlavorName}}ReleaseGoogleServices

# C) 备选 — Gradle property 强制启用（CI 场景）
./gradlew :app:assembleDevTestRelease -PenableGooglePlugin --dry-run 2>&1 | grep "isGoogleBuild"
# 预期: isGoogleBuild=true
```

**失败时**: 检查条件判断逻辑中 flavor 名是否匹配 → 陷阱 3、陷阱 6。

---

## 3. Classpath 隔离契约

```bash
# google flavor 应含全套 Google 依赖
./gradlew :app:dependencies --configuration {{flavorName}}ReleaseRuntimeClasspath 2>&1 \
    | grep -E "firebase|adjust|installreferrer|play-services-ads-identifier|com\.google\.android\.play:review"
# 预期: 看到 firebase-bom / analytics / messaging / crashlytics / adjust-android / installreferrer / review-ktx 等

# devTest 必须空
./gradlew :app:dependencies --configuration devTestReleaseRuntimeClasspath 2>&1 \
    | grep -iE "firebase|adjust|installreferrer|play-services-ads-identifier|com\.google\.android\.play:review"
# 预期: 无任何匹配

# preProduct 必须空
./gradlew :app:dependencies --configuration preProductReleaseRuntimeClasspath 2>&1 \
    | grep -iE "firebase|adjust|installreferrer|play-services-ads-identifier|com\.google\.android\.play:review"
# 预期: 无任何匹配
```

**失败时**: 检查依赖是 `{{flavorName}}Implementation` 还是写成了 `implementation` → 陷阱 6。

---

## 4. Variant Filter 验证

```bash
./gradlew tasks --all | grep -E "assemble(PreProduct|{{FlavorName}})Debug"
# 预期: 无输出（preProductDebug / {{flavorName}}Debug 已禁用）
```

**失败时**: 检查 `androidComponents.beforeVariants` 中的 flavor 名字符串是否一致 → 陷阱 6。

---

## 5. (可选) APK 字节码扫描

需要先构建 APK 才能执行：

```bash
APKANALYZER=~/Library/Android/sdk/cmdline-tools/latest/bin/apkanalyzer
# 如果 apkanalyzer 不存在,跳过本步骤

$APKANALYZER dex packages app/build/outputs/apk/devTestDebug/*.apk \
    | grep -E "com\.google\.firebase|com\.adjust|com\.google\.android\.play\.core\.review"
# devTest/preProduct 包预期: 无输出（干净）

# google 包预期: 能搜到符号
```

**失败时**: APK 中出现了不应存在的 Google 符号 → 陷阱 6（检查 sourceSet 和依赖配置）。

---

## 验证失败常见原因

| 症状 | 根因 | 跳转 |
|------|------|------|
| `Unresolved reference 'GoogleServiceImpl'` | noGoogle 下 .kt 文件未被 Kotlin 编译器扫描 | 陷阱 1 |
| `./gradlew build` 后 google 包缺资源 | task name 不含 flavor 名，plugin 未 apply | 陷阱 3 |
| devTest/preProduct classpath 含 Google 依赖 | 依赖误写为 `implementation` 而非 `{{flavorName}}Implementation` | 陷阱 6 |
| `preProductDebug` 仍出现在 task 列表 | `beforeVariants` 中的 flavor 名与 `create("...")` 不一致 | 陷阱 6 |
| `Extension of type 'AppExtension' does not exist` | 第三方 plugin 不兼容 AGP 9 | 陷阱 5 |
| `Adjust.getGoogleAdId` 返回 null | 缺少 `play-services-ads-identifier` 依赖 | 陷阱 8 |

## 测试签名文件创建

渠道隔离搭建完成后，为 devTest / preProduct 创建测试签名：

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
| 有效期 | 10000 天 | 约 27 年 |
| 签发者 | CN=Test Debug, C=PE | 便于与正式签名区分 |

> 此签名仅用于 devTest / preProduct 内部测试，**绝不用于 Google Play 上架**。建议将 `app/test-debug.jks` 加入 `.gitignore`。
