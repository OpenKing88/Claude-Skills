/**
 * BaseViewModel — 网络请求统一处理基类
 *
 * 业务目的：
 *   封装协程启动 + Loading控制 + 异常处理，供所有KYC ViewModel继承复用。
 *
 * 教学重点：
 *   1. 理解网络请求统一处理机制的作用（Loading控制、异常分类）
 *   2. 该项目优先复用现有机制，此处仅为最小参考实现
 *   3. 异常分类：AuthException → 跳转登录; BusinessException → 业务错误提示; IOException → 网络异常
 */
open class BaseViewModel : ViewModel() {

    protected val _toast = MutableSharedFlow<String>()
    val toast = _toast.asSharedFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asSharedFlow()

    /**
     * 启动协程并统一处理异常
     * @param showLoading 是否显示全局 Loading，默认 true
     *
     * ⚠️ 交互时机: 每个需要发起网络请求的页面调用此方法
     * ⚠️ 流程规则: showLoading=true时请求开始显示加载，完成后隐藏
     */
    protected fun launchRequest(
        showLoading: Boolean = true,
        block: suspend CoroutineScope.() -> Unit
    ) {
        viewModelScope.launch {
            try {
                // ⚠️ 流程规则: 请求开始时显示全局Loading，提示用户等待
                if (showLoading) _loading.value = true
                block()
            } catch (e: Exception) {
                // ⚠️ 流程规则: 根据异常类型分类处理
                when (e) {
                    // ⚠️ 交互时机: 401/AuthException → 用户凭证过期，跳转登录页
                    is AuthException -> { /* 401：跳转登录 */ }
                    // ⚠️ 交互时机: BusinessException → 后端业务错误(如校验失败)，以Toast展示业务错误信息
                    is BusinessException -> _toast.emit(e.message ?: "请求失败")
                    // ⚠️ 交互时机: IOException → 网络连接失败，提示用户检查网络
                    is IOException -> _toast.emit("网络异常，请检查网络")
                    // ⚠️ 交互时机: 其他异常 → 兜底提示
                    else -> _toast.emit("请求失败：${e.message}")
                }
            } finally {
                // ⚠️ 流程规则: 请求完成后（无论成功/失败）隐藏Loading
                if (showLoading) _loading.value = false
            }
        }
    }
}
