[← 图片处理](image-processing.md) | [→ 字段配置策略](field-config-strategy.md)

# 通用 UI 组件封装原则

KYC 流程中表单控件的**交互类型由业务语义决定**（如"教育水平"一定是选择而非输入），但**具体样式由设计稿决定**（底部弹窗、下拉框、侧滑面板等）。不同项目的设计稿不同，控件外观不应写死。

### 封装原则

**必须封装为项目级通用组件**，在 KYC 各页面中复用：

| 组件语义 | 交互类型 | 封装要求 | 典型样式（根据设计稿） |
|---------|---------|---------|---------------------|
| 字典选择器 | 单选 | **必须封装** | 底部弹窗 / 下拉框 / 侧滑面板 |
| 省市级联选择器 | 级联选择 | **必须封装** | 两级底部弹窗 / 联动滚轮 |
| 银行选择器 | 搜索+单选 | **必须封装** | 搜索弹窗 + 分组列表 |
| 拍照/图库选择器 | 二选一 | **必须封装** | 底部 ActionSheet |
| 信息确认弹窗 | 信息展示+确认 | **必须封装** | 居中卡片 / 底部面板 |
| 流程挽留弹窗 | 提示+确认/取消 | **必须封装** | 居中对话框 |
| 文本输入框 | 键盘输入 | **必须封装** | 带图标、清除按钮、格式校验 |
| 数字输入框 | 数字键盘 | **必须封装** | 带长度限制、格式化显示 |

### 控件封装规范

每个封装组件应满足：

1. **与业务解耦**：组件只负责渲染和交互，不感知具体业务字段（如"教育水平"）
2. **与样式解耦**：外观由外部传入（颜色、圆角、字体等设计 token），或读取项目主题配置
3. **统一接口**：相同语义的不同字段使用同一组件，仅传入不同数据

```
示例：字典选择器统一接口

输入：
  - label: 字段标签（如 "Nivel educativo"）
  - options: 选项列表 [{key, value}]
  - selectedKey: 当前选中值
  - onSelect: (key, value) -> Unit
  - theme: 外观配置（颜色、圆角等，可选）

输出：
  - 用户选择后回调 (key, value)
```

### 禁止做法

- ❌ 每个字段单独实现一个选择弹窗（如 `EducationDialog` + `MaritalDialog` + `WorkTypeDialog`）
- ❌ 在组件内部硬编码颜色、字体、间距
- ❌ 在组件内部写死业务逻辑（如"教育水平有7个选项"）

---

## 通用 UI 组件清单

KYC 流程通常需要以下通用弹窗/组件：

| 组件语义 | 用途 |
|---------|------|
| 选项选择弹窗 | 底部弹出的单选列表，用于字典字段选择 |
| 省市级联弹窗 | 先选省、再选市的两级选择 |
| 银行选择弹窗 | 支持搜索、按常用/字母分组的银行列表 |
| 拍照选择弹窗 | 拍照 / 图库 二选一 |
| 信息确认弹窗 | 提交前展示已填信息供用户确认 |
| 流程挽留弹窗 | 拦截返回键，提示用户确认退出（降低流失率） |

---

## 通用选择弹窗和输入框生成

KYC 流程中所有选择交互应复用统一封装的通用组件，而非为每个字段单独创建 Dialog。

### 通用单选弹窗

```kotlin
/**
 * 通用单选弹窗 — 所有字典字段选择复用。
 * 特点：渐变顶栏 + 标题 + 关闭 + BRVAH radio 列表 + 预选中 + 动态高度。
 *
 * @param title 弹窗标题（如 "Nivel educativo"）
 * @param options 选项列表 [{key, value}]
 * @param onSelected 选中回调 (KeyValueOption) -> Unit
 * @param preselectedKey 预选中 key
 */
class OptionSelectDialog(
    context: Context,
    private val title: String,
    private val options: List<KeyValueOption>,
    private val onSelected: (KeyValueOption) -> Unit,
    private val preselectedKey: Int? = null
) : BaseDialog<DialogOptionSelectBinding>(
    context,
    DialogConfig.center(width = MATCH_PARENT).copy(canceledOnTouchOutside = true)
) {
    // 列表高度自适应：screenHeight * 0.55 为上限，超出内部滚动
    // 使用 BRVAH BaseQuickAdapter + DiffUtil 做列表渲染
    // 详见 OptionSelectDialog.kt 完整实现
}
```

**使用方式**（BasicInfo 字段选择）：
```kotlin
// ViewModel 响应点击事件：
when (action) {
    is OnEducationClick -> {
        val field = fetchField(EDUCATION)  // 从进件页面信息接口或本地配置获取
        mEvent.emit(ShowOptionPicker(title = field.label, options = field.options))
    }
}

// Activity 响应事件：
is KycPersonalEvent.ShowOptionPicker -> {
    OptionSelectDialog(
        context = this,
        title = event.title,
        options = event.options,
        onSelected = { option -> viewModel.onPickerSelect(option) }
    ).show()
}
```

### 通用表单输入字段

```kotlin
/**
 * 通用表单选择行 View — KYC 各步骤复用。
 * XML 布局：左侧 label + 右侧 placeholder/选中值 + 右箭头（选择类）或输入框（输入类）。
 * 通过属性区分：
 *   - app:fieldType="select" → 显示右箭头，点击弹出选择器
 *   - app:fieldType="input"  → 显示 EditText，用于文本/数字输入
 *   - app:fieldType="display" → 纯展示，用于确认页
 *
 * 【推荐做法】封装为自定义 View（如 FormSelectView）或样式统一的 include layout
 */
```

**关键设计原则**：
1. **与业务解耦**：组件不感知"教育水平"还是"婚姻状况"，只接收 `label + options + selectedKey`
2. **样式统一**：颜色/圆角/字体从主题/设计 Token 读取
3. **错误态统一**：提供 `errorMessage: String?` 属性，显示红色边框+底部错误文字
4. **可滚动定位**：提供 `scrollToFieldIndex` 状态，Activity 收到后滚动到对应字段

### 确认弹窗

```kotlin
/**
 * 通用信息确认弹窗。
 * 展示只读表单 + "确认" + "修改" 两个按钮。
 * @param items 只读展示的字段列表 [{label, value}]
 */
class ConfirmInfoDialog(
    context: Context,
    private val items: List<ConfirmFieldItem>,  // label + value
    private val onConfirm: () -> Unit,
    private val onModify: () -> Unit
) : BaseDialog<DialogConfirmInfoBinding>(
    context,
    DialogConfig.center(width = MATCH_PARENT).copy(canceledOnTouchOutside = false)
) {
    // 列表渲染 items，每个 item 显示 label: value
}
```

---
