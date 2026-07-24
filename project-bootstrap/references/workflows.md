# Project Bootstrap — 技能编排工作流

## 编排原则

1. **严格顺序**：Phase 1→2→3→4，不可跳跃（除非已有产物）
2. **产物检查**：每个 Phase 结束前检查产物，缺失则阻塞
3. **向前回溯**：如果某 Phase 失败，检查是否上游缺失依赖
4. **显式过渡**：AI 必须明确告知用户"Phase X 完成，进入 Phase Y"

---

## Phase 1: API 层生成

### 激活条件
用户提供了 Swagger/OpenAPI URL 或 JSON 文档，或项目需要网络接口定义。

### 激活技能
`swagger-to-kotlin`

### AI 执行步骤
```
1. 确认 Swagger 文档来源（URL / 本地文件）
2. 调用 swagger-to-kotlin：
   - Phase 1 Project Detection → 自动探测包名/响应包装/序列化框架
   - Phase 2 Fetch & Parse → 拉取解析 Swagger
   - Phase 3 Generate → 生成 Bean + API 接口
   - Phase 4 Report → 输出映射表，确认后写入文件
3. 检查生成文件是否可编译
```

### 产物清单
- [ ] `*Beans.kt` — 所有请求/响应数据类（字段全可空）
- [ ] `*Api.kt` — Retrofit 接口（suspend 函数 + ApiBaseResponseBean<T> 返回类型）
- [ ] 如有枚举 → `*Enums.kt`
- [ ] 如有 oneOf/anyOf → sealed class

### 过渡到 Phase 2 的条件
- ✅ 所有 Bean 和 API 接口已生成
- ✅ 编译无错误
- ✅ Swagger↔Code 映射表已输出

### 常见阻塞
| 阻塞 | 解决 |
|------|------|
| 无 Swagger URL | 询问用户提供 URL |
| Swagger 无法访问 | 尝试本地文件或 VPN |
| 响应包装类未找到 | 手动指定 `--response-wrapper` |
| 中文定义名无法翻译 | 标记为需手动命名 |

---

## Phase 2: 网络框架封装

### 激活条件
Phase 1 完成，或项目已有 API 接口但缺少网络层。

### AI 执行步骤
```
1. 检查现有网络层：
   - Retrofit 实例是否存在（搜索 Retrofit.Builder）
   - OkHttp 客户端是否配置（拦截器链）
   - 响应包装类是否就绪（ApiBaseResponseBean）
   - BaseUrl 配置方式（BuildConfig / 动态域名）
2. 缺失则激活 **android-project-template** 技能的 **Inject Mode**：
   - 自动检测当前网络层状态，生成缺失的 OkHttpClient/Retrofit/拦截器
   - 同步检查 BaseViewModel/BaseActivity/BaseApplication 是否缺失，一并补全
   - 确保输出满足本 Phase 的产物清单
   - 具体实现参考 android-project-template 的 SKILL.md 和 templates/
3. 验证网络层可调用（至少一个编译通过的接口）
```

### 产物清单
- [ ] OkHttpClient 单例（含拦截器链）
- [ ] Retrofit 实例（含 GsonConverterFactory）
- [ ] ApiBaseResponseBean<T>（code + msg + data）
- [ ] BaseUrl 配置（BuildConfig / SharedPreferences 动态域名）
- [ ] 统一异常处理（401→登录 / 网络异常→Toast / 业务错误→msg）

### 过渡到 Phase 3 的条件
- ✅ 网络层可编译
- ✅ 至少一个 API 接口可正常调用（可在 Preview/测试中验证）
- ✅ 异常处理覆盖 Auth/Network/Business 三种类型

### 常见阻塞
| 阻塞 | 解决 |
|------|------|
| 无 BaseUrl | 从 BuildConfig 或接口文档获取 |
| 加密 Key 未知 | 搜索项目中现有 key 或询问 |
| 拦截器链不完整 | 按技能文档补全 |

---

## Phase 3: UI 技术选型

### 激活条件
Phase 2 完成，需要构建用户界面。

### 技术选型判断
```
检查 build.gradle.kts 依赖：
  ├── 有 androidx.compose 依赖 → Compose 路线
  │   └── 激活 android-compose-expert
  │       - 应用 stateful-wrapper + stateless-content 模式
  │       - 为每个页面添加 @Preview
  │       - 提取子 composable 做 UI 分层
  │
  └── 无 Compose 依赖，有 appcompat/fragment → View 路线
      └── 激活 android-viewsystem-foundations
          - Fragment + ViewBinding 模式
          - ConstraintLayout 布局
          - RecyclerView Adapter
```

### AI 执行步骤（Compose 路线）
```
1. 激活 android-compose-expert
2. 分析页面需求：
   - 需要哪些页面？（列表/详情/表单/WebView）
   - 每个页面的 UiState data class
   - 页面间导航关系
3. 按 Refactoring Mode 构建：
   - Stateful wrapper（ViewModel 集成层）
   - Stateless Content（纯 UI + 回调）
   - 子 composable 提取
   - @Preview + Mock 数据工厂
```

### 产物清单
- [ ] 页面 UiState data class（全可空字段）
- [ ] Stateful wrapper composable（每个页面）
- [ ] Stateless Content composable（每个页面）
- [ ] @Preview 函数（每个 content，覆盖正常/加载/错误/空状态）
- [ ] Mock 数据工厂（preview* 命名）

### 过渡到 Phase 4 的条件
- ✅ 所有页面骨架可编译
- ✅ 至少一个 Preview 可渲染
- ✅ UiState + Content 模式已应用

---

## Phase 4: 业务场景实现

### 激活条件
Phase 3 完成，页面骨架就绪，需要填充具体业务逻辑。

### 技能路由规则

AI 必须扫描 Phase 1 生成的 API 接口，按**接口语义关键词**自动匹配业务技能：

| 接口路径/方法名含有关键词 | 激活技能 | 说明 |
|---|---|---|
| `kyc`, `acq`, `submitAcq`, `face`, `ocr`, `dict`, `个人信息`, `身份`, `人脸`, `进件`, `联系人`, `银行卡`, `信用` | `kyc-workflow` | KYC 5-6 步标准流程 |
| `order`, `repay`, `extension`, `bill`, `loan`, `borrow`, `订单`, `还款`, `展期`, `账单` | `order-repay-extension-flow` | 订单详情+还款+展期页面 |
| `login`, `register`, `auth`, `verify`, `sms`, `otp`, `登录`, `注册`, `验证码` | （通用登录流程，按 Compose 模式直接实现） | |
| `product`, `home`, `banner`, `quota`, `产品`, `首页`, `额度` | （通用列表/卡片页面，按 Compose 模式实现） | |
| 当项目需要多渠道打包时 | `android-multi-flavor-google` | 3 flavor + Google 隔离 |

### AI 执行步骤（以 KYC 为例）
```
1. 激活 kyc-workflow
2. 扫描 Phase 1 生成的接口 → 匹配 KYC 语义
3. 按 5-6 步标准流程逐一实现：
   - Step 1: 个人信息页面 + ViewModel
   - Step 2: 联系人信息页面 + ViewModel
   - Step 3: 身份证认证页面 + OCR + ViewModel
   - Step 4: 银行卡信息页面 + ViewModel
   - Step 5: 信用信息页面（可选）+ ViewModel
   - Step 6: 人脸识别页面 + ViewModel
4. 每个页面应用 Phase 3 的 Compose 模式
5. 实现字典系统（三级缓存）和表单缓存
```

### 产物清单
- [ ] 所有业务页面的 ViewModel + State + Content
- [ ] 字典系统缓存（如适用）
- [ ] 表单本地缓存（如适用）
- [ ] 页面间导航路由

### 最终验证
```
1. 激活 test-case-audit → 审计测试用例覆盖
2. 所有页面可编译
3. 所有 Compose Content 有 Preview
```

---

## 进度追踪模板

每个 Phase 完成后，AI 输出：

```
## 开发进度

| Phase | 状态 | 技能 | 产物 |
|-------|------|------|------|
| 1. API 层 | ✅ 完成 | swagger-to-kotlin | 3 个 Bean + 1 个 Api 接口 |
| 2. 网络框架 | ✅ 完成 | - | OkHttp + Retrofit 就绪 |
| 3. UI 选型 | 🔄 进行中 | android-compose-expert | UiState 已定义，Content 构建中 |
| 4. 业务场景 | ⏳ 待处理 | kyc-workflow | - |

下一步：继续 Phase 3 — 为首页构建 HomeContent + Preview
```

---

## 快速启动命令

```bash
# 方式 1：用户口头触发
"搭建新项目，Swagger 地址是 http://xxx/swagger.json"

# 方式 2：显式激活 orchestrator
/project-bootstrap http://xxx/swagger.json

# 方式 3：分步推进
"生成 API" → "搭网络层" → "做 Compose 页面" → "实现 KYC 流程"
```

---

## 与 CLAUDE.md 的关系

- `CLAUDE.md` — 定义**静态**工作流规则（项目级，session_start 加载）
- `project-bootstrap` — 定义**动态**编排逻辑（按需激活，跨项目复用）

两者互补：CLAUDE.md 告诉 AI "有哪些技能可以串联"，project-bootstrap 告诉 AI "如何串联、何时过渡、产物检查什么"。
