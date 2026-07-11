[← 设计原则](design-principles.md) | [→ 验证流程](verify.md)

# KYC 实现常见陷阱

## 陷阱 1: 缓存数据类字段非空导致反序列化崩溃

**症状**: 用户升级 App 后打开 KYC 页面直接白屏/崩溃，或清除缓存后首次进入白屏。

**原因**: 缓存数据类中使用了非空字段（如 `val name: String`），但序列化/反序列化过程中字段缺失（版本升级新增字段、旧缓存数据结构不同）时，反射创建实例失败或抛 `MissingFieldException`。

**诊断**:
```bash
# 检查 KYC 缓存 data class 中所有字段声明
grep -rn 'data class.*Cache' app/src/main/java/com/your/kyc/ --include="*.kt"
grep -A5 'data class.*Cache' app/src/main/java/com/your/kyc/ --include="*.kt" | grep -E 'val\s+\w+\s*:'
# 查找非空声明（缺少 ? = null）
grep -rn 'val\s\+\w\+\s*:\s*\(String\|Int\|Long\)\b' app/src/main/java/com/your/kyc/cache/ --include="*.kt" | grep -v '? = null' | grep -v 'Boolean'
```

**修复**: 将所有缓存 data class 的字段声明改为可空 + 默认值 `null`：`val name: String? = null`、`val key: Int? = null`。同时确保 `loadCache()` 方法中 try/catch 兜底。

---

## 陷阱 2: 图片压缩在主线程执行导致 ANR

**症状**: 用户拍照/选图后，App 卡顿数秒甚至 ANR（Application Not Responding）。

**原因**: `BitmapFactory.decodeStream()`、`Bitmap.compress()` 等是阻塞操作，如果在主线程（Dispatchers.Main）执行，会阻塞 UI 渲染导致 ANR。

**诊断**:
```bash
# 查找 ImageCompressor 调用位置
grep -rn 'ImageCompressor\|compress(' app/src/main/java/com/your/kyc/ --include="*.kt"
# 检查是否使用了 Dispatchers.IO
grep -B3 -A3 'compress(' app/src/main/java/com/your/kyc/ --include="*.kt" | grep -E 'Dispatchers\.(IO|Default)'
```

**修复**: 将图片压缩调用包裹在 `withContext(Dispatchers.IO) { }` 中，或确保 ViewModel 的 launch 块内部调用了 `Dispatchers.IO`。

---

## 陷阱 3: OCR 返回 null 未处理导致 NPE

**症状**: 身份证拍照后，App 崩溃，错误栈指向 OCR 结果解包位置。

**原因**: OCR 接口返回的 `OcrResult` 字段可能为 null（识别失败、图片质量问题），但代码直接使用 `result.firstName` 等未判空。

**诊断**:
```bash
# 查找 OCR 结果使用位置
grep -rn 'ocrResult\|OcrResult\|ocrVerify' app/src/main/java/com/your/kyc/ --include="*.kt"
grep -A10 'ocrResult' app/src/main/java/com/your/identity/ --include="*.kt" | grep -E 'result\.\w+' | head -10
```

**修复**: OCR 回调中先判空，如果 `result == null` 则显示错误提示（如"识别失败，请重拍"），不自动回填。

---

## 陷阱 4: 字典缓存未按账号隔离导致多账号数据串

**症状**: 用户 A 登录后看到的选项（如教育水平）是用户 B 上次看到的。

**原因**: 字典缓存使用全局 key 存储，未区分账号 ID。当多账号在同一设备上使用时，后登录的账号读到前一个账号的缓存。

**诊断**:
```bash
# 查找字典缓存存储 key 的构造方式
grep -rn 'dict_\|DICT_CACHE\|dictCache' app/src/main/java/com/your/ --include="*.kt" | grep -E 'key|KEY'
grep -rn 'userId\|accountId' app/src/main/java/com/your/dict/ --include="*.kt"
```

**修复**: 缓存 key 中拼接账号标识：`"dict_${userId}_${dictType}"`。切换账号时清空对应缓存。

---

## 陷阱 5: 人脸 Face++ Token 过期未降级导致流程卡死

**症状**: 用户在人脸识别页面一直卡在"加载中..."，无法继续，点返回后退出流程。

**原因**: Face++ BizToken 有过期时间（通常 60 秒），获取 Token 后如果用户操作慢（调整位置、重新拍照），Token 已过期但代码未检测，SDK 静默失败后未触发降级逻辑。

**诊断**:
```bash
# 检查 Face++ 流程的降级处理
grep -rn 'errToYc\|faceChannel\|FacePlusPlus' app/src/main/java/com/your/face/ --include="*.kt"
grep -rn 'fetchFacePlusPlusToken\|bizToken' app/src/main/java/com/your/ --include="*.kt" | grep -v '.git'
```

**修复**: Face++ SDK 回调中 `errToYcCallBack` 必须实现降级到自研渠道。同时 Token 获取后设置超时（建议 30s），超时后自动降级。

---

## 陷阱 6: 挽留弹窗在非 KYC 模式下误弹

**症状**: 从"安全认证"等非 KYC 场景进入人脸识别，点返回时弹出"确认退出进件"弹窗，文案错误。

**原因**: 挽留弹窗逻辑未判断当前模式（KYC vs 非 KYC），所有场景统一使用 KYC 的挽留文案和行为。

**诊断**:
```bash
# 检查挽留弹窗的触发条件
grep -rn 'retain\|挽留\|BackHandler\|onBackPressed' app/src/main/java/com/your/kyc/ --include="*.kt"
grep -B5 -A10 'BackHandler\|onBackPressed' app/src/main/java/com/your/face/ --include="*.kt" | grep -E 'isKyc|isExternal'
```

**修复**: 在 BackHandler/onBackPressed 中判断模式参数，非 KYC 模式直接 `popBackStack`，不展示挽留弹窗。

---

## 陷阱 7: 信用信息级联字段：上游改为 0 后下游未清空

**症状**: 用户先选"贷款经历 2-3 次"，再选"在贷笔数 1 笔"，然后回头把"贷款经历"改为"0 次"，但在贷笔数仍然显示"1 笔"。

**原因**: 信用信息字段存在级联依赖，但上游字段值变化时，代码未清空下游字段的已选值。

**诊断**:
```bash
# 查找信用信息的级联处理
grep -rn 'loanCount\|currentLoan\|totalAmount' app/src/main/java/com/your/credit/ --include="*.kt"
grep -B5 -A10 'LOAN_COUNT_CODE\|loanCountKey' app/src/main/java/com/your/credit/ --include="*.kt" | grep -E 'copy\('
```

**修复**: 当"贷款经历"选 0 时，清空"在贷笔数"和"在贷金额"的值。当"在贷笔数"选 0 时，清空"在贷金额"的值。参照 Step 5 ViewModel 模板中的级联清空逻辑。

---

## 陷阱 8: 银行卡外部模式误写 KYC 缓存

**症状**: 用户在外部添加银行卡后，回到 KYC 流程发现银行卡信息是之前外部添加的卡（或相反）。

**原因**: 银行卡页面支持 KYC 和外部添加双模式，但两种模式共用同一缓存 key，未区分。

**诊断**:
```bash
# 检查银行卡缓存的读写
grep -rn 'saveCache\|loadCache\|bank_cache\|BANK_CACHE' app/src/main/java/com/your/bank/ --include="*.kt"
grep -B3 -A3 'isExternal' app/src/main/java/com/your/bank/ --include="*.kt" | grep -E 'cache|save|load'
```

**修复**: 外部模式下不调用 `saveCache()`，KYC 模式下才读写缓存。或在 setExternal 中判断，KYC 模式加载缓存，外部模式跳过。

---

## 陷阱 9: 身份证确认弹窗中用户点"修改"后数据未保留

**症状**: 用户在身份证页面填写姓名、身份证号，点提交后弹出确认窗，用户发现 OCR 识别有误，点"修改"回到表单，发现之前填的修改内容全部丢失，回到了 OCR 识别的原始值。

**原因**: 确认弹窗的"修改"按钮直接关闭弹窗，未保持当前 state 中的用户修改值。或者 ViewModel 在弹窗显示前保存了一份"提交用"的快照，用户修改未被更新。

**诊断**:
```bash
# 检查确认弹窗的"修改"逻辑
grep -rn 'ConfirmInfoDialog\|confirmDialog\|showConfirmDialog' app/src/main/java/com/your/identity/ --include="*.kt"
grep -B5 -A5 'OnConfirmDialogDismiss\|onModify' app/src/main/java/com/your/identity/ --include="*.kt"
```

**修复**: "修改"按钮只需关闭弹窗（`showConfirmDialog = false`），不重置表单字段。用户之前的所有输入保留在 state 中。

---

## 陷阱 10: 联系人通讯录权限拒绝后未降级为手动输入

**症状**: 用户拒绝通讯录权限后，点击联系人电话字段无反应，无法填写联系人信息，流程卡住。

**原因**: 系统通讯录选择器需要 `READ_CONTACTS` 权限，权限被拒绝后代码未捕获异常或未检查权限状态，导致点击事件没有后续处理。

**诊断**:
```bash
# 检查联系人页面的权限处理
grep -rn 'LaunchContactPicker\|contactPicker\|ContactPer' app/src/main/java/com/your/contact/ --include="*.kt"
grep -rn 'OnContactPickFailed\|pickFailed' app/src/main/java/com/your/contact/ --include="*.kt"
grep -rn 'READ_CONTACTS\|contacts' app/src/main/AndroidManifest.xml
```

**修复**: 通讯录选择前检查权限，拒绝时显示 Toast 提示"无法读取通讯录"并将对应联系人卡片切换为可编辑模式（`isEditable = true`），允许用户手动输入姓名和电话。
