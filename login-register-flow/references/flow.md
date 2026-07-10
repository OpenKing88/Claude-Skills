# 登录注册流程 — 完整实现参考

## 一、项目框架适配检查清单

AI 必须在开始实现前扫描项目并填充此清单：

### 1.1 网络层
- [ ] 网络框架：`___Retrofit___`
- [ ] API Service 访问方式：`___单例/DI___`
- [ ] 响应包装器：`___ApiBaseResponseBean<T>___`
- [ ] 成功判断：`___code == 200___` / Token 过期：`___code == 401___`
- [ ] 请求头注入方式：`___OkHttp Interceptor___`
- [ ] 数据加密：`___AES 拦截器___`

### 1.2 持久化
- [ ] 持久化方案：`___SharedPreferences / DataStore___`
- [ ] Token 读写方式：`___DeauvijasuVawhemp.zurodikosasi___`
- [ ] 协程/异步支持：`___无（同步 SP）___`

### 1.3 导航
- [ ] 导航框架：`___Compose Navigation___` / `___Fragment Navigation___`
- [ ] 路由定义：`___类型安全 @Serializable___` / `___字符串常量___`

### 1.4 状态管理
- [ ] ViewModel 基类：`___ViewModel()___`（是否带 launchLoading）
- [ ] 状态方式：`___MutableStateFlow___` / `___MutableState___`
- [ ] 事件方式：`___SharedFlow___` / `___Channel___`
- [ ] Toast/Loading 管理：`___BaseViewModel 基类___`

### 1.5 UI 组件
- [ ] UI 框架：`___Compose___` / `___View/XML___`
- [ ] 按钮组件：`___Box + clickable___`
- [ ] 输入框组件：`___BasicTextField + 自定义装饰___`
- [ ] 主题色/字体：`___MaterialTheme___`

---

## 二、数据模型

### 2.1 页面状态（全可空字段）

```kotlin
/** Login 页面状态 */
data class LoginState(
    var phone: String = "",
    var privacyChecked: Boolean = true,
    var showError: Boolean = false,
)

sealed interface LoginAction {
    data class PhoneChanged(val phone: String) : LoginAction
    data object ClearPhone : LoginAction
    data object PrivacyToggled : LoginAction
    data object Submit : LoginAction
}

sealed interface LoginEvent {
    data class NavigateToOtp(val phone: String) : LoginEvent
    data object OpenPrivacyPolicy : LoginEvent
}

/** OTP 验证码页面状态 */
data class OtpState(
    var phone: String = "",
    var code: String = "",
    var smsCountdown: Int = 0,
    var voiceCountdown: Int = 0,
    var hasError: Boolean = false,
)

sealed interface OtpAction {
    data class CodeChanged(val code: String) : OtpAction
    data object Resend : OtpAction
    data object RequestVoice : OtpAction
}

sealed interface OtpEvent {
    data object NavigateToMain : OtpEvent
}
```

### 2.2 API DTO（全可空字段）

```kotlin
data class SendCodeResult(
    var success: Boolean? = null,
    var countdownSeconds: Int? = null,
    var message: String? = null,
)

data class LoginResult(
    var token: String? = null,
    var isFirstRegister: Int? = null,
    var isAuditAccount: Int? = null,
)
```

---

## 三、ViewModel 核心逻辑

### 3.1 LoginViewModel

业务逻辑核心（以下代码为框架无关模板，`【】` 标注部分需替换为项目实际方案）：

```kotlin
class LoginViewModel : /*【替换为 BaseViewModel】*/ ViewModel() {

    private val _state = /*【替换为 MutableStateFlow/MutableState】*/ MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    private var sentPhone: String? = null

    private val _event = /*【替换为 MutableSharedFlow/Channel】*/ MutableSharedFlow<LoginEvent>()
    val event = _event.asSharedFlow()

    private val _otpState = MutableStateFlow(OtpState())
    val otpState = _otpState.asStateFlow()

    private val _otpEvent = MutableSharedFlow<OtpEvent>()
    val otpEvent = _otpEvent.asSharedFlow()

    fun onAction(action: LoginAction) = when (action) {
        is LoginAction.PhoneChanged -> {
            val filtered = action.phone.filter { it.isDigit() }.take(PHONE_LENGTH)
            _state.update { it.copy(phone = filtered, showError = false) }
        }
        is LoginAction.ClearPhone -> _state.update { it.copy(phone = "", showError = false) }
        is LoginAction.PrivacyToggled -> _state.update { it.copy(privacyChecked = !it.privacyChecked) }
        is LoginAction.Submit -> submitPhone()
    }

    private fun submitPhone() {
        val s = _state.value
        if (s.phone.length != PHONE_LENGTH) {
            _state.update { it.copy(showError = true) }
            return
        }
        if (!s.privacyChecked) {
            viewModelScope.launch { /*【Toast】*/ }
            return
        }
        if (sentPhone == s.phone && _otpState.value.smsCountdown > 0) {
            viewModelScope.launch { _event.emit(LoginEvent.NavigateToOtp(s.phone)) }
            return
        }
        /*【网络请求（带 Loading + 异常处理）】*/
        viewModelScope.launch {
            try {
                /*【showLoading(true)】*/
                /*【apiService.sendCode(phone, scene=1, type=1)】*/
                _otpState.update { it.copy(code = "", hasError = false, smsCountdown = 60, voiceCountdown = 0) }
                startSmsCountdown()
                sentPhone = s.phone
                _event.emit(LoginEvent.NavigateToOtp(s.phone))
            } catch (e: Exception) {
                /*【handleException(e)】*/
            } finally {
                /*【showLoading(false)】*/
            }
        }
    }

    // OTP 操作
    fun initOtp(phone: String) {
        if (_otpState.value.phone.isNotEmpty()) return
        _otpState.update { it.copy(phone = phone) }
        startSmsCountdown()
    }

    fun onOtpAction(action: OtpAction) {
        when (action) {
            is OtpAction.CodeChanged -> {
                val code = action.code.filter { it.isDigit() }.take(CODE_LENGTH)
                val wasComplete = _otpState.value.code.length == CODE_LENGTH
                _otpState.update { it.copy(code = code, hasError = false) }
                if (code.length == CODE_LENGTH && !wasComplete) submitOtp()
            }
            is OtpAction.Resend -> resendSms()
            is OtpAction.RequestVoice -> requestVoiceCode()
        }
    }

    private fun submitOtp() {
        val s = _otpState.value
        viewModelScope.launch {
            try {
                /*【showLoading(true)】*/
                /*【val result = apiService.registerOrLogin(phone, s.code)】*/
                // 判断成功：
                // result.token?.let { /*【tokenStorage.save(it)】*/ }
                // if (result.isFirstRegister == 1) { /*【isFirstRegisterStorage.save(true)】*/ }
                _otpEvent.emit(OtpEvent.NavigateToMain)
            } catch (e: Exception) {
                /*【handleException(e)】*/
            } finally {
                /*【showLoading(false)】*/
            }
        }
    }

    // 倒计时（同屏两个独立倒计时）
    private var smsJob: Job? = null
    private var voiceJob: Job? = null

    private fun startSmsCountdown() {
        smsJob?.cancel()
        smsJob = viewModelScope.launch {
            while (_otpState.value.smsCountdown > 0) {
                delay(1000)
                _otpState.update { it.copy(smsCountdown = (it.smsCountdown - 1).coerceAtLeast(0)) }
            }
        }
    }

    // ...重发/语音/取消逻辑（同模式）

    override fun onCleared() {
        super.onCleared()
        smsJob?.cancel()
        voiceJob?.cancel()
    }

    companion object {
        const val PHONE_LENGTH = 10
        const val CODE_LENGTH = 6
    }
}
```

### 3.2 退出/注销

```kotlin
fun logout() {
    viewModelScope.launch {
        try {
            /*【showLoading(true)】*/
            /*【apiService.logout()】*/
        } catch (_: Exception) { } finally {
            /*【tokenStorage.clear()】*/
            /*【accountStorage.clear()】*/
            /*【showLoading(false)】*/
            _event.emit(NavigateToLogin)
        }
    }
}

fun cancelAccount() {
    viewModelScope.launch {
        try {
            /*【showLoading(true)】*/
            /*【apiService.cancelAccount()】*/
        } catch (_: Exception) { } finally {
            /*【tokenStorage.clear()】*/
            /*【accountStorage.clear()】*/
            /*【showLoading(false)】*/
            _event.emit(NavigateToLogin)
        }
    }
}
```

---

## 四、启动页登录态判断

```kotlin
class SplashViewModel : /*【BaseViewModel】*/ ViewModel() {

    private val _event = MutableSharedFlow<SplashEvent>()
    val event = _event.asSharedFlow()

    fun checkLoginState() {
        viewModelScope.launch {
            val token = /*【tokenStorage.get()】*/
            if (token.isNullOrEmpty()) {
                _event.emit(SplashEvent.NavigateToLogin)
            } else {
                _event.emit(SplashEvent.NavigateToMain)
            }
        }
    }
}
```

---

## 五、Main 页首次注册处理

```kotlin
class MainViewModel : /*【BaseViewModel】*/ ViewModel() {

    init {
        viewModelScope.launch {
            if (/*【isFirstRegisterStorage.get()】*/ == true) {
                /*【isFirstRegisterStorage.save(false)】*/
                _event.emit(MainEvent.NavigateToProfileFill)
            }
            // 上报推送 Token（如有）
            val pushToken = /*【pushTokenStorage.get()】*/
            if (pushToken != null) {
                /*【apiService.updatePushToken(...)】*/
            }
        }
    }
}
```

---

## 六、UI Screen 双路径实现

### 6.1 Compose 路径

**必须按 stateful-wrapper + stateless-content 模式构建：**

```kotlin
// ── Stateful Wrapper ──
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = /* hiltViewModel() / viewModel() */,
    onNavigateToOtp: (String) -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is LoginEvent.NavigateToOtp -> onNavigateToOtp(event.phone)
                is LoginEvent.OpenPrivacyPolicy -> onOpenPrivacy()
            }
        }
    }
    LoginScreenContent(state = state, onAction = viewModel::onAction)
}

// ── Stateless Content ──
@Composable
fun LoginScreenContent(
    state: LoginState,
    onAction: (LoginAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().background(Color.White),
    ) {
        // 欢迎文案
        Text("欢迎登录/注册")

        // 手机号输入
        PhoneInputField(
            phone = state.phone,
            showError = state.showError,
            onPhoneChanged = { onAction(LoginAction.PhoneChanged(it)) },
            onClear = { onAction(LoginAction.ClearPhone) },
        )
        if (state.showError) Text("手机号格式错误", color = MaterialTheme.colorScheme.error)

        // 隐私协议
        PrivacyCheckRow(
            checked = state.privacyChecked,
            onToggle = { onAction(LoginAction.PrivacyToggled) },
        )

        // 提交按钮（600ms 防重节流）
        Button(onClick = { onAction(LoginAction.Submit) }) { Text("获取验证码") }
    }
}

// ── Preview ──
@Preview(name = "Normal", showBackground = true)
@Composable
private fun LoginScreenContentPreview() {
    PreviewTheme { LoginScreenContent(LoginState(), {}) }
}

@Preview(name = "Error", showBackground = true)
@Composable
private fun LoginScreenContentErrorPreview() {
    PreviewTheme { LoginScreenContent(LoginState(showError = true), {}) }
}
```

**OTP 6 位验证码输入（透明 TextField 覆盖方格法）：**

```kotlin
@Composable
fun OtpInputRow(code: String, hasError: Boolean, onCodeChanged: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(6) { i ->
                OtpBox(digit = code.getOrNull(i)?.toString() ?: "",
                    isFocused = isFocused && i == code.length.coerceAtMost(5),
                    hasError = hasError)
            }
        }
        BasicTextField(
            value = code,
            onValueChange = onCodeChanged,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.alpha(0.01f).focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused },
        )
    }
}
```

### 6.2 View System 路径

```kotlin
class LoginFragment : Fragment(R.layout.fragment_login) {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LoginViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLoginBinding.bind(view)
        observeState()
        observeEvents()
        setupListeners()
    }

    private fun observeState() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            binding.phoneInput.setText(state.phone)
            binding.phoneInputLayout.error = if (state.showError) "手机号格式错误" else null
            binding.privacyCheck.isChecked = state.privacyChecked
        }
    }

    private fun observeEvents() {
        viewModel.event.observe(viewLifecycleOwner) { event ->
            when (event) {
                is LoginEvent.NavigateToOtp -> findNavController().navigate(
                    LoginFragmentDirections.actionLoginToOtp(event.phone)
                )
                is LoginEvent.OpenPrivacyPolicy -> openPrivacyUrl()
            }
        }
    }

    private fun setupListeners() {
        binding.phoneInput.doAfterTextChanged { text ->
            viewModel.onAction(LoginAction.PhoneChanged(text?.toString() ?: ""))
        }
        binding.privacyCheck.setOnCheckedChangeListener { _, checked ->
            viewModel.onAction(LoginAction.PrivacyToggled)
        }
        binding.submitBtn.setOnClickListener(throttleFirst(600) {
            viewModel.onAction(LoginAction.Submit)
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

---

## 七、异常处理

| 场景 | 触发条件 | 处理 |
|------|---------|------|
| Token 过期 | API 返回 code=401 | 全局拦截器 → 清除 Token → 弹窗 → 跳转 Login |
| 网络错误 | IOException/超时 | Toast「网络异常，请检查网络」 |
| 验证码错误 | OTP 提交后服务端返回错误 | OtpState.hasError=true，输入框标红，不清除内容 |
| 防重复点击 | 所有按钮 onClick | 600ms 节流（使用项目现有封装或自行实现） |
| 防重复发送 | 同一手机号倒计时内 | sentPhone 检查 → 直接跳转 OTP 页 |

---

## 八、路由定义

### Compose Navigation（类型安全）

```kotlin
@Serializable data object LoginRoute
@Serializable data class OtpRoute(val phone: String)
@Serializable data class MainRoute(val index: Int = 0)

// OTP 成功后清除登录回栈：
navController.navigate(MainRoute()) {
    popUpTo(LoginRoute) { inclusive = true }
}
```

### Fragment Navigation

```kotlin
<navigation android:id="@+id/login_graph" app:startDestination="@id/loginFragment">
    <fragment android:id="@+id/loginFragment" android:name=".login.LoginFragment">
        <action android:id="@+id/actionLoginToOtp" android:destination="@id/otpFragment" />
    </fragment>
    <fragment android:id="@+id/otpFragment" android:name=".login.OtpFragment">
        <argument android:name="phone" app:argType="string" />
        <action android:id="@+id/actionOtpToMain" android:destination="@id/mainFragment"
            app:popUpTo="@id/loginFragment" app:popUpToInclusive="true" />
    </fragment>
</navigation>
```

---

## 九、实现检查清单

### 功能完整性
- [ ] Splash 能正确判断登录态并跳转
- [ ] Login 页能输入手机号并校验长度
- [ ] 隐私协议勾选后才能提交
- [ ] 点击提交后调用发送验证码接口
- [ ] 同一手机号在倒计时内不重复发送，直接跳转
- [ ] OTP 页显示手机号和 6 位输入框
- [ ] 输入满 6 位自动提交
- [ ] 验证码错误时输入框标红
- [ ] 重发按钮有 60s 倒计时，语音验证码独立倒计时
- [ ] 登录成功后存储 Token 和 Account
- [ ] 首次注册标记正确保存和使用
- [ ] 登录成功后清除登录页回栈
- [ ] 退出/注销后清除本地数据并跳转登录页

### 技术方案适配
- [ ] UI 已按项目技术栈选择 Compose 或 View 路径
- [ ] 所有 `【】` 标注的占位已替换为项目实际代码
- [ ] Compose 路径已添加 @Preview + Mock 数据
- [ ] 网络调用使用项目实际 API Service 方法
- [ ] 持久化使用项目现有 SP/DataStore 方案

### 异常处理
- [ ] 401 触发强制退出弹窗
- [ ] 网络错误有 Toast 提示
- [ ] 所有按钮有防重复点击
- [ ] Loading 状态正确显示/隐藏

### 代码质量
- [ ] 所有 DTO 字段可空 `?: null`
- [ ] ViewModel 在 onCleared 中取消倒计时
- [ ] View 路径在 onDestroyView 中置空 binding
