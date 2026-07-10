# KYC 业务流程通用知识

## 概述

KYC（Know Your Customer）是贷款应用中的核心进件流程，用户需依次完成身份认证以获取借款资格。不同项目的技术实现（Compose/XML、路由命名、接口字段名）可能不同，但**业务语义和流程步骤是一致的**。

**核心原则**：实现 KYC 页面时，应**先读接口注释理解字段语义**，再对应到页面功能，而非硬编码字段名。

---

## KYC 标准流程（5-6 步）

```
[入口：登录后 / 首页贷款申请]
    ↓
Step 1: 个人信息（基础资料）
    ↓
Step 2: 联系人信息（紧急联系人）
    ↓
Step 3: 身份证认证（证件上传 + OCR）
    ↓
Step 4: 银行卡信息（收款账户）
    ↓
    ┌───────────────────────────────────┐
    ↓                                   ↓
Step 5: 信用信息                         Step 6: 人脸识别（活体检测）
（可选，由后端 acpElementInfo 配置控制）   （必选）
    └───────────────────┬───────────────┘
                        ↓
                [贷款确认页]
```

### 步骤可选性说明

| 步骤 | 可选 | 判断方式 |
|------|------|---------|
| 个人信息 | ❌ 必选 | 始终展示 |
| 联系人信息 | ❌ 必选 | 始终展示 |
| 身份证认证 | ❌ 必选 | 始终展示 |
| 银行卡信息 | ❌ 必选 | 始终展示 |
| 信用信息 | ✅ **可选** | 调用 `acpElementInfoUsingGET`（进件页面元素查询接口）查询 step=5 是否有返回字段；**接口不返回该步骤或字段为空则跳过** |
| 人脸识别 | ❌ 必选 | 始终展示，但**渠道（自研/三方SDK）可选** |

---

## 网络请求统一处理机制（项目适配优先）

KYC 流程中每个页面都需要发起网络请求（提交数据、查询字典、上传图片等）。skill **不强制要求**必须实现为 `launchLoading` 这种特定形式，而是遵循以下原则：

### 检查清单（实现 KYC 前执行）

先检查项目中是否已存在类似的网络请求统一处理机制：

```
检查项：
  □ 是否有全局 Loading 弹窗控制（请求开始时显示、结束时隐藏）？
  □ 是否有统一的异常处理（401 跳转登录、网络错误弹窗、业务错误 Toast）？
  □ 是否有协程启动封装（自动处理 try/catch、线程切换）？
  □ ViewModel 是否已有基类提供上述能力？
```

**如果项目已有上述机制**：直接在 KYC ViewModel 中复用，无需新建。skill 中的代码示例会标注为伪代码形式（`【网络请求】`），由你替换为项目实际的方法名。

**如果项目没有上述机制**：提供一个最小实现参考（见下方）。

### 最小实现参考（项目无现成机制时）

```kotlin
/**
 * 最小版网络请求统一处理：封装协程启动 + Loading + 异常处理
 * 如果项目已有类似机制，直接复用，不需要这段代码。
 */
open class BaseViewModel : ViewModel() {

    protected val _toast = MutableSharedFlow<String>()
    val toast = _toast.asSharedFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asSharedFlow()

    /**
     * 启动协程并统一处理异常
     * @param showLoading 是否显示全局 Loading，默认 true
     */
    protected fun launchRequest(
        showLoading: Boolean = true,
        block: suspend CoroutineScope.() -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (showLoading) _loading.value = true
                block()
            } catch (e: Exception) {
                when (e) {
                    is AuthException -> { /* 401：跳转登录 */ }
                    is BusinessException -> _toast.emit(e.message ?: "请求失败")
                    is IOException -> _toast.emit("网络异常，请检查网络")
                    else -> _toast.emit("请求失败：${e.message}")
                }
            } finally {
                if (showLoading) _loading.value = false
            }
        }
    }
}
```

**skill 后续代码中的约定**：
- 需要发起网络请求的地方标注为 `【网络请求】{ ... }`
- 你需要将其替换为项目实际的方法，如 `launchLoading { ... }`、`launchRequest { ... }`、`viewModelScope.launch { ... }` 等

---

## Step 1: 个人信息

### 业务目的
收集用户的基础社会属性，用于风控评估。

### 标准字段
| 字段语义 | 输入方式 | 典型选项 |
|---------|---------|---------|
| 教育水平 | 底部弹窗选择 | 学前教育、小学、中学、高中/技术、本科/高等技术、硕士/博士、无学历 |
| 婚姻状况 | 底部弹窗选择 | 已婚、未婚、离异、丧偶（根据项目字典） |
| 工作类型 | 底部弹窗选择 | 全职、兼职、自由职业、个体户、学生、无业（根据项目字典） |
| 月收入 | 底部弹窗选择 | 多个收入区间（根据项目字典） |
| 居住省市 | 级联选择 | 先选省 → 再选市，地区数据由接口提供 |

### 完整状态模板

```kotlin
/** 个人信息页面状态 */
data class KycPersonalState(
    val educationLevel: String = "",
    val educationLevelKey: Int? = null,
    val maritalStatus: String = "",
    val maritalStatusKey: Int? = null,
    val jobType: String = "",
    val jobTypeKey: Int? = null,
    val monthlyIncome: String = "",
    val monthlyIncomeKey: Int? = null,
    val provinceCity: String = "",
    val selectedProvinceId: Int? = null,
    val selectedCityId: Int? = null,
    /** 当前显示的选择弹窗字段（null=不显示） */
    val pickerField: KycExtraField? = null,
    /** 地区数据缓存 */
    val areaListResp: AreaListResp? = null,
    /** 地区选择当前步骤（省/市） */
    val areaPickerStep: AreaPickerStep? = null,
    /** 校验失败的字段 key 集合 */
    val errorFields: Set<String> = emptySet(),
    /** 自动链式弹窗时，需要滚动定位到的字段索引（0-4），null 表示无滚动需求 */
    val scrollToFieldIndex: Int? = null,
)

sealed interface AreaPickerStep {
    data object Province : AreaPickerStep
    data class City(val provinceId: Int, val provinceName: String) : AreaPickerStep
}

sealed interface KycPersonalAction {
    data object OnBackClick : KycPersonalAction
    data object OnEducationClick : KycPersonalAction
    data object OnMaritalStatusClick : KycPersonalAction
    data object OnJobTypeClick : KycPersonalAction
    data object OnIncomeClick : KycPersonalAction
    data object OnProvinceCityClick : KycPersonalAction
    data object OnSubmitClick : KycPersonalAction
    data class OnPickerSelect(val key: Int) : KycPersonalAction
    data object OnPickerDismiss : KycPersonalAction
    data class OnAreaSelect(val item: AreaItem) : KycPersonalAction
    data object OnAreaBack : KycPersonalAction
    data object OnAreaDismiss : KycPersonalAction
}

sealed interface KycPersonalEvent {
    data object NavigateBack : KycPersonalEvent
    data object NavigateToNext : KycPersonalEvent
}
```

### ViewModel 核心逻辑模板

```kotlin
class KycPersonalViewModel : BaseViewModel() {

    private val mState = MutableStateFlow(KycPersonalState())
    val state = mState.asStateFlow()

    private val mEvent = MutableSharedFlow<KycPersonalEvent>()
    val event = mEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            /** 从本地缓存恢复表单数据（24h TTL） */
            loadCache()
            /** 预热地区数据 */
            if (mState.value.areaListResp == null) {
                【网络请求】 {
                    val resp = apiService.getAreaList()
                    mState.update { copy(areaListResp = resp) }
                }
            }
        }
    }

    fun onAction(action: KycPersonalAction) {
        viewModelScope.launch {
            when (action) {
                is KycPersonalAction.OnBackClick -> mEvent.emit(NavigateBack)

                is KycPersonalAction.OnEducationClick -> openPicker(EDUCATION)
                is KycPersonalAction.OnMaritalStatusClick -> openPicker(MARITAL)
                is KycPersonalAction.OnJobTypeClick -> openPicker(WORK_TYPE)
                is KycPersonalAction.OnIncomeClick -> openPicker(MONTHLY_INCOME)

                is KycPersonalAction.OnProvinceCityClick -> {
                    /** 已选过省则直接打开市列表 */
                    if (state.value.selectedProvinceId != null) {
                        mState.update { copy(areaPickerStep = City(...)) }
                    } else {
                        mState.update { copy(areaPickerStep = Province) }
                    }
                }

                is KycPersonalAction.OnAreaSelect -> {
                    when (val step = state.value.areaPickerStep) {
                        is Province -> {
                            /** 选择省后自动进入市选择 */
                            mState.update {
                                copy(
                                    selectedProvinceId = action.item.id,
                                    areaPickerStep = City(action.item.id, action.item.name)
                                )
                            }
                        }
                        is City -> {
                            mState.update {
                                copy(
                                    provinceCity = "${step.provinceName} / ${action.item.name}",
                                    selectedCityId = action.item.id,
                                    areaPickerStep = null,
                                    errorFields = errorFields - "provinceCity"
                                )
                            }
                            saveCache()
                        }
                        else -> {}
                    }
                }

                is KycPersonalAction.OnPickerSelect -> {
                    val field = state.value.pickerField ?: return@launch
                    val option = field.options.find { it.key == action.key }

                    /** 链式弹窗：选择后自动打开下一个空字段 */
                    val nextField = findNextEmptyField(afterCode = field.code)

                    mState.update {
                        when (field.code) {
                            EDUCATION.code -> copy(
                                educationLevel = option?.value ?: "",
                                educationLevelKey = option?.key,
                                pickerField = nextField
                            )
                            // ... 其他字段类似
                            else -> copy(pickerField = nextField)
                        }
                    }
                    saveCache()
                }

                is KycPersonalAction.OnPickerDismiss -> {
                    mState.update { copy(pickerField = null) }
                }

                is KycPersonalAction.OnSubmitClick -> {
                    val errors = validate()
                    if (errors.isNotEmpty()) {
                        mState.update { copy(errorFields = errors) }
                        return@launch
                    }
                    【网络请求】 { submitData() }
                }

                else -> {}
            }
        }
    }

    private suspend fun submitData() {
        val s = state.value
        val items = mutableListOf<AcqDataItem>()
        /** 构建字段列表，使用 KycDictManager 中的 fieldName */
        items += AcqDataItem(fieldName = EDUCATION.fieldName, value = s.educationLevelKey?.toString() ?: "")
        items += AcqDataItem(fieldName = MARITAL.fieldName, value = s.maritalStatusKey?.toString() ?: "")
        items += AcqDataItem(fieldName = WORK_TYPE.fieldName, value = s.jobTypeKey?.toString() ?: "")
        items += AcqDataItem(fieldName = MONTHLY_INCOME.fieldName, value = s.monthlyIncomeKey?.toString() ?: "")
        /** 省市地区构造成 JSON 字符串 */
        items += AcqDataItem(
            fieldName = AREA_FIELD_NAME,
            value = """{"province":"${s.selectedProvinceId}","city":"${s.selectedCityId}"}"""
        )

        val req = SubmitAcqDataReq(t3e8L = 1, items = items)
        apiService.submitAcqData(req)
        saveCache()
        mEvent.emit(NavigateToNext)
    }

    private fun validate(): Set<String> {
        val s = state.value
        val errors = mutableSetOf<String>()
        if (s.educationLevel.isEmpty()) errors += "educationLevel"
        if (s.maritalStatus.isEmpty()) errors += "maritalStatus"
        if (s.jobType.isEmpty()) errors += "jobType"
        if (s.monthlyIncome.isEmpty()) errors += "monthlyIncome"
        if (s.provinceCity.isEmpty()) errors += "provinceCity"
        return errors
    }

    private fun findNextEmptyField(afterCode: Int?): KycExtraField? {
        val ordered = listOf(EDUCATION, MARITAL, WORK_TYPE, MONTHLY_INCOME)
        val start = afterCode?.let { code -> ordered.indexOfFirst { it.code == code } + 1 } ?: 0
        for (i in start until ordered.size) {
            if (isFieldEmpty(ordered[i].code)) return fetchField(ordered[i])
        }
        return null
    }

    private suspend fun loadCache() { /* 从 DataStore/Preferences 读取，必须 try/catch，详见「缓存序列化安全」*/ }
    private suspend fun saveCache() { /* 写入 DataStore/Preferences，缓存数据结构字段必须全可空，详见「缓存序列化安全」*/ }
    private fun openPicker(meta: DictMeta) { /* 从字典缓存/网络获取选项后打开 */ }
    private fun isFieldEmpty(code: Int): Boolean { /* 判断字段是否未填写 */ }
}
```

### 实现要点
- 所有选择字段的选项来自**后端字典接口**，不应前端硬编码
- 每个字段需要同时保存**展示文本**（给用户看）和**提交值**（传给后端）
- 省市选择通常需要单独的地区数据接口
- 页面支持本地缓存（24h TTL），退出重进可恢复。**⚠️ 缓存数据结构字段必须全可空，详见「缓存序列化安全」**
- **链式弹窗优化**：选择某字段后自动检查下一个空字段并自动弹出对应选择器，同时自动滚动定位到对应字段

### 典型接口模式
```
GET /area/list          — 获取省市地区数据
POST /acq/submit        — 提交个人信息（步骤标识=1）
```

---

## Step 2: 联系人信息

### 业务目的
收集紧急联系人，用于贷后催收和风控。

### 标准字段
每个联系人卡片包含：
| 字段语义 | 输入方式 |
|---------|---------|
| 关系 | 底部弹窗选择（父母、配偶、子女、朋友等） |
| 电话 | 系统通讯录选择（首选）或手动输入 |
| 姓名 | 从通讯录自动回填 或 手动输入 |

### 完整状态模板

```kotlin
/** 联系人信息页面状态 */
data class KycContactState(
    val contacts: List<ContactItem> = List(2) { ContactItem() },
    val maxContacts: Int = 2,
    /** 当前显示的关系选择弹窗字段 */
    val pickerField: KycExtraField? = null,
    /** 当前正在选择关系的联系人索引 */
    val pickerContactIndex: Int = -1,
    /** key = 字段标识如 "relation_0"，value = 错误提示文本 */
    val errorMessages: Map<String, String> = emptyMap(),
)

data class ContactItem(
    val relation: String = "",
    val relationKey: Int? = null,
    val phone: String = "",
    val name: String = "",
    /** 电话和姓名字段是否可手动编辑：调起联系人失败后为 true */
    val isEditable: Boolean = false,
)

sealed interface KycContactAction {
    data object OnBackClick : KycContactAction
    data class OnRelationClick(val index: Int) : KycContactAction
    data class OnPhoneClick(val index: Int) : KycContactAction
    data class OnPhoneChange(val index: Int, val value: String) : KycContactAction
    data class OnNameChange(val index: Int, val value: String) : KycContactAction
    data object OnAddContactClick : KycContactAction
    data class OnDeleteContactClick(val index: Int) : KycContactAction
    data object OnSubmitClick : KycContactAction
    data class OnPickerSelect(val key: Int) : KycContactAction
    data object OnPickerDismiss : KycContactAction
    /** 从系统通讯录选择后回填 */
    data class OnContactPicked(val name: String, val phone: String) : KycContactAction
    /** 通讯录选择失败，切换为手动输入 */
    data class OnContactPickFailed(val index: Int) : KycContactAction
}

sealed interface KycContactEvent {
    data object NavigateBack : KycContactEvent
    data object NavigateToNext : KycContactEvent
    /** 触发系统通讯录选择器 */
    data class LaunchContactPicker(val index: Int) : KycContactEvent
}
```

### ViewModel 核心逻辑模板

```kotlin
class KycContactViewModel : BaseViewModel() {

    private val mState = MutableStateFlow(KycContactState())
    val state = mState.asStateFlow()

    private val mEvent = MutableSharedFlow<KycContactEvent>()
    val event = mEvent.asSharedFlow()

    fun onAction(action: KycContactAction) {
        viewModelScope.launch {
            when (action) {
                is OnBackClick -> mEvent.emit(NavigateBack)

                is OnRelationClick -> {
                    /** 打开关系选择弹窗 */
                    val field = fetchField(CONTACT_RELATION)
                    mState.update { copy(pickerField = field, pickerContactIndex = action.index) }
                }

                is OnPhoneClick -> {
                    /** 优先调起系统通讯录选择器 */
                    mEvent.emit(LaunchContactPicker(action.index))
                }

                is OnContactPicked -> {
                    /** 从通讯录回填姓名和电话 */
                    val idx = state.value.pickerContactIndex
                    updateContact(idx) { copy(name = action.name, phone = action.phone, isEditable = false) }
                    saveCache()
                }

                is OnContactPickFailed -> {
                    /** 通讯录选择失败，切换为手动输入模式 */
                    updateContact(action.index) { copy(isEditable = true) }
                }

                is OnPickerSelect -> {
                    val idx = state.value.pickerContactIndex
                    val option = state.value.pickerField?.options?.find { it.key == action.key }
                    updateContact(idx) {
                        copy(relation = option?.value ?: "", relationKey = option?.key)
                    }
                    mState.update { copy(pickerField = null) }
                    saveCache()
                }

                is OnAddContactClick -> {
                    if (state.value.contacts.size < state.value.maxContacts) {
                        mState.update { copy(contacts = contacts + ContactItem()) }
                    }
                }

                is OnDeleteContactClick -> {
                    val list = state.value.contacts.toMutableList()
                    list.removeAt(action.index)
                    mState.update { copy(contacts = list) }
                    saveCache()
                }

                is OnSubmitClick -> {
                    val errors = validate()
                    if (errors.isNotEmpty()) {
                        mState.update { copy(errorMessages = errors) }
                        return@launch
                    }
                    【网络请求】 { submitData() }
                }

                else -> {}
            }
        }
    }

    private suspend fun submitData() {
        val items = state.value.contacts.mapIndexed { index, contact ->
            listOf(
                AcqDataItem(fieldName = "relation_$index", value = contact.relationKey?.toString() ?: ""),
                AcqDataItem(fieldName = "phone_$index", value = contact.phone),
                AcqDataItem(fieldName = "name_$index", value = contact.name),
            )
        }.flatten()

        apiService.submitAcqData(SubmitAcqDataReq(t3e8L = 2, items = items))
        saveCache()
        mEvent.emit(NavigateToNext)
    }

    private fun validate(): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        state.value.contacts.forEachIndexed { index, contact ->
            if (contact.relation.isEmpty()) errors["relation_$index"] = "请选择关系"
            if (contact.phone.isEmpty()) errors["phone_$index"] = "请输入电话"
            if (contact.name.isEmpty()) errors["name_$index"] = "请输入姓名"
        }
        return errors
    }
}
```

### 实现要点
- 默认展示 2 个联系人卡片，可添加至最大数量（由配置控制）
- 电话输入优先调起系统通讯录选择器（`ACTION_PICK + Phone.CONTENT_TYPE`），失败降级为手动输入
- 从通讯录选择后自动解析姓名和电话号码
- 每个联系人的关系选项来自后端字典

### 典型接口模式
```
POST /acq/submit        — 提交联系人信息（步骤标识=2）
```

---

## Step 3: 身份证认证

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

## Step 4: 银行卡信息

### 业务目的
收集收款账户，用于放款。

### 标准字段
| 字段语义 | 输入方式 |
|---------|---------|
| 卡类型 | 单选（CLABE / 储蓄卡 / 信用卡等，根据项目） |
| 银行名称 | 底部弹窗选择（支持搜索和常用银行分组） |
| 银行账号 | 数字输入框，长度根据卡类型动态限制 |

### 双模式支持（可选）

银行卡页面通常需要支持两种使用场景，通过路由参数 `isExternal` 区分：

| 模式 | 路由参数 | 提交接口 | 成功行为 | 缓存 |
|------|---------|---------|---------|------|
| KYC 步骤模式 | `isExternal = false`（默认） | `submitAcqData(t3e8L=4)` | 跳转下一步（信用认证/人脸识别） | 写本地缓存 |
| 外部添加模式 | `isExternal = true` | `submitBankCard`（独立结构化接口） | `popBackStack` 关闭页面 | 不写缓存 |

**典型路由定义**：
```kotlin
@Serializable
data class KycBankAccountRoute(val isExternal: Boolean = false)
```

### 完整状态模板

```kotlin
/** 银行卡信息页面状态 */
data class KycBankState(
    /** 银行卡类型：0=CLABE, 1=Tarjeta de débito（借记卡） */
    val accountType: Int = 0,
    val bankId: Int? = null,
    val bankName: String = "",
    val accountNumber: String = "",
    val showBankDialog: Boolean = false,
    val bankList: List<BankInfoResp> = emptyList(),
    val bankSearchText: String = "",
    val errorFields: Set<String> = emptySet(),
    val errorMessages: Map<String, String> = emptyMap(),
    val showConfirmDialog: Boolean = false,
    /** false=KYC 步骤模式，true=外部添加模式 */
    val isExternal: Boolean = false,
)

sealed interface KycBankAction {
    data object OnBackClick : KycBankAction
    data class OnAccountTypeChange(val type: Int) : KycBankAction
    data object OnBankClick : KycBankAction
    data class OnBankSelect(val bank: BankInfoResp) : KycBankAction
    data object OnBankDialogDismiss : KycBankAction
    data class OnBankSearchChange(val text: String) : KycBankAction
    data class OnAccountNumberChange(val value: String) : KycBankAction
    data object OnSubmitClick : KycBankAction
    data object OnConfirmDialogDismiss : KycBankAction
    data object OnConfirmDialogSubmit : KycBankAction
}

sealed interface KycBankEvent {
    data object NavigateBack : KycBankEvent
    /** @param skipCredit true 表示跳过信用认证直接跳转人脸 */
    data class NavigateToNext(val skipCredit: Boolean = false) : KycBankEvent
}
```

### ViewModel 核心逻辑模板

```kotlin
class KycBankViewModel : BaseViewModel() {

    private val mState = MutableStateFlow(KycBankState())
    val state = mState.asStateFlow()

    private val mEvent = MutableSharedFlow<KycBankEvent>()
    val event = mEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            /** 加载银行列表（内存/本地/网络） */
            val bankList = bankRepository.getBankList()
            mState.update { copy(bankList = bankList) }
        }
    }

    /** 注入路由参数，区分 KYC/外部模式 */
    fun setExternal(isExternal: Boolean) {
        if (state.value.isExternal == isExternal) return
        mState.update { copy(isExternal = isExternal) }
        if (!isExternal) {
            viewModelScope.launch { loadCache() }
        }
    }

    fun onAction(action: KycBankAction) {
        viewModelScope.launch {
            when (action) {
                is OnBackClick -> mEvent.emit(NavigateBack)

                is OnAccountTypeChange -> {
                    mState.update {
                        copy(accountType = action.type, accountNumber = "")
                    }
                    saveCache()
                }

                is OnBankSelect -> {
                    mState.update {
                        copy(
                            bankId = action.bank.id,
                            bankName = action.bank.name,
                            showBankDialog = false
                        )
                    }
                    saveCache()
                }

                is OnAccountNumberChange -> {
                    /** 只允许数字，长度限制 */
                    val maxLen = if (state.value.accountType == 0) 18 else 16
                    val filtered = action.value.filter { it.isDigit() }.take(maxLen)
                    mState.update { copy(accountNumber = filtered) }
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

                else -> {}
            }
        }
    }

    private suspend fun submitData() {
        val s = state.value
        if (s.isExternal) {
            /** 外部模式：结构化接口 */
            apiService.submitBankCard(
                SubmitBankCardReq(
                    cardNumber = s.accountNumber,
                    bankId = s.bankId?.toString() ?: "",
                    accountType = s.accountType,
                )
            )
            mEvent.emit(NavigateBack)
        } else {
            /** KYC 模式：AcqDataItem 列表 */
            val items = mutableListOf<AcqDataItem>()
            items += AcqDataItem(ACCOUNT_TYPE_FIELD, if (s.accountType == 0) "40" else "3")
            items += AcqDataItem(BANK_ID_FIELD, s.bankId?.toString() ?: "")
            items += AcqDataItem(CARD_NUMBER_FIELD, s.accountNumber)
            apiService.submitAcqData(SubmitAcqDataReq(t3e8L = 4, items = items))
            saveCache()
            val skipCredit = creditConfigRepository.isCreditDisabled()
            mEvent.emit(NavigateToNext(skipCredit))
        }
    }

    private fun validate(): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        val s = state.value
        if (s.bankId == null) errors["bankName"] = "请选择银行"
        if (s.accountNumber.isEmpty()) errors["accountNumber"] = "请输入账号"
        else {
            val required = if (s.accountType == 0) 18 else 16
            if (s.accountNumber.length != required) {
                errors["accountNumber"] = "账号长度应为 $required 位"
            }
        }
        return errors
    }
}
```

### 实现要点
- 卡类型决定账号长度限制（如 CLABE=18位，储蓄卡=16位）
- 银行列表通常来自独立接口，支持内存+本地+网络三级缓存
- 银行选择弹窗通常支持搜索和按常用/字母分组
- 提交前展示确认弹窗（卡号脱敏显示）

### 典型接口模式
```
GET /bank/list          — 获取银行列表
POST /acq/submit        — KYC 模式下提交银行卡（步骤标识=4）
POST /bank/card/submit  — 外部模式下提交银行卡（结构化接口）
```

---

## Step 5: 信用信息（可选）

### 业务目的
收集用户信用历史，用于风控额度评估。

### 标准字段（条件展示）
| 字段语义 | 展示条件 | 输入方式 |
|---------|---------|---------|
| 贷款经历 | 始终展示 | 底部弹窗选择（0次、1次、2-3次...） |
| 在贷笔数 | 贷款经历 > 0 | 底部弹窗选择 |
| 在贷金额 | 在贷笔数 > 0 | 底部弹窗选择 |

### 完整状态模板

```kotlin
/** 信用信息页面状态 */
data class KycCreditState(
    val loanCount: String = "",
    val loanCountKey: Int? = null,
    val currentLoanCount: String = "",
    val currentLoanCountKey: Int? = null,
    val totalLoanAmount: String = "",
    val totalLoanAmountKey: Int? = null,
    /** 是否显示贷款经历选择弹窗 */
    val showLoanCountPicker: Boolean = false,
    /** 是否显示在贷笔数选择弹窗 */
    val showCurrentLoanPicker: Boolean = false,
    /** 是否显示在贷金额选择弹窗 */
    val showAmountPicker: Boolean = false,
    /** 校验失败的字段 key 集合 */
    val errorFields: Set<String> = emptySet(),
)

sealed interface KycCreditAction {
    data object OnBackClick : KycCreditAction
    data object OnLoanCountClick : KycCreditAction
    data object OnCurrentLoanClick : KycCreditAction
    data object OnAmountClick : KycCreditAction
    data object OnSubmitClick : KycCreditAction
    data class OnPickerSelect(val fieldCode: Int, val key: Int) : KycCreditAction
    data object OnPickerDismiss : KycCreditAction
}

sealed interface KycCreditEvent {
    data object NavigateBack : KycCreditEvent
    data object NavigateToNext : KycCreditEvent
}
```

### ViewModel 核心逻辑模板

```kotlin
class KycCreditViewModel : BaseViewModel() {

    private val mState = MutableStateFlow(KycCreditState())
    val state = mState.asStateFlow()

    private val mEvent = MutableSharedFlow<KycCreditEvent>()
    val event = mEvent.asSharedFlow()

    init {
        viewModelScope.launch { loadCache() }
    }

    fun onAction(action: KycCreditAction) {
        viewModelScope.launch {
            when (action) {
                is OnBackClick -> mEvent.emit(NavigateBack)

                is OnLoanCountClick -> {
                    mState.update { copy(showLoanCountPicker = true) }
                }

                is OnCurrentLoanClick -> {
                    /** 仅在贷款经历 > 0 时展示 */
                    if (state.value.loanCountKey != 0) {
                        mState.update { copy(showCurrentLoanPicker = true) }
                    }
                }

                is OnAmountClick -> {
                    /** 仅在在贷笔数 > 0 时展示 */
                    if (state.value.currentLoanCountKey != 0) {
                        mState.update { copy(showAmountPicker = true) }
                    }
                }

                is OnPickerSelect -> {
                    when (action.fieldCode) {
                        LOAN_COUNT_CODE -> {
                            mState.update {
                                copy(
                                    loanCountKey = action.key,
                                    /** 贷款经历选 0 时清空下游字段 */
                                    currentLoanCount = if (action.key == 0) "" else currentLoanCount,
                                    currentLoanCountKey = if (action.key == 0) null else currentLoanCountKey,
                                    totalLoanAmount = if (action.key == 0) "" else totalLoanAmount,
                                    totalLoanAmountKey = if (action.key == 0) null else totalLoanAmountKey,
                                )
                            }
                        }
                        CURRENT_LOAN_CODE -> {
                            mState.update {
                                copy(
                                    currentLoanCountKey = action.key,
                                    /** 在贷笔数选 0 时清空在贷金额 */
                                    totalLoanAmount = if (action.key == 0) "" else totalLoanAmount,
                                    totalLoanAmountKey = if (action.key == 0) null else totalLoanAmountKey,
                                )
                            }
                        }
                        TOTAL_AMOUNT_CODE -> {
                            mState.update { copy(totalLoanAmountKey = action.key) }
                        }
                    }
                    mState.update {
                        copy(
                            showLoanCountPicker = false,
                            showCurrentLoanPicker = false,
                            showAmountPicker = false,
                        )
                    }
                    saveCache()
                }

                is OnSubmitClick -> {
                    val errors = validate()
                    if (errors.isNotEmpty()) {
                        mState.update { copy(errorFields = errors) }
                        return@launch
                    }
                    【网络请求】 { submitData() }
                }

                else -> {}
            }
        }
    }

    private suspend fun submitData() {
        val s = state.value
        val items = mutableListOf<AcqDataItem>()
        items += AcqDataItem(LOAN_COUNT_FIELD, s.loanCountKey?.toString() ?: "")
        if (s.loanCountKey != 0) {
            items += AcqDataItem(CURRENT_LOAN_FIELD, s.currentLoanCountKey?.toString() ?: "")
        }
        if (s.currentLoanCountKey != 0) {
            items += AcqDataItem(TOTAL_AMOUNT_FIELD, s.totalLoanAmountKey?.toString() ?: "")
        }
        apiService.submitAcqData(SubmitAcqDataReq(t3e8L = 5, items = items))
        saveCache()
        mEvent.emit(NavigateToNext)
    }
}
```

### 实现要点
- 该步骤是否展示由后端配置控制（进件页面信息接口返回）
- 字段间存在**级联依赖**：先选贷款经历，若>0才展示在贷笔数；在贷笔数>0才展示在贷金额
- 所有选项来自后端字典接口

### 典型接口模式
```
POST /acq/submit        — 提交信用信息（步骤标识=5）
```

---

## Step 6: 人脸识别

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

## 接口占位代码（接口未定义时）

当项目后端接口尚未就绪，需要前端先完成流程代码时，按以下标准创建接口占位，保证 KYC 流程代码完整可编译。

### 1. 进件数据提交接口

```kotlin
// network/loan/LoanApiService.kt
@POST("/api/acq/submit")
suspend fun submitAcqData(@Body req: SubmitAcqDataReq): ApiEnvelope<SubmitAcqDataResp>

// network/loan/Request.kt
@Serializable
data class SubmitAcqDataReq(
    /** 新增/更新标识：0=新增，1=更新 */
    val d4zlHwbZAOmI6H0_kntT: Int = 0,
    /** 进件数据项列表 */
    val qtj4X2fyBTK2qJm1BaZA: List<AcqDataItem>,
    /** 流程标识：固定 2 */
    val eUVwQJ3vQxc08aUj7miC: Int = 2,
    /** 步骤标识：1=个人信息 2=联系人 3=身份证 4=银行卡 5=信用 6=人脸 */
    val t3e8L: Int,
)

@Serializable
data class AcqDataItem(
    /** 字段名（混淆串） */
    val oMr2jKzRVLB99cjeb: String,
    /** 字段值 */
    val u1j7ovclLk89d11: String,
)

@Serializable
data class SubmitAcqDataResp(
    /** 按后端实际返回定义 */
    val code: Int = 0,
)
```

### 2. 银行卡外部提交接口（可选）

```kotlin
// network/loan/LoanApiService.kt
@POST("/api/bank/card/submit")
suspend fun submitBankCard(@Body req: SubmitBankCardReq): ApiEnvelope<SubmitBankCardResp>

// network/loan/Request.kt
@Serializable
data class SubmitBankCardReq(
    /** 银行卡号 */
    val uzcbAW5bYlSMCnOEeT: String,
    /** 银行 ID */
    val bFjmTbDSM: String,
    /** 账户类型：0=CLABE, 1=借记卡 */
    val zjTYcQpr: Int,
)

@Serializable
data class SubmitBankCardResp(
    val code: Int = 0,
)
```

### 3. 人脸渠道查询接口

```kotlin
// network/loan/LoanApiService.kt
@GET("/api/face/channel")
suspend fun getFaceChannel(): ApiEnvelope<FaceChannelResp>

// network/loan/Response.kt
@Serializable
data class FaceChannelResp(
    /** 人脸渠道：1=accu, 2=yc, 3=facePlusPlus */
    val p0Mgp4IL8bD: String? = null,
)
```

### 4. Face++ Token 接口

```kotlin
// network/loan/LoanApiService.kt
@GET("/api/face++/token")
suspend fun getFacePlusPlusToken(): ApiEnvelope<FacePlusPlusTokenResp>

// network/loan/Response.kt
@Serializable
data class FacePlusPlusTokenResp(
    /** SDK host */
    val kJEXq150: String? = null,
    /** BizToken */
    val iy5h_MHpS9o6Q: String? = null,
)
```

### 5. 人脸比对接口

```kotlin
// network/upload/UploadApiService.kt
@Multipart
@POST("/api/face/compare")
suspend fun faceCompare(
    @Part("p0Mgp4IL8bD") channel: String,
    @Part("d4zlHwbZAOmI6H0_kntT") actionType: String,
    @Part("eUVwQJ3vQxc08aUj7miC") flowType: String,
    @Part("t3e8L") step: String,
    // 自研渠道特有参数
    @Part("agtFiTgpWQh6Ws") latitude: String? = null,
    @Part("p8jHjwsM_QYOS") longitude: String? = null,
    @Part niM6smmG: MultipartBody.Part? = null,
    // Face++ 渠道特有参数
    @Part("aTDUsZ7P9AoNN7F2yt") bizToken: String? = null,
): ApiEnvelope<FaceCompareResp>

@Serializable
data class FaceCompareResp(
    /** 比对结果：1=成功 */
    val sPkYn_M: Int? = null,
)
```

### 6. 字典接口

```kotlin
// network/common/CommonApiService.kt
@GET("/api/dict/list")
suspend fun getDictList(@Query("type") type: String): ApiEnvelope<DictResp>

// network/common/Response.kt
@Serializable
data class DictResp(
    val ina9fIx7Cpygdw0: List<DictItem>? = null,
)

@Serializable
data class DictItem(
    val kYM1d41M1oVAiE: String? = null,
    val dY19_rl9XKoi8q: String? = null,
)
```

### 7. 银行列表接口

```kotlin
// network/loan/LoanApiService.kt
@GET("/api/bank/list")
suspend fun getBankList(): ApiEnvelope<BankListResp>

// network/loan/Response.kt
@Serializable
data class BankListResp(
    val wzZ30fZ7nba_dyaTH: List<BankInfoResp>? = null,
)

@Serializable
data class BankInfoResp(
    val wnz5RTXbacHU_ksMPAAR: Int? = null,
    val iP_Lz5kBf76A: String? = null,
)
```

### 8. OCR 识别接口

```kotlin
// network/upload/UploadApiService.kt
@Multipart
@POST("/api/ocr/verify")
suspend fun ocrVerify(
    @Part("type") type: String,
    @Part("latitude") latitude: String?,
    @Part("longitude") longitude: String?,
    @Part image: MultipartBody.Part,
): ApiEnvelope<OcrResp>

@Serializable
data class OcrResp(
    val firstName: String? = null,
    val paternalSurname: String? = null,
    val maternalSurname: String? = null,
    val idNumber: String? = null,
    val imageUrl: String? = null,
)
```

### 9. 地区列表接口

```kotlin
// network/common/CommonApiService.kt
@GET("/api/area/list")
suspend fun getAreaList(): ApiEnvelope<AreaListResp>

// network/common/Response.kt
@Serializable
data class AreaListResp(
    val rebk0UE: List<AreaItem>? = null,
)

@Serializable
data class AreaItem(
    val wnz5RTXbacHU_ksMPAAR: Int? = null,
    val qwdJtB6bC: String? = null,
)
```

---

## 通用数据提交模式

### 进件数据提交

所有 KYC 步骤共用同一个提交接口，通过**步骤标识**区分：

| 步骤标识 | 对应页面 |
|---------|---------|
| 1 | 个人信息 |
| 2 | 联系人信息 |
| 3 | 身份证认证 |
| 4 | 银行卡信息（KYC 模式） |
| 5 | 信用信息 |
| 6 | 人脸识别 |

### 字段命名原则
- 后端接口字段通常使用**混淆字符串**（如 `rfe7VQG4IS9ZIjJmN0bX`）
- **必须通过接口注释/KDoc 理解字段语义**，不能凭字段名猜测
- 每个选择字段需要同时传递**展示文本**和**选项 key**

### 进件页面信息查询
```
GET /acq/page/info?step={step}  — 查询某步骤的表单元素配置
```

后端返回该步骤需要展示的字段列表、字段类型、是否必填、选项数据源等。前端根据返回动态渲染表单。

---

## 字典系统

### 核心概念
KYC 页面中的选择字段（教育水平、婚姻状况、工作类型等）的选项不应当前端硬编码，而应通过**字典系统**动态获取。

### 字典字段标准结构
```
字段元信息：
  - code: 字段唯一标识（如 10001）
  - label: 展示标签（如 "Nivel educativo"）
  - fieldName: 提交时使用的混淆字段名
  - options: 选项列表

选项结构：
  - key: 选项提交值（数字）
  - value: 选项展示文本
```

### 字典缓存策略（三级）
```
读取顺序：内存缓存 → 本地持久化缓存（24h TTL） → 网络接口
```

- **内存缓存**：进程内 ConcurrentHashMap，重启后丢失
- **本地缓存**：DataStore/SharedPreferences，按账号隔离，24小时有效期
- **网络兜底**：通用字典接口，返回完整字段定义和选项列表

### 测试模式
字典系统通常支持测试模式开关，开启时直接返回本地硬编码数据，不走网络。便于开发和测试。

### 常见字典字段
| 语义 | 典型选项数 | 用途 |
|------|-----------|------|
| 教育水平 | 5-7 | 个人信息页 |
| 婚姻状况 | 4 | 个人信息页 |
| 工作类型 | 5-6 | 个人信息页 |
| 月收入 | 5-6 | 个人信息页 |
| 联系人关系 | 4 | 联系人页 |
| 银行卡类型 | 2-3 | 银行卡页 |
| 贷款经历 | 10+ | 信用信息页 |
| 在贷笔数 | 10+ | 信用信息页 |
| 在贷金额 | 4-6 | 信用信息页 |

---

## 缓存机制

### 页面表单缓存

每个 KYC 页面都应支持本地缓存，降低用户重复填写成本。

**缓存特性**：
- 24 小时 TTL
- 按用户账号隔离（多账号不冲突）
- 页面级别隔离（不同页面互不干扰）
- 缓存内容包括：用户已填写的所有表单字段（展示文本 + 提交值）

**缓存时机**：
- 页面初始化时读取缓存恢复数据
- 用户每次修改表单后自动保存缓存

### 缓存序列化安全（CRITICAL）

**所有用于本地缓存的数据结构字段，必须定义为可空类型并设置默认值 `null`：**

```kotlin
// ✅ 正确：所有字段可空 + 默认值 null —— 序列化安全
data class KycPersonalCache(
    var educationLevel: String? = null,
    var educationLevelKey: Int? = null,
    var maritalStatus: String? = null,
    var monthlyIncome: String? = null,
    var provinceCity: String? = null,
)

// ❌ 错误：非空字段 —— 反序列化崩溃风险
data class KycPersonalCache(
    val educationLevel: String,          // 字段缺失 → NPE
    val maritalStatus: String = "",      // 空串默认值可避免崩溃，但与 null 语义不一致，且不同序列化框架行为不同
)
```

**为什么必须可空：**

1. **序列化框架差异** — 项目可能混用多种序列化方案（Gson、kotlinx.serialization），它们对字段缺失的处理方式不同：
   - Gson (`GsonConverterFactory`)：JSON 中字段缺失 → 保持字段默认值。可空类型默认 `null` 安全；非空类型在反射创建实例时可能抛异常
   - kotlinx.serialization：JSON 中字段缺失 → 如果有默认值则使用默认值，否则抛 `MissingFieldException`
   - 手动 `JSONObject` 解析：`optString()` 安全返回空串，`getString()` 字段缺失抛 `JSONException`

2. **版本演进兼容** — App 升级后代码可能增删缓存字段，旧缓存 JSON 反序列化到新数据结构时：
   - 新增字段：旧 JSON 中不存在 → 可空类型自动为 `null` ✓
   - 删除字段：旧 JSON 中有该字段 → 新类中无对应字段，Gson 忽略 ✓
   - 修改字段类型：旧 `Int` → 新 `String` → 可空类型返回 `null`，非空类型直接崩溃 ✗

3. **缓存数据完整性不可信任** — 本地缓存可能因为以下原因损坏或不完整：
   - 用户手动清除应用数据（残留文件）
   - 系统备份恢复（跨设备、跨版本）
   - 存储空间不足导致写入截断
   - SharedPreferences XML 解析异常

**字段默认值策略总结：**

| 字段类型 | 推荐声明 | 说明 |
|---------|---------|------|
| 字符串 | `var name: String? = null` | `null` 在 UI 层映射为空串 `?: ""` |
| 整数 | `var key: Int? = null` | `null` 表示"未选择"，0 可能是有效值 |
| 浮点 | `var amount: Double? = null` | 同上 |
| 布尔 | `var flag: Boolean = false` | 布尔可用非空，只有两种状态且 false 是合理默认 |
| 列表 | `var items: List<Item?>? = null` | `null` 列表和空列表语义不同时用 `null`；否则可用 `listOf()` |
| 嵌套对象 | `var child: ChildCache? = null` | 嵌套缓存对象同样全可空 |

**缓存读写示例（防御式）：**

```kotlin
// 写入缓存 — 直接序列化 data class
private suspend fun saveCache() {
    val cache = KycPersonalCache(
        educationLevel = state.value.educationLevel,
        educationLevelKey = state.value.educationLevelKey,
        // ...
    )
    val json = Gson().toJson(cache)
    prefs.edit().putString(CACHE_KEY, json).apply()
}

// 读取缓存 — try/catch 兜底，反序列化失败不崩溃
private suspend fun loadCache(): KycPersonalCache? {
    return try {
        val json = prefs.getString(CACHE_KEY, null) ?: return null
        Gson().fromJson(json, KycPersonalCache::class.java)
    } catch (e: Exception) {
        // 缓存损坏 → 清除并静默降级到空表单
        prefs.edit().remove(CACHE_KEY).apply()
        null
    }
}
```

**禁止做法：**
- ❌ 在缓存数据结构中使用非空字段（`val x: String`），即使设置了默认值也可能在不同序列化框架下行为不一致
- ❌ 缓存反序列化时不加 try/catch，崩溃会直接白屏
- ❌ 缓存写入时过滤 `null` 值（如 Gson 默认不序列化 null），这会导致读取时缺少字段
- ❌ 在 UI 层直接对缓存数据使用 `!!` 强制解包

### 字典缓存

字典数据和页面表单数据分开缓存：
- 字典缓存：管理选择器选项，三层策略（内存→本地→网络）
- 表单缓存：管理用户输入，两层策略（本地 DataStore + TTL）

---

## 通用 UI 组件封装原则

KYC 流程中表单控件的**交互类型由业务语义决定**（如"教育水平"一定是选择而非输入），但**具体样式由设计稿决定**（底部弹窗、下拉框、侧滑面板等）。不同项目的设计稿不同，控件外观不应写死。

### 封装原则

**必须封装为项目级通用组件**，在 KYC 各页面中复用：

| 组件语义 | 交互类型 | 封装要求 | 典型样式（根据设计稿） |
|---------|---------|---------|---------------------|
| 字典选择器 | 单选 | **必须封装** | 底部弹窗 / 下拉框 / 侧滑面板 |
| 省市级联选择器 | 级联选择 | **必须封装** | 两级底部弹窗 / 联动滚轮 |
| 银行选择器 | 搜索+单选 | **必须封装** | 搜索弹窗 + 分组列表 |
| 拍照/图库选择器 | 二选一 | **必须封装** | 底部 ActionSheet |
| 信息确认弹窗 | 信息展示+确认 | **必须封装** | 居中卡片 / 底部面板 |
| 流程挽留弹窗 | 提示+确认/取消 | **必须封装** | 居中对话框 |
| 文本输入框 | 键盘输入 | **必须封装** | 带图标、清除按钮、格式校验 |
| 数字输入框 | 数字键盘 | **必须封装** | 带长度限制、格式化显示 |

### 控件封装规范

每个封装组件应满足：

1. **与业务解耦**：组件只负责渲染和交互，不感知具体业务字段（如"教育水平"）
2. **与样式解耦**：外观由外部传入（颜色、圆角、字体等设计 token），或读取项目主题配置
3. **统一接口**：相同语义的不同字段使用同一组件，仅传入不同数据

```
示例：字典选择器统一接口

输入：
  - label: 字段标签（如 "Nivel educativo"）
  - options: 选项列表 [{key, value}]
  - selectedKey: 当前选中值
  - onSelect: (key, value) -> Unit
  - theme: 外观配置（颜色、圆角等，可选）

输出：
  - 用户选择后回调 (key, value)
```

### 禁止做法

- ❌ 每个字段单独实现一个选择弹窗（如 `EducationDialog` + `MaritalDialog` + `WorkTypeDialog`）
- ❌ 在组件内部硬编码颜色、字体、间距
- ❌ 在组件内部写死业务逻辑（如"教育水平有7个选项"）

---

## 图片处理规范

KYC 流程中涉及图片上传的场景（身份证、人脸识别等）需要**统一图片处理策略**。

### 图片压缩（强制要求）

**所有用户上传的图片必须经过压缩处理**，禁止直接上传原图：

| 场景 | 建议压缩后大小  | 建议质量     | 建议分辨率 |
|------|----------|----------|-----------|
| 身份证照片 | ≤ 500KB  | 80%      | ≤ 1920px 长边 |
| 人脸照片 | ≤ 500KB  | 80%      | ≤ 1280px 长边 |
| 活体检测截图 | 按 SDK 要求 | 按 SDK 要求 | 按 SDK 要求 |

**压缩策略**：
1. 先按目标分辨率缩放（保持宽高比）
2. 再按目标质量压缩（JPEG）
3. 若仍超过目标大小，循环降低质量直到满足要求
4. 压缩失败时降级：优先保证尺寸限制，其次保证质量

**压缩失败处理**：
```kotlin
val bytes = ImageCompressUtil.compress(imageUri, context)
    ?: throw IllegalStateException("图片压缩失败")
// 压缩失败走错误恢复流程，绝不 fallback 原图字节（避免大图 readBytes 触发 OOM）
```

### 图片选择 API 统一

**选择图片和拍照必须统一调用方式**，项目中只应存在一套图片获取逻辑：

```
统一封装：ImagePicker

方法：
  - pickFromCamera()      — 调起相机拍照
  - pickFromGallery()     — 调起系统图库
  - pickWithChoice()      — 弹出选择（拍照 / 图库）

返回：
  - URI / FilePath / Bitmap（统一为项目约定的类型）
  - 错误信息（权限拒绝、取消选择、读取失败等）
```

**禁止做法**：
- ❌ 不同页面使用不同的图片选择库或 API
- ❌ 身份证页面用 CameraX，人脸识别页面用系统相机
- ❌ 图库选择在不同页面使用不同的 Intent 方式

### 图片处理流程（标准）

```
1. 用户触发上传（点击上传区域 / 开始验证）
   ↓
2. 调起统一 ImagePicker（拍照 / 图库）
   ↓
3. 获取原始图片 URI
   ↓
4. 读取图片元信息（宽高、大小、方向）
   ↓
5. 按场景策略压缩（缩放 + 质量压缩）
   ↓
6. 校正图片方向（处理手机旋转 EXIF）
   ↓
7. 生成压缩后的图片文件 / 字节数组
   ↓
8. 上传压缩后的图片到后端
```

### 图片缓存策略

- 压缩后的图片通常不需要本地长期缓存（后端存储后清理）
- 身份证页面的照片预览可以临时缓存，但退出页面后释放
- 人脸识别的照片不缓存，直接上传后丢弃

### 图片回显：使用三方框架加载 URI（强制建议）

KYC 流程中拍照或图库选择后，页面上通常需要**回显预览图片**。**强烈建议使用 Coil / Glide 等成熟的图片加载框架直接通过 URI 加载**，而不是自行解码 Bitmap。

**原因**：
- 自行 `BitmapFactory.decodeStream(uri)` 加载高分辨率原图极易触发 OOM
- 三方框架内置了采样缩放（`inSampleSize`）、内存缓存、Bitmap 复用池、生命周期感知取消等机制
- 即使已经用 `ImageCompressor` 压缩过，压缩后的文件也可能有几百 KB，直接 decode 为 Bitmap 仍可能占用大量内存（500KB JPEG 解码后可达 ~10MB ARGB8888）

**推荐用法**：

```kotlin
// Coil（推荐 — 轻量、Kotlin 优先、协程原生支持）
// 在 ImageView 中加载 URI 预览
imageView.load(uri) {
    crossfade(true)
    size(1024, 1024)  // 限制解码尺寸，避免全分辨率加载
    memoryCachePolicy(CachePolicy.DISABLED)  // 预览类图片不建议占内存缓存
}

// Glide（备选 — 功能更全、Java 生态兼容性好）
Glide.with(context)
    .load(uri)
    .override(1024, 1024)  // 限制尺寸
    .skipMemoryCache(true) // 预览图片不占内存
    .into(imageView)

// 添加依赖（任选其一）：
// implementation("io.coil-kt:coil:2.x")
// implementation("com.github.bumptech.glide:glide:4.x")
```

**禁止做法**：
```kotlin
// ❌ 自行 decode URI 加载原图 — 极易 OOM
val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
imageView.setImageBitmap(bitmap)

// ❌ 即使读了 bounds 手动采样，也容易出 bug（旋转、EXIF、内存计算误差等）
val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
val bitmap = BitmapFactory.decodeStream(inputStream, null, opts)
imageView.setImageBitmap(bitmap)
```

**特别注意**：压缩是给**上传**用的（需要 ≤500KB 的 JPEG 文件），回显是给**展示**用的（需要缩放后的 Bitmap 给 ImageView）。两者目的不同，不要混用 —— 压缩文件可以继续传给 Coil/Glide 加载，框架会自动做展示级别的缩放。

---

## 拍照/图库选择参考实现

以下代码来自实际项目，供实现时参考。**使用前必须对照项目实际情况检查内存风险和线程安全**。

### 自定义相机（CameraX + 后置摄像头）

```kotlin
/**
 * 自定义相机页面，用于身份证拍照。
 * 使用 CameraX 后置摄像头预览，蒙层抠孔取景框 + 拍照后通过 FileProvider 返回 URI。
 *
 * 【内存/ANR 检查要点】
 * 1. cameraExecutor 使用 newSingleThreadExecutor，拍照回调在子线程，不会阻塞主线程
 * 2. ProcessCameraProvider 通过 addListener 异步获取，主线程回调
 * 3. onDestroy 时必须 shutdown() executor，否则线程泄漏
 * 4. outputFile 在 cacheDir 创建，系统可自动清理；取消拍照时需手动 delete()
 * 5. ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY 会牺牲质量换速度，身份证场景可用
 * 6. 蒙层抠孔尺寸测量依赖 root.post {} 等布局完成，避免宽高为 0
 */
class CameraActivity : BaseActivity<ActivityCameraBinding, CameraViewModel>() {

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var outputFile: File
    private var photoTaken = false

    override fun onInit() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        outputFile = File(cacheDir, "id_photo_${System.currentTimeMillis()}.jpg")
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.viewFinder.surfaceProvider
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
            } catch (_: Exception) {
                toast("相机不可用")
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        capture.takePicture(outputOptions, cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    photoTaken = true
                    val uri = FileProvider.getUriForFile(
                        this@CameraActivity, "${packageName}.fileprovider", outputFile
                    )
                    setResult(RESULT_OK, Intent().apply { data = uri; addFlags(FLAG_GRANT_READ_URI_PERMISSION) })
                    finish()
                }
                override fun onError(exception: ImageCaptureException) {
                    toast("拍照失败")
                    finish()
                }
            }
        )
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()  // 必须：防止线程泄漏
        if (!photoTaken && ::outputFile.isInitialized && outputFile.exists()) {
            outputFile.delete()    // 取消拍照时清理临时文件
        }
        super.onDestroy()
    }
}
```

### 拍照/图库选择弹窗

```kotlin
/**
 * 拍照 / 图库 二选一弹窗。
 * 内嵌在 IdentityInfoActivity 中，由 ViewModel 事件驱动显示/隐藏。
 *
 * 【注意事项】
 * - 不使用 Dialog，而是作为 View 嵌入页面布局（visibility 控制），避免 Dialog 生命周期问题
 * - 点击外部区域关闭通过遮罩层 onClick 实现
 */
// 在 XML 布局中：
// <LinearLayout android:id="@+id/photoChoiceDialog" android:visibility="gone">
//     <TextView android:text="Tomar foto" android:onClick="onTakePhoto" />
//     <TextView android:text="Seleccionar de galería" android:onClick="onPickGallery" />
// </LinearLayout>

// 在 Activity 中：
private fun showPhotoChoice() {
    binding.photoChoiceDialog.visibility = View.VISIBLE
}

private fun hidePhotoChoice() {
    binding.photoChoiceDialog.visibility = View.GONE
}

// 拍照：启动自定义 CameraActivity
private val cameraLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == RESULT_OK) {
        result.data?.data?.let { uri -> viewModel.onImageReceived(uri) }
    }
}
```

### 图片压缩参考实现

```kotlin
/**
 * 图片压缩工具。
 * 两步解码（bounds 探测 → ByteArray 解码确保 inSampleSize 被遵守）避免 OOM，
 * JPEG 循环降质 + 分辨率兜底确保目标大小。
 *
 * 【内存/ANR 检查要点】
 * 1. 必须在 IO 线程调用（withContext(Dispatchers.IO)）—— 文件读写 + Bitmap 解码都是阻塞操作
 * 2. bounds 探测阶段用 inJustDecodeBounds = true，不分配像素内存（O(1) 内存）
 * 3. 内存预算校验：估计解码后占用内存 = w * h * 4 bytes，超出 MAX 则加倍 inSampleSize
 * 4. ByteArray 二次解码：避免某些 ROM 上 BitmapFactory.decodeStream 忽略 inSampleSize 的 bug
 * 5. FileOutputStream.channel.truncate(0) 复用文件避免重复创建
 * 6. 质量循环退到 MIN_QUALITY 仍超限 → 降分辨率兜底（最多 2 轮，每轮缩至 75%）
 * 7. 降分辨率产生的中间 Bitmap 必须 recycle()，最终 Bitmap 在 finally 中回收
 * 8. 整个方法 try-catch Throwable，任何异常返回 null（不崩溃）
 */
object ImageCompressor {
    private const val MAX_FILE_SIZE = 500 * 1024L       // 目标：≤500KB
    private const val MIN_QUALITY = 30                   // 最低 JPEG 质量
    private const val MAX_DECODE_MEMORY = 32 * 1024 * 1024L  // 单张解码内存上限 ~32MB

    fun compress(context: Context, uri: Uri, maxSide: Int = 1920, filePrefix: String = "img"): File? {
        return try {
            // 1. bounds 探测 → 只读尺寸，不分配内存
            val (rawW, rawH) = context.contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, opts)
                opts.outWidth to opts.outHeight
            } ?: return null

            // 2. 计算 inSampleSize（2 的幂次）+ 内存预算
            var sampleSize = 1
            while (rawW / sampleSize > maxSide || rawH / sampleSize > maxSide) sampleSize *= 2
            var estBytes = (rawW / sampleSize).toLong() * (rawH / sampleSize) * 4
            while (estBytes > MAX_DECODE_MEMORY) { sampleSize *= 2; estBytes = (rawW / sampleSize).toLong() * (rawH / sampleSize) * 4 }

            // 3. ByteArray 解码（确保 inSampleSize 在所有 ROM 上被遵守）
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }) ?: return null

            // 4. 精确缩放 + 5. JPEG 质量循环 + 6. 降分辨率兜底
            // ...（详细实现见上方完整代码）
            file.takeIf { it.length() > 0 }
        } catch (_: Throwable) { null }
    }
}
```

### 使用示例（OCR 场景）

```kotlin
// 在 ViewModel 中：
fun onImageReceived(uri: Uri) {
    viewModelScope.launch {
        val file = withContext(Dispatchers.IO) {
            ImageCompressor.compress(context, uri, maxSide = 1920, filePrefix = "ocr")
        }
        if (file == null) {
            _toast.emit("图片处理失败")
            return@launch
        }
        // 构造 Multipart 上传
        val part = MultipartBody.Part.createFormData(
            "image_field_name", file.name,
            file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        )
        apiService.ocrVerify(image = part, type = "1")
    }
}
```

### 风险和兜底策略总结

| 风险点 | 触发条件 | 兜底方案 |
|--------|---------|---------|
| OOM | 原图 > 4000px 且 inSampleSize 被 ROM 忽略 | ByteArray 二次解码 + 内存预算校验 |
| ANR | 主线程 readBytes / decodeBitmap / compress | 必须在 Dispatchers.IO 上执行 |
| 临时文件泄漏 | 拍照取消 / 压缩异常 | onDestroy 清理 + cacheDir 系统自动回收 |
| 压缩死循环 | 图片纹理复杂，质量降到最低仍超限 | 降分辨率兜底（最多 2 轮） |
| FileProvider 未配置 | 首次使用相机 | 检查 AndroidManifest.xml 中 FileProvider 声明 |
| Executor 泄漏 | onDestroy 未 shutdown | 必须 shutdown() + exit() |
| 图片回显 OOM | 自行 decode URI 原图到 Bitmap | 使用 Coil/Glide 加载 URI，框架内置采样和缓存 |
| 回显与压缩混淆 | 用压缩文件直接 decode 展示 | 压缩=给上传，回显=给展示，走 Coil/Glide |

---

## 通用 UI 组件清单

KYC 流程通常需要以下通用弹窗/组件：

| 组件语义 | 用途 |
|---------|------|
| 选项选择弹窗 | 底部弹出的单选列表，用于字典字段选择 |
| 省市级联弹窗 | 先选省、再选市的两级选择 |
| 银行选择弹窗 | 支持搜索、按常用/字母分组的银行列表 |
| 拍照选择弹窗 | 拍照 / 图库 二选一 |
| 信息确认弹窗 | 提交前展示已填信息供用户确认 |
| 流程挽留弹窗 | 拦截返回键，提示用户确认退出（降低流失率） |

---

## KYC 字段配置策略（硬编码 vs 接口驱动）

KYC 各步骤的字段选项（教育水平、婚姻状况、关系等）有**两种实现策略**，根据项目情况选择。

### 策略对比

| 维度 | 方案 A：硬编码（静态列表） | 方案 B：接口驱动（字典系统） |
|------|--------------------------|---------------------------|
| 数据来源 | 前端 `LocalOptionDataSource` / `object` 常量 | 后端 `dataDictInfoUsingGET(type)` 接口 |
| 选项更新 | **需要发版** | 接口更新即时生效，无需发版 |
| 离线可用 | ✅ 始终可用 | ❌ 依赖网络，需缓存 |
| 复杂度 | 低：一个静态 list | 高：需三级缓存 + 测试模式 |
| 适用场景 | 选项极少变动、快速上线 | 运营需求多、需要 AB 测试 |
| 参考项目 | Rapidinero（当前项目采用此方案） | Skill 通用模板 |

### 方案 A：硬编码实现

```kotlin
/** 本地选项数据源 — 所有 KYC 选择字段选项硬编码 */
object LocalOptionDataSource {
    data class KeyValueOption(val key: Int, val value: String)

    fun educationLevels() = listOf(
        KeyValueOption(1, "Educación preescolar"),
        KeyValueOption(2, "Educación primaria"),
        // ...
    )

    fun maritalStatuses() = listOf(
        KeyValueOption(1, "Soltero/a"),
        KeyValueOption(2, "Casado/a"),
        // ...
    )
}

// ViewModel 中直接引用：
private fun optionsFor(field: BasicInfoField): List<KeyValueOption> {
    return when (field) {
        BasicInfoField.EDUCATION -> LocalOptionDataSource.educationLevels()
        BasicInfoField.MARITAL_STATUS -> LocalOptionDataSource.maritalStatuses()
        // ...
    }
}
```

### 方案 B：接口驱动实现

```kotlin
/**
 * 字典管理器 — 三级缓存策略。
 * 读取顺序：内存缓存 → DataStore 本地缓存（24h TTL） → 网络接口
 */
object DictManager {
    // 内存缓存：ConcurrentHashMap<type, List<DictItem>>
    private val memoryCache = ConcurrentHashMap<String, List<DictItem>>()
    private val cacheManager by lazy { CacheManager.get(AppContextProvider.get()) }

    suspend fun getDict(type: String): List<DictItem> {
        // 1. 内存命中
        memoryCache[type]?.let { return it }
        // 2. 本地 DataStore（24h TTL 校验）
        val local = cacheManager.getObject<CachedDict>("dict_$type")
        if (local != null && !local.isExpired()) {
            memoryCache[type] = local.items
            return local.items
        }
        // 3. 网络兜底
        val resp = apiService.dataDictInfoUsingGET(type)
        val items = resp.data?.p1fvVXvNRGoRO1Jm.orEmpty().map {
            DictItem(key = it.zDb82PNoG3de, value = it.zjGBOedGc67WH)
        }
        memoryCache[type] = items
        cacheManager.putObject("dict_$type", CachedDict(items, System.currentTimeMillis()))
        return items
    }
}

data class CachedDict(val items: List<DictItem>, val timestamp: Long) {
    private val ttlMs = 24 * 60 * 60 * 1000L  // 24h
    fun isExpired() = System.currentTimeMillis() - timestamp > ttlMs
}
```

### 判断标准

**选择方案 A（硬编码）** 当满足以下所有条件：
- 选项极少变动（如性别、婚姻状况）
- 项目无字典接口或字典接口不覆盖这些字段
- 加快开发速度优先于运营灵活性

**选择方案 B（接口驱动）** 当满足以下任一条件：
- 后端有字典接口（`dataDictInfoUsingGET` 或类似）且持续维护
- 运营需要频繁调整选项（如收入区间、工作类型）
- 需要 AB 测试不同选项展示

**混用策略**（推荐）：对稳定字段用硬编码，对可能变动的字段用接口驱动。

---

## 通用选择弹窗和输入框生成

KYC 流程中所有选择交互应复用统一封装的通用组件，而非为每个字段单独创建 Dialog。

### 通用单选弹窗

```kotlin
/**
 * 通用单选弹窗 — 所有字典字段选择复用。
 * 特点：渐变顶栏 + 标题 + 关闭 + BRVAH radio 列表 + 预选中 + 动态高度。
 *
 * @param title 弹窗标题（如 "Nivel educativo"）
 * @param options 选项列表 [{key, value}]
 * @param onSelected 选中回调 (KeyValueOption) -> Unit
 * @param preselectedKey 预选中 key
 */
class OptionSelectDialog(
    context: Context,
    private val title: String,
    private val options: List<KeyValueOption>,
    private val onSelected: (KeyValueOption) -> Unit,
    private val preselectedKey: Int? = null
) : BaseDialog<DialogOptionSelectBinding>(
    context,
    DialogConfig.center(width = MATCH_PARENT).copy(canceledOnTouchOutside = true)
) {
    // 列表高度自适应：screenHeight * 0.55 为上限，超出内部滚动
    // 使用 BRVAH BaseQuickAdapter + DiffUtil 做列表渲染
    // 详见 OptionSelectDialog.kt 完整实现
}
```

**使用方式**（BasicInfo 字段选择）：
```kotlin
// ViewModel 响应点击事件：
when (action) {
    is OnEducationClick -> {
        val field = fetchField(EDUCATION)  // 从进件页面信息接口或本地配置获取
        mEvent.emit(ShowOptionPicker(title = field.label, options = field.options))
    }
}

// Activity 响应事件：
is KycPersonalEvent.ShowOptionPicker -> {
    OptionSelectDialog(
        context = this,
        title = event.title,
        options = event.options,
        onSelected = { option -> viewModel.onPickerSelect(option) }
    ).show()
}
```

### 通用表单输入字段

```kotlin
/**
 * 通用表单选择行 View — KYC 各步骤复用。
 * XML 布局：左侧 label + 右侧 placeholder/选中值 + 右箭头（选择类）或输入框（输入类）。
 * 通过属性区分：
 *   - app:fieldType="select" → 显示右箭头，点击弹出选择器
 *   - app:fieldType="input"  → 显示 EditText，用于文本/数字输入
 *   - app:fieldType="display" → 纯展示，用于确认页
 *
 * 【推荐做法】封装为自定义 View（如 FormSelectView）或样式统一的 include layout
 */
```

**关键设计原则**：
1. **与业务解耦**：组件不感知"教育水平"还是"婚姻状况"，只接收 `label + options + selectedKey`
2. **样式统一**：颜色/圆角/字体从主题/设计 Token 读取
3. **错误态统一**：提供 `errorMessage: String?` 属性，显示红色边框+底部错误文字
4. **可滚动定位**：提供 `scrollToFieldIndex` 状态，Activity 收到后滚动到对应字段

### 确认弹窗

```kotlin
/**
 * 通用信息确认弹窗。
 * 展示只读表单 + "确认" + "修改" 两个按钮。
 * @param items 只读展示的字段列表 [{label, value}]
 */
class ConfirmInfoDialog(
    context: Context,
    private val items: List<ConfirmFieldItem>,  // label + value
    private val onConfirm: () -> Unit,
    private val onModify: () -> Unit
) : BaseDialog<DialogConfirmInfoBinding>(
    context,
    DialogConfig.center(width = MATCH_PARENT).copy(canceledOnTouchOutside = false)
) {
    // 列表渲染 items，每个 item 显示 label: value
}
```

---

## 接口自动扫描与语义匹配

实现 KYC 流程时，skill 应**自动扫描项目中的 API 接口文件**，根据语义识别 KYC 相关接口，而非要求用户手动告知。

### 扫描策略

```
扫描目标：
  项目中的 Retrofit API Service 接口文件（通常为 *ApiService.kt）

扫描方法：
  1. 读取 API Service 文件全文
  2. 对每个接口方法收集：KDoc 注释 + @GET/@POST 路径 + 参数列表 + 响应类型
  3. 按语义关键词匹配 KYC 相关接口

语义关键词（匹配 KYC 接口）：
  进件 / acquisition / acp / acq / submit
  OCR / ocr / verify / recognition
  人脸 / face / check / compare
  银行卡 / bank / card / bankCard
  字典 / dict / dictionary
  区域 / 省市 / area / province / city
  步骤 / step / page / element
  信息认证 / 身份 / identity
  联系人 / contact
  进度 / progress
```

### 接口语义推断流程

```
对于每个匹配到的接口：

1. 读 KDoc 注释 → 获取业务语义（原始路径 + 中文描述）
2. 读 @GET/@POST 路径 → 确认操作类型
3. 读 @Query / @Part / @Body 参数名 → 理解参数语义（读参数上的 KDoc）
4. 读返回类型（BaseBean<ModelXX>）→ 追踪响应字段含义（读 ModelXX 的 KDoc）
5. 交叉验证：检查哪些 KYC ViewModel 实际调用了该接口
```

### 核心进件提交接口识别

最重要的接口是**进件数据提交接口**。识别特征：

```
特征 1：方法名包含 submit / acp / acq / submitAcpInfo
特征 2：@Body 参数包含 List<*> 类型的 items 字段（键值对列表）
特征 3：请求体中有一个 step 字段（Int 类型，注释提到 "步骤" / "1-6"）
特征 4：被多个 KYC ViewModel 调用，且 step 值不同（1/2/3/4/5/6）
```

**匹配模板**（以实际项目为例）：

```kotlin
// 识别到的进件提交接口（示例 — 实际接口因项目而异）：
/**
 * 提交进件数据
 * /api/user/acquisition/submitAcpInfo       ← 原始路径（业务语义）
 * HTTP POST /qYm_uuV/ebyOwFndavmb/t7lIf0   ← 混淆路径
 * @param u8UqQ submitAcqInfoReq
 */
@POST("/qYm_uuV/ebyOwFndavmb/t7lIf0")
suspend fun submitAcpInfoUsingPOST(
    @Body u8UqQ: Model40?
): BaseBean<String?>

// 请求体 Model40 字段语义（从 KDoc 提取）：
//   qPBWjdc6ytuwI8q3C92 : Int?     — 是否更新（0=新增，1=更新）
//   nVtaU : Long?                  — 进件流程 ID
//   koauGzyGQsODmcOj : Int?        — 步骤编号（1-6）
//   tBH6TMf1JkShQqelT8cW : List<Model41>? — 进件数据键值对
//     Model41.wCf7lmX : String?    — 字段 key（来自进件页面元素查询接口）
//     Model41.fRjkoK8Bffa5fl : String? — 字段 value（用户填写的内容）
```

### 接口字段文档化

实现 KYC 步骤时，需要知道每个步骤应提交哪些字段（key-value 对）。有两种方式获取：

**方式 1（接口驱动）**：调用 `acpElementInfoUsingGET(step=N)` → 从返回的 `EntryResp` 列表获取：
- `wCf7lmX` → 提交 key
- `akeHDR4A2` → 是否必填
- `jongFLyn3j` → 显示文本（label）
- `tjUDfh` → 字段类型（1=文本 2=下拉 3=数字 4=单选 6=图像 7=人脸）
- `rRInTRu79Sr5` → 选项列表（如果是下拉/单选）
- `q76BZK` → 验证规则

**方式 2（代码查找）**：查看已有 ViewModel 中的 `buildRequest()` / `buildSubmitRequest()` 方法，找到硬编码的 key 和 value 构造逻辑。

### 其他 KYC 接口识别

| 语义 | 识别特征 |
|------|---------|
| OCR 识别 | `@Multipart` + 方法名含 ocr / verify + 参数含 image/file part |
| 人脸比对 | `@Multipart` + 方法名含 face / check + 前端摄像头采集 |
| 银行列表 | `@GET` + 方法名含 bankList / queryBank + 无参 |
| 字典查询 | `@GET` + 方法名含 dict / dataDict + `@Query type` |
| 区域查询 | `@GET` + 方法名含 area / province / city |
| 进件页面元素 | `@GET` + 方法名含 element / pageInfo + `@Query step` |
| 进件进度 | `@GET` + 方法名含 progress / acquisitionProgress + 无参 |
| 进件数据查询 | `@GET` + 方法名含 queryAcq / queryAcquisition + `@Query step` |

### 使用建议

**实现新 KYC 步骤时**，按以下顺序获取接口信息：

1. **扫描 API Service 文件** → 找到所有 KYC 相关接口
2. **调用 `acpElementInfoUsingGET(step=N)`** → 获取该步骤的字段配置（如果项目有此接口）
3. **查找已有步骤的实现** → 参考同级 ViewModel 的 `buildRequest()` 方法，了解 `Model41` 的 key-value 格式
4. **确认接口参数语义** → 读 `Model40` 等请求体类的 KDoc

---

## 关键设计原则

1. **接口驱动 UI**：先调用进件页面信息接口，根据返回的字段配置动态渲染表单，不硬编码字段
2. **字段语义优先**：接口字段名通常是混淆的，必须通过注释理解语义，不能猜测
3. **双层缓存**：字典缓存（选项数据）和表单缓存（用户输入）分离管理
4. **条件展示**：信用信息等页面的字段存在级联依赖，需根据用户选择动态展示
5. **渠道适配**：人脸识别等步骤需要查询后端配置决定技术方案，不前端写死
6. **挽留机制**：每个页面拦截物理返回，弹出挽留弹窗降低流失
7. **确认机制**：身份证、银行卡等关键信息提交前展示确认弹窗
8. **OCR 辅助**：身份证页面支持 OCR 自动回填，但允许用户修改
9. **通讯录集成**：联系人页面优先使用系统通讯录选择，提升体验
10. **控件复用**：相同交互类型的字段使用统一封装的通用组件，禁止为每个字段单独实现
11. **样式解耦**：控件外观由设计 token / 主题配置决定，组件内部不硬编码颜色、字体、间距
12. **图片压缩**：所有上传图片必须经过压缩（身份证≤500KB，人脸≤300KB），禁止直接上传原图
13. **图片 API 统一**：拍照和图库选择必须统一封装，项目中只存在一套图片获取逻辑
14. **银行卡双模式**：银行卡页面支持 KYC 步骤模式和外部添加模式，通过 `isExternal` 路由参数区分
15. **人脸非 KYC 模式**：人脸识别支持非 KYC 场景调用，通过 `isKyc` 路由参数区分，结果通过 `savedStateHandle` 回传
16. **Face++ 降级机制**：Face++ SDK 失败时自动降级到自研渠道，保证流程可用性
17. **网络请求适配**：不强制使用特定形式（如 `launchLoading`），优先复用项目现有机制
18. **接口自动扫描**：实现前先扫描项目 API Service 文件，根据 KDoc 语义匹配 KYC 接口，而非假设接口签名
19. **字段配置可选**：字段选项可采用硬编码（小项目/稳定字段）或接口驱动（大项目/运营需求），两种方案均可
20. **图片压缩防崩溃**：压缩必须在 IO 线程执行，含 OOM 保护（inSampleSize + 内存预算）、临时文件清理、降分辨率兜底
21. **三方渠道可选**：人脸识别三方 SDK（如 Face++）为可选集成；即使集成也必须有自研兜底

---

## 实现检查清单

实现新的 KYC 页面时，按以下清单检查：

- [ ] 扫描项目 API Service 文件，识别并记录所有 KYC 相关接口的语义
- [ ] 根据项目情况选择字段配置策略（硬编码 / 接口驱动 / 混用）
- [ ] 通过进件页面信息接口获取该步骤的字段配置（如接口存在）
- [ ] 通过字典接口获取选择字段的选项列表（如采用接口驱动方案）
- [ ] 页面初始化时读取本地缓存恢复数据
- [ ] 用户修改表单后自动保存缓存（含防抖 + onPause flush）
- [ ] 缓存考虑 TTL 机制（建议 24h），避免过期数据残留
- [ ] 拦截物理返回键，展示挽留弹窗
- [ ] 提交前展示确认弹窗（关键信息页：身份证/银行卡）
- [ ] 提交数据包含展示文本和选项 key
- [ ] 提交接口使用正确的步骤标识
- [ ] 字典数据支持内存→本地→网络三级缓存（如采用接口驱动方案）
- [ ] 选择字段使用统一封装的通用组件（不单独实现）
- [ ] 确认弹窗使用通用组件（不每个步骤单独写）
- [ ] 控件外观由设计 token / 主题配置决定（不硬编码样式）
- [ ] 图片上传前经过压缩处理（身份证≤500KB，人脸≤500KB）
- [ ] 拍照和图库选择使用统一封装的 ImagePicker
- [ ] 图片压缩在 IO 线程执行，含 OOM/ANR 保护（内存预算 + ByteArray 解码 + 降分辨率兜底）
- [ ] 图片压缩异常时返回 null 而非崩溃，调起方处理 null 分支
- [ ] 拍照使用统一封装的 CameraX 实现，executor 在 onDestroy 正确 shutdown
- [ ] 拍照和图库选择使用统一封装的入口（PhotoChoiceDialog 或 ImagePicker）
- [ ] 临时文件在取消/异常时清理（onDestroy + cacheDir 自动回收）
- [ ] 图片回显使用 Coil/Glide 加载 URI，禁止自行 BitmapFactory.decode 原图
- [ ] 银行卡页面支持 KYC/外部添加双模式（如需要）
- [ ] 人脸识别支持 KYC/非 KYC 双模式（如需要）
- [ ] 人脸三方渠道（如 Face++）失败时降级到自研渠道（如集成三方 SDK）
- [ ] 信用信息步骤根据进件页面信息接口返回值决定是否展示（可选步骤）
- [ ] 网络请求复用项目现有统一处理机制（或提供最小实现）
- [ ] 编译通过 + 缓存读写正常 + 各项选择弹窗可用 + 提交流程畅通
