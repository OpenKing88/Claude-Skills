[← 字典系统](dictionary-system.md) | [→ 图片处理](image-processing.md)

# 缓存机制

### 页面表单缓存

每个 KYC 页面都应支持本地缓存，降低用户重复填写成本。

**缓存特性**：
- 24 小时 TTL
- 按用户账号隔离（多账号不冲突）
- 页面级别隔离（不同页面互不干扰）
- 缓存内容包括：用户已填写的所有表单字段（展示文本 + 提交值）

**缓存时机**：
- 页面初始化时读取缓存恢复数据
- 用户每次修改表单后自动保存缓存

### 缓存序列化安全（CRITICAL）

**所有用于本地缓存的数据结构字段，必须定义为可空类型并设置默认值 `null`：**

```kotlin
// ✅ 正确：所有字段可空 + 默认值 null —— 序列化安全
data class KycPersonalCache(
    var educationLevel: String? = null,
    var educationLevelKey: Int? = null,
    var maritalStatus: String? = null,
    var monthlyIncome: String? = null,
    var provinceCity: String? = null,
)

// ❌ 错误：非空字段 —— 反序列化崩溃风险
data class KycPersonalCache(
    val educationLevel: String,          // 字段缺失 → NPE
    val maritalStatus: String = "",      // 空串默认值可避免崩溃，但与 null 语义不一致，且不同序列化框架行为不同
)
```

**为什么必须可空：**

1. **序列化框架差异** — 项目可能混用多种序列化方案（Gson、kotlinx.serialization），它们对字段缺失的处理方式不同：
   - Gson (`GsonConverterFactory`)：JSON 中字段缺失 → 保持字段默认值。可空类型默认 `null` 安全；非空类型在反射创建实例时可能抛异常
   - kotlinx.serialization：JSON 中字段缺失 → 如果有默认值则使用默认值，否则抛 `MissingFieldException`
   - 手动 `JSONObject` 解析：`optString()` 安全返回空串，`getString()` 字段缺失抛 `JSONException`

2. **版本演进兼容** — App 升级后代码可能增删缓存字段，旧缓存 JSON 反序列化到新数据结构时：
   - 新增字段：旧 JSON 中不存在 → 可空类型自动为 `null` ✓
   - 删除字段：旧 JSON 中有该字段 → 新类中无对应字段，Gson 忽略 ✓
   - 修改字段类型：旧 `Int` → 新 `String` → 可空类型返回 `null`，非空类型直接崩溃 ✗

3. **缓存数据完整性不可信任** — 本地缓存可能因为以下原因损坏或不完整：
   - 用户手动清除应用数据（残留文件）
   - 系统备份恢复（跨设备、跨版本）
   - 存储空间不足导致写入截断
   - SharedPreferences XML 解析异常

**字段默认值策略总结：**

| 字段类型 | 推荐声明 | 说明 |
|---------|---------|------|
| 字符串 | `var name: String? = null` | `null` 在 UI 层映射为空串 `?: ""` |
| 整数 | `var key: Int? = null` | `null` 表示"未选择"，0 可能是有效值 |
| 浮点 | `var amount: Double? = null` | 同上 |
| 布尔 | `var flag: Boolean = false` | 布尔可用非空，只有两种状态且 false 是合理默认 |
| 列表 | `var items: List<Item?>? = null` | `null` 列表和空列表语义不同时用 `null`；否则可用 `listOf()` |
| 嵌套对象 | `var child: ChildCache? = null` | 嵌套缓存对象同样全可空 |

**缓存读写示例（防御式）：**

```kotlin
// 写入缓存 — 直接序列化 data class
private suspend fun saveCache() {
    val cache = KycPersonalCache(
        educationLevel = state.value.educationLevel,
        educationLevelKey = state.value.educationLevelKey,
        // ...
    )
    val json = Gson().toJson(cache)
    prefs.edit().putString(CACHE_KEY, json).apply()
}

// 读取缓存 — try/catch 兜底，反序列化失败不崩溃
private suspend fun loadCache(): KycPersonalCache? {
    return try {
        val json = prefs.getString(CACHE_KEY, null) ?: return null
        Gson().fromJson(json, KycPersonalCache::class.java)
    } catch (e: Exception) {
        // 缓存损坏 → 清除并静默降级到空表单
        prefs.edit().remove(CACHE_KEY).apply()
        null
    }
}
```

**禁止做法：**
- ❌ 在缓存数据结构中使用非空字段（`val x: String`），即使设置了默认值也可能在不同序列化框架下行为不一致
- ❌ 缓存反序列化时不加 try/catch，崩溃会直接白屏
- ❌ 缓存写入时过滤 `null` 值（如 Gson 默认不序列化 null），这会导致读取时缺少字段
- ❌ 在 UI 层直接对缓存数据使用 `!!` 强制解包

### 字典缓存

字典数据和页面表单数据分开缓存：
- 字典缓存：管理选择器选项，三层策略（内存→本地→网络）
- 表单缓存：管理用户输入，两层策略（本地 DataStore + TTL）

---
