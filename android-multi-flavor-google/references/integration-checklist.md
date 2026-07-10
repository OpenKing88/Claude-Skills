# 集成清单

## 接入真实 SDK 的步骤

代码已按完整可运行状态写入，接入时只需填充配置与替换占位符：

1. **`<AppPrefix>` 替换**：将所有文件名与类名中的 `<AppPrefix>` 替换为项目简称驼峰形式（如 `Cbah`、`Wf`、`App`），避免模块间同名冲突
2. **`gradle/libs.versions.toml`**：确认版本号与项目最新需求对齐
3. **`app/build.gradle.kts`**：确认 `"{{flavorName}}Implementation"(...)` 依赖列表与项目实际接入的 SDK 一致
4. **`app/src/{{flavorName}}/google-services.json`**：从 Firebase Console 下载放入
5. **归因方案选择**：
   - **默认（Adjust）** → 在 `AdjustAttributionImpl.kt` 中替换 `{{adjustToken}}` 为真实 Adjust App Token
   - **AppsFlyer** → 在 `AppsFlyerAttributionImpl.kt` 中替换 `{{appsFlyerDevKey}}` 为真实 Dev Key
6. 跑 `./gradlew :app:assemble{{FlavorName}}Release` 验证完整打包

## 归因方案切换清单

| 检查项 | Adjust（默认） | AppsFlyer |
|--------|-------------|-----------|
| `libs.versions.toml` 依赖 | `adjust-sdk` + `adjust-webbridge` | `appsflyer-sdk` |
| `build.gradle.kts` 依赖 | `libs.adjust.sdk` + `libs.adjust.webbridge` | `libs.appsflyer.sdk` |
| `= {{appPrefix}}AttributionImpl` | `{{appPrefix}}AdjustAttributionImpl()` | `{{appPrefix}}AppsFlyerAttributionImpl()` |
| `AndroidManifest.xml` Receiver | 无需注册 | 取消注释 2 个 Receiver |
| SDK App Key | `{{adjustToken}}` | `{{appsFlyerDevKey}}` |
| ProGuard keep rules | `com.adjust.sdk.**` | `com.appsflyer.**` |
| 共用依赖 | `installreferrer` + `play-services-ads-identifier` | 同左 |
| WebView 桥接 | `adjust-webbridge` 保留 | 移除（AppsFlyer 无需此依赖） |

## CI/CD 建议

- **编译任务必须使用显式变体名**：例如 `assembleGoogleRelease`，严禁使用裸 `assemble` 或 `build`，避免触发所有变体导致构建时间过长
- **Android CI 矩阵应运行全部 4 个变体**：`devTestDebug`、`devTestRelease`、`preProductRelease`、`googleRelease`，确保每个变体均能正常编译
- **Google 变体 CI 参数**：在 google 变体任务中传递 `-PenableGooglePlugin` 以强制启用插件
- **ProGuard 映射文件**：将 googleRelease 变体的 mapping 文件（`app/build/outputs/mapping/googleRelease/mapping.txt`）保存为 CI 制品，用于线上崩溃反混淆
- **Firebase App Distribution**：CI 可将 `googleRelease` APK 上传至 Firebase App Distribution 供测试人员使用，`devTestDebug` 可上传给内部测试者快速验证

## 手动操作清单

- [ ] 从 Firebase Console 下载 `google-services.json` 并放入 `app/src/{{flavorName}}/google-services.json`
- [ ] 在 `AdjustAttributionImpl.kt` 中替换 `{{adjustToken}}` 为真实的 Adjust App Token（若使用 Adjust）
- [ ] 在 `AppsFlyerAttributionImpl.kt` 中替换 `{{appsFlyerDevKey}}` 为真实的 AppsFlyer Dev Key（若使用 AppsFlyer）
- [ ] 将通知渠道图标文件放入 `app/src/{{flavorName}}/res/drawable/` 目录，替换默认占位图标
- [ ] 执行 `./gradlew :app:assemble{{FlavorName}}Release` 验证完整打包通过
- [ ] 在 Firebase Console / Adjust Dashboard / AppsFlyer Dashboard 中确认归因数据正常上报
