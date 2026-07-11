[← Step 1: 个人信息](step-1-personal-info.md) | [→ Step 3: 身份证认证](step-3-identity.md)

# Step 2: 联系人信息

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
