# Android ViewSystem Foundations Patterns

Category: `ui`

## Selection Notes

Best fit when a request matches the trigger language in `SKILL.md` and the implementation focus involves handling XML layouts, ConstraintLayout, Fragments, ViewBinding, DataBinding, and classic Android UI lifecycle patterns. Reach for neighboring skills only after this skill has framed the main problem.

## Default Review Sequence

1. Identify whether the surface is Compose, View system, or a mixed interoperability screen.
2. Choose the lowest-friction UI pattern meeting responsiveness, accessibility, and performance needs.
3. Build around stable state, explicit side effects, and reusable design tokens.
4. Test edge cases — long text, font scaling, RTL, narrow devices — in fixture apps.
5. Validate with unit, UI, and screenshot-friendly checks before handoff.

## Handoff Shortlist

- `android-compose-xml-interoperability`
- `android-testing-ui`
