/**
 * OptionSelectDialog — 通用单选弹窗
 *
 * 业务目的：
 *   一个弹窗供ALL字典字段选择复用（教育水平、婚姻状况、工作类型、月收入、联系人关系等）。
 *   禁止为每个字段单独创建Dialog（如EducationDialog、MaritalDialog等）。
 *
 * 教学重点：
 *   1. 一个Dialog复用所有字典字段选择，传入不同title和options即可
 *   2. 列表高度自适应：max 55%屏幕高度，超出内部滚动
 *   3. 支持pre-selection，高亮当前已选项
 *   4. 与业务解耦：不感知具体业务字段语义
 *   5. 与样式解耦：外观由外部传入或读取主题配置
 */

/**
 * 通用单选弹窗 — 所有字典字段选择复用。
 * 特点：渐变顶栏 + 标题 + 关闭 + BRVAH radio 列表 + 预选中 + 动态高度。
 *
 * ⚠️ 可复用: 一个Dialog用于ALL字典字段选择（教育水平、婚姻状况、工作类型、收入、关系等）
 * ⚠️ 反模式: 不要创建EducationDialog、MaritalDialog、WorkTypeDialog等各自独立的弹窗
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
    // ⚠️ 高度规则: max 55% screen height, scrollable beyond
    // ⚠️ 预选中: 高亮当前已选择的option
    // 使用 BRVAH BaseQuickAdapter + DiffUtil 做列表渲染
    // 详见 OptionSelectDialog.kt 完整实现
}
