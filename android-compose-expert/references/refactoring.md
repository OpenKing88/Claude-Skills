# Compose 页面重构工作流

系统性将 Compose 页面重构为 **stateful-wrapper + stateless-content** 模式，实现状态与 UI 隔离、分层清晰、可预览。

---

## Phase 1: 分析现有结构

重构前必须先理解目标页面的组成。逐个检查以下维度：

### 1.1 识别 State 变量

扫描所有 `mutableStateOf`、`collectAsState*`、`derivedStateOf`：

```kotlin
// 这些是 state — 需要提升到 wrapper 层
var selectedIndex by remember { mutableStateOf(0) }
val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
val totals by remember { derivedStateOf { ... } }
```

记录：state 名称、类型、来源（ViewModel vs local）。

### 1.2 识别 UI 区块

将页面按视觉区域划分：

```
┌─────────────────────┐
│  TopBar / Header     │  ← 独立 composable
├─────────────────────┤
│  Main Content        │  ← 可能包含多个子区块
│  ├─ Section A        │
│  ├─ Section B        │
│  └─ Section C        │
├─────────────────────┤
│  Bottom / Footer     │  ← 独立 composable
├─────────────────────┤
│  Dialogs / Overlays  │  ← 独立 composable
└─────────────────────┘
```

**规则：** 每个视觉区块 → 一个提取的 composable 函数。

### 1.3 识别副作用

```kotlin
LaunchedEffect(key) { ... }      // 生命周期相关
DisposableEffect(key) { ... }    // 资源清理
SideEffect { ... }               // 每次重组后执行
rememberCoroutineScope()         // 手动协程作用域
```

记录：触发 key、用途、是否需要在 wrapper 还是 Content 中。

### 1.4 识别事件/回调

```
onClick, onValueChange, onAction → lambda 回调参数
ViewModel::method              → 保留在 wrapper 层
Navigation::navigate           → 保留在 wrapper 层或作为回调传入
```

---

## Phase 2: 定义数据结构

### 2.1 创建 UiState data class

将分散的 state 变量收敛为一个数据结构：

```kotlin
/**
 * [PageName] UI 状态
 * Auto-generated during refactoring
 */
data class HomeUiState(
    var isLoading: Boolean = true,
    var userInfo: UserInfoData? = null,
    var banners: List<BannerData?>? = null,
    var errorMessage: String? = null,
    var selectedTab: Int = 0,
)
```

**命名约定：** `{PageName}UiState` 或 `{PageName}Data`

**字段规则（对齐项目约定）：**
- 所有字段：`var field: Type? = null` 或带默认值
- 布尔值可用 `var isLoading: Boolean = false`（非可空 + 默认值）
- 列表：`var items: List<ItemData?>? = null`

### 2.2 定义 Callback 类型（可选）

如果回调很多，可以提取为数据类或使用 typealias：

```kotlin
// 简单场景：直接作为 lambda 参数
// 复杂场景：提取为 sealed class
sealed class HomeAction {
    data class OnBannerClick(val banner: BannerData) : HomeAction()
    data class OnProductClick(val product: ProductData) : HomeAction()
    object OnRefresh : HomeAction()
}
```

**判断标准：** 超过 5 个回调 → 提取为 sealed class；少于 5 个 → 直接作为 lambda。

### 2.3 创建 Mock 数据工厂

为每个 UiState 和子数据类创建 preview 用的工厂函数：

```kotlin
// 命名模式: preview{DataClassName}()
fun previewHomeUiState() = HomeUiState(
    isLoading = false,
    userInfo = previewUserInfo(),
    banners = listOf(previewBanner("1"), previewBanner("2")),
)

fun previewUserInfo() = UserInfoData(
    var name: String? = "张三",
    var avatar: String? = null,
)

fun previewBanner(id: String = "1") = BannerData(
    var id: String? = id,
    var title: String? = "限时优惠",
    var imageUrl: String? = null,
)
```

**规则：**
- 每个 data class 都要有对应的 `preview*()` 工厂函数
- Mock 数据要真实可信，不要用 `"test"`、`"foo"` 等占位符
- 至少覆盖：正常状态、空/null 状态、长文本/边界状态

---

## Phase 3: 执行重构

### Step 1: 创建 stateless Content composable

从原页面 composable 中提取纯 UI 部分：

```kotlin
// ✅ 原页面 (stateful wrapper)
@Composable
fun HomePage(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigate: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 副作用保留在 wrapper 层
    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    HomePageContent(
        uiState = uiState,
        onBannerClick = { viewModel.onBannerClick(it) },
        onRefresh = { viewModel.loadData() },
        onNavigate = onNavigate,
    )
}

// ✅ 提取的 stateless Content
@Composable
fun HomePageContent(
    uiState: HomeUiState,
    onBannerClick: (BannerData) -> Unit,
    onRefresh: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 纯 UI — 只从 uiState 读取，只通过回调通知
    Scaffold(modifier = modifier) {
        when {
            uiState.isLoading -> LoadingContent()
            uiState.errorMessage != null -> ErrorContent(
                message = uiState.errorMessage!!,
                onRetry = onRefresh,
            )
            else -> MainContent(
                banners = uiState.banners,
                onBannerClick = onBannerClick,
            )
        }
    }
}
```

**关键原则：**
- Content 接收 `Modifier = Modifier` 作为参数（root composable 规则）
- Content 不访问 ViewModel、不调用 `collectAsState`
- Content 的参数顺序：data → callbacks → modifier

### Step 2: 将 state 提升到 wrapper 层

检查 Content 中是否有遗漏的 local state：

```kotlin
// ❌ Content 中有 local state — 必须提升
@Composable
fun MyContent(...) {
    var expanded by remember { mutableStateOf(false) }  // 这是 UI-only state，可以保留
    var formText by remember { mutableStateOf("") }      // 这需要提升（业务数据）
}

// ✅ UI-only state（展开/折叠、动画状态等）可以保留在 Content 中
// ✅ 业务数据 state 必须提升到 wrapper 或 ViewModel
```

**保留在 Content 中的例外：**
- `LazyListState`、`ScrollState`（UI 滚动位置）
- `TextFieldValue`（输入框中间态）
- 动画状态（`animateFloatAsState`）
- 展开/折叠 toggle（纯视觉状态）

### Step 3: 将回调转换为 lambda 参数

ViewModel 方法调用 → lambda 参数：

```kotlin
// ❌ Before: Content 直接调用 ViewModel
@Composable
fun BadContent(viewModel: HomeViewModel) {
    Button(onClick = { viewModel.submit() })  // 耦合 ViewModel
}

// ✅ After: lambda 回调
@Composable
fun GoodContent(onSubmit: () -> Unit) {
    Button(onClick = onSubmit)
}
```

### Step 4: 提取子 composable

按视觉区块拆分 Content 中的大段代码：

```kotlin
// ❌ Before: God Content (>100 行)
@Composable
fun HomePageContent(uiState: HomeUiState, ...) {
    Column {
        // 50 行 header 代码...
        // 80 行列表代码...
        // 30 行 footer 代码...
    }
}

// ✅ After: 提取子 composable
@Composable
fun HomePageContent(uiState: HomeUiState, ...) {
    Column {
        HomeHeader(
            userInfo = uiState.userInfo,
            onProfileClick = onProfileClick,
        )
        HomeBannerCarousel(
            banners = uiState.banners ?: emptyList(),
            onBannerClick = onBannerClick,
        )
        HomeProductList(
            products = uiState.products ?: emptyList(),
            onProductClick = onProductClick,
        )
        HomeFooter(
            lockGroups = uiState.lockGroups,
            onLockGroupClick = onLockGroupClick,
        )
    }
}
```

**提取规则：**
- 超过 20 行的连续 UI 代码 → 提取为独立 composable
- 有独立语义的 UI 区块 → 提取（即使不足 20 行）
- 提取的函数设为 `private`（同文件）或 `internal`（跨文件）
- 每个提取的 composable 接收它需要的最小数据集（不是整个 UiState）

**参数传递原则：**
```kotlin
// ❌ 传递整个 UiState（子组件知道太多）
@Composable
fun HomeHeader(uiState: HomeUiState) { ... }

// ✅ 只传需要的数据（最小权限原则）
@Composable
fun HomeHeader(
    userInfo: UserInfoData?,
    notificationCount: Int = 0,
    onProfileClick: () -> Unit,
) { ... }
```

### Step 5: 为每个 composable 添加 @Preview

每个提取的 composable 都要有 preview：

```kotlin
// ----- Previews -----

@Preview(name = "Normal", showBackground = true)
@Composable
private fun HomePageContentPreview() {
    PreviewTheme {
        HomePageContent(
            uiState = previewHomeUiState(),
            onBannerClick = {},
            onRefresh = {},
            onNavigate = {},
        )
    }
}

@Preview(name = "Loading", showBackground = true)
@Composable
private fun HomePageContentLoadingPreview() {
    PreviewTheme {
        HomePageContent(
            uiState = HomeUiState(isLoading = true),
            onBannerClick = {},
            onRefresh = {},
            onNavigate = {},
        )
    }
}

@Preview(name = "Error", showBackground = true)
@Composable
private fun HomePageContentErrorPreview() {
    PreviewTheme {
        HomePageContent(
            uiState = HomeUiState(errorMessage = "网络连接失败，请重试"),
            onBannerClick = {},
            onRefresh = {},
            onNavigate = {},
        )
    }
}

@Preview(name = "Empty", showBackground = true)
@Composable
private fun HomeHeaderEmptyPreview() {
    PreviewTheme {
        HomeHeader(
            userInfo = null,
            onProfileClick = {},
        )
    }
}
```

**Preview 命名规范：** `{ComposableName}{State}Preview()`
- 例如：`HomePageContentPreview`, `HomePageContentLoadingPreview`, `HomeHeaderEmptyPreview`

---

## Phase 4: 验证检查清单

重构完成后逐项检查：

### 结构与分层
- [ ] 原页面保留为 stateful wrapper（~30-50 行），其余逻辑在 Content 中
- [ ] Content composable 不持有 ViewModel 引用
- [ ] Content composable 不调用 `collectAsState*`、`hiltViewModel()`
- [ ] 每个视觉区块已提取为独立 composable
- [ ] 提取的 composable 接收最小数据集（不传整个 UiState）

### State 与数据
- [ ] 所有业务 state 在 wrapper 或 ViewModel 中
- [ ] 仅 UI-only state（展开/折叠/动画）在 Content 层
- [ ] 副作用（LaunchedEffect）在 wrapper 层
- [ ] UiState data class 所有字段可空或带默认值
- [ ] 数据类字段命名遵循项目约定（obfuscated vs semantic）

### Preview 与测试
- [ ] 每个 `@Composable` 函数有 `@Preview`
- [ ] Mock 数据工厂函数已创建（`preview*()` 命名）
- [ ] Preview 覆盖：正常、加载中、错误、空数据、长文本
- [ ] Preview 使用 `PreviewTheme` 包装
- [ ] Content 无 ViewModel 依赖，可直接在 Preview 中渲染

### 代码质量
- [ ] 无 God composable（单个函数 >100 行需进一步拆分）
- [ ] 无超过 5 层的嵌套（Box → Column → Row → Card → Box）
- [ ] Modifier 参数在 root composable 中正确传递
- [ ] 列表使用 `key` 参数（LazyColumn items）
- [ ] 无 `!!` 强制解包（使用 `?.let {}` 或 `?: return`）

---

## 重构前后对比模板

### Before（典型未重构页面）

```kotlin
@Composable
fun MyPage(viewModel: MyViewModel = hiltViewModel()) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // 50 行内联 header UI...
        Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(Color.Blue)) {
            Text(data?.title ?: "", color = Color.White, fontSize = 24.sp)
            IconButton(onClick = { viewModel.onSettingClick() }) {
                Icon(Icons.Default.Settings, "")
            }
        }
        // 80 行内联列表 UI...
        LazyColumn {
            items(data?.items ?: emptyList()) { item ->
                Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Column {
                        Text(item.title ?: "")
                        Text(item.desc ?: "")
                        Button(onClick = { viewModel.onItemClick(item) }) {
                            Text("详情")
                        }
                    }
                }
            }
        }
        // 30 行内联 footer...
        if (isLoading) CircularProgressIndicator()
    }
}
// ❌ 问题: 无 Preview, ViewModel 耦合到每一层, God composable, 无数据隔离
```

### After（重构后）

```kotlin
// ==================== Stateful Wrapper ====================
@Composable
fun MyPage(
    viewModel: MyViewModel = hiltViewModel(),
    onNavigate: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    MyPageContent(
        uiState = uiState,
        onSettingClick = viewModel::onSettingClick,
        onItemClick = viewModel::onItemClick,
        onNavigate = onNavigate,
    )
}

// ==================== Stateless Content ====================
@Composable
fun MyPageContent(
    uiState: MyPageUiState,
    onSettingClick: () -> Unit,
    onItemClick: (ItemData) -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        MyPageHeader(
            title = uiState.title,
            onSettingClick = onSettingClick,
        )
        MyPageList(
            items = uiState.items ?: emptyList(),
            isLoading = uiState.isLoading,
            onItemClick = onItemClick,
        )
    }
}

// ==================== Sub-Composables ====================
@Composable
private fun MyPageHeader(
    title: String?,
    onSettingClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(Color.Blue),
    ) {
        Text(
            text = title ?: "",
            color = Color.White,
            fontSize = 24.sp,
        )
        IconButton(
            onClick = onSettingClick,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(Icons.Default.Settings, contentDescription = "设置")
        }
    }
}

@Composable
private fun MyPageList(
    items: List<ItemData>,
    isLoading: Boolean,
    onItemClick: (ItemData) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isLoading) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(modifier = modifier) {
            items(items, key = { it.id }) { item ->
                MyPageListItem(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
private fun MyPageListItem(
    item: ItemData,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = item.title ?: "")
            Text(text = item.desc ?: "")
            Button(onClick = onClick) {
                Text("详情")
            }
        }
    }
}

// ==================== Data Classes ====================
data class MyPageUiState(
    var isLoading: Boolean = true,
    var title: String? = null,
    var items: List<ItemData?>? = null,
    var errorMessage: String? = null,
)

data class ItemData(
    var id: String? = null,
    var title: String? = null,
    var desc: String? = null,
)

// ==================== Mock Data Factories ====================
fun previewMyPageUiState() = MyPageUiState(
    isLoading = false,
    title = "我的页面",
    items = listOf(previewItemData("1"), previewItemData("2")),
)

fun previewItemData(id: String = "1") = ItemData(
    id = id,
    title = "项目 $id",
    desc = "这是项目 $id 的描述信息",
)

// ==================== Previews ====================
@Preview(name = "Normal", showBackground = true)
@Composable
private fun MyPageContentPreview() {
    PreviewTheme {
        MyPageContent(
            uiState = previewMyPageUiState(),
            onSettingClick = {},
            onItemClick = {},
            onNavigate = {},
        )
    }
}

@Preview(name = "Loading", showBackground = true)
@Composable
private fun MyPageContentLoadingPreview() {
    PreviewTheme {
        MyPageContent(
            uiState = MyPageUiState(isLoading = true),
            onSettingClick = {},
            onItemClick = {},
            onNavigate = {},
        )
    }
}

@Preview(name = "Empty", showBackground = true)
@Composable
private fun MyPageListEmptyPreview() {
    PreviewTheme {
        MyPageList(
            items = emptyList(),
            isLoading = false,
            onItemClick = {},
        )
    }
}
```

### 结果对比

| 指标 | Before | After |
|------|--------|-------|
| 最大函数行数 | ~160 | ~50 |
| ViewModel 耦合层数 | 3 层 | 1 层（仅 wrapper） |
| @Preview 数量 | 0 | 3+ |
| 可测试性 | 差（需 mock ViewModel） | 好（纯函数 + 数据） |
| 数据隔离 | 无（state 散落各处） | 完整（UiState data class） |
| 可复用性 | 低 | 高（每个 composable 独立） |

---

## 特殊情况处理

### 有 HorizontalPager 的页面

Pager state 可以保留在 Content 层（UI-only state），但当前页索引如果是业务数据则提升：

```kotlin
@Composable
fun TabPageContent(...) {
    val pagerState = rememberPagerState { tabs.size }  // UI-only, 可保留
    // selectedPageIndex 如果是业务关心的，则作为参数传入
}
```

### 有 WebView 的页面

WebView 通过 `AndroidView` 加载，属于副作用。WebView URL 作为 state 传入 Content：

```kotlin
@Composable
fun WebViewContent(
    url: String?,         // 从 wrapper 传入
    onUrlChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
)
```

### 有 BottomSheet/Dialog 的页面

Sheet/Dialog 的显示状态作为 state 传入 Content：

```kotlin
@Composable
fun MyPageContent(
    showFilterSheet: Boolean = false,  // state 在 wrapper 中
    onDismissFilter: () -> Unit,
    ...
)
```

---

## Source

- `references/view-composition.md` — Stateful vs Stateless pattern, Screen-Level Composables
- `references/preview.md` — @Preview best practices, PreviewParameterProvider
- `references/state-management.md` — State hoisting, derivedStateOf, snapshotFlow
- `references/atomic-design.md` — Component level classification
