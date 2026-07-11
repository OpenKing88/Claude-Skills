/**
 * Step 3: 身份证认证 — KYC进件流程第三步
 *
 * 业务目的：
 *   验证用户真实身份，通过证件OCR自动提取信息。
 *
 * 教学重点：
 *   1. 用户点击上传区域 → 弹出选择弹窗（拍照/图库）
 *   2. 获得图片后调用OCR接口识别
 *   3. OCR自动回填表单字段，但允许用户手动修改
 *   4. 提交前必须有确认弹窗，防止OCR错误导致数据错误
 *   5. 图片压缩必须在IO线程执行（Dispatchers.IO）
 *   6. 图片回显使用Coil/Glide加载URI，禁止BitmapFactory.decode直接解码
 *   7. 提交接口使用步骤标识 t3e8L=3
 */

// ============================================================
// State
// ============================================================

/** 身份证认证页面状态 */
data class KycIdentityState(
    /** 拍照后的图片 URI */
    val capturedImageUri: Uri? = null,
    /** 是否正在上传/识别中 */
    val isUploading: Boolean = false,
    /** 是否已上传成功（显示照片而非空态） */
    val hasPhoto: Boolean = false,
    /** OCR 识别结果 */
    val ocrResult: OcrResult? = null,
    /** 是否展开表单字段 */
    val showFormFields: Boolean = false,
    /** 姓名 */
    val firstName: String = "",
    /** 父姓 */
    val paternalSurname: String = "",
    /** 母姓 */
    val maternalSurname: String = "",
    /** 身份证号 */
    val idNumber: String = "",
    /** 校验失败的字段 key 集合 */
    val errorFields: Set<String> = emptySet(),
    /** 错误提示文本 */
    val errorMessages: Map<String, String> = emptyMap(),
    /** 是否显示拍照/图库选择弹窗 */
    val showPhotoChoiceDialog: Boolean = false,
    /** 服务器返回的图片 URL */
    val imageUrl: String? = null,
    /** 是否显示信息确认弹窗 */
    val showConfirmDialog: Boolean = false,
)

data class OcrResult(
    val firstName: String,
    val paternalSurname: String,
    val maternalSurname: String,
    val idNumber: String,
    val imageUrl: String?,
)

// ============================================================
// Action / Event
// ============================================================

sealed interface KycIdentityAction {
    data object OnBackClick : KycIdentityAction
    data object OnCameraClick : KycIdentityAction
    data object OnPhotoChoiceDismiss : KycIdentityAction
    data object OnTakePhotoClick : KycIdentityAction
    data object OnGalleryClick : KycIdentityAction
    data class OnImageCaptured(val uri: Uri) : KycIdentityAction
    data class OnImageSelected(val uri: Uri) : KycIdentityAction
    data class OnFirstNameChange(val value: String) : KycIdentityAction
    data class OnPaternalSurnameChange(val value: String) : KycIdentityAction
    data class OnMaternalSurnameChange(val value: String) : KycIdentityAction
    data class OnIdNumberChange(val value: String) : KycIdentityAction
    data object OnSubmitClick : KycIdentityAction
    data object OnConfirmDialogDismiss : KycIdentityAction
    data object OnConfirmDialogSubmit : KycIdentityAction
}

sealed interface KycIdentityEvent {
    data object NavigateBack : KycIdentityEvent
    data object NavigateToNext : KycIdentityEvent
    data object LaunchCamera : KycIdentityEvent
    data object LaunchGallery : KycIdentityEvent
}

// ============================================================
// ViewModel
// ============================================================

class KycIdentityViewModel : BaseViewModel() {

    private val mState = MutableStateFlow(KycIdentityState())
    val state = mState.asStateFlow()

    private val mEvent = MutableSharedFlow<KycIdentityEvent>()
    val event = mEvent.asSharedFlow()

    fun onAction(action: KycIdentityAction) {
        viewModelScope.launch {
            when (action) {
                is OnBackClick -> mEvent.emit(NavigateBack)

                is OnCameraClick -> {
                    // ⚠️ 交互时机: 点击上传区域 → 弹出拍照/图库选择弹窗
                    mState.update { copy(showPhotoChoiceDialog = true) }
                }

                is OnTakePhotoClick -> {
                    mState.update { copy(showPhotoChoiceDialog = false) }
                    mEvent.emit(LaunchCamera)
                }

                is OnGalleryClick -> {
                    mState.update { copy(showPhotoChoiceDialog = false) }
                    mEvent.emit(LaunchGallery)
                }

                is OnImageCaptured, is OnImageSelected -> {
                    // ⚠️ 交互时机: 获得图片URI后 → 调用OCR接口识别
                    // ⚠️ 流程规则: 压缩必须是Dispatchers.IO → ocrVerify → 自动回填表单
                    val uri = (action as? OnImageCaptured)?.uri ?: (action as OnImageSelected).uri
                    mState.update { copy(capturedImageUri = uri, isUploading = true) }
                    【网络请求】 { performOcr(uri) }
                }

                is OnSubmitClick -> {
                    val errors = validate()
                    if (errors.isNotEmpty()) {
                        mState.update { copy(errorMessages = errors) }
                        return@launch
                    }
                    // ⚠️ 交互时机: 校验通过 → 显示确认弹窗（二次确认减少OCR错误）
                    // ⚠️ 流程规则: 用户确认后才真正提交，点击"修改"可关闭弹窗继续编辑
                    mState.update { copy(showConfirmDialog = true) }
                }

                is OnConfirmDialogSubmit -> {
                    // ⚠️ 交互时机: 用户在确认弹窗中点击"确认" → 提交数据
                    mState.update { copy(showConfirmDialog = false) }
                    【网络请求】 { submitData() }
                }

                is OnConfirmDialogDismiss -> {
                    // ⚠️ 交互时机: 用户点击"修改" → 关闭确认弹窗，返回编辑
                    // ⚠️ 流程规则: 确认弹窗不可外部取消（canceledOnTouchOutside = false）
                    mState.update { copy(showConfirmDialog = false) }
                }

                else -> { /* 文本变化直接更新 state */ }
            }
        }
    }

    private suspend fun performOcr(imageUri: Uri) {
        // ⚠️ 接口调用: 压缩图片 → 获取定位 → OCR识别 → 自动回填表单
        // ⚠️ 流程规则: compress必须在Dispatchers.IO上执行，file I/O + Bitmap decode都是阻塞操作
        val bytes = imageCompressor.compress(imageUri) ?: throw IllegalStateException("压缩失败")
        /** 获取定位 */
        val (lat, lng) = locationProvider.getLocation()

        val result = apiService.ocrVerify(
            type = "FRONT",
            latitude = lat?.toString(),
            longitude = lng?.toString(),
            image = bytes
        )

        // ⚠️ 交互时机: OCR成功后自动填充表单，用户可手动修改
        mState.update {
            copy(
                isUploading = false,
                hasPhoto = true,
                ocrResult = result,
                showFormFields = true,
                firstName = result.firstName,
                paternalSurname = result.paternalSurname,
                maternalSurname = result.maternalSurname,
                idNumber = result.idNumber,
                imageUrl = result.imageUrl,
            )
        }
    }

    private suspend fun submitData() {
        // ⚠️ 接口调用: t3e8L=3 表示身份证步骤
        val s = state.value
        val items = listOf(
            AcqDataItem(FIRST_NAME_FIELD, s.firstName),
            AcqDataItem(PATERNAL_FIELD, s.paternalSurname),
            AcqDataItem(MATERNAL_FIELD, s.maternalSurname),
            AcqDataItem(ID_NUMBER_FIELD, s.idNumber),
            AcqDataItem(PHOTO_URL_FIELD, s.imageUrl ?: ""),
        )
        apiService.submitAcqData(SubmitAcqDataReq(t3e8L = 3, items = items))
        mEvent.emit(NavigateToNext)
    }
}
