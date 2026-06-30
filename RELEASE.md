# v1.7.3 — LazyColumn key crash

v1.7.2 crashed when two terminal lines had the same text:

```
FATAL EXCEPTION: main
java.lang.IllegalArgumentException: Key "108068241" was already used.
  If you are using LazyColumn/Row please make sure you provide a
  unique key for each item.
  at androidx.compose.ui.layout.LayoutNodeSubcompositionsState
     .subcompose
```

`items(output, key = { it.hashCode() })` collided as soon as the shell
produced a duplicate line (a re-printed prompt, a blank line, an
output line repeated by a long-running command). Compose recycled
the slot, hit the duplicate key, threw, and the app got kicked to
the launcher mid-setup.

## Fix
`itemsIndexed(output, key = { i, _ -> i })` — list index, guaranteed
unique. Two same-text lines now sit in distinct rows instead of
colliding.

## Commits
- `b8ae478` fix(terminal): unique LazyColumn key — was crashing on duplicate lines

## Install
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Artifact
- `app-release.apk` — ~24 MB, minSdk 26, targetSdk 36, arm64-v8a only.
