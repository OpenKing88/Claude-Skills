---
name: login-register-flow
version: "1.1.0"
description: >
  手机号+验证码登录注册全流程自动搭建方案。

  核心能力：
  1. 自动检测项目技术栈（Compose/View、Retrofit/Ktor、DataStore/SharedPreferences）
  2. 根据检测结果自动创建完整页面 UI（LoginScreen + OtpScreen + Splash 登录态判断）
  3. 自动串联 Login→OTP→Main 完整导航链路（含回栈清除 + Token 管理）
  4. Compose 路径自动生成 stateful-wrapper + stateless-Content + @Preview
  5. View 路径自动生成 Fragment + ViewBinding + ViewModel 模板
  6. 自动适配项目网络层（Retrofit API Service）、持久化层（SP/DataStore）、导航层

  触发关键词：登录, 注册, login, register, 验证码, OTP, 手机号登录, 短信验证,
  退出登录, 注销, logout, sign in, sign up, phone auth, SMS verification,
  登录注册流程, login flow, register flow, auth flow, 创建登录页面, 搭建登录流程,
  登录模块, 注册模块, auth module.
---

# 登录注册流程 Skill

## 自动搭建能力（AI 必须执行）

激活本 Skill 后，AI 将自动按以下步骤**全流程搭建**登录注册模块，无需用户逐步引导：

```
Step 1: 扫描项目技术栈（UI框架/网络层/持久化/导航/ViewModel基类）
    ↓
Step 2: 自动创建 LoginScreen（手机号输入 + 隐私协议 + 获取验证码按钮）
    ↓
Step 3: 自动创建 OtpScreen（6位验证码输入 + 60s倒计时 + 自动提交 + 语音验证码）
    ↓
Step 4: 自动创建 Splash 登录态判断逻辑（Token为空→Login / Token有效→Main）
    ↓
Step 5: 自动创建 LoginViewModel（含防重复发送/倒计时管理/验证码提交/错误处理）
    ↓
Step 6: 自动串联导航链路（Login → Otp(phone) → Main，登录成功后清除回栈）
    ↓
Step 7: 自动集成 Token/Account 持久化（使用项目现有存储方案）
    ↓
Step 8: 自动创建退出/注销逻辑（清数据 → 跳 Login）
    ↓
Step 9（Compose）：为每个 Content 自动生成 @Preview + Mock 数据工厂
Step 9（View）：自动生成 Fragment + ViewBinding + lifecycle 绑定
    ↓
输出：完整可运行的登录注册模块代码
```

**AI 必须创建的文件清单：**

| 文件 | 内容 |
|------|------|
| LoginViewModel.kt | 手机号校验/发送验证码/防重复/倒计时/OTP提交/退出注销 |
| LoginScreen.kt（Compose）或 LoginFragment.kt + fragment_login.xml（View） | 手机号输入/隐私协议/提交按钮 |
| OtpScreen.kt（Compose）或 OtpFragment.kt + fragment_otp.xml（View） | 6位验证码输入/重发/语音验证码 |
| SplashViewModel.kt（或扩展现有） | Token 检查 → 跳转判断 |
| 路由注册（NavHost/NavGraph） | LoginRoute, OtpRoute(phone), MainRoute + 回栈清除 |

---

## 技术方案自适应（CRITICAL — AI 必须首先执行）

在生成任何代码前，AI 必须先扫描项目确定技术方案，然后走对应路径：

### 1. UI 框架检测

```bash
检查 build.gradle.kts：
  ├── 有 androidx.compose 依赖 → Compose 路径（6.1 节）
  └── 无 Compose，有 fragment + viewbinding → View 路径（6.2 节）
```

**Compose 路径：**
- Screen 层：stateful-wrapper + stateless-Content 模式
- 每个 Content 必须带 `@Preview` + Mock 数据工厂
- 参考 `android-compose-expert` Refactoring Mode

**View 路径：**
- Fragment + ViewBinding（或 DataBinding）
- ViewModel 通过 `by viewModels()` 或 `ViewModelProvider` 获取
- Layout 使用 ConstraintLayout / LinearLayout

### 2. 网络框架检测

```bash
搜索 Retrofit / Ktor / Volley 引用：
  ├── retrofit2.Retrofit → Retrofit + Gson + suspend 函数
  ├── io.ktor → Ktor HttpClient + kotlinx.serialization
  └── 其他 → 适配为项目实际方式
```

### 3. 持久化检测

```bash
搜索 DataStore / SharedPreferences / MMKV：
  ├── androidx.datastore → DataStore（Flow 读取）
  ├── SharedPreferences → getString/putString（同步读写）
  └── MMKV → mmkv.decodeString/encodeString
```

### 4. 导航检测

```bash
搜索 NavHost / Fragment / Navigation：
  ├── androidx.navigation.compose → Compose Navigation（类型安全路由）
  ├── androidx.navigation.fragment → Fragment Navigation
  └── 自定义路由 → 适配项目方案
```

### 5. ViewModel 基类检测

```bash
搜索 ViewModel 继承关系：
  ├── BaseViewModel → 复用项目的 launchLoading / 异常处理
  ├── AndroidViewModel → 有 Application 引用
  └── ViewModel → 标准 AndroidX ViewModel
```

---

## 业务流程图

```
App 冷启动
    │
    ▼
SplashScreen ──► 检查本地登录态（Token 是否存在）
    │
    ├─ Token 为空 ──► LoginScreen
    │                    │
    │                    ▼
    │              输入手机号 + 勾选隐私协议
    │                    │
    │                    ▼
    │              点击「获取验证码」
    │                    │
    │                    ▼
    │              [API] 发送验证码（SMS/语音）
    │                    │
    │                    ▼
    │              跳转 OtpScreen + 启动 60s 倒计时
    │                    │
    │                    ▼
    │              输入 6 位验证码（自动提交）
    │                    │
    │                    ▼
    │              [API] 注册/登录合一接口
    │                    │
    │                    ▼
    │              存储 Token + Account + 首次注册标记
    │                    │
    │                    ▼
    │              跳转 MainScreen（清除 Login 回栈）
    │
    └─ Token 有效 ──► MainScreen

MainScreen init:
    ├─ 首次注册标记=true ──► 自动跳转个人信息完善页
    └─ 有推送 Token ──► 上报服务端

退出/注销:
    ├─ 退出登录 ──► [API] logout ──► 清除 Token/Account ──► LoginScreen
    └─ 注销账号 ──► [API] cancelAccount ──► 清除 Token/Account ──► LoginScreen
```

---

## 数据模型（所有字段必须可空）

为兼容不同序列化框架，所有 DTO 和数据存储结构字段必须定义为可空类型：

```kotlin
// ✅ 正确：全可空
data class LoginResult(
    var token: String? = null,
    var isFirstRegister: Int? = null,
    var isAuditAccount: Int? = null,
)

// ❌ 错误：非空字段在字段缺失时会崩溃
data class LoginResult(
    val token: String,
    val isFirstRegister: Int = 0,
)
```

详细实现参考 `references/flow.md`。

---

## UI 双路径：Compose vs View System

### Compose 路径（项目使用 Compose 时走此路径）

**必须遵循的状态隔离模式：**

```kotlin
// Stateful Wrapper（集成 ViewModel + 导航）
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = /* 项目 ViewModel 获取方式 */,
    onNavigateToOtp: (String) -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.event.collect { ... } }
    LoginScreenContent(state = state, onAction = viewModel::onAction)
}

// Stateless Content（纯 UI，Preview 可用）
@Composable
fun LoginScreenContent(
    state: LoginState,
    onAction: (LoginAction) -> Unit,
    modifier: Modifier = Modifier,
) { /* UI 代码 */ }

// Previews
@Preview(name = "Normal", showBackground = true)
@Composable
private fun LoginScreenContentPreview() {
    PreviewTheme { LoginScreenContent(LoginState(), {}) }
}
```

### View System 路径（项目使用 View/XML 时走此路径）

```kotlin
class LoginFragment : Fragment(R.layout.fragment_login) {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LoginViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLoginBinding.bind(view)
        observeState()
        setupListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // 防止泄漏
    }

    private fun observeState() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            binding.phoneInput.setText(state.phone)
        }
    }
}
```

---

## 与其它技能的串联

| 阶段 | 技能 | 用途 |
|------|------|------|
| API 生成 | `swagger-to-kotlin` | 如果登录/验证码接口未生成，先调用此技能 |
| UI 重构 | `android-compose-expert` | Compose 路径完成后，按 Refactoring Mode 验证分层 |
| 测试审计 | `test-case-audit` | 登录流程完成后审计测试覆盖 |
| 全流程编排 | `project-bootstrap` | 作为 Phase 4 业务场景的一部分被自动激活 |

---

## 技能交接

登录流程完成后：
1. 如果登录后需要 KYC 认证 → 自动激活 `kyc-workflow`
2. 如果有多渠道构建需求 → 自动激活 `android-multi-flavor-google`
3. 测试覆盖检查 → 自动激活 `test-case-audit`

---

## References

- `references/flow.md` — 完整实现细节（ViewModel 模板、UI 组件、异常处理、持久化接口）
