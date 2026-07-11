/**
 * Step 1: 个人信息 — KYC进件流程第一步
 *
 * 业务目的：
 *   收集用户的基础社会属性（教育水平、婚姻状况、工作类型、月收入、居住省市），用于风控评估。
 *
 * 教学重点：
 *   1. 所有选择字段的选项来自后端字典接口，不应前端硬编码
 *   2. 每个字段需同时保存展示文本和提交值
 *   3. 省市选择需要单独的地区数据接口
 *   4. 页面支持本地缓存（24h TTL），退出重进可恢复
 *   5. 链式弹窗优化：选择后自动检查下一个空字段并弹出
 *   6. 提交接口使用步骤标识 t3e8L=1
 */

// ============================================================
// State
// ============================================================

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

// ============================================================
// Action / Event
// ============================================================

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

// ============================================================
// ViewModel
// ============================================================

class KycPersonalViewModel : BaseViewModel() {

    private val mState = MutableStateFlow(KycPersonalState())
    val state = mState.asStateFlow()

    private val mEvent = MutableSharedFlow<KycPersonalEvent>()
    val event = mEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            // ⚠️ 交互时机: 页面初始化时从本地缓存恢复表单数据
            // ⚠️ 流程规则: 缓存24h TTL，读取必须try/catch，详见「缓存序列化安全」
            loadCache()
            // ⚠️ 交互时机: 预热地区数据，确保用户点击省市区时无需等待
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
                    // ⚠️ 交互时机: 点击居住省市 → 判断是否已有省选择，有则直接进入市选择
                    if (state.value.selectedProvinceId != null) {
                        mState.update { copy(areaPickerStep = City(...)) }
                    } else {
                        mState.update { copy(areaPickerStep = Province) }
                    }
                }

                is KycPersonalAction.OnAreaSelect -> {
                    // ⚠️ 交互时机: 地区选择 — 2步级联（省 → 市）
                    // ⚠️ 流程规则: 选择省后自动进入市选择，选市后关闭弹窗并缓存
                    when (val step = state.value.areaPickerStep) {
                        is Province -> {
                            // ⚠️ 交互时机: 选省后自动打开市列表，无需用户再次点击
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
                            // ⚠️ 流程规则: 每次字段变更后自动保存缓存
                            saveCache()
                        }
                        else -> {}
                    }
                }

                is KycPersonalAction.OnPickerSelect -> {
                    // ⚠️ 交互时机: 链式选择 — 用户选择一个字段后自动检查下一个空字段
                    // ⚠️ 流程规则: findNextEmptyField → auto-open next picker，提升填写效率
                    val field = state.value.pickerField ?: return@launch
                    val option = field.options.find { it.key == action.key }

                    // ⚠️ 链式弹窗：选择后自动打开下一个空字段
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
                    // ⚠️ 流程规则: 每次字段变更后自动保存缓存
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
                    // ⚠️ 接口调用: 调用网络请求，提交个人信息数据
                    // ⚠️ 交互时机: 校验通过后发起提交
                    【网络请求】 { submitData() }
                }

                else -> {}
            }
        }
    }

    private suspend fun submitData() {
        // ⚠️ 接口调用: 构建AcqDataItem列表后调用submitAcqData(t3e8L=1)
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

        // ⚠️ 接口调用: t3e8L=1 表示个人信息步骤
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
