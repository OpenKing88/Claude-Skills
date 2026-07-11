/**
 * Step 6: 人脸识别 — KYC进件流程第六步（必选）
 *
 * 业务目的：
 *   通过活体检测确认用户本人操作，防止身份冒用。
 *
 * 教学重点：
 *   1. 查询后端配置决定使用哪种渠道（自研/Face++），不应前端写死
 *   2. channel="3" → Face++渠道，其他值/不存在 → 自研渠道
 *   3. Face++流程：getToken → FaceSdkTools.startFaceTanCheck → CompletableDeferred(60s timeout) → faceCompare
 *   4. 降级机制：Token为空/SDK errToYc/超时 → 自动降级到自研
 *   5. 倒计时机制（6s后自动请求相机权限）
 *   6. 双模式：isKyc标志决定成功行为（KYC: 上传风控+跳转, 非KYC: savedStateHandle结果+popBackStack）
 *   7. KYC模式BackHandler显示挽留弹窗，非KYC模式直接返回
 */

// ============================================================
// State
// ============================================================

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

// ============================================================
// Action / Event
// ============================================================

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

// ============================================================
// ViewModel
// ============================================================

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
            // ⚠️ 交互时机: 倒计时结束 → 自动请求相机权限
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
                    // ⚠️ 接口调用: 拍照后压缩并上传比对
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
                    // ⚠️ 流程规则: 权限通过后查询人脸渠道
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
            // ⚠️ 接口调用: getFaceChannel() 获取渠道配置
            val resp = apiService.getFaceChannel()
            val channel = resp.channel

            // ⚠️ 流程规则: channel="3" → Face++, 其他 → 自研
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
        // ⚠️ 接口调用: 获取Face++ Token
        val tokenResp = apiService.getFacePlusPlusToken()
        val host = tokenResp.host
        val bizToken = tokenResp.bizToken

        // ⚠️ 降级机制: Token null/empty → 降级到自研
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
            // ⚠️ 降级机制: SDK errToYc回调 → 降级到自研
            errToYcCallBack = { completer.complete(null) },
            errToast = { completer.completeExceptionally(RuntimeException("errToast")) },
            onCancelCallBack = {
                isCancelledByUser = true
                completer.complete(null)
            }
        )

        // ⚠️ 交互时机: CompletableDeferred + 60s超时等待SDK回调
        val deferredResult = runCatching {
            withTimeout(60_000L) { completer.await() }
        }

        when {
            isCancelledByUser -> {
                mState.update { copy(screenState = Initial) }
            }
            deferredResult.getOrNull() != null -> {
                // ⚠️ 接口调用: faceCompare(channel=3, bizToken) 完成比对
                faceCompareWithBizToken(deferredResult.getOrNull()!!)
            }
            deferredResult.isFailure -> {
                // ⚠️ 降级机制: 超时 → Toast提示 + 降级到自研
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
