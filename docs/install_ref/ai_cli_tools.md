# AI CLI Tools

*Scripts: `setup_codex_debian.sh`, `setup_claude_code_debian.sh`, `setup_gemini_cli_debian.sh`, `setup_cline_debian.sh`, `setup_kilocode_debian.sh`, `setup_opencode_debian.sh`, `setup_kiro_cli_debian.sh`*

---

## Overview

Installs terminal-native AI coding agents on Debian ARM64. All npm-based tools share the same Node.js installation at `/opt/nodejs`.

---

## Node.js (Shared Dependency)

| Component | Version | Path |
|-----------|---------|------|
| Node.js | v22.13.1 LTS | `/opt/nodejs` |
| npm | Bundled | `/opt/nodejs/bin/npm` |

> **Pattern:** All npm-based AI CLIs use the same Node.js at `/opt/nodejs`. The setup scripts check for Node.js first and only install if missing.

---

## OpenAI Codex CLI

| Component | Version | Path |
|-----------|---------|------|
| Package | `@openai/codex` (latest) | `/opt/nodejs/lib/node_modules/@openai/codex` |
| Binary | `codex` | `/opt/nodejs/bin/codex` → `/usr/local/bin/codex` |

**Script:** `setup_codex_debian.sh`

**Environment Variable:** `OPENAI_API_KEY=sk-...`

---

## Anthropic Claude Code

| Component | Version | Path |
|-----------|---------|------|
| Package | `@anthropic-ai/claude-code` (latest) | `/opt/nodejs/lib/node_modules/@anthropic-ai/claude-code` |
| Binary | `claude` | `/opt/nodejs/bin/claude` → `/usr/local/bin/claude` |

**Script:** `setup_claude_code_debian.sh`

**Environment Variable:** `ANTHROPIC_API_KEY=sk-ant-...`

---

## Google Gemini CLI

| Component | Version | Path |
|-----------|---------|------|
| Package | `@google/gemini-cli` (latest) | `/opt/nodejs/lib/node_modules/@google/gemini-cli` |
| Binary | `gemini` | `/opt/nodejs/bin/gemini` → `/usr/local/bin/gemini` |

**Script:** `setup_gemini_cli_debian.sh`

**Environment Variable:** `GOOGLE_API_KEY=AIza...`

---

## Cline (VS Code Extension)

| Component | Type | Extension ID |
|-----------|------|------------|
| Cline AI Agent | VS Code/Cursor Extension | `saoudrizwan.claude-dev` |

**Script:** `setup_cline_debian.sh`

**Prerequisite:** VS Code or Cursor must be installed first (available from IDE Tools).

**Usage:** Open VS Code → Extensions → Cline → Configure API key

---

## KiloCode (VS Code Extension)

| Component | Type | Extension ID |
|-----------|------|------------|
| KiloCode AI Agent | VS Code/Cursor Extension | `kilocode.kilo-code` |

**Script:** `setup_kilocode_debian.sh`

**Prerequisite:** VS Code or Cursor must be installed first (available from IDE Tools).

**Usage:** Open VS Code → Extensions → KiloCode → Configure API key

---

## OpenCode (Go Binary)

| Component | Version | Path |
|-----------|---------|------|
| OpenCode Binary | latest | `/opt/opencode/opencode` |
| Symlink | `opencode` | `/usr/local/bin/opencode` |

**Script:** `setup_opencode_debian.sh`

**Install Method:** Downloads ARM64 binary from GitHub releases. Falls back to building from source via Go if binary unavailable.

---

## Amazon Kiro CLI

| Component | Version | Path |
|-----------|---------|------|
| Package | `@anthropic-ai/kiro` (latest) | `/opt/nodejs/lib/node_modules/@anthropic-ai/kiro` |
| Binary | `kiro` | `/opt/nodejs/bin/kiro` → `/usr/local/bin/kiro` |

**Script:** `setup_kiro_cli_debian.sh`

**Usage:** `kiro auth login` to configure credentials

---

## Common Patterns

All AI CLI install scripts follow the same structure:

1. **Error Handler** — Prints step, sends callback to app
2. **Node.js Check** — Installs Node.js v22.13.1 at `/opt/nodejs` if missing
3. **Tool Install** — npm global install or binary download
4. **Verification** — Checks binary is on PATH
5. **Shell Profile** — Adds `/opt/nodejs/bin` to `.bashrc`, `.zshrc`, `/etc/profile.d/nodejs.sh`
6. **Idempotency Marker** — Creates `/home/flux/.nativecode/<name>_installed`
7. **Callback** — Sends `nativecode://callback?result=success&name=ai_tools_<name>`

### PATH Configuration

```bash
export PATH=$PATH:/opt/nodejs/bin
```

Added to:
- `/home/flux/.bashrc`
- `/home/flux/.zshrc` (if zsh installed)
- `/etc/profile.d/nodejs.sh`

### Callback Format

```
nativecode://callback?result=success&name=ai_tools_codex
nativecode://callback?result=failure&name=ai_tools_claude_code
nativecode://callback?result=success&name=ai_tools_gemini_cli
```