[← Step 6: 人脸识别](step-6-face-recognition.md) | [→ 字典系统](dictionary-system.md)

# 接口占位代码（接口未定义时）

当项目后端接口尚未就绪，需要前端先完成流程代码时，按以下标准创建接口占位，保证 KYC 流程代码完整可编译。

### 1. 进件数据提交接口

```kotlin
// network/loan/LoanApiService.kt
@POST("/api/acq/submit")
suspend fun submitAcqData(@Body req: SubmitAcqDataReq): ApiEnvelope<SubmitAcqDataResp>

// network/loan/Request.kt
@Serializable
data class SubmitAcqDataReq(
    /** 新增/更新标识：0=新增，1=更新 */
    val d4zlHwbZAOmI6H0_kntT: Int = 0,
    /** 进件数据项列表 */
    val qtj4X2fyBTK2qJm1BaZA: List<AcqDataItem>,
    /** 流程标识：固定 2 */
    val eUVwQJ3vQxc08aUj7miC: Int = 2,
    /** 步骤标识：1=个人信息 2=联系人 3=身份证 4=银行卡 5=信用 6=人脸 */
    val t3e8L: Int,
)

@Serializable
data class AcqDataItem(
    /** 字段名（混淆串） */
    val oMr2jKzRVLB99cjeb: String,
    /** 字段值 */
    val u1j7ovclLk89d11: String,
)

@Serializable
data class SubmitAcqDataResp(
    /** 按后端实际返回定义 */
    val code: Int = 0,
)
```

### 2. 银行卡外部提交接口（可选）

```kotlin
// network/loan/LoanApiService.kt
@POST("/api/bank/card/submit")
suspend fun submitBankCard(@Body req: SubmitBankCardReq): ApiEnvelope<SubmitBankCardResp>

// network/loan/Request.kt
@Serializable
data class SubmitBankCardReq(
    /** 银行卡号 */
    val uzcbAW5bYlSMCnOEeT: String,
    /** 银行 ID */
    val bFjmTbDSM: String,
    /** 账户类型：0=CLABE, 1=借记卡 */
    val zjTYcQpr: Int,
)

@Serializable
data class SubmitBankCardResp(
    val code: Int = 0,
)
```

### 3. 人脸渠道查询接口

```kotlin
// network/loan/LoanApiService.kt
@GET("/api/face/channel")
suspend fun getFaceChannel(): ApiEnvelope<FaceChannelResp>

// network/loan/Response.kt
@Serializable
data class FaceChannelResp(
    /** 人脸渠道：1=accu, 2=yc, 3=facePlusPlus */
    val p0Mgp4IL8bD: String? = null,
)
```

### 4. Face++ Token 接口

```kotlin
// network/loan/LoanApiService.kt
@GET("/api/face++/token")
suspend fun getFacePlusPlusToken(): ApiEnvelope<FacePlusPlusTokenResp>

// network/loan/Response.kt
@Serializable
data class FacePlusPlusTokenResp(
    /** SDK host */
    val kJEXq150: String? = null,
    /** BizToken */
    val iy5h_MHpS9o6Q: String? = null,
)
```

### 5. 人脸比对接口

```kotlin
// network/upload/UploadApiService.kt
@Multipart
@POST("/api/face/compare")
suspend fun faceCompare(
    @Part("p0Mgp4IL8bD") channel: String,
    @Part("d4zlHwbZAOmI6H0_kntT") actionType: String,
    @Part("eUVwQJ3vQxc08aUj7miC") flowType: String,
    @Part("t3e8L") step: String,
    // 自研渠道特有参数
    @Part("agtFiTgpWQh6Ws") latitude: String? = null,
    @Part("p8jHjwsM_QYOS") longitude: String? = null,
    @Part niM6smmG: MultipartBody.Part? = null,
    // Face++ 渠道特有参数
    @Part("aTDUsZ7P9AoNN7F2yt") bizToken: String? = null,
): ApiEnvelope<FaceCompareResp>

@Serializable
data class FaceCompareResp(
    /** 比对结果：1=成功 */
    val sPkYn_M: Int? = null,
)
```

### 6. 字典接口

```kotlin
// network/common/CommonApiService.kt
@GET("/api/dict/list")
suspend fun getDictList(@Query("type") type: String): ApiEnvelope<DictResp>

// network/common/Response.kt
@Serializable
data class DictResp(
    val ina9fIx7Cpygdw0: List<DictItem>? = null,
)

@Serializable
data class DictItem(
    val kYM1d41M1oVAiE: String? = null,
    val dY19_rl9XKoi8q: String? = null,
)
```

### 7. 银行列表接口

```kotlin
// network/loan/LoanApiService.kt
@GET("/api/bank/list")
suspend fun getBankList(): ApiEnvelope<BankListResp>

// network/loan/Response.kt
@Serializable
data class BankListResp(
    val wzZ30fZ7nba_dyaTH: List<BankInfoResp>? = null,
)

@Serializable
data class BankInfoResp(
    val wnz5RTXbacHU_ksMPAAR: Int? = null,
    val iP_Lz5kBf76A: String? = null,
)
```

### 8. OCR 识别接口

```kotlin
// network/upload/UploadApiService.kt
@Multipart
@POST("/api/ocr/verify")
suspend fun ocrVerify(
    @Part("type") type: String,
    @Part("latitude") latitude: String?,
    @Part("longitude") longitude: String?,
    @Part image: MultipartBody.Part,
): ApiEnvelope<OcrResp>

@Serializable
data class OcrResp(
    val firstName: String? = null,
    val paternalSurname: String? = null,
    val maternalSurname: String? = null,
    val idNumber: String? = null,
    val imageUrl: String? = null,
)
```

### 9. 地区列表接口

```kotlin
// network/common/CommonApiService.kt
@GET("/api/area/list")
suspend fun getAreaList(): ApiEnvelope<AreaListResp>

// network/common/Response.kt
@Serializable
data class AreaListResp(
    val rebk0UE: List<AreaItem>? = null,
)

@Serializable
data class AreaItem(
    val wnz5RTXbacHU_ksMPAAR: Int? = null,
    val qwdJtB6bC: String? = null,
)
```

---

## 通用数据提交模式

### 进件数据提交

所有 KYC 步骤共用同一个提交接口，通过**步骤标识**区分：

| 步骤标识 | 对应页面 |
|---------|---------|
| 1 | 个人信息 |
| 2 | 联系人信息 |
| 3 | 身份证认证 |
| 4 | 银行卡信息（KYC 模式） |
| 5 | 信用信息 |
| 6 | 人脸识别 |

### 字段命名原则
- 后端接口字段通常使用**混淆字符串**（如 `rfe7VQG4IS9ZIjJmN0bX`）
- **必须通过接口注释/KDoc 理解字段语义**，不能凭字段名猜测
- 每个选择字段需要同时传递**展示文本**和**选项 key**

### 进件页面信息查询
```
GET /acq/page/info?step={step}  — 查询某步骤的表单元素配置
```

后端返回该步骤需要展示的字段列表、字段类型、是否必填、选项数据源等。前端根据返回动态渲染表单。

---


## 接口自动扫描与语义匹配

实现 KYC 流程时，skill 应**自动扫描项目中的 API 接口文件**，根据语义识别 KYC 相关接口，而非要求用户手动告知。

### 扫描策略

```
扫描目标：
  项目中的 Retrofit API Service 接口文件（通常为 *ApiService.kt）

扫描方法：
  1. 读取 API Service 文件全文
  2. 对每个接口方法收集：KDoc 注释 + @GET/@POST 路径 + 参数列表 + 响应类型
  3. 按语义关键词匹配 KYC 相关接口

语义关键词（匹配 KYC 接口）：
  进件 / acquisition / acp / acq / submit
  OCR / ocr / verify / recognition
  人脸 / face / check / compare
  银行卡 / bank / card / bankCard
  字典 / dict / dictionary
  区域 / 省市 / area / province / city
  步骤 / step / page / element
  信息认证 / 身份 / identity
  联系人 / contact
  进度 / progress
```

### 接口语义推断流程

```
对于每个匹配到的接口：

1. 读 KDoc 注释 → 获取业务语义（原始路径 + 中文描述）
2. 读 @GET/@POST 路径 → 确认操作类型
3. 读 @Query / @Part / @Body 参数名 → 理解参数语义（读参数上的 KDoc）
4. 读返回类型（BaseBean<ModelXX>）→ 追踪响应字段含义（读 ModelXX 的 KDoc）
5. 交叉验证：检查哪些 KYC ViewModel 实际调用了该接口
```

### 核心进件提交接口识别

最重要的接口是**进件数据提交接口**。识别特征：

```
特征 1：方法名包含 submit / acp / acq / submitAcpInfo
特征 2：@Body 参数包含 List<*> 类型的 items 字段（键值对列表）
特征 3：请求体中有一个 step 字段（Int 类型，注释提到 "步骤" / "1-6"）
特征 4：被多个 KYC ViewModel 调用，且 step 值不同（1/2/3/4/5/6）
```

**匹配模板**（以实际项目为例）：

```kotlin
// 识别到的进件提交接口（示例 — 实际接口因项目而异）：
/**
 * 提交进件数据
 * /api/user/acquisition/submitAcpInfo       ← 原始路径（业务语义）
 * HTTP POST /qYm_uuV/ebyOwFndavmb/t7lIf0   ← 混淆路径
 * @param u8UqQ submitAcqInfoReq
 */
@POST("/qYm_uuV/ebyOwFndavmb/t7lIf0")
suspend fun submitAcpInfoUsingPOST(
    @Body u8UqQ: Model40?
): BaseBean<String?>

// 请求体 Model40 字段语义（从 KDoc 提取）：
//   qPBWjdc6ytuwI8q3C92 : Int?     — 是否更新（0=新增，1=更新）
//   nVtaU : Long?                  — 进件流程 ID
//   koauGzyGQsODmcOj : Int?        — 步骤编号（1-6）
//   tBH6TMf1JkShQqelT8cW : List<Model41>? — 进件数据键值对
//     Model41.wCf7lmX : String?    — 字段 key（来自进件页面元素查询接口）
//     Model41.fRjkoK8Bffa5fl : String? — 字段 value（用户填写的内容）
```

### 接口字段文档化

实现 KYC 步骤时，需要知道每个步骤应提交哪些字段（key-value 对）。有两种方式获取：

**方式 1（接口驱动）**：调用 `acpElementInfoUsingGET(step=N)` → 从返回的 `EntryResp` 列表获取：
- `wCf7lmX` → 提交 key
- `akeHDR4A2` → 是否必填
- `jongFLyn3j` → 显示文本（label）
- `tjUDfh` → 字段类型（1=文本 2=下拉 3=数字 4=单选 6=图像 7=人脸）
- `rRInTRu79Sr5` → 选项列表（如果是下拉/单选）
- `q76BZK` → 验证规则

**方式 2（代码查找）**：查看已有 ViewModel 中的 `buildRequest()` / `buildSubmitRequest()` 方法，找到硬编码的 key 和 value 构造逻辑。

### 其他 KYC 接口识别

| 语义 | 识别特征 |
|------|---------|
| OCR 识别 | `@Multipart` + 方法名含 ocr / verify + 参数含 image/file part |
| 人脸比对 | `@Multipart` + 方法名含 face / check + 前端摄像头采集 |
| 银行列表 | `@GET` + 方法名含 bankList / queryBank + 无参 |
| 字典查询 | `@GET` + 方法名含 dict / dataDict + `@Query type` |
| 区域查询 | `@GET` + 方法名含 area / province / city |
| 进件页面元素 | `@GET` + 方法名含 element / pageInfo + `@Query step` |
| 进件进度 | `@GET` + 方法名含 progress / acquisitionProgress + 无参 |
| 进件数据查询 | `@GET` + 方法名含 queryAcq / queryAcquisition + `@Query step` |

### 使用建议

**实现新 KYC 步骤时**，按以下顺序获取接口信息：

1. **扫描 API Service 文件** → 找到所有 KYC 相关接口
2. **调用 `acpElementInfoUsingGET(step=N)`** → 获取该步骤的字段配置（如果项目有此接口）
3. **查找已有步骤的实现** → 参考同级 ViewModel 的 `buildRequest()` 方法，了解 `Model41` 的 key-value 格式
4. **确认接口参数语义** → 读 `Model40` 等请求体类的 KDoc

---

