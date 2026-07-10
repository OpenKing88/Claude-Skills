# Auto-Init: Android Compose Project Detection

Activate on `session_start`. Detect whether the current project is an Android Compose project and
silently activate the skill with a brief announcement.

---

## Detection Gate

Run in order. Stop on first match.

### Step 1 — Gradle scan

Look for `build.gradle.kts`, `build.gradle`, or `libs.versions.toml` in the working
directory or one level up. Check file contents for any of:

- `compose`
- `androidx.compose`
- `activity-compose`
- `material3`

```bash
# Check working directory and parent
for dir in . ..; do
  for file in build.gradle.kts build.gradle libs.versions.toml; do
    if [ -f "$dir/$file" ]; then
      grep -qi "compose\|androidx\.compose\|activity-compose\|material3" "$dir/$file" && echo "DETECTED" && break 2
    fi
  done
done
```

### Step 2 — Source scan fallback

If no Gradle file found or no Compose reference in Gradle, scan Kotlin source files.

```bash
# Find up to 10 .kt files (exclude build dirs), check for @Composable
find . -name "*.kt" -not -path "*/build/*" -print -quit 2>/dev/null | head -10 | \
  xargs grep -l "@Composable" 2>/dev/null | head -1
```

If any file contains `@Composable`, detection succeeds.

---

## On Detection

Print one line:

```
Android Compose project detected — android-compose-expert skill active.
```

Then proceed normally — wait for the user's request and follow the standard workflow
in `SKILL.md`.

## On No Detection

Do nothing. The skill remains available if the user explicitly triggers it via
keyword (e.g., mentions `@Composable`, `LazyColumn`, `NavHost`, etc.) later in
the session.
