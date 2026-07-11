/**
 * 缓存序列化安全 — KYC表单缓存数据结构定义
 *
 * 业务目的：
 *   每个KYC页面都应支持本地缓存（24h TTL），降低用户重复填写成本。
 *   缓存数据结构字段必须全部可空，保证序列化安全。
 *
 * 教学重点：
 *   1. ALL字段必须可空且有默认值null，保证新旧版本兼容
 *   2. 读取必须try/catch包裹，反序列化失败时清除缓存降级到空表单
 *   3. null在UI层映射为空串（?: ""）
 *   4. Int?的null表示"未选择"，0可能是有效值
 *   5. 版本演进：旧缓存JSON→新data class（增减字段），可空类型保证安全
 */

// ============================================================
// 缓存数据模型定义
// ============================================================

/**
 * ✅ 正确：所有字段可空 + 默认值 null —— 序列化安全
 *
 * ⚠️ 流程规则: ALL字段必须为nullable with default null
 * ⚠️ 流程规则: 版本演进时，旧缓存JSON→新data class可空类型保证安全
 * ⚠️ 流程规则: null在UI层映射为空串(?:"")，Int.null表示"未选择"(0可能是有效值)
 */
data class KycPersonalCache(
    var educationLevel: String? = null,
    var educationLevelKey: Int? = null,
    var maritalStatus: String? = null,
    var monthlyIncome: String? = null,
    var provinceCity: String? = null,
)

/**
 * ❌ 错误：非空字段 —— 反序列化崩溃风险
 *
 * 问题：
 * - 字段缺失 → NPE
 * - 空串默认值虽可避免崩溃，但与null语义不一致，且不同序列化框架行为不同
 */
// data class KycPersonalCache(
//     val educationLevel: String,          // 字段缺失 → NPE
//     val maritalStatus: String = "",      // 空串默认值可避免崩溃，但与 null 语义不一致，且不同序列化框架行为不同
// )

// ============================================================
// 缓存读写示例（防御式）
// ============================================================

// ⚠️ 写入缓存 — 直接序列化 data class
private suspend fun saveCache() {
    val cache = KycPersonalCache(
        educationLevel = state.value.educationLevel,
        educationLevelKey = state.value.educationLevelKey,
        // ...
    )
    val json = Gson().toJson(cache)
    prefs.edit().putString(CACHE_KEY, json).apply()
}

// ⚠️ 读取缓存 — try/catch 兜底，反序列化失败不崩溃
// ⚠️ 交互时机: 缓存损坏 → 清除并静默降级到空表单
private suspend fun loadCache(): KycPersonalCache? {
    return try {
        val json = prefs.getString(CACHE_KEY, null) ?: return null
        Gson().fromJson(json, KycPersonalCache::class.java)
    } catch (e: Exception) {
        // ⚠️ 流程规则: 缓存损坏 → 清除并静默降级到空表单，不崩溃
        prefs.edit().remove(CACHE_KEY).apply()
        null
    }
}
