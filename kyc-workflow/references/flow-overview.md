[→ Step 1: 个人信息](step-1-personal-info.md)

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
