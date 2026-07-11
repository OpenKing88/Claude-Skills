[← Step 4: 银行卡信息](step-4-bank-card.md) | [→ Step 6: 人脸识别](step-6-face-recognition.md)

# Step 5: 信用信息（可选）

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
