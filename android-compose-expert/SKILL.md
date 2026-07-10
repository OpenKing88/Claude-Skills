---
name: android-compose-expert
description: >
  Android Compose expert skill for Android-only UI development. Guides state management,
  view composition, animations, navigation, performance, design-to-code workflows,
  and production crash patterns. Backed by actual source code analysis from
  androidx/androidx (branch: androidx-main).
  Use this skill whenever the user mentions Compose, @Composable, remember, LaunchedEffect,
  Scaffold, NavHost, MaterialTheme, LazyColumn, Modifier, recomposition, Material3,
  "Android UI", "Kotlin UI", "compose layout", "compose navigation", "compose animation",
  "material3", "compose styles", "design to compose", "build this UI", "implement this design",
  "review this PR", "review this code", "check this diff", "design system",
  "component library", "atomic", "reusable component", "design tokens", "atoms", "molecules",
  or asks about modern Kotlin UI development patterns for Android. Even casual mentions
  like "my compose screen is slow" or "how do I pass data between screens" should trigger
  this skill.
  Also trigger on session_start to auto-detect Android Compose projects — see references/auto-init.md.
  Also trigger when users ask to refactor or restructure Compose pages — see Refactoring Mode.
  Trigger phrases for refactoring: 重构, refactor, 改造compose, 改造页面, 拆分composable,
  UI分层, 状态隔离, 添加preview, 优化composable结构, 提取composable, 页面重构,
  stateful/stateless, Content模式, 优化结构, 代码分层.
version: 1.1.0
---

# Android Compose Expert Skill

Non-opinionated, practical guidance for writing correct, performant Android Compose code.
Backed by analysis of actual source code from `androidx/androidx` (branch: `androidx-main`).

This skill is Android-only. It does not cover Compose Multiplatform, Desktop, iOS, Web, or TV Compose.

## Review Mode

**Activate when** the input contains a GitHub PR URL (`github.com/.+/pull/\d+`) or
explicit review phrases: "review this PR", "review this diff", "check this code",
"what's wrong with this".

When Review Mode activates:
1. Do **not** follow the generation workflow below
2. Read `references/pr-review.md` and follow its workflow exclusively
3. Output a structured local review report — do not post to GitHub

## Refactoring Mode

**Activate when** the user asks to refactor, restructure, or clean up a Compose page/screen.
Trigger phrases include: 重构, refactor, 改造compose, 改造页面, 拆分composable,
UI分层, 状态隔离, 添加preview, 提取composable, stateful/stateless, Content模式,
优化结构, 代码分层, 页面重构.

When Refactoring Mode activates:
1. Do **not** follow the general generation workflow below
2. Read `references/refactoring.md` and follow its workflow exclusively
3. Apply the **stateful-wrapper + stateless-content** pattern as the first step
4. Extract sub-composables for each logical UI section (header, body, footer, dialogs, etc.)
5. Add `@Preview` with mock data factories for EVERY extracted composable
6. Report structural changes: before line count → after line count, list of extracted composables

**Core refactoring rules (non-negotiable):**
- All state (mutableStateOf, collectAsState) lives in the wrapper, never in Content
- Content composable is PURE — receives data + callbacks, returns nothing
- Content never holds ViewModel references
- Every `@Composable` that renders visible UI gets a `@Preview`
- Mock data factories use the naming pattern `preview{ClassName}()` or `mock{DataClassName}()`
- Data classes for UI state follow the convention: `XxxUiState` or `XxxData`, all fields nullable with defaults

## Workflow

When helping with Android Compose code, follow this checklist:

### 1. Understand the request
- What Compose layer is involved? (Runtime, UI, Foundation, Material3, Navigation)
- Is this a state problem, layout problem, performance problem, or architecture question?
- Is this a single-screen component or a full-screen flow?

### 2. Analyze the design (if visual reference provided)
- If the user shares a Figma frame, screenshot, or design spec, consult `references/design-to-compose.md`
- Decompose the design into a composable tree using the 5-step methodology
- Map design tokens to MaterialTheme, spacing to CompositionLocals
- Identify animation needs and consult `references/animation.md` for recipes

### 3. Consult the right reference
Read the relevant reference file(s) from `references/` before answering:

| Topic | Reference File |
|-------|---------------|
| `@State`, `remember`, `mutableStateOf`, state hoisting, `derivedStateOf`, `snapshotFlow` | `references/state-management.md` |
| Structuring composables, slots, extraction, preview | `references/view-composition.md` — for design system structure, also see `references/atomic-design.md` |
| Modifier ordering, custom modifiers, `Modifier.Node` | `references/modifiers.md` |
| `LaunchedEffect`, `DisposableEffect`, `SideEffect`, `rememberCoroutineScope` | `references/side-effects.md` |
| `CompositionLocal`, `LocalContext`, `LocalDensity`, custom locals | `references/composition-locals.md` |
| `LazyColumn`, `LazyRow`, `LazyGrid`, `Pager`, keys, content types | `references/lists-scrolling.md` |
| `NavHost`, type-safe routes, deep links, shared element transitions | `references/navigation.md` |
| `animate*AsState`, `AnimatedVisibility`, `Crossfade`, transitions | `references/animation.md` — for M3 token selection, also see `references/material3-motion.md` |
| `MaterialTheme`, `ColorScheme`, dynamic color, `Typography`, shapes | `references/theming-material3.md` — for motion, see `references/material3-motion.md`; for design tokens, see `references/atomic-design.md` |
| Recomposition skipping, stability, baseline profiles, benchmarking | `references/performance.md` |
| Semantics, content descriptions, traversal order, testing | `references/accessibility.md` |
| Removed/replaced APIs, migration paths from older Compose versions | `references/deprecated-patterns.md` |
| `@Preview`, `PreviewParameterProvider`, interactive mode, screenshot testing | `references/preview.md` |
| Figma/screenshot decomposition, design tokens, spacing, modifier ordering | `references/design-to-compose.md` |
| Production crash patterns, defensive coding, state/performance rules | `references/production-crash-playbook.md` |
| M3 motion tokens, `MotionTokens`, `MotionScheme`, animation duration, easing | `references/material3-motion.md` |
| PR URL, code review, "review this PR", "what's wrong with this" | `references/pr-review.md` |
| Session start, project detection | `references/auto-init.md` |
| Atomic design, design system, reusable component, component library, design tokens | `references/atomic-design.md` |
| Compose 页面重构、UI 分层、状态隔离、预览添加、stateful/stateless 改造 | `references/refactoring.md` |

### 4. Apply and verify
- Write code that follows the patterns in the reference
- Flag any anti-patterns you see in the user's existing code
- Suggest the minimal correct solution — don't over-engineer

### 4a. Component building mode
When the request involves building a component (composable that renders UI):
- Consult `references/atomic-design.md`
- Classify the component level (atom, molecule, organism, template)
- Apply the "Ask" prompt from Section 5 before scaffolding code
- Ensure the component satisfies the atom contract (modifier, slots, tokens, defaults)

### 5. Cite the source
When referencing Compose internals, point to the exact source file:
```
// See: compose/runtime/runtime/src/commonMain/kotlin/androidx/compose/runtime/Composer.kt
```

## Key Principles

1. **Compose thinks in three phases**: Composition → Layout → Drawing. State reads in each
   phase only trigger work for that phase and later ones.

2. **Recomposition is frequent and cheap** — but only if you help the compiler skip unchanged
   scopes. Use stable types, avoid allocations in composable bodies.

3. **Modifier order matters**. `Modifier.padding(16.dp).background(Color.Red)` is visually
   different from `Modifier.background(Color.Red).padding(16.dp)`.

4. **State should live as low as possible** and be hoisted only as high as needed. Don't put
   everything in a ViewModel just because you can.

5. **Side effects exist to bridge Compose's declarative world with imperative APIs**. Use the
   right one for the job — misusing them causes bugs that are hard to trace.

6. **Always use `collectAsStateWithLifecycle()`** when collecting StateFlow/SharedFlow in
   Android Compose. It stops collection when the composable is not STARTED, preventing
   memory leaks and unnecessary background work.

7. **Every `@Composable` that renders visible UI must have a `@Preview` function.** Extract a
   stateless `*Content` composable from every screen for previewability. Previews are the
   fastest correctness check — use `PreviewParameterProvider` for multi-state coverage.

8. **Screens must be stateless + previewable.** Every screen composable splits into a
   stateful wrapper (ViewModel / state hoisting) and a stateless `*Content` composable
   (pure UI, receives data + callbacks). When refactoring, this state-data-UI isolation
   is the first and most important step. The Content composable never holds ViewModel
   references — only plain data and lambda callbacks.

## Source Code Receipts

Beyond the guidance docs, this skill bundles the **actual source code** from
`androidx/androidx` (branch: `androidx-main`). When you need to verify how something works internally, or the
user asks "show me the actual implementation", read the raw source from
`references/source-code/`:

| Module | Source Reference | Key Files Inside |
|--------|-----------------|------------------|
| Runtime | `references/source-code/runtime-source.md` | Composer.kt, Recomposer.kt, State.kt, Effects.kt, CompositionLocal.kt, Remember.kt, SlotTable.kt, Snapshot.kt |
| UI | `references/source-code/ui-source.md` | AndroidCompositionLocals.android.kt, Modifier.kt, Layout.kt, LayoutNode.kt, ModifierNodeElement.kt, DrawModifier.kt |
| Foundation | `references/source-code/foundation-source.md` | LazyList.kt, LazyGrid.kt, BasicTextField.kt, Clickable.kt, Scrollable.kt, Pager.kt |
| Material3 | `references/source-code/material3-source.md` | MaterialTheme.kt, ColorScheme.kt, Button.kt, Scaffold.kt, TextField.kt, NavigationBar.kt |
| Navigation | `references/source-code/navigation-source.md` | NavHost.kt, ComposeNavigator.kt, NavGraphBuilder.kt, DialogNavigator.kt |

### Two-layer approach
1. **Start with guidance** — read the topic-specific reference (e.g., `references/state-management.md`)
2. **Go deeper with source** — if the user wants receipts or you need to verify, read from `references/source-code/`

### Source tree map
```
androidx/androidx (branch: androidx-main)
├── compose/runtime/runtime/src/commonMain/kotlin/androidx/compose/runtime/
├── compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/
├── compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/
├── compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/
├── compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/
└── compose/navigation/navigation-compose/src/commonMain/kotlin/androidx/navigation/compose/
```
