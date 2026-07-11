[← Step 5: 信用信息](step-5-credit-info.md) | [→ 接口占位代码](api-patterns.md)

# Step 6: 人脸识别

### 业务目的
通过活体检测确认用户本人操作，防止身份冒用。

### 标准流程
```
1. 查询人脸渠道配置（后端决定使用哪种渠道）
   ↓
2a. 自研渠道：启动前置相机 → 用户拍照 → 上传后端比对
   ↓
2b. 第三方渠道（Face++）：获取 BizToken → 调起 SDK → SDK 完成活体检测 → 回调结果
   ↓
3. 比对成功 → 进入贷款确认页
```

### 渠道适配（可选）

人脸识别支持两种渠道，**根据后端配置动态选择**。如果项目不需要三方 SDK 或后端始终返回自研渠道，可只实现自研方案。

| 渠道类型 | 技术方案 | 是否可选 | 特点 |
|---------|---------|---------|------|
| 自研 | CameraX + 后端人脸比对 | ❌ 必选（兜底方案） | 前端拍照，后端算法比对 |
| Face++ 等三方 | SDK 负责活体检测 | ✅ **可选** | 前端只需调起和接收回调，体验更流畅 |

**决策逻辑**：
1. 先调用渠道查询接口（如果项目有此接口）获取 `channel` 值
2. `channel == "3"` → 走 Face++ 渠道（如果已集成该 SDK）
3. 其他值 / 接口不存在 → 走自研渠道
4. Face++ 获取 Token 失败 / SDK 回调 errToYc → **自动降级到自研**

### 非 KYC 模式支持

人脸识别页面通常也需要支持非 KYC 场景调用（如账户安全认证、操作二次确认等），通过路由参数 `isKyc` 区分：

| 模式 | 路由参数 | 成功行为 | 失败/返回行为 |
|------|---------|---------|-------------|
| KYC 模式 | `isKyc = true`（默认） | 触发风控埋点上传 → 跳转贷款确认页 | 挽留弹窗 → 回传失败结果 |
| 非 KYC 模式 | `isKyc = false` | 回传 `true` 到 `savedStateHandle` → `popBackStack` | 回传 `false` 到 `savedStateHandle` → `popBackStack` |

**典型路由定义**：
```kotlin
@Serializable
data class KycFaceRecognitionRoute(val isKyc: Boolean = true)
```

**结果回传契约**：
```kotlin
/** 人脸认证结果回传 Key：非 KYC 模式下调用方通过 savedStateHandle 读取 */
const val FACE_RECOGNITION_RESULT_KEY = "face_recognition_result"

// 非 KYC 模式下成功时回传
nav.setPreviousSavedStateHandle(FACE_RECOGNITION_RESULT_KEY, true)
nav.popBackStack()

// 调用方读取结果
val result = navController.previousBackStackEntry?.savedStateHandle
    ?.get<Boolean>(FACE_RECOGNITION_RESULT_KEY)
```

**非 KYC 模式下的行为差异**：
- 不展示挽留弹窗（直接回传失败结果并返回）
- 比对成功后不回传风控埋点，直接回传结果并关闭页面
- BackHandler 直接返回而非触发挽留

### 完整状态模板

```kotlin
enum class ScreenMode {
    /** 自研相机渠道 */
    InHouse,
    /** Face++ 第三方渠道 */
    FacePlusPlus,
}

enum class FaceScreenState {
    /** 初始状态：展示提示信息 + 倒计时 */
    Initial,
    /** 相机预览状态 */
    Camera,
    /** 识别中 */
    Recognizing,
    /** 识别完成 */
    Completed,
}

/** 人脸识别页面状态 */
data class KycFaceState(
    val screenState: FaceScreenState = FaceScreenState.Initial,
    val screenMode: ScreenMode = ScreenMode.InHouse,
    /** 拍摄后的图片 URI */
    val capturedImageUri: Uri? = null,
    /** 人脸渠道：1=accu, 2=yc, 3=facePlusPlus */
    val faceChannel: String? = null,
    /** 是否正在查询渠道 */
    val isCheckingChannel: Boolean = false,
    /** 倒计时剩余秒数 */
    val countdownSeconds: Int = 6,
    /** 是否正在倒计时 */
    val isCountdownActive: Boolean = true,
    /** 挽留弹窗是否显示 */
    val isRetainDialogVisible: Boolean = false,
)

sealed interface KycFaceAction {
    data object OnBackClick : KycFaceAction
    data class OnPhotoTaken(val uri: Uri) : KycFaceAction
    data object OnRetryClick : KycFaceAction
    data object OnStartVerificationClick : KycFaceAction
    /** Screen 层权限通过后回调 */
    data object OnPermissionGranted : KycFaceAction
    data object OnRetainDialogShown : KycFaceAction
    data object OnRetainDialogDismissed : KycFaceAction
}

sealed interface KycFaceEvent {
    data object NavigateBack : KycFaceEvent
    data object NavigateToNext : KycFaceEvent
    /** Screen 层触发权限申请 */
    data object RequestPermission : KycFaceEvent
}
```

### ViewModel 核心逻辑模板

```kotlin
class KycFaceViewModel : BaseViewModel() {

    private val mState = MutableStateFlow(KycFaceState())
    val state = mState.asStateFlow()

    private val mEvent = MutableSharedFlow<KycFaceEvent>()
    val event = mEvent.asSharedFlow()

    private var countdownJob: Job? = null

    init { startCountdown() }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            mState.update { copy(isCountdownActive = true, countdownSeconds = 6) }
            var remaining = 6
            while (remaining > 0) {
                delay(1000L)
                remaining--
                mState.update { copy(countdownSeconds = remaining) }
            }
            if (!mState.value.isRetainDialogVisible) {
                mEvent.emit(RequestPermission)
            }
        }
    }

    fun onAction(action: KycFaceAction) {
        viewModelScope.launch {
            when (action) {
                is OnBackClick -> mEvent.emit(NavigateBack)

                is OnPhotoTaken -> {
                    mState.update {
                        copy(capturedImageUri = action.uri, screenState = Recognizing)
                    }
                    【网络请求】 { uploadAndCompare(action.uri) }
                }

                is OnRetryClick -> {
                    mState.update { copy(screenState = Camera, capturedImageUri = null) }
                }

                is OnStartVerificationClick -> {
                    countdownJob?.cancel()
                    mState.update { copy(isCountdownActive = false, countdownSeconds = 0) }
                    mEvent.emit(RequestPermission)
                }

                is OnPermissionGranted -> {
                    checkFaceChannel()
                }

                is OnRetainDialogShown -> {
                    mState.update { copy(isRetainDialogVisible = true) }
                }

                is OnRetainDialogDismissed -> {
                    mState.update { copy(isRetainDialogVisible = false) }
                }
            }
        }
    }

    /** 查询人脸渠道，决定走自研还是 Face++ */
    private fun checkFaceChannel() = 【网络请求】 {
        mState.update { copy(isCheckingChannel = true) }
        try {
            val resp = apiService.getFaceChannel()
            val channel = resp.channel

            if (channel == "3") {
                mState.update { copy(faceChannel = "3", screenMode = FacePlusPlus) }
                fetchFacePlusPlusTokenAndStart()
            } else {
                mState.update {
                    copy(
                        faceChannel = channel ?: "2",
                        screenMode = InHouse,
                        screenState = Camera
                    )
                }
            }
        } finally {
            mState.update { copy(isCheckingChannel = false) }
        }
    }

    /** Face++ 渠道：获取 Token 并启动 SDK */
    private suspend fun fetchFacePlusPlusTokenAndStart() {
        val tokenResp = apiService.getFacePlusPlusToken()
        val host = tokenResp.host
        val bizToken = tokenResp.bizToken

        if (host.isNullOrBlank() || bizToken.isNullOrBlank()) {
            errToYc() // 降级到自研
            return
        }

        val completer = CompletableDeferred<String?>()
        var isCancelledByUser = false

        FaceSdkTools.startFaceTanCheck(
            context = appContext,
            inBizToken = bizToken,
            host = host,
            sucCallBack = { completer.complete(it) },
            errToYcCallBack = { completer.complete(null) },
            errToast = { completer.completeExceptionally(RuntimeException("errToast")) },
            onCancelCallBack = {
                isCancelledByUser = true
                completer.complete(null)
            }
        )

        val deferredResult = runCatching {
            withTimeout(60_000L) { completer.await() }
        }

        when {
            isCancelledByUser -> {
                mState.update { copy(screenState = Initial) }
            }
            deferredResult.getOrNull() != null -> {
                faceCompareWithBizToken(deferredResult.getOrNull()!!)
            }
            deferredResult.isFailure -> {
                _toast.emit("验证失败，请重试")
                mState.update { copy(screenState = Initial) }
            }
            else -> errToYc()
        }
    }

    private suspend fun faceCompareWithBizToken(bizToken: String) {
        val result = apiService.faceCompare(
            channel = "3",
            bizToken = bizToken,
            step = "6"
        )
        if (result.success == 1) {
            delay(1500)
            mEvent.emit(NavigateToNext)
        } else {
            _toast.emit("验证失败，请重试")
            mState.update { copy(screenState = Initial) }
        }
    }

    /** 自研渠道：压缩图片并调用比对 */
    private suspend fun uploadAndCompare(imageUri: Uri) {
        val bytes = imageCompressor.compress(imageUri)
            ?: throw IllegalStateException("图片压缩失败")
        val (lat, lng) = locationProvider.getLocation()

        val result = apiService.faceCompare(
            channel = state.value.faceChannel ?: "2",
            latitude = lat?.toString(),
            longitude = lng?.toString(),
            imageBytes = bytes,
            step = "6"
        )
        if (result.success == 1) {
            mState.update { copy(screenState = Completed) }
            delay(1500)
            mEvent.emit(NavigateToNext)
        } else {
            _toast.emit("验证失败，请重试")
            mState.update { copy(screenState = Initial, capturedImageUri = null) }
        }
    }

    /** 降级到自研渠道 */
    private fun errToYc() {
        mState.update {
            copy(
                faceChannel = "2",
                screenMode = InHouse,
                screenState = Initial,
            )
        }
    }
}
```

### 实现要点
- 必须先查询后端配置决定使用哪种渠道，不应前端写死
- 自研渠道需要：相机权限申请、取景框 UI、快门拍照、图片压缩上传
- Face++ 渠道需要：获取 BizToken → 调起 SDK → 处理回调结果 → 用 finalBizToken 调比对接口
- **降级机制**：Face++ Token 获取失败 / SDK errToYc 回调时，自动降级到自研渠道
- 通常有倒计时机制（如"6 秒后开始验证"）给用户阅读提示
- 提示文案通常包括：不遮挡面部、保持手机稳定、不在暗处、过程中不退出

### 典型接口模式
```
GET /face/channel       — 查询人脸认证渠道配置
GET /face++/token       — 获取 Face++ SDK Token（如使用 Face++）
POST /face/compare      — 人脸比对（自研 / Face++ 均走此接口）
```

---
