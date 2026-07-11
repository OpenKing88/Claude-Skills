[← Step 2: 联系人信息](step-2-contact-info.md) | [→ Step 4: 银行卡信息](step-4-bank-card.md)

# Step 3: 身份证认证

### 业务目的
验证用户真实身份，通过证件 OCR 自动提取信息。

### 标准流程
```
1. 用户点击上传区域
   ↓
2. 弹出选择弹窗（拍照 / 图库）
   ↓
3a. 选择"拍照" → 跳转自定义相机页面（CameraX）
   ↓
3b. 选择"图库" → 系统图库选择器
   ↓
4. 获得图片后 → 调用 OCR 接口识别
   ↓
5. OCR 自动回填表单字段（姓名、父姓、母姓、身份证号）
   ↓
6. 用户可手动修改 OCR 结果
   ↓
7. 提交前弹出确认弹窗（二次确认减少错误）
   ↓
8. 确认后提交进件数据
```

### 标准字段
| 字段语义 | 来源 | 可编辑 |
|---------|------|--------|
| 姓名 | OCR 自动回填 | 是 |
| 父姓 | OCR 自动回填 | 是 |
| 母姓 | OCR 自动回填 | 是（部分项目可能没有） |
| 身份证号 | OCR 自动回填 | 是 |
| 证件照片 | 用户上传 | 否（需重新拍照） |

### 完整状态模板

```kotlin
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
```

### ViewModel 核心逻辑模板

```kotlin
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
                    mState.update { copy(showConfirmDialog = true) }
                }

                is OnConfirmDialogSubmit -> {
                    mState.update { copy(showConfirmDialog = false) }
                    【网络请求】 { submitData() }
                }

                is OnConfirmDialogDismiss -> {
                    mState.update { copy(showConfirmDialog = false) }
                }

                else -> { /* 文本变化直接更新 state */ }
            }
        }
    }

    private suspend fun performOcr(imageUri: Uri) {
        /** 压缩图片 */
        val bytes = imageCompressor.compress(imageUri) ?: throw IllegalStateException("压缩失败")
        /** 获取定位 */
        val (lat, lng) = locationProvider.getLocation()

        val result = apiService.ocrVerify(
            type = "FRONT",
            latitude = lat?.toString(),
            longitude = lng?.toString(),
            image = bytes
        )

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
```

### 实现要点
- OCR 接口通常需要同时传：图片字节、经度、纬度、证件面类型（FRONT/BACK）
- OCR 返回的字段名是混淆的，**必须通过接口 KDoc 注释理解每个字段的含义**
- 页面应展示正确示例和错误示例（边框不完整、模糊、反光）引导用户拍摄
- 提交前必须有确认弹窗，防止 OCR 错误导致数据错误
- 自定义相机页面通常使用 CameraX，支持闪光灯、前后置切换
- **压缩失败处理**：绝不 fallback 原图字节，避免 OOM

### 典型接口模式
```
POST /ocr/verify        — 上传身份证图片 + OCR 识别
POST /acq/submit        — 提交身份证信息（步骤标识=3）
```

---
