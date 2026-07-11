/**
 * Step 4: 银行卡信息 — KYC进件流程第四步
 *
 * 业务目的：
 *   收集收款账户，用于放款。
 *
 * 教学重点：
 *   1. 双模式支持：isExternal标志决定不同提交API和导航行为
 *   2. 卡类型决定账号长度限制（CLABE=18位，储蓄卡=16位）
 *   3. 银行列表支持三级缓存（内存→本地→网络）
 *   4. 银行选择弹窗支持搜索和按常用/字母分组
 *   5. 提交前展示确认弹窗（卡号脱敏显示）
 *   6. KYC模式用submitAcqData(t3e8L=4)，外部模式用submitBankCard结构化接口
 */

// ============================================================
// State
// ============================================================

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

// ============================================================
// Action / Event
// ============================================================

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

// ============================================================
// ViewModel
// ============================================================

class KycBankViewModel : BaseViewModel() {

    private val mState = MutableStateFlow(KycBankState())
    val state = mState.asStateFlow()

    private val mEvent = MutableSharedFlow<KycBankEvent>()
    val event = mEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            // ⚠️ 接口调用: 加载银行列表（三级缓存：内存→本地→网络）
            val bankList = bankRepository.getBankList()
            mState.update { copy(bankList = bankList) }
        }
    }

    /** 注入路由参数，区分 KYC/外部模式 */
    fun setExternal(isExternal: Boolean) {
        if (state.value.isExternal == isExternal) return
        mState.update { copy(isExternal = isExternal) }
        if (!isExternal) {
            // ⚠️ 流程规则: KYC模式下加载本地缓存恢复数据，外部模式不缓存
            viewModelScope.launch { loadCache() }
        }
    }

    fun onAction(action: KycBankAction) {
        viewModelScope.launch {
            when (action) {
                is OnBackClick -> mEvent.emit(NavigateBack)

                is OnAccountTypeChange -> {
                    // ⚠️ 流程规则: 切换卡类型时清空已输入的卡号
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
                    // ⚠️ 流程规则: 只允许数字输入，根据卡类型限制长度
                    // ⚠️ CLABE=18, Debit=16, 过滤非数字字符
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
        // ⚠️ 双模式: isExternal标志决定不同的提交API和导航行为
        if (s.isExternal) {
            // ⚠️ 外部模式: 使用submitBankCard结构化接口 + popBackStack关闭页面
            // ⚠️ 流程规则: 外部模式不写缓存
            apiService.submitBankCard(
                SubmitBankCardReq(
                    cardNumber = s.accountNumber,
                    bankId = s.bankId?.toString() ?: "",
                    accountType = s.accountType,
                )
            )
            mEvent.emit(NavigateBack)
        } else {
            // ⚠️ KYC模式: 使用submitAcqData(t3e8L=4) + 写缓存 + 跳转下一步
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
