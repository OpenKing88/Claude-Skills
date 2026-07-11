/**
 * ConfirmInfoDialog — 通用信息确认弹窗
 *
 * 业务目的：
 *   在关键信息提交前（身份证、银行卡等）展示已填信息供用户二次确认，减少错误。
 *
 * 教学重点：
 *   1. 只读展示所有已填字段为label:value键值对
 *   2. 两个按钮："确认"→提交，"修改"→关闭弹窗让用户继续编辑
 *   3. canceledOnTouchOutside=false，用户必须明确选择，不可外部取消
 *   4. 与业务解耦：传入items列表，不感知具体字段
 *   5. 身份证、银行卡等关键信息页必须使用
 */

/**
 * 通用信息确认弹窗。
 * 展示只读表单 + "确认" + "修改" 两个按钮。
 *
 * ⚠️ 只读展示: 显示所有已填字段为label:value键值对，不可编辑
 * ⚠️ 两个按钮: "确认"触发提交，"修改"关闭弹窗让用户返回编辑
 * ⚠️ 不可取消: canceledOnTouchOutside=false，用户必须明确选择确认或修改
 *
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
