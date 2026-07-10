# Code Generation Templates

## Template Engine

Templates use Jinja2-like syntax with these delimiters:
- `{{variable}}` — variable substitution
- `{% for x in y %}...{% endfor %}` — loop
- `{% if condition %}...{% endif %}` — conditional

---

## 1. API Description Annotation

**File:** `ApiDescription.kt` (only generated if `--with-api-desc=true` and doesn't exist)

```kotlin
package {{packageName}}

/**
 * API 接口描述注解
 * 用于标记 Retrofit 接口方法的中文描述，配合日志拦截器在运行时打印接口说明
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiDescription(val value: String)
```

---

## 2. Data Class (Bean)

**File:** `{{GroupName}}Beans.kt`

```kotlin
package {{packageName}}

import androidx.annotation.Keep
{% if serializationFramework == "kotlinx" %}
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
{% elif serializationFramework == "gson" %}
import com.google.gson.annotations.SerializedName
{% endif %}

{% for class in classes %}
/**
 * {{class.swaggerDescription|default(class.swaggerTitle)}}
{% if class.swaggerTitle and class.swaggerTitle != class.className %}
 * Swagger title: {{class.swaggerTitle}}
{% endif %}
 */
@Keep
{% if serializationFramework == "kotlinx" %}
@Serializable
{% endif %}
data class {{class.className}}(
{% for field in class.fields %}
    {% if field.description %}
    /** {{field.chineseDescription}} ({{field.semanticName}}) */
    {% endif %}
    {% if serializationFramework == "gson" %}
    @SerializedName("{{field.obfuscatedName}}")
    {% elif serializationFramework == "kotlinx" %}
    @SerialName("{{field.obfuscatedName}}")
    {% endif %}
    {% if field.nullable %}
    var {{field.obfuscatedName}}: {{field.kotlinType}}? = null,
    {% else %}
    val {{field.obfuscatedName}}: {{field.kotlinType}},
    {% endif %}
{% endfor %}
)
{% if not loop.last %}

{% endif %}
{% endfor %}
```

**Key changes from v1.0:**
- `@Keep` uses `androidx.annotation.Keep` (universal import), not Gson-specific `@Keep`
- All fields default to `var field: Type? = null` regardless of `required` array
- `val` non-nullable only used when `--strict-required` is set AND field IS in `required`
- Gson mode uses `@SerializedName`; kotlinx mode uses `@SerialName`

### Field Variable Binding

| Template Variable | Source |
|-------------------|--------|
| `field.obfuscatedName` | Swagger property key (e.g., `rFz6Mre0cvgkNLK`) |
| `field.semanticName` | Parsed from description group 1 (e.g., `bankAccountId`) |
| `field.chineseDescription` | Parsed from description group 3 (e.g., `收款银行账号ID`) |
| `field.kotlinType` | Mapped from Swagger type (see type-mapping.md) |
| `field.nullable` | Default `true`. Only `false` when `--strict-required` AND field IS in `required` array |
| `class.className` | Generated semantic class name |
| `class.swaggerTitle` | Original Swagger definition title |
| `class.swaggerDescription` | Swagger definition description |

---

## 2a. Enum Class

**File:** `{{GroupName}}Beans.kt` (or `CommonEnums.kt` for shared enums)

```kotlin
package {{packageName}}

import androidx.annotation.Keep

{% for enum in enums %}
/**
 * {{enum.description|default(enum.className)}}
{% if enum.swaggerTitle %}
 * Swagger title: {{enum.swaggerTitle}}
{% endif %}
 */
@Keep
enum class {{enum.className}}(var {{enum.valueField}}: {{enum.valueType}}) {
{% for member in enum.members %}
    /** {{member.description|default(member.name)}} */
    {{member.name}}({{member.valueLiteral}}){% if not loop.last %},{% else %};{% endif %}
{% endfor %}

    companion object {
        /**
         * Parse from raw value, returns null for unknown values
         */
        fun from{{enum.valueField.replaceFirstChar(Char::uppercase)}}(raw: {{enum.valueType}}): {{enum.className}}? {
            return entries.firstOrNull { it.{{enum.valueField}} == raw }
        }
    }
}
{% if not loop.last %}

{% endif %}
{% endfor %}
```

### Enum Variable Binding

| Template Variable | Source |
|-------------------|--------|
| `enum.className` | Generated enum class name (PascalCase) |
| `enum.description` | Swagger schema description |
| `enum.valueField` | `"value"` for string enums, `"code"` for integer enums |
| `enum.valueType` | `"String"` or `"Int"` |
| `enum.members[].name` | Enum constant name (PascalCase, sanitized) |
| `enum.members[].description` | Member-level description if available |
| `enum.members[].valueLiteral` | `"\"rawValue\""` for strings, `123` for integers |

**Sanitization rules for member names:**
- Strip non-alphanumeric chars except underscore
- Prefix with `_` if name starts with a digit
- Convert to PascalCase (upper camel)

---

## 2b. Sealed Class (Polymorphic)

**File:** `{{GroupName}}Beans.kt`

```kotlin
package {{packageName}}

import androidx.annotation.Keep
{% if serializationFramework == "gson" %}
import com.google.gson.annotations.SerializedName
{% elif serializationFramework == "kotlinx" %}
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
{% endif %}

/**
 * {{sealed.description|default(sealed.className)}}
 * Polymorphic type with {{sealed.variants|length}} variants
 */
@Keep
{% if serializationFramework == "kotlinx" %}
@Serializable
{% endif %}
sealed class {{sealed.className}} {
{% if sealed.discriminator %}
    abstract var {{sealed.discriminator}}: String?
{% endif %}

{% for variant in sealed.variants %}
    /**
     * {{variant.description|default(variant.className)}}
     */
    @Keep
    {% if serializationFramework == "kotlinx" %}
    @Serializable
    {% endif %}
    data class {{variant.className}}(
{% for field in variant.fields %}
        {% if field.description %}
        /** {{field.chineseDescription}} ({{field.semanticName}}) */
        {% endif %}
        {% if serializationFramework == "gson" %}
        @SerializedName("{{field.obfuscatedName}}")
        {% elif serializationFramework == "kotlinx" %}
        @SerialName("{{field.obfuscatedName}}")
        {% endif %}
        {% if sealed.discriminator and field.obfuscatedName == sealed.discriminator %}
        override var {{field.obfuscatedName}}: String? = null,
        {% else %}
        var {{field.obfuscatedName}}: {{field.kotlinType}}? = null,
        {% endif %}
{% endfor %}
    ) : {{sealed.className}}()
{% if not loop.last %}

{% endif %}
{% endfor %}
}
```

### Sealed Variable Binding

| Template Variable | Source |
|-------------------|--------|
| `sealed.className` | Generated sealed class name |
| `sealed.description` | Schema description |
| `sealed.discriminator` | Discriminator propertyName if present, else empty |
| `sealed.variants` | Array of variant schemas from oneOf/anyOf |
| `variant.className` | Generated variant class name |
| `variant.fields` | Properties of the variant's schema |

---

## 3. Retrofit API Interface

**File:** `{{GroupName}}Api.kt`

```kotlin
package {{packageName}}

{% if hasQueryParams %}
import retrofit2.http.Query
{% endif %}
{% if hasBodyParams %}
import retrofit2.http.Body
{% endif %}
{% if hasMultipart %}
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.Part
{% endif %}
{% if hasPathParams %}
import retrofit2.http.Path
{% endif %}
{% if hasHeaderParams %}
import retrofit2.http.Header
{% endif %}
import retrofit2.http.GET
import retrofit2.http.POST
{% if hasPutEndpoints %}
import retrofit2.http.PUT
{% endif %}
{% if hasDeleteEndpoints %}
import retrofit2.http.DELETE
{% endif %}
{% if withApiDesc %}
import {{apiDescriptionImport}}
{% endif %}
import {{responseWrapperImport}}

/**
 * {{groupDescription}}
 * Auto-generated from Swagger tag: {{swaggerTag}}
 */
interface {{interfaceName}} {

{% for method in methods %}
{% if withApiDesc %}
    @ApiDescription("{{method.summary}}")
{% endif %}
{% if method.isMultipart %}
    @Multipart
{% endif %}
    @{{method.httpMethod}}("{{method.path}}")
    suspend fun {{method.methodName}}(
{% for param in method.params %}
        @{{param.annotation}}{% if param.annotation == "Query" %}("{{param.swaggerName}}"){% endif %}{% if param.annotation == "Path" %}("{{param.swaggerName}}"){% endif %}{% if param.annotation == "Header" %}("{{param.swaggerName}}"){% endif %} {{param.paramName}}: {{param.kotlinType}},
{% endfor %}
    ): {{method.returnType}}

{% endfor %}
}
```

### Method Variable Binding

| Template Variable | Source |
|-------------------|--------|
| `method.summary` | Swagger endpoint `summary` field |
| `method.httpMethod` | `GET`, `POST`, `PUT`, `DELETE` (uppercased) |
| `method.path` | Swagger path string (obfuscated or real) |
| `method.methodName` | Generated from operationId (see naming rules in SKILL.md) |
| `method.returnType` | From response resolution (e.g., `ApiBaseResponseBean<Foo?>`) |
| `method.isMultipart` | `true` if `consumes` contains `multipart/form-data` |
| `param.annotation` | `Body`, `Query`, `Part`, `Header`, `Path` |
| `param.swaggerName` | Original parameter name from Swagger (for `@Query`/`@Path`/`@Header` value) |
| `param.paramName` | Generated parameter name (camelCase) |
| `param.kotlinType` | Mapped Kotlin type |

### Parameter Detection Rules

| Swagger `in` | Annotation | Notes |
|-------------|------------|-------|
| `body` | `@Body` | POST/PUT request body |
| `query` | `@Query("name")` | GET URL query parameter |
| `path` | `@Path("name")` | URL path placeholder (e.g., `/{id}`) |
| `formData` | `@Part` | Multipart form field (requires `@Multipart`) |
| `header` | `@Header("name")` | Custom request header |

---

## 4. Complete Output Example

Given Swagger input:
```json
{
  "paths": {
    "/otXV8/nlzJkOWgmuMK/u0311v": {
      "post": {
        "tags": ["订单类api-熊理晶"],
        "summary": "预下单",
        "operationId": "submitPreOrderUsingPOST",
        "parameters": [{
          "in": "body",
          "name": "body",
          "schema": { "$ref": "#/definitions/SubmitPreOrderReq" }
        }],
        "responses": {
          "200": {
            "schema": { "$ref": "#/definitions/公共响应体«Void»" }
          }
        }
      }
    }
  },
  "definitions": {
    "SubmitPreOrderReq": {
      "type": "object",
      "title": "SubmitPreOrderReq",
      "required": ["ousPnsicyE5"],
      "properties": {
        "rFz6Mre0cvgkNLK": {
          "type": "integer", "format": "int64",
          "description": "bankAccountId :rFz6Mre0cvgkNLK: 收款银行账号ID"
        },
        "ousPnsicyE5": {
          "type": "array",
          "description": "confirmLoanParams :ousPnsicyE5: 贷款确认参数列表",
          "items": { "$ref": "#/definitions/ConfirmLoanParamsItem" }
        }
      }
    },
    "ConfirmLoanParamsItem": {
      "type": "object",
      "properties": {
        "dgixDcLFug": {
          "type": "string",
          "description": "productCode :dgixDcLFug: 产品编码"
        },
        "ovbZZz_dgw3o7QW": {
          "type": "number",
          "description": "loanAmount :ovbZZz_dgw3o7QW: 借款金额"
        }
      }
    }
  }
}
```

Generated output (default: Gson serialization, all fields nullable):

**OrderBeans.kt:**
```kotlin
package com.danabaik.ccah.appnet.gen

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * SubmitPreOrderReq
 */
@Keep
data class SubmitPreOrderReq(
    /** 收款银行账号ID (bankAccountId) */
    @SerializedName("rFz6Mre0cvgkNLK")
    var rFz6Mre0cvgkNLK: Long? = null,

    /** 贷款确认参数列表 (confirmLoanParams) */
    @SerializedName("ousPnsicyE5")
    var ousPnsicyE5: List<ConfirmLoanParamsItem?>? = null,
)

/**
 * ConfirmLoanParamsItem
 */
@Keep
data class ConfirmLoanParamsItem(
    /** 产品编码 (productCode) */
    @SerializedName("dgixDcLFug")
    var dgixDcLFug: String? = null,

    /** 借款金额 (loanAmount) */
    @SerializedName("ovbZZz_dgw3o7QW")
    var ovbZZz_dgw3o7QW: Double? = null,
)
```

**Key differences from v1.0:**
- `ousPnsicyE5` is now `var ... List<...>? = null` even though it's in `required` — follows the global nullable rule
- `@Keep` from `androidx.annotation`, not `kotlinx.serialization`
- No `@Serializable` — Gson is the default, and Gson doesn't need it
- `@SerializedName` on every field
- All fields use `var ...? = null`

**OrderApi.kt:**
```kotlin
package com.danabaik.ccah.appnet.gen

import retrofit2.http.Body
import retrofit2.http.POST
import com.danabaik.ccah.appnet.gen.ApiDescription
import com.danabaik.ccah.appbean.ApiBaseResponseBean

/**
 * 订单相关 API
 * Auto-generated from Swagger tag: 订单类api-熊理晶
 */
interface OrderApi {

    @ApiDescription("预下单")
    @POST("/otXV8/nlzJkOWgmuMK/u0311v")
    suspend fun submitPreOrder(
        @Body body: SubmitPreOrderReq,
    ): ApiBaseResponseBean<Any?>

}
```

---

## 5. Enum Generation Example

Given Swagger input:
```json
{
  "OrderStatus": {
    "type": "string",
    "enum": ["PENDING", "PROCESSING", "COMPLETED", "CANCELLED"],
    "description": "订单状态"
  }
}
```

Generated output:
```kotlin
import androidx.annotation.Keep

/**
 * 订单状态
 */
@Keep
enum class OrderStatus(var value: String) {
    /** PENDING */
    PENDING("PENDING"),
    /** PROCESSING */
    PROCESSING("PROCESSING"),
    /** COMPLETED */
    COMPLETED("COMPLETED"),
    /** CANCELLED */
    CANCELLED("CANCELLED");

    companion object {
        fun fromValue(raw: String): OrderStatus? {
            return entries.firstOrNull { it.value == raw }
        }
    }
}
```

---

## 6. Sealed Class Generation Example

Given OpenAPI 3.x input:
```json
{
  "PaymentResult": {
    "oneOf": [
      { "$ref": "#/components/schemas/SuccessResult" },
      { "$ref": "#/components/schemas/FailureResult" }
    ]
  },
  "SuccessResult": {
    "type": "object",
    "properties": {
      "zftBwmP": { "type": "string", "description": "transactionId :zftBwmP: 交易ID" }
    }
  },
  "FailureResult": {
    "type": "object",
    "properties": {
      "sGslfjaU": { "type": "string", "description": "errorCode :sGslfjaU: 错误码" },
      "hbbrHaLsw": { "type": "string", "description": "errorMsg :hbbrHaLsw: 错误信息" }
    }
  }
}
```

Generated output:
```kotlin
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * PaymentResult
 * Polymorphic type with 2 variants
 */
@Keep
sealed class PaymentResult {
    /**
     * SuccessResult
     */
    @Keep
    data class SuccessResult(
        /** 交易ID (transactionId) */
        @SerializedName("zftBwmP")
        var zftBwmP: String? = null,
    ) : PaymentResult()

    /**
     * FailureResult
     */
    @Keep
    data class FailureResult(
        /** 错误码 (errorCode) */
        @SerializedName("sGslfjaU")
        var sGslfjaU: String? = null,

        /** 错误信息 (errorMsg) */
        @SerializedName("hbbrHaLsw")
        var hbbrHaLsw: String? = null,
    ) : PaymentResult()
}
```
