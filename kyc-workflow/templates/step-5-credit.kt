/**
 * Step 5: 信用信息 — KYC进件流程第五步（可选步骤）
 *
 * 业务目的：
 *   收集用户信用历史，用于风控额度评估。
 *
 * 教学重点：
 *   1. 该步骤是否展示由后端配置控制（acpElementInfo(step=5)返回字段时显示）
 *   2. 字段间存在级联依赖：先选贷款经历，若>0才展示在贷笔数；在贷笔数>0才展示在贷金额
 *   3. 级联清除规则：loanCount=0 → 清空currentLoan+totalAmount；currentLoan=0 → 清空totalAmount
 *   4. 提交时只包含上游字段值非零的下游字段
 *   5. 提交接口使用步骤标识 t3e8L=5
 */

// ============================================================
// State
// ============================================================

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

// ============================================================
// Action / Event
// ============================================================

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

// ============================================================
// ViewModel
// ============================================================

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
                    // ⚠️ 流程规则: 仅在贷款经历>0时展示在贷笔数弹窗
                    if (state.value.loanCountKey != 0) {
                        mState.update { copy(showCurrentLoanPicker = true) }
                    }
                }

                is OnAmountClick -> {
                    // ⚠️ 流程规则: 仅在在贷笔数>0时展示在贷金额弹窗
                    if (state.value.currentLoanCountKey != 0) {
                        mState.update { copy(showAmountPicker = true) }
                    }
                }

                is OnPickerSelect -> {
                    // ⚠️ 流程规则: 级联清除 — 上游为0时清除下游字段
                    when (action.fieldCode) {
                        LOAN_COUNT_CODE -> {
                            mState.update {
                                copy(
                                    loanCountKey = action.key,
                                    // ⚠️ 级联清除: loanCount=0 → 清空currentLoan + totalAmount
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
                                    // ⚠️ 级联清除: currentLoan=0 → 清空totalAmount
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
                    // ⚠️ 接口调用: 校验通过后提交
                    【网络请求】 { submitData() }
                }

                else -> {}
            }
        }
    }

    private suspend fun submitData() {
        // ⚠️ 流程规则: 只提交上游字段值非零的字段
        // ⚠️ 接口调用: t3e8L=5 表示信用信息步骤
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
