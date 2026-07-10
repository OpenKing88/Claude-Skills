# Type Mapping Reference

## Swagger/OpenAPI → Kotlin Type Conversion

### Primitive Types

| Swagger/OpenAPI | Kotlin Type | Notes |
|-----------------|-------------|-------|
| `integer` (int32) | `Int` | |
| `integer` (int64) | `Long` | |
| `number` | `Double` | Swagger 2.0: `number`. OpenAPI 3.x: `number` with optional `format: float` |
| `number` (double) | `Double` | |
| `string` | `String` | |
| `string` (date) | `String` | Add KDoc `// ISO date (yyyy-MM-dd)` |
| `string` (date-time) | `String` | Add KDoc `// ISO datetime` |
| `string` (byte) | `String` | Base64 encoded |
| `string` (binary) | `String` | |
| `boolean` | `Boolean` | |
| `file` | `MultipartBody.Part` | Only in formData parameters |

### Complex Types

| Swagger/OpenAPI | Kotlin Type |
|-----------------|-------------|
| `array` of `T` | `List<T>` |
| `$ref: "#/definitions/Foo"` | `Foo` |
| `$ref: "#/components/schemas/Foo"` | `Foo` |
| inline `object` | Nested `data class` |
| `enum` (string values) | `enum class` with `@Serializable` |
| oneOf / anyOf (OpenAPI 3.x) | `sealed class` with `@Serializable` (kotlinx) |

### Nullability Rules (DEFAULT: ALL FIELDS NULLABLE)

**Default behavior (`--nullable-all=true`, the default):**

ALL fields are generated as nullable with default `null`:
```kotlin
var fieldName: Type? = null
```

This applies regardless of:
- Whether the field is in the schema's `required` array
- Whether the field has `nullable: true` (OpenAPI 3.x)
- Whether the schema even has a `required` array

**Rationale:**
- Backend APIs frequently omit fields that Swagger marks as "required"
- Obfuscated APIs often have inaccurate `required` declarations
- Gson deserialization crashes on missing non-nullable fields
- The project codebase uses `var field: Type? = null` almost exclusively (~99% of fields)

**Exception: `--strict-required` mode (only when `--nullable-all=false`):**

A field is **non-nullable** (`val field: Type`) ONLY if:
1. Field IS in `required` array AND
2. Field has no `nullable: true` AND
3. `--strict-required` is explicitly set

A field remains **nullable with default `null`** (`var field: Type? = null`) if:
1. Field name is NOT in the schema's `required` array, OR
2. Schema has no `required` array at all, OR
3. Field has `nullable: true` (OpenAPI 3.x)

**Summary table:**

| Mode | `required` array | Field in `required`? | Generated Code |
|------|-----------------|----------------------|----------------|
| Default | present | yes | `var f: Type? = null` |
| Default | present | no | `var f: Type? = null` |
| Default | absent | N/A | `var f: Type? = null` |
| `--strict-required` | present | yes | `val f: Type` |
| `--strict-required` | present | no | `var f: Type? = null` |
| `--strict-required` | absent | N/A | `var f: Type? = null` |

### Special Cases

| Swagger Pattern | Kotlin Handling |
|-----------------|-----------------|
| `additionalProperties: { type: string }` | `Map<String, String?>` |
| `additionalProperties: { $ref: ... }` | `Map<String, RefType?>` |
| `additionalProperties: { type: integer }` | `Map<String, Int?>` |
| `allOf: [Base, Extension]` | `data class Extension(...)` with Base's properties inlined (see allOf section below) |
| `discriminator` + `oneOf` | `sealed class` with `@SerializedName` on discriminator |
| `oneOf` / `anyOf` (OpenAPI 3.x) | `sealed class` with data class variants (see sealed class section below) |

### Enum Types

When the Swagger schema defines `enum` with a list of values:

```json
{
  "OrderStatus": {
    "type": "string",
    "enum": ["PENDING", "PROCESSING", "COMPLETED", "CANCELLED"],
    "description": "订单状态枚举"
  }
}
```

Generate:
```kotlin
@Keep
enum class OrderStatus(var value: String) {
    /** 待处理 */
    PENDING("PENDING"),
    /** 处理中 */
    PROCESSING("PROCESSING"),
    /** 已完成 */
    COMPLETED("COMPLETED"),
    /** 已取消 */
    CANCELLED("CANCELLED"),
}
```

**Enum value field naming:** Use `value` for string enums, `code` for integer enums:
- String enum → `enum class Name(var value: String)`
- Integer enum → `enum class Name(var code: Int)`

**When to generate an enum vs. Int/String field:**
- Generate enum when: schema has `enum` keyword with explicit value list
- Use Int/String field with comment when: schema is `type: integer` or `type: string` without `enum` keyword, even if description lists possible values

### Sealed Class Types (Polymorphic)

**OpenAPI 3.x `oneOf` / `anyOf`:**

When a schema uses `oneOf` or `anyOf`:
```json
{
  "PaymentMethod": {
    "oneOf": [
      { "$ref": "#/components/schemas/CardPayment" },
      { "$ref": "#/components/schemas/BankTransfer" }
    ]
  }
}
```

Generate:
```kotlin
@Keep
sealed class PaymentMethod {
    data class CardPayment(
        var cardNumber: String? = null,
        var expiryDate: String? = null,
    ) : PaymentMethod()

    data class BankTransfer(
        var bankCode: String? = null,
        var accountNumber: String? = null,
    ) : PaymentMethod()
}
```

**Discriminator pattern:**

When the schema uses `discriminator` with `oneOf`:
```json
{
  "Animal": {
    "type": "object",
    "discriminator": { "propertyName": "animalType" },
    "oneOf": [
      { "$ref": "#/components/schemas/Dog" },
      { "$ref": "#/components/schemas/Cat" }
    ]
  }
}
```

Generate a sealed class with the discriminator field:
```kotlin
@Keep
sealed class Animal {
    abstract var animalType: String?

    data class Dog(
        override var animalType: String? = null,
        var breed: String? = null,
    ) : Animal()

    data class Cat(
        override var animalType: String? = null,
        var color: String? = null,
    ) : Animal()
}
```

### allOf Handling

When a schema uses `allOf` to compose multiple definitions:

```json
{
  "CreateOrderRequest": {
    "allOf": [
      { "$ref": "#/definitions/BaseOrder" },
      {
        "type": "object",
        "properties": {
          "priority": { "type": "integer" }
        }
      }
    ]
  }
}
```

**Algorithm:**
1. For each `$ref` in the `allOf` array, resolve and fetch the referenced schema's properties
2. Merge all properties from all sources into a single flat list
3. If the same field appears in multiple sources, the last one wins (rightmost priority)
4. Generate a single `data class` with all merged fields (all nullable by default)
5. Add KDoc comment noting the composed sources

**Implementation notes:**
- Kotlin doesn't support multiple inheritance for data classes, so inlining is the only practical approach
- The `required` arrays from all composed schemas are merged
- Nested `allOf` chains are resolved recursively

### Map Types (additionalProperties)

When the schema includes `additionalProperties`, it indicates a dynamic key-value map.

**Detection:**
- Swagger 2.0: `"additionalProperties": { "type": "string" }`
- OpenAPI 3.x: `"additionalProperties": { "type": "string" }`

**Generation:**

| Swagger | Kotlin |
|---------|--------|
| `additionalProperties: { type: string }` | `var field: Map<String, String?>? = null` |
| `additionalProperties: { type: integer }` | `var field: Map<String, Int?>? = null` |
| `additionalProperties: { type: number }` | `var field: Map<String, Double?>? = null` |
| `additionalProperties: { $ref: "#/definitions/Foo" }` | `var field: Map<String, Foo?>? = null` |
| `additionalProperties: { type: array, items: { type: string } }` | `var field: Map<String, List<String?>?>? = null` |

**Import required:** No additional import for `Map` (Kotlin stdlib).

**Note:** Map value types follow the global nullable rule (all nullable by default).

---

## Response Type Resolution Algorithm

```
Input: Swagger endpoint's responses['200'] schema

Step 1: Extract Schema Reference
  - Swagger 2.0: responses['200']['schema']['$ref']
  - OpenAPI 3.x: responses['200']['content']['application/json']['schema']['$ref']

Step 2: Match «T» Pattern
  If schema reference matches regex `(.+?)«(.+?)»`:
    wrapper_class = resolve_class(group_1)
    inner_type = resolve_class(group_2)
    return "ApiBaseResponseBean<{inner_type}>"

Step 3: Match code/msg/data Pattern
  If resolved schema has properties { code, msg, data }:
    data_type = resolve_type(data_schema)
    return "ApiBaseResponseBean<{data_type}>"

Step 4: Direct Response (no wrapper)
  data_type = resolve_type(schema)
  return "ApiBaseResponseBean<{data_type}>"
```

### Void Handling

If `inner_type` or `data_type` resolves to "Void":
- Default: Use `Any?` (nullable, for compatibility with Gson)
- The response wrapper becomes `ApiBaseResponseBean<Any?>`
- Caller should check `fazuqufu` (code == 200) instead of inspecting the data field

### List Response Handling

Swagger may nest list in the wrapper:
```
公共响应体«List«Foo»»  →  ApiBaseResponseBean<List<Foo>>
```

Parse nested «» recursively or check for "List«" prefix.

---

## Field Description Parsing

### Format Pattern

Many obfuscated Swagger docs use a 3-part description format:

```
"semanticFieldName :obfuscatedFieldName: 中文说明"
```

Regex: `^([^:]+?)\s*:([^:]+?):\s*(.+)$`

| Group | Meaning | Usage |
|-------|---------|-------|
| 1 | Semantic name (English) | KDoc comment: `/** description (semanticName) */` |
| 2 | Obfuscated name | Field name in code (if obfuscated style) |
| 3 | Chinese description | KDoc comment: `/**  description ... */` |

### KDoc Comment Template

When description contains the 3-part pattern:
```kotlin
/** {group3} ({group1}) */
@SerializedName("{obfuscatedName}")
var {obfuscatedName}: {Type}? = null
```

When description is plain text:
```kotlin
/** {description} */
@SerializedName("{obfuscatedName}")
var {obfuscatedName}: {Type}? = null
```

When description is empty/missing:
```kotlin
@SerializedName("{obfuscatedName}")
var {obfuscatedName}: {Type}? = null
```

### Example

Input:
```json
{
  "rFz6Mre0cvgkNLK": {
    "type": "integer", "format": "int64",
    "description": "bankAccountId :rFz6Mre0cvgkNLK: 收款银行账号ID"
  }
}
```

Output (default nullable mode):
```kotlin
/** 收款银行账号ID (bankAccountId) */
@SerializedName("rFz6Mre0cvgkNLK")
var rFz6Mre0cvgkNLK: Long? = null
```
