/**
 * Step 2: 联系人信息 — KYC进件流程第二步
 *
 * 业务目的：
 *   收集紧急联系人，用于贷后催收和风控。
 *
 * 教学重点：
 *   1. 默认展示2个联系人卡片，可添加至最大数量（由配置控制）
 *   2. 电话输入优先调起系统通讯录选择器（ACTION_PICK），失败降级为手动输入
 *   3. 从通讯录选择后自动解析姓名和电话号码
 *   4. 每个联系人的关系选项来自后端字典
 *   5. 提交时mapIndexed构建字段列表，使用步骤标识 t3e8L=2
 */

// ============================================================
// State
// ============================================================

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

// ============================================================
// Action / Event
// ============================================================

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

// ============================================================
// ViewModel
// ============================================================

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
                    // ⚠️ 交互时机: 点击"关系" → 打开关系选择弹窗
                    // ⚠️ 流程规则: 每个联系人独立选择关系，记录当前正在操作的联系人索引
                    val field = fetchField(CONTACT_RELATION)
                    mState.update { copy(pickerField = field, pickerContactIndex = action.index) }
                }

                is OnPhoneClick -> {
                    // ⚠️ 交互时机: 点击电话 → 优先调起系统通讯录选择器
                    // ⚠️ 流程规则: ACTION_PICK → 解析name+phone → 自动回填；失败则降级为手动输入
                    mEvent.emit(LaunchContactPicker(action.index))
                }

                is OnContactPicked -> {
                    // ⚠️ 交互时机: 从系统通讯录选择后自动回填姓名和电话
                    // ⚠️ 流程规则: 解析通讯录返回的name和phone，设置isEditable=false
                    val idx = state.value.pickerContactIndex
                    updateContact(idx) { copy(name = action.name, phone = action.phone, isEditable = false) }
                    saveCache()
                }

                is OnContactPickFailed -> {
                    // ⚠️ 交互时机: 通讯录选择失败（无权限/无数据），切换为手动输入模式
                    updateContact(action.index) { copy(isEditable = true) }
                }

                is OnPickerSelect -> {
                    // ⚠️ 交互时机: 用户在关系弹窗中选择后回填到当前联系人
                    val idx = state.value.pickerContactIndex
                    val option = state.value.pickerField?.options?.find { it.key == action.key }
                    updateContact(idx) {
                        copy(relation = option?.value ?: "", relationKey = option?.key)
                    }
                    mState.update { copy(pickerField = null) }
                    saveCache()
                }

                is OnAddContactClick -> {
                    // ⚠️ 交互时机: 添加联系人（不超过maxContacts）
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
                    // ⚠️ 接口调用: 校验通过后发起提交
                    【网络请求】 { submitData() }
                }

                else -> {}
            }
        }
    }

    private suspend fun submitData() {
        // ⚠️ 接口调用: 遍历contacts，mapIndexed构建AcqDataItem列表，t3e8L=2
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
