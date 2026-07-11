[← 流程概述](flow-overview.md) | [→ Step 2: 联系人信息](step-2-contact-info.md)

# Step 1: 个人信息

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
