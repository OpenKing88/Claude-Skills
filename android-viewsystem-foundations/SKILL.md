---
name: android-viewsystem-foundations
version: 0.1.0
category: ui
tags:
  - android
  - xml
  - views
  - fragments
owned_by: "@android-agent-skills/maintainers"
include:
  - xml layout android issue
  - fragment lifecycle android
  - constraintlayout cleanup
  - viewbinding databinding android
  - legacy view system screen
  - recyclerview adapter android
  - fragment transaction issue android
exclude:
  - compose-only side effects
  - retrofit serialization only
  - gradle wrapper bumps
  - pure compose layout
  - configuration cache problems
test_targets:
  - examples/orbittasks-compose
  - examples/orbittasks-xml
  - benchmarks/triggers.jsonl
---

# Android ViewSystem Foundations

Use this skill when the main surface is XML, Fragment, RecyclerView, or binding lifecycle work rather than Compose-first UI.

## Workflow

1. Identify the target surface — Fragment, Activity, custom view, RecyclerView, or mixed Compose/View screen.
2. Anchor ownership — view bindings to the view lifecycle, adapters to explicit item models.
3. Fix layout issues with classic View tools first — ConstraintLayout, RecyclerView diffing, window insets, binding-safe updates.
4. Test against Fragment recreation, long text, font scaling, RTL, and process-lifecycle edges.
5. Hand off to Compose or UI testing only after View-system ownership is stable.

## Guardrails

- Prioritize stable state and predictable rendering over animation
- Respect accessibility — contentDescription, focus order, minimum touch targets
- Don't mix ownership models without an explicit boundary
- Prefer measured performance work over micro-optimizations
- Clear ViewBinding references when the Fragment view is destroyed

## Anti-Patterns

- Embedding navigation or business logic in leaf components
- Fixed dimensions that break with dynamic text
- Ignoring accessibility semantics
- Porting XML patterns directly into Compose without adapting the mental model
- Holding Fragment view state in stale bindings, view references, or adapters across view recreation

## Done Checklist

- [ ] Explicit implementation path tied to the right surface
- [ ] Example commands and benchmark prompts exercised or updated
- [ ] Handoffs documented when crossing boundaries
- [ ] Official references covering the chosen pattern

## Official References

- [Fragments guide](https://developer.android.com/guide/fragments)
- [View binding](https://developer.android.com/topic/libraries/view-binding)
- [Constraint Layout training](https://developer.android.com/training/constraint-layout)
- [Data Binding Library](https://developer.android.com/topic/libraries/data-binding)
