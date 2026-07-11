[← UI 组件](ui-components.md) | [→ 设计原则](design-principles.md)

# KYC 字段配置策略（硬编码 vs 接口驱动）

KYC 各步骤的字段选项（教育水平、婚姻状况、关系等）有**两种实现策略**，根据项目情况选择。

### 策略对比

| 维度 | 方案 A：硬编码（静态列表） | 方案 B：接口驱动（字典系统） |
|------|--------------------------|---------------------------|
| 数据来源 | 前端 `LocalOptionDataSource` / `object` 常量 | 后端 `dataDictInfoUsingGET(type)` 接口 |
| 选项更新 | **需要发版** | 接口更新即时生效，无需发版 |
| 离线可用 | ✅ 始终可用 | ❌ 依赖网络，需缓存 |
| 复杂度 | 低：一个静态 list | 高：需三级缓存 + 测试模式 |
| 适用场景 | 选项极少变动、快速上线 | 运营需求多、需要 AB 测试 |
| 参考项目 | Rapidinero（当前项目采用此方案） | Skill 通用模板 |

### 方案 A：硬编码实现

```kotlin
/** 本地选项数据源 — 所有 KYC 选择字段选项硬编码 */
object LocalOptionDataSource {
    data class KeyValueOption(val key: Int, val value: String)

    fun educationLevels() = listOf(
        KeyValueOption(1, "Educación preescolar"),
        KeyValueOption(2, "Educación primaria"),
        // ...
    )

    fun maritalStatuses() = listOf(
        KeyValueOption(1, "Soltero/a"),
        KeyValueOption(2, "Casado/a"),
        // ...
    )
}

// ViewModel 中直接引用：
private fun optionsFor(field: BasicInfoField): List<KeyValueOption> {
    return when (field) {
        BasicInfoField.EDUCATION -> LocalOptionDataSource.educationLevels()
        BasicInfoField.MARITAL_STATUS -> LocalOptionDataSource.maritalStatuses()
        // ...
    }
}
```

### 方案 B：接口驱动实现

```kotlin
/**
 * 字典管理器 — 三级缓存策略。
 * 读取顺序：内存缓存 → DataStore 本地缓存（24h TTL） → 网络接口
 */
object DictManager {
    // 内存缓存：ConcurrentHashMap<type, List<DictItem>>
    private val memoryCache = ConcurrentHashMap<String, List<DictItem>>()
    private val cacheManager by lazy { CacheManager.get(AppContextProvider.get()) }

    suspend fun getDict(type: String): List<DictItem> {
        // 1. 内存命中
        memoryCache[type]?.let { return it }
        // 2. 本地 DataStore（24h TTL 校验）
        val local = cacheManager.getObject<CachedDict>("dict_$type")
        if (local != null && !local.isExpired()) {
            memoryCache[type] = local.items
            return local.items
        }
        // 3. 网络兜底
        val resp = apiService.dataDictInfoUsingGET(type)
        val items = resp.data?.p1fvVXvNRGoRO1Jm.orEmpty().map {
            DictItem(key = it.zDb82PNoG3de, value = it.zjGBOedGc67WH)
        }
        memoryCache[type] = items
        cacheManager.putObject("dict_$type", CachedDict(items, System.currentTimeMillis()))
        return items
    }
}

data class CachedDict(val items: List<DictItem>, val timestamp: Long) {
    private val ttlMs = 24 * 60 * 60 * 1000L  // 24h
    fun isExpired() = System.currentTimeMillis() - timestamp > ttlMs
}
```

### 判断标准

**选择方案 A（硬编码）** 当满足以下所有条件：
- 选项极少变动（如性别、婚姻状况）
- 项目无字典接口或字典接口不覆盖这些字段
- 加快开发速度优先于运营灵活性

**选择方案 B（接口驱动）** 当满足以下任一条件：
- 后端有字典接口（`dataDictInfoUsingGET` 或类似）且持续维护
- 运营需要频繁调整选项（如收入区间、工作类型）
- 需要 AB 测试不同选项展示

**混用策略**（推荐）：对稳定字段用硬编码，对可能变动的字段用接口驱动。

---
