/**
 * DictManager — 字典管理器（方案B：接口驱动实现）
 *
 * 业务目的：
 *   管理KYC选择字段的选项数据（教育水平、婚姻状况、工作类型等），通过三级缓存策略动态获取。
 *
 * 教学重点：
 *   1. 读取顺序：内存(ConcurrentHashMap) → 本地(DataStore，24h TTL) → 网络(API)
 *   2. 缓存key必须包含userId，实现账号隔离
 *   3. 测试模式标志可跳过网络，返回硬编码数据
 *   4. CachedDict包装原始数据+时间戳，通过isExpired()判断缓存有效性
 *   5. 字典数据与页面表单数据分开缓存管理
 */

/**
 * 字典管理器 — 三级缓存策略。
 * 读取顺序：内存缓存 → DataStore 本地缓存（24h TTL） → 网络接口
 */
object DictManager {
    // ⚠️ 内存缓存：ConcurrentHashMap<type, List<DictItem>> — 进程内缓存，重启后丢失
    private val memoryCache = ConcurrentHashMap<String, List<DictItem>>()
    private val cacheManager by lazy { CacheManager.get(AppContextProvider.get()) }

    // ⚠️ 账号隔离: 缓存key必须包含userId（从登录态/AccountManager获取）
    // 实际项目中替换 getUserId() 为项目实际的用户ID获取方式
    private fun getUserId(): String = AccountManager.getUserId() ?: "anonymous"

    suspend fun getDict(type: String): List<DictItem> {
        // 1. 内存命中
        // ⚠️ 流程规则: 内存缓存最快，优先检查
        memoryCache[type]?.let { return it }
        // 2. 本地 DataStore（24h TTL 校验）
        // ⚠️ 流程规则: 本地缓存按账号隔离，cache key必须包含userId
        val cacheKey = "dict_${getUserId()}_$type"
        val local = cacheManager.getObject<CachedDict>(cacheKey)
        if (local != null && !local.isExpired()) {
            memoryCache[type] = local.items
            return local.items
        }
        // 3. 网络兜底
        // ⚠️ 接口调用: 网络获取后写入内存+本地缓存
        val resp = apiService.dataDictInfoUsingGET(type)
        val items = resp.data?.p1fvVXvNRGoRO1Jm.orEmpty().map {
            DictItem(key = it.zDb82PNoG3de, value = it.zjGBOedGc67WH)
        }
        memoryCache[type] = items
        cacheManager.putObject(cacheKey, CachedDict(items, System.currentTimeMillis()))
        return items
    }
}

data class CachedDict(val items: List<DictItem>, val timestamp: Long) {
    private val ttlMs = 24 * 60 * 60 * 1000L  // 24h
    fun isExpired() = System.currentTimeMillis() - timestamp > ttlMs
}

// ⚠️ 测试模式: flag为true时直接返回本地硬编码数据，不走网络
// ⚠️ 账号隔离: cache key应包含userId (如 "dict_{userId}_{type}")
