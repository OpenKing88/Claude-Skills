[← 常见陷阱](traps.md)

# KYC 验证流程

## Step 1: 接口存在性检查

确认 KYC 流程依赖的所有后端接口在项目中都已定义。以下 grep 命令逐一检查：

```bash
# 1. 进件数据提交接口（核心）
grep -rn -E 'submitAcqData|submitAcpInfo|submitAcquisition' app/src/main/java/com/your/ --include="ApiService.kt" --include="*.api"

# 2. OCR 识别接口
grep -rn -E 'ocrVerify|ocrCheck|ocrRecognition' app/src/main/java/com/your/ --include="ApiService.kt" --include="*.api"

# 3. 人脸比对接口
grep -rn -E 'faceCompare|faceCheck|faceVerify' app/src/main/java/com/your/ --include="ApiService.kt" --include="*.api"

# 4. 进件页面元素查询接口（控制步骤/字段展示）
grep -rn -E 'acpElementInfo|elementInfo|pageElement|pageInfo' app/src/main/java/com/your/ --include="ApiService.kt"

# 5. 银行列表接口
grep -rn 'getBankList\|bankList\|queryBankList' app/src/main/java/com/your/ --include="ApiService.kt"

# 6. 字典查询接口
grep -rn -E 'getDict|dictList|dataDictInfo' app/src/main/java/com/your/ --include="ApiService.kt"

# 7. 地区列表接口
grep -rn -E 'areaList|getAreaList|provinceList' app/src/main/java/com/your/ --include="ApiService.kt"
```

**检查要点**:
- 每个接口的请求参数（`@Body`/`@Query`/`@Part`）与 KYC ViewModel 中调用一致
- 返回类型（`ApiEnvelope<T>`）与 ViewModel 中解包方式一致
- 混淆字段名与其 KDoc 注释对应（是否正确标识了业务语义）

---

## Step 2: 缓存模型安全检查

确认所有缓存数据类的字段声明正确，避免反序列化崩溃。

```bash
# 查找所有 KYC 缓存 data class
grep -rn 'data class.*Cache\|data class.*State' app/src/main/java/com/your/kyc/cache/ --include="*.kt"

# 检查非空字段（风险）
# 查找 String/Int/Long 类型且没有 ? = null 的字段
grep -rn -E '^\s*(val|var)\s+\w+\s*:\s*(String|Int|Long|Double)\s*[=?]' app/src/main/java/com/your/kyc/cache/ --include="*.kt" | grep -v '? = null' | grep -v 'Boolean'

# 检查 try/catch 包裹缓存读取
grep -rn 'loadCache\|readCache\|getCache' app/src/main/java/com/your/kyc/ --include="*.kt" | head -10
grep -B2 -A5 'loadCache' app/src/main/java/com/your/kyc/ --include="*.kt" | grep -E 'try|catch'
```

**检查要点**:
- 所有缓存 data class 的 String/Int/Long/Double 字段必须是 `? = null`
- `loadCache()` 方法必须有 try/catch 兜底
- 缓存 key 是否包含账号 ID 隔离（多账号场景）
- `saveCache()` 是否在每次用户修改后调用

---

## Step 3: 图片处理检查

确认图片压缩在 IO 线程执行且有 OOM 保护。

```bash
# 查找 ImageCompressor 的使用
grep -rn 'ImageCompressor\|ImageCompressUtil' app/src/main/java/com/your/ --include="*.kt"

# 检查是否在 IO 线程调用
grep -B5 -A3 'compress(' app/src/main/java/com/your/ --include="*.kt" | grep -E 'Dispatchers\.(IO|Default)|withContext'

# 检查图片回显方式
grep -rn '.load(uri)\|load(.*uri\|setImageBitmap' app/src/main/java/com/your/kyc/ --include="*.kt" | head -10

# 检查是否使用了 Coil/Glide
grep -rn 'coil\|glide' app/src/main/java/com/your/ --include="*.kt" | head -5
```

**检查要点**:
- `ImageCompressor.compress()` 必须在 `withContext(Dispatchers.IO)` 中调用
- 压缩失败返回 `null`，调用方处理 null 分支（不忽略）
- 图片回显使用 Coil/Glide 加载 URI，不自行 decode Bitmap
- `onDestroy()` 中 `cameraExecutor.shutdown()` 已调用

---

## Step 4: UI 组件复用检查

确认选择弹窗使用统一封装的通用组件，而非每个字段单独实现。

```bash
# 检查是否存在独立 Dialog 命名
grep -rn 'class.*Dialog' app/src/main/java/com/your/kyc/ --include="*.kt" | grep -vE 'OptionSelectDialog|ConfirmInfoDialog|BaseDialog'

# 检查是否使用了 OptionSelectDialog
grep -rn 'OptionSelectDialog' app/src/main/java/com/your/kyc/ --include="*.kt"

# 检查弹窗事件使用
grep -rn 'ShowOptionPicker\|ShowPicker\|OnPicker' app/src/main/java/com/your/kyc/ --include="*.kt" | head -10
```

**检查要点**:
- 不应存在 `EducationDialog`、`MaritalDialog`、`WorkTypeDialog` 等 per-field Dialog
- 所有字典选择字段统一走 `OptionSelectDialog` 或类似通用组件
- 确认弹窗复用 `ConfirmInfoDialog` 或类似通用组件

---

## Step 5: 挽留弹窗检查

确认每个 KYC 页面都实现了返回键拦截 + 挽留弹窗。

```bash
# 检查 BackHandler/onBackPressed 实现
grep -rn 'BackHandler\|onBackPressed\|OnBackClick' app/src/main/java/com/your/kyc/ --include="*.kt"

# 检查挽留弹窗的使用
grep -rn -E 'retain|RetainDialog|挽留|exitConfirm' app/src/main/java/com/your/kyc/ --include="*.kt"

# 检查非 KYC 模式是否跳过挽留
grep -B5 -A5 'isKyc' app/src/main/java/com/your/kyc/ --include="*.kt" | grep -E 'BackHandler|retain|popBack'
```

**检查要点**:
- 每个 KYC 页面都有 BackHandler/onBackPressed 拦截
- 拦截逻辑中判断 `isKyc` 参数，KYC 模式显示挽留弹窗，非 KYC 模式直接返回
- 挽留弹窗文案正确（提示退出会导致进件中断等）

---

## Step 6: 编译验证

确认 KYC 代码完整可编译，无语法/类型错误。

```bash
# 在项目根目录运行编译验证
cd /path/to/project
./gradlew compileDebugKotlin
```

**检查要点**:
- 编译通过，无报错
- 所有接口方法签名与 API Service 定义一致
- 所有 import 正确
- 所有 `@Serializable`/`@Parcelize` 注解正确添加
