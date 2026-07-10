---
name: swagger-to-kotlin
description: >
  Given a Swagger/OpenAPI documentation URL, automatically generate Kotlin Retrofit API interfaces
  and request/response Bean classes. Supports Swagger 2.0 and OpenAPI 3.x. Auto-detects project
  structure (package name, response wrapper, naming conventions, serialization framework).

  AUTO-TRIGGER: Any URL containing "swagger", "openapi", "api-doc", "v2/api-docs" or matching
  patterns like "*/swagger*.json", "*/api-docs*" will automatically activate this skill.

  English triggers: "swagger-to-kotlin", "generate Retrofit interface", "generate API beans",
  "generate network code from API docs", "swagger to kotlin", "openapi to kotlin",
  "generate API interface", "create Retrofit API", "generate data class from swagger",
  "swagger codegen", "openapi codegen".

  Chinese triggers: 创建接口, 生成接口, 生成API, 创建API, 根据Swagger生成, 根据文档生成接口,
  接口代码生成, API代码生成, 生成请求类, 生成响应类, 生成Bean, 生成数据类, 从接口文档生成,
  Swagger文档生成, OpenAPI生成, 网络层代码生成, 生成Retrofit接口, 创建网络接口,
  接口定义生成, 根据后端文档生成, 生成网络层代码, 根据接口文档创建.
version: 1.2.0
---

# Swagger → Kotlin Retrofit Code Generator

Generates Kotlin Retrofit API interfaces and data classes from Swagger/OpenAPI documentation.
**Non-destructive** — only generates new files, never modifies existing code.

## Core Principles

1. **Auto-detect before generate** — always probe the project first to understand its conventions
2. **Generate, don't modify** — output new files, let the developer decide where to integrate
3. **Semantic by default** — use readable class/method names unless the project uses obfuscation
4. **Transparent matching** — always output a Swagger ↔ Code mapping table so the developer can verify

## Workflow

### Phase 1: Project Detection

Before fetching Swagger, auto-detect the project's network layer conventions:

1. **Retrofit interface location** — search for files containing `interface.*@GET|@POST` or `retrofit2.http.GET|POST`
2. **Response wrapper class** — search for `data class.*ResponseBean` or `class.*ApiBaseResponse` or generic wrappers with `code`, `msg`, `data` fields
3. **Package name** — from existing Retrofit interface or `AndroidManifest.xml`
4. **Serialization framework** — check `build.gradle.kts` for `kotlinx-serialization`, `gson`, or `moshi`
5. **Naming convention** — analyze class names in the bean directory: semantic (SubmitPreOrderReq) vs obfuscated (ZojofoigasniHeauritiren)
6. **Base URL source** — find `Retrofit.Builder().baseUrl()` or `BuildConfig.*url` references
7. **Existing OkHttp interceptors** — to understand the interceptor chain (for `@ApiDescription` logging integration)

Output a summary of detected conventions for the user to confirm before proceeding.

### Phase 2: Fetch & Parse Swagger

1. Fetch the Swagger JSON from the provided URL
2. Detect version:
   - **Swagger 2.0**: `"swagger": "2.0"` → paths at `data.paths`, schemas at `data.definitions`
   - **OpenAPI 3.x**: `"openapi": "3.x.x"` → paths at `data.paths`, schemas at `data.components.schemas`
3. Extract all endpoints: path + HTTP method + summary + operationId + parameters + responses + tags
4. Extract all schemas: definition name + properties + required fields + nested refs

Output statistics: total endpoints, total schemas, tag distribution, response wrapper pattern.

### Phase 3: Generate Code

Generate code in this order (dependencies first):

1. **Enum classes** — generate all enums first (other classes may reference them)
2. **Sealed classes** — generate polymorphic types (oneOf/anyOf/discriminator)
3. **Bean classes** (from definitions/schemas) — generate all data classes
4. **API interface** (from paths) — generate Retrofit interface methods
5. **`@ApiDescription` annotation** — if `--with-api-desc` is true and doesn't exist yet

**All generated classes** (data class, enum class, sealed class) must include:
- `@Keep` annotation (from `androidx.annotation`)
- `@SerializedName("fieldName")` on every field (Gson default)

**Field nullability rule (applied globally):**
- ALL fields → `var fieldName: Type? = null` regardless of `required` array
- Unless `--strict-required` is set and field IS in `required` → `val fieldName: Type`

**File splitting strategy:**
- Group by Swagger `tags` (e.g., "订单类api" → `OrderApi.kt` + `OrderBeans.kt`)
- Shared types used by multiple groups → `CommonBeans.kt`
- Enum classes with broad usage → `CommonEnums.kt`
- Default output directory: same package as existing Retrofit interface, or `{basePackage}.api.gen/`

Refer to `references/type-mapping.md` for the complete type conversion table.
Refer to `references/templates.md` for code generation templates.

### Phase 4: Report & Confirm

Output before writing files:

1. **Generation Summary**: how many files, classes, methods will be generated
2. **Swagger ↔ Code Mapping Table**: each Swagger endpoint → generated method + request/response types
3. **Low-confidence Items**: Chinese definition names, Void responses, endpoints with missing schemas
4. **Ask user to confirm**, then write files

---

## Key Rules

### Response Type Extraction

Swagger responses often use a generic wrapper pattern. The skill MUST detect and handle this:

**Pattern 1: «T» notation (Chinese/Java-style generics)**
```
"公共响应体«Void»"              → wrapper class + Void (use Any)
"公共响应体«注册、登录完成返回类»"  → wrapper class + LoginResult
"ApiResponse«UserData»"          → wrapper class + UserData
```
Regex: `(.+?)«(.+?)»` — capture group 1 is wrapper name, group 2 is inner type.

**Pattern 2: Inline code/msg/data fields**
If the 200 response schema has `{ code, msg, data }` fields where `data` is the actual payload:
```json
{ "code": { "type": "integer" }, "msg": { "type": "string" }, "data": { "$ref": "#/definitions/Foo" } }
```
→ This is a response wrapper, use `ApiBaseResponseBean<Foo>`.

**Pattern 3: Direct response (no wrapper)**
If the response schema is directly a `$ref` to a definition:
→ Use `ApiBaseResponseBean<ResponseType>`.

**Void detection:** If the inner type is "Void", use Kotlin's `Any` (or `Nothing?` for strict mode).

See `references/type-mapping.md` for the full response resolution algorithm.

### Field Description Parsing

Swagger field descriptions often contain semantic mappings in the format:
```
"semanticName :obfuscatedName: 中文描述"
```

Parse this pattern with regex: `^(\S+)\s*:(\S+):\s*(.*)$`
- Group 1: semantic name → use in KDoc comment
- Group 2: obfuscated name → use as field name (if project uses obfuscation)
- Group 3: Chinese description → use in KDoc comment

Example:
```
"bankAccountId :rFz6Mre0cvgkNLK: 收款银行账号ID"
→ /** 收款银行账号ID (bankAccountId) */
   var rFz6Mre0cvgkNLK: Long? = null
```

### Naming Conventions

Method names (priority order):
1. Parse `operationId` → strip `UsingPOST`/`UsingGET`/`UsingDELETE`/`UsingPUT` suffix
2. If operationId is missing, derive from last path segment + HTTP method
3. If Chinese definition title, translate to English using context from operationId

Class names (priority order):
1. Definition `title` if it's in English (e.g., "SubmitPreOrderReq")
2. Definition key from `$ref` if it's in English
3. Chinese definition title → translate to English using operationId context
4. Fallback: mark as needing manual naming

### Field Nullability (CRITICAL)

**By default, ALL fields are generated as nullable with `= null` default:**
```kotlin
var fieldName: Type? = null
```

This is the safest default because:
- Backend may omit fields that are logically "required" in the Swagger schema
- Obfuscated APIs often don't accurately declare `required` arrays
- Nullable fields prevent Gson deserialization crashes on missing JSON keys

**Exception (`--strict-required` mode only):**
- If `--strict-required` is set AND a field is in the schema's `required` array → `val fieldName: Type`
- This mode should only be used when the Swagger schema is known to be 100% accurate

### Annotations on Generated Classes

All generated data classes, enum classes, and sealed classes MUST include:
- `@Keep` — prevents ProGuard/R8 from stripping the class (essential for reflection-based serialization)
- `@SerializedName("obfuscatedName")` — for Gson serialization (default), maps the obfuscated field name

### Enum Types

When the Swagger schema defines `enum` with string/number values:
- Generate a Kotlin `enum class` with `@Keep`
- Use `var value: String` (or `Int`) as the constructor parameter
- Add `@SerializedName` for Gson compatibility if the enum values need custom serialization
- This is preferred over generating Int/String fields with comment-enumerated possible values

### Sealed Class / Polymorphic Types (OpenAPI 3.x)

When the schema uses `oneOf`, `anyOf`, or `discriminator`:
- Generate a `sealed class` with `@Keep`
- Each variant becomes a `data class` extending the sealed class
- All variant fields are nullable by default (following the global nullable rule)
- Use `@SerializedName` for discriminator field mapping

### Map Types (additionalProperties)

When the schema uses `additionalProperties`:
- `additionalProperties: { type: string }` → `Map<String, String?>`
- `additionalProperties: { $ref: ... }` → `Map<String, RefType?>`

### allOf Composition

When the schema uses `allOf: [Base, Extension]`:
1. Resolve all `$ref` references in the `allOf` array
2. Merge properties from all referenced schemas into a single flat data class
3. Fields from all sources follow the global nullable rule

### Query Parameters (GET requests)

GET endpoints may have `@Query` parameters:
```json
{ "in": "query", "name": "hu81N7NFyYuSaHLBK", "type": "string", "description": "key :hu81N7NFyYuSaHLBK: key标识" }
```
→ `@Query("hu81N7NFyYuSaHLBK") paramName: String`

### Multipart Uploads

Endpoints with `consumes: ["multipart/form-data"]`:
- Use `@Multipart` annotation
- Parameters become `@Part paramName: MultipartBody.Part`
- The Swagger `parameters` with `in: "formData"` map to `@Part` params

---

## Options Reference

| Option | Values | Default | Description |
|--------|--------|---------|-------------|
| `--pkg` | package name | auto-detect | Output package for generated files |
| `--out` | directory path | auto-detect | Output directory |
| `--response-wrapper` | class name | auto-detect | Response wrapper class (e.g., `ApiBaseResponseBean`) |
| `--tags` | comma-separated | all | Only generate for specific Swagger tags |
| `--naming` | `semantic`, `obfuscated` | `semantic` | Class/method naming style |
| `--serialization` | `gson`, `kotlinx` | `gson` | Serialization framework for annotations. `gson` → `@SerializedName`, `kotlinx` → `@SerialName` |
| `--nullable-all` | `true`, `false` | `true` | Generate ALL fields as `var field: Type? = null`. When `false`, falls back to `required`-based nullability |
| `--strict-required` | `true`, `false` | `false` | Honor `required` array: fields in `required` become non-nullable `val`. Only effective when `--nullable-all=false` |
| `--with-api-desc` | `true`, `false` | `true` | Generate `@ApiDescription` annotations |
| `--split-by-tag` | `true`, `false` | `true` | Split API interfaces by Swagger tag |

---

## References

- `references/type-mapping.md` — Complete Swagger → Kotlin type conversion table and response resolution
- `references/templates.md` — Code generation templates for all output file types
