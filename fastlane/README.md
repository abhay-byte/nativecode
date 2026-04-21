# F-Droid & Play Store Metadata Summary

## App Information
- **Package Name**: com.zenithblue.fluxlinux
- **Public Brand**: NativeCode
- **Store/App Name**: NativeCode
- **Current Version**: 1.0 (versionCode: 1)
- **Target SDK**: 36 (Android 16)
- **Min SDK**: 26 (Android 8.0)
- **Note**: Package identifiers still use `fluxlinux` until the app/package rename is done

## Directory Structure
```
fastlane/
└── metadata/
    └── android/
        └── en-US/
            ├── title.txt
            ├── short_description.txt
            ├── full_description.txt
            ├── changelogs/
            │   ├── 1.txt
            │   └── ...
            └── images/
                ├── icon.png
                ├── featureGraphic.png
                └── phoneScreenshots/
                    ├── 1.png
                    ├── 2.png
                    ├── ...
                    └── 7.png
```

## Metadata Targets

| File | Characters | Status |
|------|------------|--------|
| title.txt | 31 | ✅ Under 50 |
| short_description.txt | 51 | ✅ Under 80 |
| full_description.txt | 488 | ✅ Under 500 |
| changelogs/*.txt | Varies | ✅ Present |
| images/* | - | ✅ Present |

## Content

### title.txt
```
NativeCode - Android Vibe Coding
```

### short_description.txt
```
AI coding tools, IDEs, and MCP servers on Android.
```

### full_description.txt
```
NativeCode turns Android into a vibe coding workstation with AI coding CLIs,
desktop IDEs, MCP/LSP integrations, and the Linux runtime needed to make them useful.
```

### changelogs
Historical changelogs remain in place. Branding references in user-facing metadata were updated where needed, but the version history itself was not rewritten.

## Current Positioning

- **Core message**: Android vibe coding workstation
- **CLI examples**: Codex, Claude Code, Gemini CLI, aider, QwenCode, OpenCode
- **IDE examples**: VS Code, Cursor, Windsurf, Trae, Kiro IDE, Codex App
- **MCP/LSP examples**: Context7, context-mode, filesystem, android-mcp, kotlin-mcp, github-mcp, playwright-mcp, Speckit, Agency Agent

## Notes

- Existing screenshots and media assets were kept as-is.
- `docs/` content was intentionally left untouched.
- The GPLv3 `LICENSE` file does not include product branding, so it did not require content changes.
