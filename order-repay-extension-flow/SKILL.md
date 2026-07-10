---
name: order-repay-extension-flow
version: "1.0.0"
description: >
  信贷 App 核心页面完整实现方案，涵盖订单详情页、还款详情页、展期页面。
  自动扫描项目网络层匹配已有接口定义，优先复用。
  触发关键词：订单详情, 还款详情, 展期, 账单详情, order detail, repayment detail, 
  extension, loan order, bill detail, 还款计划, 还款页面, 展期页面.
---

# 订单详情 / 还款详情 / 展期页面 Skill

## 概述

本 Skill 提供完整的信贷 App 核心页面实现方案：
1. **订单详情页** — 展示订单状态、产品信息、借款金额、收款账户等
2. **还款详情页** — 展示待还金额、逾期状态、还款渠道选择、WebView 支付
3. **展期页** — 展示展期预览信息、直接展期/付费展期双分支流程

**执行前扫描要求**：
AI 必须先扫描目标项目的网络层/API 定义区域（常见目录名如 `network/`、`api/`、`service/`、`remote/` 等，具体以项目实际结构为准），检查是否已存在类似语义的接口和 DTO。如果存在，优先复用已有定义；如果不存在，按本 Skill 的语义占位创建新接口。

**扫描检查清单**：
- [ ] 是否有「订单详情/账单详情」接口（返回订单基本信息、状态、金额、日期）
- [ ] 是否有「还款渠道列表」接口（返回支付方式列表）
- [ ] 是否有「生成还款码/支付链接」接口（返回支付 URL）
- [ ] 是否有「展期预览」接口（返回新到期日、展期费、展期天数）
- [ ] 是否有「确认展期」接口（直接确认，无需支付）
- [ ] 是否有「还款成功弹窗检查」接口（返回是否展示成功弹窗）
- [ ] DTO 中是否有语义对应的字段（如 orderId、status、amount、dueDate、productName 等）

---

## 一、订单详情页（OrderDetail）

### 1.1 页面结构（框架无关描述）

```
┌─────────────────────────────────────────┐
│  标题栏：返回按钮 + "Detalles del pedido" │
├─────────────────────────────────────────┤
│                                         │
│  ┌─────────────────────────────────┐   │
│  │      [状态插画图]                │   │
│  │                                 │   │
│  │      "审核中" / "已拒绝" 等      │   │
│  │      状态描述文案                │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  产品信息区                      │   │
│  │  [Logo] 产品名称                 │   │
│  │  借款金额：$ 10,000              │   │
│  │  借款期限：7 días                │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  收款账户区                      │   │
│  │  银行卡号：****1234              │   │
│  │  银行名称：BBVA                  │   │
│  │  到期日期：2023-02-15            │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  [底部按钮：根据状态变化]         │   │
│  │  "Volver" / "Pagar ahora" 等    │   │
│  └─────────────────────────────────┘   │
│                                         │
└─────────────────────────────────────────┘
```

**核心设计**：页面内容完全由「订单状态」驱动。不同状态对应不同的插画、文案、按钮行为。

### 1.2 状态配置驱动 UI（核心设计模式）

```kotlin
/**
 * [ADAPT] 状态配置数据类，根据项目实际状态码调整
 */
data class OrderStatusConfig(
    /** [SEMANTIC] 状态插画资源 ID */
    val statusIconRes: Int,
    /** [SEMANTIC] 状态描述文案 */
    val statusText: String,
    /** [SEMANTIC] 是否使用错误主题色（如红色），否则用默认主题 */
    val isErrorTheme: Boolean = false,
    /** [SEMANTIC] 底部按钮文案 */
    val buttonText: String,
    /** [SEMANTIC] 底部按钮点击行为 */
    val buttonAction: OrderDetailAction,
    /** [SEMANTIC] 是否显示底部按钮 */
    val buttonVisible: Boolean = true,
    /** [SEMANTIC] 是否显示产品详情区域 */
    val showProductDetail: Boolean = true,
)

/**
 * [ADAPT] 根据订单状态码解析 UI 配置。
 * 每种状态对应唯一的视觉表现和交互行为。
 *
 * [IMPORTANT] 状态码和文案需根据项目实际调整，以下仅为常见信贷状态示例：
 */
fun resolveStatusConfig(status: Int): OrderStatusConfig = when (status) {
    /* [SEMANTIC] 审核中 */
    20 -> OrderStatusConfig(
        statusIconRes = R.drawable.status_review,
        statusText = "Su solicitud está siendo revisada; por favor, espere.",
        buttonText = "Volver",
        buttonAction = OrderDetailAction.OnReturnClick,
    )
    /* [SEMANTIC] 审核拒绝 */
    22 -> OrderStatusConfig(
        statusIconRes = R.drawable.status_refuse,
        statusText = "Su solicitud no ha sido aprobada.",
        buttonText = "Volver a solicitar",
        buttonAction = OrderDetailAction.OnReapplyClick,
    )
    /* [SEMANTIC] 放款中 */
    3 -> OrderStatusConfig(
        statusIconRes = R.drawable.status_inprogress,
        statusText = "¡Solicitud aprobada! Préstamo en trámite.",
        buttonText = "Volver",
        buttonAction = OrderDetailAction.OnReturnClick,
    )
    /* [SEMANTIC] 还款中 — 唯一可跳转还款页的状态 */
    4 -> OrderStatusConfig(
        statusIconRes = R.drawable.status_inprogress,
        statusText = "Su préstamo está en curso; por favor, pague a tiempo.",
        buttonText = "Pagar ahora",
        buttonAction = OrderDetailAction.OnRepayClick,
    )
    /* [SEMANTIC] 放款失败（卡号异常等） */
    5 -> OrderStatusConfig(
        statusIconRes = R.drawable.status_carderror,
        statusText = "¡Hay un problema con su cuenta de recepción!",
        isErrorTheme = true,
        buttonText = "Contactar con atención al cliente",
        buttonAction = OrderDetailAction.OnContactCustomerServiceClick,
    )
    /* [SEMANTIC] 已结清 */
    6 -> OrderStatusConfig(
        statusIconRes = R.drawable.status_success,
        statusText = "Pedido liquidado, solicita otros productos.",
        buttonText = "Solicitar otros",
        buttonAction = OrderDetailAction.OnReapplyClick,
    )
    /* [SEMANTIC] 异常关闭/转账失败 */
    7 -> OrderStatusConfig(
        statusIconRes = R.drawable.status_error,
        statusText = "¡Se ha producido un error durante la transferencia!",
        buttonText = "Volver a solicitar",
        buttonAction = OrderDetailAction.OnReapplyClick,
    )
    else -> OrderStatusConfig(
        statusIconRes = R.drawable.status_default,
        statusText = "",
        buttonText = "Volver",
        buttonAction = OrderDetailAction.OnReturnClick,
    )
}
```

### 1.3 数据模型

```kotlin
/** 页面状态 */
data class OrderDetailState(
    val orderId: String = "",
    /** [SEMANTIC] 订单状态码，驱动整个页面 UI */
    val status: Int = 0,
    /** [SEMANTIC] 产品名称 */
    val productName: String = "",
    /** [SEMANTIC] 产品 Logo URL */
    val productLogoUrl: String? = null,
    /** [SEMANTIC] 借款金额（已格式化展示文本） */
    val loanAmount: String = "",
    /** [SEMANTIC] 借款期限（已格式化展示文本，如 "7 días"） */
    val loanTerm: String = "",
    /** [SEMANTIC] 到期日期 */
    val dueDate: String = "",
    /** [SEMANTIC] 银行卡号（脱敏后，如 "****1234"） */
    val cardNumber: String = "",
    /** [SEMANTIC] 银行名称 */
    val bankName: String = "",
)

/** 用户操作 */
sealed interface OrderDetailAction {
    data object OnBackClick : OrderDetailAction
    data object OnReturnClick : OrderDetailAction
    /** [SEMANTIC] 重新申请 */
    data object OnReapplyClick : OrderDetailAction
    /** [SEMANTIC] 去还款 — 仅 status=还款中时可用 */
    data object OnRepayClick : OrderDetailAction
    /** [SEMANTIC] 联系客服 */
    data object OnContactCustomerServiceClick : OrderDetailAction
}

/** 一次性导航事件 */
sealed interface OrderDetailEvent {
    data object GoBack : OrderDetailEvent
    /** [SEMANTIC] 跳转还款页，携带订单 ID */
    data class GoRepayment(val orderId: String) : OrderDetailEvent
    /** [SEMANTIC] 跳转 WebView */
    data class GoWebView(val url: String, val title: String) : OrderDetailEvent
}
```

### 1.4 ViewModel 核心逻辑

```kotlin
class OrderDetailViewModel : /* [ADAPT] 项目 ViewModel 基类 */ {

    private val _state = /* [ADAPT] */ mutableStateOf(OrderDetailState())
    val state get() = _state

    /** [ADAPT] 状态配置由当前 status 动态计算 */
    val statusConfig: OrderStatusConfig
        get() = resolveStatusConfig(_state.value.status)

    /** 加载订单详情 */
    fun loadOrderDetail(orderId: String) = /* [ADAPT] launchLoading */ {
        /* [ADAPT] 调用订单详情接口 */
        val resp = apiService.getOrderDetail(orderId).toResult()
        val item = resp?.orderItem

        _state.value = _state.value.copy(
            orderId = orderId,
            /* [SEMANTIC] 订单状态码 */
            status = item?.status ?: 0,
            /* [SEMANTIC] 产品名称 */
            productName = item?.productName ?: "",
            /* [SEMANTIC] 产品 Logo URL */
            productLogoUrl = item?.productLogoUrl,
            /* [SEMANTIC] 借款金额 — 注意：State 存格式化后的字符串 */
            loanAmount = item?.loanAmount.formatCurrency(),
            /* [SEMANTIC] 借款期限 */
            loanTerm = item?.loanTerm?.let { "$it días" } ?: "",
            /* [SEMANTIC] 到期日期 */
            dueDate = item?.dueDate ?: "",
            /* [SEMANTIC] 银行卡号 */
            cardNumber = item?.cardNumber ?: "",
            /* [SEMANTIC] 银行名称 */
            bankName = item?.bankName ?: "",
        )
    }

    fun onAction(action: OrderDetailAction) {
        when (action) {
            OrderDetailAction.OnBackClick,
            OrderDetailAction.OnReturnClick -> emitEvent(OrderDetailEvent.GoBack)

            OrderDetailAction.OnReapplyClick -> emitEvent(OrderDetailEvent.GoBack)

            OrderDetailAction.OnRepayClick -> {
                emitEvent(OrderDetailEvent.GoRepayment(_state.value.orderId))
            }

            OrderDetailAction.OnContactCustomerServiceClick -> {
                /* [ADAPT] 由 UI 层拦截，弹出客服弹窗 */
            }
        }
    }
}
```

### 1.5 接口占位定义

```kotlin
/**
 * [API] 获取订单详情
 * [ADAPT] 替换为实际 URL 和网络框架
 *
 * @param orderId [SEMANTIC] 订单 ID
 */
suspend fun getOrderDetail(orderId: String): OrderDetailResult

/**
 * [SEMANTIC] 订单详情响应
 */
data class OrderDetailResult(
    /** [SEMANTIC] 订单明细项 */
    val orderItem: OrderDetailItem? = null,
)

data class OrderDetailItem(
    /** [SEMANTIC] 订单 ID */
    val orderId: String? = null,
    /** [SEMANTIC] 订单状态码：20审核中/22拒绝/3放款中/4还款中/5放款失败/6已结清/7异常关闭 */
    val status: Int? = null,
    /** [SEMANTIC] 产品名称 */
    val productName: String? = null,
    /** [SEMANTIC] 产品 Logo URL */
    val productLogoUrl: String? = null,
    /** [SEMANTIC] 借款金额（原始数值） */
    val loanAmount: Double? = null,
    /** [SEMANTIC] 借款期限（天数） */
    val loanTerm: Int? = null,
    /** [SEMANTIC] 到期日期 */
    val dueDate: String? = null,
    /** [SEMANTIC] 银行卡号 */
    val cardNumber: String? = null,
    /** [SEMANTIC] 银行名称 */
    val bankName: String? = null,
    /** [SEMANTIC] 实际到账金额 */
    val receivedAmount: Double? = null,
    /** [SEMANTIC] 应还金额 */
    val pendingAmount: Double? = null,
    /** [SEMANTIC] 服务费 */
    val serviceFee: Double? = null,
    /** [SEMANTIC] 逾期罚息 */
    val overdueFee: Double? = null,
    /** [SEMANTIC] 展期开关：true=允许展期 */
    val extensionEnabled: Boolean? = null,
    /** [SEMANTIC] 账单 ID */
    val billId: Long? = null,
    /** [SEMANTIC] 剩余还款天数 */
    val remainingDays: Int? = null,
)
```

---

## 二、还款详情页（Repayment）

### 2.1 页面结构（框架无关描述）

```
┌─────────────────────────────────────────┐
│  标题栏：返回按钮 + "Detalles de pago"    │
├─────────────────────────────────────────┤
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  [产品Logo]  产品名称            │   │
│  │                                 │   │
│  │  [状态标签]  "Vence en 3 días"   │   │
│  │           或 "Vencido hace 7"   │   │
│  │                                 │   │
│  │  待还金额                       │   │
│  │  $ 10,000                       │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  还款信息明细                    │   │
│  │  借款金额      $ 10,000         │   │
│  │  到账金额      $ 9,500          │   │
│  │  服务费        $ 500            │   │
│  │  逾期费用      $ 200            │   │
│  │  还款日期      2023-02-15       │   │
│  │  分期数        1                │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  安全提示（可关闭）              │   │
│  │  "请勿向陌生人转账..."           │   │
│  └─────────────────────────────────┘   │
│                                         │
│  [展期还款按钮]（如 extensionEnabled）  │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  [引流产品卡片]（可选）           │   │
│  │  "最高可借 $ 30,000"             │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  [底部支付按钮] "Pagar ahora"    │   │
│  └─────────────────────────────────┘   │
│                                         │
└─────────────────────────────────────────┘
```

**关键设计**：
1. **状态标签颜色**：剩余天数 < 0 → 红色（逾期），≥ 0 → 绿色（正常）
2. **生命周期检测还款成功**：页面 `onResume` 时查询后端是否还款成功，展示成功弹窗
3. **支付流程**：点击支付 → 获取渠道列表 → 选择渠道 → 生成支付链接 → WebView 支付

### 2.2 数据模型

```kotlin
/** 状态标签类型 */
enum class StatusTagType {
    Normal,   // 未逾期（绿色）
    Overdue,  // 逾期（红色）
}

/** 页面状态 */
data class RepaymentState(
    val orderId: String = "",
    /** [SEMANTIC] 产品 Logo URL */
    val productLogoUrl: String = "",
    /** [SEMANTIC] 产品名称 */
    val productName: String = "",
    /** [SEMANTIC] 待还金额（格式化展示文本） */
    val pendingAmount: String = "",
    /** [SEMANTIC] 待还金额（原始 Double，用于构造支付请求） */
    val pendingAmountRaw: Double = 0.0,
    /** [SEMANTIC] 状态标签类型（Normal/Overdue） */
    val statusTag: StatusTagType = StatusTagType.Normal,
    /** [SEMANTIC] 状态标签文案（如 "Vence en 3 días"） */
    val statusTagText: String = "",
    /** [SEMANTIC] 状态描述 */
    val statusDescription: String = "",
    /** [SEMANTIC] 借款金额 */
    val loanAmount: String = "",
    /** [SEMANTIC] 放款日期 */
    val payDate: String = "",
    /** [SEMANTIC] 借款期限 */
    val loanTerm: String = "",
    /** [SEMANTIC] 到期日期 */
    val dueDate: String = "",
    /** [SEMANTIC] 实际到账金额 */
    val receivedAmount: String = "",
    /** [SEMANTIC] 账单总金额（用于构造 AllocationItem） */
    val totalBillAmount: Double? = null,
    /** [SEMANTIC] 逾期费用 */
    val overdueFee: String = "",
    /** [SEMANTIC] 服务费 */
    val serviceFee: String = "",
    /** [SEMANTIC] 是否显示展期按钮 */
    val showExtension: Boolean = false,
    /** [SEMANTIC] 账单 ID */
    val billId: Long? = null,
    /** [SEMANTIC] 是否显示渠道选择弹窗 */
    val showChannelDialog: Boolean = false,
    /** [SEMANTIC] 还款渠道列表 */
    val channels: List<RepayChannel> = emptyList(),
    /** [SEMANTIC] 是否显示还款成功弹窗 */
    val showRepaySuccessDialog: Boolean = false,
)

/** 还款渠道 */
data class RepayChannel(
    /** [SEMANTIC] 渠道名称 */
    val name: String? = null,
    /** [SEMANTIC] 渠道图标 URL */
    val iconUrl: String? = null,
    /** [SEMANTIC] 渠道代码 */
    val channelCode: String? = null,
    /** [SEMANTIC] 支付方式 */
    val paymentMethod: String? = null,
)

/** 用户操作 */
sealed interface RepaymentAction {
    data object OnBackClick : RepaymentAction
    /** [SEMANTIC] 点击支付按钮 */
    data object OnPayClick : RepaymentAction
    /** [SEMANTIC] 点击展期还款 */
    data object OnExtensionClick : RepaymentAction
    /** [SEMANTIC] 点击引流产品卡片 */
    data object OnLoanProductClick : RepaymentAction
    /** [SEMANTIC] 选择还款渠道 */
    data class OnChannelSelected(val channel: RepayChannel) : RepaymentAction
    /** [SEMANTIC] 关闭渠道弹窗 */
    data object OnChannelDialogDismiss : RepaymentAction
}

/** 一次性导航事件 */
sealed interface RepaymentEvent {
    data object GoBack : RepaymentEvent
    /** [SEMANTIC] 跳转展期页，携带订单ID、账单ID、到期日 */
    data class GoExtension(val orderId: String, val billId: Long?, val dueDate: String?) : RepaymentEvent
    /** [SEMANTIC] 跳转借款产品页 */
    data class GoLoanProduct(val productCode: String) : RepaymentEvent
    /** [SEMANTIC] 跳转 WebView 支付 */
    data class GoWebView(val url: String, val title: String) : RepaymentEvent
}
```

### 2.3 ViewModel 核心逻辑

```kotlin
class RepaymentViewModel : /* [ADAPT] */ {

    private val _state = /* [ADAPT] */ mutableStateOf(RepaymentState())
    val state get() = _state

    /** 加载还款详情（通常复用订单详情接口） */
    fun loadRepaymentDetail(orderId: String) = /* [ADAPT] launchLoading */ {
        val resp = apiService.getOrderDetail(orderId).toResult()
        val item = resp?.orderItem

        /** [核心逻辑] 根据剩余还款天数判断状态标签 */
        val remainingDays = item?.remainingDays ?: 0
        val statusTag = if (remainingDays < 0) StatusTagType.Overdue else StatusTagType.Normal
        val statusTagText = when {
            remainingDays < 0 -> "Vencido hace ${-remainingDays} días"
            remainingDays == 0 -> "Vence hoy"
            else -> "Vence en $remainingDays días"
        }

        _state.value = _state.value.copy(
            orderId = orderId,
            billId = item?.billId,
            productLogoUrl = item?.productLogoUrl ?: "",
            productName = item?.productName ?: "",
            /** [SEMANTIC] 待还金额 — 展示文本 */
            pendingAmount = resp?.totalPendingAmount.formatCurrency(),
            /** [SEMANTIC] 待还金额 — 原始值，用于构造支付请求 */
            pendingAmountRaw = resp?.totalPendingAmount ?: 0.0,
            statusTag = statusTag,
            statusTagText = statusTagText,
            loanAmount = item?.loanAmount.formatCurrency(),
            dueDate = item?.dueDate ?: "",
            receivedAmount = item?.receivedAmount.formatCurrency(),
            totalBillAmount = item?.pendingAmount,
            overdueFee = item?.overdueFee.formatCurrency(),
            serviceFee = item?.serviceFee.formatCurrency(),
            /** [SEMANTIC] 展期开关 */
            showExtension = item?.extensionEnabled == true,
        )
    }

    /** 获取还款渠道列表 */
    fun loadRepayChannels() = /* [ADAPT] launchLoading */ {
        val channels = apiService.getRepayChannels().toResult() ?: emptyList()
        _state.value = _state.value.copy(
            channels = channels,
            showChannelDialog = true,
        )
    }

    /** 选择渠道后生成还款码/支付链接 */
    fun generateRepayCode(channel: RepayChannel) = /* [ADAPT] launchLoading */ {
        val req = GeneratePayCodeReq(
            /** [SEMANTIC] 支付分配信息 */
            allocations = listOf(
                AllocationItem(
                    amount = _state.value.totalBillAmount,
                    orderId = _state.value.orderId,
                    billId = _state.value.billId,
                )
            ),
            /** [SEMANTIC] 支付总金额 */
            totalAmount = _state.value.pendingAmountRaw,
            /** [SEMANTIC] 订单类型：1=普通还款 */
            orderType = 1,
            /** [SEMANTIC] 渠道代码 */
            channelCode = channel.channelCode ?: "",
            /** [SEMANTIC] 支付方式 */
            paymentMethod = channel.paymentMethod,
        )

        val result = apiService.generatePayCode(req).toResult()

        /** [核心逻辑] 优先使用 H5 URL，其次使用原生支付 URL */
        val url = result?.h5Url
            ?: result?.nativeUrl
            ?: ""
        val title = result?.productName ?: "Pago"

        emitEvent(RepaymentEvent.GoWebView(url, title))
    }

    /** [核心逻辑] 生命周期 onResume 时检查还款成功弹窗 */
    fun checkRepaySuccessDialog() = /* [ADAPT] launchLoading(showLoading=false) */ {
        val result = apiService.checkRepaySuccess().toResult()
        if (result?.shouldShowDialog == true) {
            _state.value = _state.value.copy(showRepaySuccessDialog = true)
        }
    }

    fun dismissRepaySuccessDialog() {
        _state.value = _state.value.copy(showRepaySuccessDialog = false)
    }

    fun onAction(action: RepaymentAction) {
        when (action) {
            RepaymentAction.OnBackClick -> emitEvent(RepaymentEvent.GoBack)
            RepaymentAction.OnPayClick -> loadRepayChannels()
            RepaymentAction.OnExtensionClick -> emitEvent(
                RepaymentEvent.GoExtension(
                    _state.value.orderId,
                    _state.value.billId,
                    _state.value.dueDate
                )
            )
            RepaymentAction.OnLoanProductClick -> emitEvent(RepaymentEvent.GoLoanProduct(""))
            is RepaymentAction.OnChannelSelected -> {
                _state.value = _state.value.copy(showChannelDialog = false)
                generateRepayCode(action.channel)
            }
            RepaymentAction.OnChannelDialogDismiss -> {
                _state.value = _state.value.copy(showChannelDialog = false)
            }
        }
    }
}
```

### 2.4 接口占位定义

```kotlin
/** [API] 获取还款渠道列表 */
suspend fun getRepayChannels(): List<RepayChannel>

/** [API] 生成还款码/支付链接 */
suspend fun generatePayCode(req: GeneratePayCodeReq): PayCodeResult

/** [API] 检查是否展示还款成功弹窗 */
suspend fun checkRepaySuccess(): RepaySuccessCheckResult

data class GeneratePayCodeReq(
    /** [SEMANTIC] 支付分配列表（支持多订单合并支付） */
    val allocations: List<AllocationItem>? = null,
    /** [SEMANTIC] 支付总金额 */
    val totalAmount: Double = 0.0,
    /** [SEMANTIC] 订单类型：1=普通还款，2=展期 */
    val orderType: Int? = null,
    /** [SEMANTIC] 渠道代码 */
    val channelCode: String = "",
    /** [SEMANTIC] 支付方式 */
    val paymentMethod: String? = null,
)

data class AllocationItem(
    /** [SEMANTIC] 分配金额 */
    val amount: Double? = null,
    /** [SEMANTIC] 订单号 */
    val orderId: String? = null,
    /** [SEMANTIC] 账单 ID */
    val billId: Long? = null,
)

data class PayCodeResult(
    /** [SEMANTIC] H5 支付 URL（优先使用） */
    val h5Url: String? = null,
    /** [SEMANTIC] 原生支付 URL */
    val nativeUrl: String? = null,
    /** [SEMANTIC] 产品名称（用于页面标题） */
    val productName: String? = null,
)

data class RepaySuccessCheckResult(
    /** [SEMANTIC] 是否展示还款成功弹窗 */
    val shouldShowDialog: Boolean? = null,
)
```

---

## 三、展期页（Extension）

### 3.1 页面结构（框架无关描述）

```
┌─────────────────────────────────────────┐
│  标题栏：返回按钮 + "Prórroga de pago"    │
├─────────────────────────────────────────┤
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  展期信息卡片                    │   │
│  │                                 │   │
│  │  原到期日    ~~2023-02-15~~     │   │
│  │       ↓                         │   │
│  │  新到期日    2023-02-22         │   │
│  │                                 │   │
│  │  延长期限    7 días             │   │
│  │  展期费用    $ 500              │   │
│  │                                 │   │
│  │  到期应还    $ 10,500           │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  [底部确认按钮]                  │   │
│  │  "Confirmar aplazamiento"       │   │
│  └─────────────────────────────────┘   │
│                                         │
└─────────────────────────────────────────┘

二次确认弹窗（ExtensionConfirmDialog）：
┌─────────────────────────────────────────┐
│  "确认延期还款？"                      │
│  新到期日：2023-02-22                   │
│  展期费用：$ 500                        │
│                                         │
│  [取消]        [确认]                   │
└─────────────────────────────────────────┘
```

**关键设计：双分支流程**
1. **直接展期**（`canExtendByRepaid = true`）：无需支付展期费，直接调用确认接口
2. **付费展期**（`canExtendByRepaid = false`）：需要支付展期费，走「渠道选择 → 生成支付码 → WebView 支付」流程

### 3.2 数据模型

```kotlin
/** 页面状态 */
data class ExtensionState(
    val orderId: String = "",
    /** [SEMANTIC] 账单 ID */
    val billId: Long = 0,
    /** [SEMANTIC] 是否支持直接展期（无需支付展期费） */
    val canExtendByRepaid: Boolean = false,
    /** [SEMANTIC] 延迟后新还款日期 */
    val newDueDate: String = "",
    /** [SEMANTIC] 展期费用（格式化展示文本） */
    val extensionFee: String = "",
    /** [SEMANTIC] 展期费用（原始 Double，用于支付） */
    val extensionFeeRaw: Double = 0.0,
    /** [SEMANTIC] 延长期限（如 "7 días"） */
    val extensionDays: String = "",
    /** [SEMANTIC] 原还款日（展示删除线） */
    val originalDueDate: String = "",
    /** [SEMANTIC] 到期应还金额 */
    val pendingAmount: String = "",
    /** [SEMANTIC] 到期应还金额（原始值） */
    val pendingAmountRaw: Double = 0.0,
    /** [SEMANTIC] 是否显示二次确认弹窗 */
    val showConfirmDialog: Boolean = false,
    /** [SEMANTIC] 是否显示支付渠道弹窗 */
    val showChannelDialog: Boolean = false,
    /** [SEMANTIC] 支付渠道列表 */
    val channels: List<RepayChannel> = emptyList(),
)

/** 用户操作 */
sealed interface ExtensionAction {
    data object OnBackClick : ExtensionAction
    /** [SEMANTIC] 点击确认延期还款按钮 */
    data object OnConfirmClick : ExtensionAction
    /** [SEMANTIC] 二次确认弹窗点击确认 */
    data object OnConfirmDialogConfirm : ExtensionAction
    /** [SEMANTIC] 二次确认弹窗点击取消/关闭 */
    data object OnConfirmDialogDismiss : ExtensionAction
    /** [SEMANTIC] 支付渠道弹窗关闭 */
    data object OnChannelDialogDismiss : ExtensionAction
    /** [SEMANTIC] 选择支付渠道 */
    data class OnChannelSelected(val channel: RepayChannel) : ExtensionAction
}

/** 一次性导航事件 */
sealed interface ExtensionEvent {
    data object GoBack : ExtensionEvent
    /** [SEMANTIC] 展期成功，跳转账单列表 */
    data object GoOrderList : ExtensionEvent
    /** [SEMANTIC] 跳转 WebView 支付 */
    data class GoWebView(val url: String, val title: String) : ExtensionEvent
}
```

### 3.3 ViewModel 核心逻辑

```kotlin
class ExtensionViewModel : /* [ADAPT] */ {

    private val _state = /* [ADAPT] */ mutableStateOf(ExtensionState())
    val state get() = _state

    /** 加载展期预览信息 */
    fun loadExtensionInfo(orderId: String, billId: Long, repayDate: String?) = /* [ADAPT] */ {
        val resp = apiService.getExtensionPreview(
            ExtensionPreviewReq(billId = billId)
        ).toResult()

        _state.value = _state.value.copy(
            orderId = orderId,
            billId = billId,
            /** [SEMANTIC] 是否可直接展期（无需支付展期费） */
            canExtendByRepaid = resp?.canExtendDirectly == true,
            /** [SEMANTIC] 新到期日 */
            newDueDate = resp?.newDueDate ?: "",
            /** [SEMANTIC] 展期费用 */
            extensionFee = resp?.extensionFee.formatCurrency(),
            extensionFeeRaw = resp?.extensionFee ?: 0.0,
            /** [SEMANTIC] 展期天数 */
            extensionDays = resp?.extensionDays?.let { "$it días" } ?: "",
            /** [SEMANTIC] 原到期日 */
            originalDueDate = repayDate ?: "",
            /** [SEMANTIC] 到期应还金额 */
            pendingAmount = resp?.pendingAmount.formatCurrency(),
            pendingAmountRaw = resp?.pendingAmount ?: 0.0,
        )
    }

    /** [核心逻辑] 二次确认弹窗点击确认后的处理 */
    fun onConfirmDialogConfirm() = /* [ADAPT] */ {
        if (_state.value.canExtendByRepaid) {
            /** 分支 A：直接展期（无需支付展期费） */
            confirmExtensionDirectly()
        } else {
            /** 分支 B：需要支付展期费 */
            _state.value = _state.value.copy(showConfirmDialog = false)
            loadRepayChannels()
        }
    }

    /** 直接确认展期 */
    private suspend fun confirmExtensionDirectly() {
        apiService.confirmExtension(
            ConfirmExtensionReq(billId = _state.value.billId)
        ).toResult()

        showToast("订单延期成功")
        emitEvent(ExtensionEvent.GoOrderList)
    }

    /** 获取支付渠道列表（复用还款页逻辑） */
    fun loadRepayChannels() = /* [ADAPT] */ {
        val channels = apiService.getRepayChannels().toResult() ?: emptyList()
        _state.value = _state.value.copy(
            channels = channels,
            showChannelDialog = true,
        )
    }

    /** 生成展期费支付码（复用还款页逻辑，仅 orderType 不同） */
    fun generateRepayCode(channel: RepayChannel) = /* [ADAPT] */ {
        val req = GeneratePayCodeReq(
            allocations = listOf(
                AllocationItem(
                    amount = _state.value.extensionFeeRaw,
                    orderId = _state.value.orderId,
                    billId = _state.value.billId,
                )
            ),
            totalAmount = _state.value.extensionFeeRaw,
            /** [核心差异] 订单类型 = 2（展期订单） */
            orderType = 2,
            channelCode = channel.channelCode ?: "",
            paymentMethod = channel.paymentMethod,
        )

        val result = apiService.generatePayCode(req).toResult()
        val url = result?.h5Url ?: result?.nativeUrl ?: ""
        val title = result?.productName ?: "Pago"

        emitEvent(ExtensionEvent.GoWebView(url, title))
    }

    fun onAction(action: ExtensionAction) {
        when (action) {
            ExtensionAction.OnBackClick -> emitEvent(ExtensionEvent.GoBack)

            ExtensionAction.OnConfirmClick -> {
                _state.value = _state.value.copy(showConfirmDialog = true)
            }

            ExtensionAction.OnConfirmDialogConfirm -> {
                _state.value = _state.value.copy(showConfirmDialog = false)
                onConfirmDialogConfirm()
            }

            ExtensionAction.OnConfirmDialogDismiss -> {
                _state.value = _state.value.copy(showConfirmDialog = false)
            }

            ExtensionAction.OnChannelDialogDismiss -> {
                _state.value = _state.value.copy(showChannelDialog = false)
            }

            is ExtensionAction.OnChannelSelected -> {
                _state.value = _state.value.copy(showChannelDialog = false)
                generateRepayCode(action.channel)
            }
        }
    }
}
```

### 3.4 接口占位定义

```kotlin
/** [API] 获取展期预览信息 */
suspend fun getExtensionPreview(req: ExtensionPreviewReq): ExtensionPreviewResult

/** [API] 直接确认展期（无需支付展期费） */
suspend fun confirmExtension(req: ConfirmExtensionReq): ExtensionConfirmResult

data class ExtensionPreviewReq(
    /** [SEMANTIC] 账单 ID */
    val billId: Long = 0,
)

data class ExtensionPreviewResult(
    /** [SEMANTIC] 是否可直接展期（true=无需支付展期费） */
    val canExtendDirectly: Boolean? = null,
    /** [SEMANTIC] 展期天数 */
    val extensionDays: Int? = null,
    /** [SEMANTIC] 展期费用 */
    val extensionFee: Double? = null,
    /** [SEMANTIC] 展期后新还款日期 */
    val newDueDate: String? = null,
    /** [SEMANTIC] 到期应还金额 */
    val pendingAmount: Double? = null,
    /** [SEMANTIC] 已还金额 */
    val repaidAmount: Double? = null,
)

data class ConfirmExtensionReq(
    /** [SEMANTIC] 账单 ID */
    val billId: Long = 0,
)
```

---

## 四、三个页面的关联关系

```
订单列表 / 首页
    │
    ▼
订单详情页（OrderDetailRoute）
    │
    ├─ 状态 = 还款中 ──► 点击"Pagar ahora"
    │                      │
    │                      ▼
    │              还款详情页（RepaymentRoute）
    │                      │
    │                      ├─ 点击"立即支付"
    │                      │      │
    │                      │      ▼
    │                      │  渠道选择弹窗
    │                      │      │
    │                      │      ▼
    │                      │  生成支付码 → WebView 支付
    │                      │
    │                      ├─ 点击"展期还款"
    │                      │      │
    │                      │      ▼
    │                      │  展期页（ExtensionRoute）
    │                      │      │
    │                      │      ├─ canExtendDirectly=true
    │                      │      │      │
    │                      │      │      ▼
    │                      │      │  直接确认展期 → 成功 → 订单列表
    │                      │      │
    │                      │      └─ canExtendDirectly=false
    │                      │             │
    │                      │             ▼
    │                      │         渠道选择 → 支付展期费 → WebView
    │                      │             │
    │                      │             ▼
    │                      │         支付成功 → 订单列表
    │                      │
    │                      └─ onResume 检查还款成功弹窗
    │
    └─ 其他状态 ──► 返回 / 客服 / 重新申请
```

---

## 五、通用设计模式

### 5.1 金额格式化规范（强制）

```kotlin
/**
 * State 中必须同时保留原始值和展示文本。
 * 展示文本用于 UI 显示，原始值用于构造接口请求。
 */
data class XxxState(
    /** 展示文本（如 "$ 10,000"） */
    val amount: String = "",
    /** 原始值（如 10000.0），用于构造请求 */
    val amountRaw: Double = 0.0,
)

// ViewModel 中同时写入两者
_state.value = _state.value.copy(
    amount = resp?.amount.formatCurrency(),
    amountRaw = resp?.amount ?: 0.0,
)
```

### 5.2 支付流程统一模式

还款和展期的支付流程完全一致，仅 `orderType` 不同：
- `orderType = 1` → 普通还款
- `orderType = 2` → 展期费支付

```kotlin
// 通用支付流程
fun processPayment(channel: RepayChannel, orderType: Int, amount: Double) {
    val req = GeneratePayCodeReq(
        allocations = [AllocationItem(amount, orderId, billId)],
        totalAmount = amount,
        orderType = orderType,
        channelCode = channel.channelCode,
        paymentMethod = channel.paymentMethod,
    )
    val result = apiService.generatePayCode(req).toResult()
    val url = result?.h5Url ?: result?.nativeUrl ?: ""
    navigateToWebView(url)
}
```

### 5.3 生命周期检测还款成功

```kotlin
// 在页面 Screen 层监听生命周期
onResume {
    viewModel.checkRepaySuccessDialog()
}

// ViewModel 中
fun checkRepaySuccessDialog() = launchLoading(showLoading = false) {
    val result = apiService.checkRepaySuccess().toResult()
    if (result?.shouldShowDialog == true) {
        _state.value = _state.value.copy(showRepaySuccessDialog = true)
    }
}
```

---

## 六、实现检查清单

### 订单详情页
- [ ] 页面进入时调用订单详情接口
- [ ] 根据状态码匹配状态配置（插画、文案、按钮）
- [ ] 展示产品信息（名称、Logo、借款金额、期限）
- [ ] 展示收款账户（银行卡号、银行名称、到期日）
- [ ] 底部按钮根据状态变化（返回/还款/客服/重新申请）
- [ ] 状态 4（还款中）点击按钮跳转还款页

### 还款详情页
- [ ] 页面进入时加载还款信息（复用订单详情接口）
- [ ] 根据剩余天数计算状态标签颜色和文案
- [ ] 展示待还金额、借款金额、到账金额、服务费、逾期费
- [ ] 展示安全提示（可关闭）
- [ ] 根据展期开关控制展期按钮显隐
- [ ] 点击支付 → 获取渠道列表 → 展示渠道弹窗
- [ ] 选择渠道 → 生成支付码 → 跳转 WebView
- [ ] onResume 检查还款成功弹窗
- [ ] 还款成功弹窗：关闭 → 首页，申请 → 贷款产品页

### 展期页
- [ ] 页面进入时加载展期预览信息
- [ ] 展示原到期日（删除线）→ 新到期日
- [ ] 展示展期天数、展期费用、到期应还金额
- [ ] 点击确认 → 展示二次确认弹窗
- [ ] 弹窗确认后分双分支：
  - 直接展期 → 调确认接口 → Toast 成功 → 订单列表
  - 付费展期 → 渠道选择 → 生成支付码 → WebView → 订单列表
