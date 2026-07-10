# @Preview Best Practices for Android Compose

Every `@Composable` that renders visible UI must have a corresponding `@Preview` function.
Previews are not optional tooling — they are the fastest way to validate layout, theme, and
state behavior without running the app.

---

## 1. Mandatory Preview Rule

| Composable Type | Preview Required | Notes |
|-----------------|-----------------|-------|
| Screen-level | Yes | Wrap in `PreviewTheme`, pass fake data |
| Organism-level | Yes | Multi-state via `PreviewParameterProvider` |
| Molecule-level | Yes | Show default, active, disabled states |
| Atom-level | Yes | Even simple `Text` or `Icon` atoms |
| Internal helpers emitting no layout | No | Pure logic wrappers, state holders |

**Screen-level preview anti-pattern:**

```kotlin
// BAD — viewModel() crashes in Preview (no Activity/Fragment scope)
@Preview
@Composable
fun HomeScreenPreview() {
    HomeScreen(viewModel = hiltViewModel())
}

// GOOD — preview-friendly wrapper with fake data
@Preview
@Composable
fun HomeScreenPreview() {
    PreviewTheme {
        HomeScreenContent(
            uiState = HomeUiState.Success(items = fakeItems),
            onItemClick = {},
            onRefresh = {}
        )
    }
}
```

Extract a stateless `*Content` composable from every screen for previewability and testability.

---

## 2. @Preview Parameter Reference

```kotlin
@Preview(
    name = "Description",           // IDE sidebar label
    group = "Auth Flow",            // IDE grouping
    widthDp = 360,                  // Fixed width
    heightDp = 640,                 // Fixed height
    showBackground = true,          // Render background color
    backgroundColor = 0xFFFFFFFF,   // ARGB hex (use with showBackground)
    showSystemUi = true,            // Status bar + navigation bar
    uiMode = UI_MODE_NIGHT_YES,     // Configuration.UI_MODE_NIGHT_YES
    locale = "zh-rCN",              // Locale for string resources
    fontScale = 1.5f,               // Accessibility font scaling
    device = Devices.PIXEL_7        // Predefined or custom spec string
)
```

**Custom device spec example:**

```kotlin
@Preview(device = "spec:width=411dp,height=891dp,dpi=420,isRound=false,chinSize=0dp")
```

---

## 3. Reusable PreviewTheme Wrapper

Always wrap previews in your app's theme. Never preview a composable outside of `MaterialTheme`.

```kotlin
@Composable
fun PreviewTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MyAppTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            content()
        }
    }
}
```

Usage:

```kotlin
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
fun UserCardPreview() {
    PreviewTheme(darkTheme = isSystemInDarkTheme()) {
        UserCard(user = User("Alice"))
    }
}
```

---

## 4. Multi-State Preview with PreviewParameterProvider

Use `PreviewParameterProvider` to generate multiple preview states from a single composable.
This is more maintainable than stacking `@Preview` annotations for every data variant.

```kotlin
class UserPreviewProvider : PreviewParameterProvider<User> {
    override val values = sequenceOf(
        User(name = "Alice", avatarUrl = "https://example.com/alice.jpg"),
        User(name = "Bob", avatarUrl = null),              // No avatar
        User(name = "", avatarUrl = null),                  // Empty name (edge case)
        User(name = "Very Long Name That Might Wrap", avatarUrl = null), // Long text
    )
}

@Preview
@Composable
fun UserCardPreview(
    @PreviewParameter(UserPreviewProvider::class) user: User
) {
    PreviewTheme { UserCard(user = user) }
}
```

**Best practice:** Create a `preview/` source set or package for shared preview data and providers.

---

## 5. Multi-Dimension Preview Strategy

Avoid stacking 6+ `@Preview` annotations on one function. Organize by dimension:

```kotlin
// File: UserCardPreview.kt — basic states
@Preview(name = "Default")
@Composable
fun UserCardDefaultPreview() { ... }

// File: UserCardThemePreview.kt — theme variants
@PreviewLightDark
@Composable
fun UserCardThemePreview() { ... }

// File: UserCardResponsivePreview.kt — screen sizes
@PreviewScreenSizes
@Composable
fun UserCardResponsivePreview() { ... }
```

Use `group` for IDE sidebar filtering when files grow large.

---

## 6. Naming and Organization

### Function Naming

```kotlin
// Pattern: [ComponentName][State]Preview
@Preview
@Composable
fun UserCardPreview()                   // Default state

@Preview
@Composable
fun UserCardLoadingPreview()            // Loading state

@Preview
@Composable
fun UserCardEmptyPreview()              // Empty/null state
```

### File Location

**Option A — Co-located at file bottom (recommended for small projects):**

```kotlin
// UserCard.kt
@Composable
fun UserCard(...) { ... }

// ----- Previews -----
@Preview
@Composable
private fun UserCardPreview() { ... }
```

**Option B — Dedicated `preview/` package (recommended for large projects):**

```
src/main/java/com/example/ui/preview/
├── UserCardPreview.kt
├── UserCardThemePreview.kt
└── PreviewData.kt          // Shared fake data
```

Pick one convention and enforce it across the codebase.

---

## 7. Built-in Multi-Preview Annotations

Prefer built-in convenience annotations over manual stacking:

| Annotation | Generates |
|-----------|-----------|
| `@PreviewLightDark` | Light + Dark theme previews |
| `@PreviewFontScale` | 0.85f, 1.0f, 1.15f, 1.3f, 1.5f, 1.8f, 2.0f font scales |
| `@PreviewScreenSizes` | Phone, Foldable, Tablet, Desktop dimensions |
| `@PreviewDynamicColors` | Dynamic color on + off |

```kotlin
@PreviewScreenSizes
@PreviewLightDark
@Composable
fun LoginScreenPreview() {
    PreviewTheme { LoginScreenContent(...) }
}
```

> Note: These annotations stack — each combination produces a separate preview cell in the IDE.

---

## 8. Interactive Mode

Enable **Interactive Mode** in Android Studio (Preview panel → ▶️ icon) to test:
- Click handlers
- Scroll gestures
- Text input
- State changes

Limitations:
- System back gesture is not simulated
- Permission dialogs do not appear
- `LaunchedEffect` runs but coroutine scope is tied to preview lifecycle

---

## 9. Limitations and Defensive Patterns

### Do NOT call ViewModel in Preview

```kotlin
// CRASHES in Preview — no ViewModelStoreOwner
@Preview
@Composable
fun BadPreview() {
    val viewModel: MyViewModel = hiltViewModel() // IllegalStateException
}
```

**Solution:** Hoist all ViewModel access to the screen root. The `*Content` composable accepts plain data + callbacks.

```kotlin
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreenContent(
        uiState = uiState,
        onItemClick = viewModel::onItemClick
    )
}

@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    onItemClick: (Item) -> Unit,
    modifier: Modifier = Modifier
) { /* previewable + testable */ }
```

### Do NOT perform I/O in Preview

No network calls, database access, or file reads. Use fake/static data.

### Do NOT rely on Preview for animation timing

`LaunchedEffect` and `rememberCoroutineScope` execute in Preview but the timing
and recomposition behavior may differ from runtime. Validate animations on device.

---

## 10. From Preview to Screenshot Testing

Reuse existing `@Preview` functions for automated screenshot tests to avoid duplication:

**Paparazzi example:**

```kotlin
@RunWith(TestParameterInjector::class)
class UserCardScreenshotTest {
    @get:Rule val paparazzi = Paparazzi()

    @Test
    fun userCard_default() {
        paparazzi.snapshot { UserCardPreview() }
    }
}
```

This ensures your preview and your screenshot test never diverge.

---

## Source

- `androidx.compose.ui:ui-tooling-preview`
- `androidx.compose.ui:ui-tooling` (for interactive preview)
