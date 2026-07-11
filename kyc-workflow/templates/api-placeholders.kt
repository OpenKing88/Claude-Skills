/**
 * API接口占位代码 — 后端接口未定义时使用
 *
 * 业务目的：
 *   当项目后端接口尚未就绪，需要前端先完成流程代码时，按以下标准创建接口占位，
 *   保证KYC流程代码完整可编译。等后端就绪后替换为实际接口定义。
 *
 * 教学重点：
 *   1. 所有接口为占位代码，后端未就绪时使用
 *   2. 字段名是混淆字符串（后端定义），必须通过KDoc注释理解语义
 *   3. 替换时保持接口签名一致，仅更新实际字段名和路径
 *   4. 包含9组接口：进件提交、银行卡外部提交、人脸渠道、Face++ Token、人脸比对、字典、银行列表、OCR、地区列表
 */

// ============================================================
// 1. 进件数据提交接口
// ============================================================

// network/loan/LoanApiService.kt
/**
 * ⚠️ 占位: 后端未就绪时的占位接口
 * ⚠️ 替换: 后端就绪后替换为实际接口路径和字段名
 */
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

// ============================================================
// 2. 银行卡外部提交接口（可选）
// ============================================================

// network/loan/LoanApiService.kt
/**
 * ⚠️ 占位: 银行卡外部添加模式的提交接口，非KYC步骤模式使用
 * ⚠️ 替换: 后端就绪后替换为实际接口路径和字段名
 */
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

// ============================================================
// 3. 人脸渠道查询接口
// ============================================================

// network/loan/LoanApiService.kt
/**
 * ⚠️ 占位: 查询人脸认证渠道配置
 * ⚠️ 替换: 后端就绪后替换为实际接口路径和字段名
 */
@GET("/api/face/channel")
suspend fun getFaceChannel(): ApiEnvelope<FaceChannelResp>

// network/loan/Response.kt
@Serializable
data class FaceChannelResp(
    /** 人脸渠道：1=accu, 2=yc, 3=facePlusPlus */
    val p0Mgp4IL8bD: String? = null,
)

// ============================================================
// 4. Face++ Token 接口
// ============================================================

// network/loan/LoanApiService.kt
/**
 * ⚠️ 占位: 获取Face++ SDK BizToken
 * ⚠️ 替换: 后端就绪后替换为实际接口路径和字段名
 */
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

// ============================================================
// 5. 人脸比对接口
// ============================================================

// network/upload/UploadApiService.kt
/**
 * ⚠️ 占位: 人脸比对接口（自研/Face++均走此接口）
 * ⚠️ 替换: 后端就绪后替换为实际参数字段名
 */
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

// ============================================================
// 6. 字典接口
// ============================================================

// network/common/CommonApiService.kt
/**
 * ⚠️ 占位: 查询字典数据（教育水平、婚姻状况、工作类型等选项）
 * ⚠️ 替换: 后端就绪后替换为实际接口路径和字段名
 */
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

// ============================================================
// 7. 银行列表接口
// ============================================================

// network/loan/LoanApiService.kt
/**
 * ⚠️ 占位: 获取银行列表（KYC银行卡选择使用）
 * ⚠️ 替换: 后端就绪后替换为实际接口路径和字段名
 */
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

// ============================================================
// 8. OCR 识别接口
// ============================================================

// network/upload/UploadApiService.kt
/**
 * ⚠️ 占位: 上传证件图片+OCR识别
 * ⚠️ 替换: 后端就绪后替换为实际接口路径
 */
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

// ============================================================
// 9. 地区列表接口
// ============================================================

// network/common/CommonApiService.kt
/**
 * ⚠️ 占位: 获取省市地区列表（个人信息页级联选择使用）
 * ⚠️ 替换: 后端就绪后替换为实际接口路径和字段名
 */
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
