[← Step 3: 身份证认证](step-3-identity.md) | [→ Step 5: 信用信息](step-5-credit-info.md)

# Step 4: 银行卡信息

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
