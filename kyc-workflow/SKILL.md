---
name: kyc-workflow
version: "4.0.0"
description: >
  Android/Kotlin 项目通用 KYC 进件流程技能。
  交互式问询模式: 7问确认需求 → 逐步骤展示流程摘要 → 适配项目架构生成代码。
  验证模式: 6步检查清单审计存量KYC实现质量。
---

# KYC 进件流程 Skill

Android/Kotlin 项目通用 KYC（Know Your Customer）技能。**不绑定特定项目的字段名**（字段语义通过接口 KDoc 自适应），但技术栈固定为 **Android/Kotlin**（ViewModel + Retrofit + Coroutines）。

## Mode Activation

| Mode | 触发关键词 | 说明 |
|------|-----------|------|
| **Setup Mode**（默认） | "实现KYC", "搭建进件流程", "build kyc", "KYC流程", "进件流程", "身份认证" | 7问问询 → 逐步骤展示流程摘要 → 用户确认 → 适配项目生成代码 |
| **Verify Mode** | "验证KYC", "检查进件流程", "verify kyc", "审查KYC", "KYC审计" | 对存量项目跑 6 步检查清单，输出 PASS/FAIL/WARN + 修复建议 |

---

## Setup Mode

### 触发后的工作流程

```
用户触发 Setup Mode
    ↓
Phase 1: 7问问询（收集需求）
    ↓
Phase 2: 输出整体计划摘要
    ↓
Phase 3: 逐步骤执行（对每个步骤: 读 → 讲 → 确认 → 写）
    ↓
Phase 4: 输出完成报告
```

### Phase 1: 7问问询

**必须按顺序逐一询问**，每问等待用户回答后再继续。不要跳过任何问题。

```
1️⃣ 项目UI框架？
   → [Jetpack Compose] / [XML View（Fragment/Activity）] / [Compose + View 混合]

2️⃣ 要实现哪些KYC步骤？
   → [全部6步（默认）] / [指定步骤] / [单个步骤]
   可选步骤: ①个人信息 ②联系人 ③身份证 ④银行卡 ⑤信用信息(可选) ⑥人脸识别

3️⃣ 字段配置策略？
   → [硬编码方案A] — 选项极少变动，快速上线，选项写死在 LocalOptionDataSource
   → [接口驱动方案B] — 运营需要灵活调整，通过字典接口动态获取选项
   → [混用（推荐）] — 稳定字段（性别、婚姻）硬编码 + 可能变动字段（收入、工作）走接口

4️⃣ 后端接口状态？
   → [全部就绪] — 接口和 DTO 已定义，直接复用
   → [部分就绪] — 核心接口已有，部分需要占位
   → [完全未就绪] — 所有接口都需要占位，先完成流程框架代码

5️⃣ 人脸识别渠道？
   → [仅自研（最小化）] — CameraX 拍照 + 后端比对，无三方 SDK 依赖
   → [自研 + Face++ 三方SDK] — 含渠道查询 + BizToken + SDK 调起 + 降级兜底

6️⃣ 银行卡是否需要外部添加模式？
   → [是] — 支持 KYC 步骤内 + 独立入口（如"我的银行卡"页面）两种调用方式
   → [否] — 仅 KYC 步骤内使用

7️⃣ 人脸识别是否需要非KYC模式？
   → [是] — 支持 KYC 流程内 + 安全认证等独立调用，结果通过 savedStateHandle 回传
   → [否] — 仅 KYC 流程内使用
```

### Phase 2: 输出整体计划摘要

问询完毕后，AI 必须输出整体计划摘要：

```
## KYC 实现计划

| 项目 | 你的选择 |
|------|---------|
| UI框架 | Compose |
| 步骤范围 | 全部6步（含信用信息） |
| 字段策略 | 混用 |
| 接口状态 | 部分就绪，需要占位 |
| 人脸渠道 | 自研 + Face++ |
| 银行卡模式 | 含外部添加 |
| 人脸模式 | 含非KYC |

将实现以下文件: [列出所有将创建/修改的文件]
```

用户确认计划后进入 Phase 3。

### Phase 3: 逐步骤生成规则

对每个步骤，AI 必须严格执行 **读 → 讲 → 确认 → 写** 四步：

#### 3a. 读
读取对应文件：
- `references/step-N-*.md` — 该步骤的业务逻辑说明
- `templates/step-N-*.kt` — 该步骤的参考代码模板
- 同时读取相关的通用主题文件（如字典系统、缓存机制等）

#### 3b. 讲（输出业务流程摘要）

**必须**向用户展示该步骤的以下内容：

```
## Step N: [步骤名称] — 业务流程摘要

### 用户操作序列
1. 用户看到 [页面初始状态]
2. 用户点击 [操作] → 触发 [交互]
3. [条件分支] → [不同行为]

### 接口调用时序
| 时机 | 接口 | 参数 | 用途 |
|------|------|------|------|
| 页面初始化 | acpElementInfo(step=N) | step编号 | 查询该步骤字段配置 |
| 用户提交 | submitAcqData | step=N + items列表 | 提交表单数据 |
| ... | ... | ... | ... |

### 关键交互规则
- ⚠️ 挽留弹窗触发时机: [何时拦截返回键]
- ⚠️ 确认弹窗触发时机: [提交前是否需要二次确认]
- ⚠️ 级联逻辑: [哪些字段间存在依赖关系，如何联动]
- ⚠️ 接口调用顺序: [先调什么、再调什么、为什么]
```

#### 3c. 确认
等待用户确认流程理解正确。用户可以：
- "确认，继续" → 进入写作阶段
- "调整: [具体修改]" → AI 修正流程理解后再次确认

#### 3d. 写

1. **扫描目标项目**：找到项目的 API Service 文件、ViewModel 基类、导航方式、网络请求封装
2. **结合模板逻辑**：将模板中的业务逻辑适配到项目架构中
3. **生成代码**：写入项目文件，保持与项目现有代码风格一致

### Phase 4: 输出完成报告

所有步骤完成后，输出：

```
## KYC 实现完成报告

### 已创建/修改的文件
- [ ] path/to/file1.kt — 描述
- [ ] path/to/file2.kt — 描述

### 待手动处理的项
- [ ] 接口占位: [哪些接口需要后端就绪后替换]
- [ ] 字典配置: [需要配置哪些字典 type]
- [ ] 图片压缩: [需要确认目标项目的压缩参数]

### 推荐的下一步
- 运行验证: "验证KYC" 或 "verify kyc"
- 集成测试: [建议的测试路径]
```

---

## Verify Mode

### 触发后的工作流程

```
用户触发 Verify Mode
    ↓
扫描目标项目 KYC 相关代码
    ↓
按 6 步检查清单逐项检查
    ↓
输出结果: [PASS] / [FAIL] / [WARN] + 修复建议
```

### 6 步检查清单

详见 `references/verify.md`，概要如下：

| # | 检查项 | 方法 |
|---|--------|------|
| 1 | 接口存在性 | grep 关键接口名 (submitAcqData/ocrVerify/faceCompare/...) |
| 2 | 缓存模型安全 | 检查缓存 data class 字段是否全可空 + try/catch |
| 3 | 图片处理 | 检查 ImageCompressor 是否存在 + IO线程 |
| 4 | UI组件复用 | 检查是否有通用 OptionSelectDialog vs 每字段独立 Dialog |
| 5 | 挽留弹窗 | 检查每个步骤是否拦截 BackHandler/onBackPressed |
| 6 | 编译验证 | `./gradlew compileDebugKotlin` |

每步输出 `[PASS]` / `[FAIL]` / `[WARN]` + 详情 + 修复建议（不自动修复）。

---

## Reference Files

### 流程步骤
| 主题 | 文件 | 内容 |
|------|------|------|
| 流程概述 | `references/flow-overview.md` | 标准5-6步流程、步骤可选性表、网络请求统一处理机制 |
| Step 1 | `references/step-1-personal-info.md` | 个人信息：字段语义、链式弹窗、省市级联、State/Action/Event/VM |
| Step 2 | `references/step-2-contact-info.md` | 联系人：多卡片管理、通讯录集成、手动输入降级 |
| Step 3 | `references/step-3-identity.md` | 身份证：OCR流程、确认弹窗、CameraX拍照、图片压缩 |
| Step 4 | `references/step-4-bank-card.md` | 银行卡：KYC/外部双模式、搜索分组银行选择、账号长度校验 |
| Step 5 | `references/step-5-credit-info.md` | 信用信息：可选步骤、级联字段、条件展示逻辑 |
| Step 6 | `references/step-6-face-recognition.md` | 人脸识别：自研/Face++双渠道、BizToken、降级兜底、非KYC模式 |

### 通用主题
| 主题 | 文件 | 内容 |
|------|------|------|
| 字典系统 | `references/dictionary-system.md` | 三级缓存（内存→本地→网络）、测试模式、硬编码回退、字段列表 |
| 缓存机制 | `references/cache-mechanism.md` | 表单缓存（24h TTL + 账号隔离）、序列化安全（全可空原则）、防御式读写 |
| 图片处理 | `references/image-processing.md` | 压缩策略（两步解码 + OOM保护 + 降分辨率兜底）、拍照/图库、Coil/Glide回显、风险表 |
| UI组件 | `references/ui-components.md` | 通用组件封装原则、组件清单、选择器/确认弹窗/挽留弹窗/输入框规范、禁止做法 |
| API模式 | `references/api-patterns.md` | 9类接口占位代码、自动扫描策略、语义匹配规则、进件提交通用模式 |
| 字段配置 | `references/field-config-strategy.md` | 方案A(硬编码) vs 方案B(接口驱动) 对比、混用推荐、判断标准 |
| 设计原则 | `references/design-principles.md` | 21条关键设计原则 + 29项实现检查清单 |

### 验证与诊断
| 主题 | 文件 | 内容 |
|------|------|------|
| 常见陷阱 | `references/traps.md` | 10-12条常见陷阱（症状→诊断→修复映射表） |
| 验证流程 | `references/verify.md` | 6步验证流程（检查项 + 命令 + 预期结果 + 修复建议） |

---

## Templates

所有模板位于 `templates/` 目录。**模板是业务流程教学材料，不是占位符替换生成器**——AI 读取后理解业务规则和交互时机，然后结合目标项目架构适配生成代码。

| 模板 | 内容 | 教学重点 |
|------|------|---------|
| `templates/base-viewmodel.kt` | BaseViewModel 最小实现 | launchRequest 统一处理: Loading + 异常分类 + 线程切换 |
| `templates/step-1-personal.kt` | Step 1 State+Action+Event+ViewModel | 链式弹窗: 选择后自动打开下一个空字段 |
| `templates/step-2-contact.kt` | Step 2 State+Action+Event+ViewModel | 通讯录集成: pick→回填→失败降级手动输入 |
| `templates/step-3-identity.kt` | Step 3 State+Action+Event+ViewModel | OCR流程: 拍照→压缩→上传→回填→确认弹窗 |
| `templates/step-4-bank.kt` | Step 4 State+Action+Event+ViewModel | 双模式: isExternal 路由参数 → 不同接口 + 不同行为 |
| `templates/step-5-credit.kt` | Step 5 State+Action+Event+ViewModel | 级联清空: 上游选0 → 清空下游字段 |
| `templates/step-6-face.kt` | Step 6 State+Action+Event+ViewModel | 渠道切换: 查询→Face++→获取Token→SDK→降级→自研 |
| `templates/dict-manager.kt` | 字典管理器（方案B） | 三级缓存: ConcurrentHashMap → DataStore(24h TTL) → 网络 |
| `templates/image-compressor.kt` | 图片压缩工具 | OOM保护: bounds探测 → ByteArray解码 → 内存预算 → 降分辨率兜底 |
| `templates/option-select-dialog.kt` | 通用单选弹窗 | BRVAH列表 + 预选中 + 动态高度（≤55%屏幕） |
| `templates/confirm-dialog.kt` | 通用确认弹窗 | 只读表单 + 确认/修改双按钮 + 不可点外部关闭 |
| `templates/api-placeholders.kt` | 9类接口占位定义 | 混淆字段名 + KDoc语义标注 + @Multipart人脸比对 |
| `templates/cache-model.kt` | 缓存数据模型 | 全可空字段 + 防御式读写 + try/catch 兜底 |

---

## 核心原则速查

1. **接口驱动 UI**：先读接口 KDoc 理解字段语义，再对应页面功能
2. **字段语义优先**：接口字段名通常是混淆的，禁止猜测
3. **双层缓存**：字典缓存（选项数据）和表单缓存（用户输入）分离
4. **条件展示**：信用信息字段存在级联依赖，根据用户选择动态展示/隐藏
5. **渠道适配**：人脸识别查询后端配置决定技术方案，不自前端写死
6. **挽留机制**：每个页面拦截物理返回，弹出挽留弹窗降低流失
7. **确认机制**：身份证、银行卡关键信息提交前展示确认弹窗
8. **OCR 辅助**：身份证支持 OCR 自动回填，但允许用户手动修改
9. **通讯录集成**：联系人页面优先系统通讯录，失败降级手动输入
10. **控件复用**：同类型字段用统一组件，禁止每字段单独实现 Dialog
11. **三方可选**：人脸 Face++ 为可选集成，即使集成也必须有自研兜底
12. **图片压缩必做**：所有上传图片必须压缩（≤500KB），IO线程执行，含 OOM 保护
13. **缓存全可空**：所有缓存数据结构字段必须可空 + 默认值 null + 反序列化 try/catch
