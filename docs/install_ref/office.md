# Office Suite

*Script: `setup_office_debian.sh`*

---

## Overview

Installs office productivity applications for documents, spreadsheets, email, and PDFs.

---

## LibreOffice

| App | Purpose |
|-----|---------|
| Writer | Word processing |
| Calc | Spreadsheets |
| Impress | Presentations |
| Draw | Diagrams & vector graphics |

---

## Email & PIM

| Tool | Purpose |
|------|---------|
| Thunderbird | Email client |

---

## PDF Tools

| Tool | Purpose |
|------|---------|
| Evince | PDF viewer |
| Xournal++ | PDF annotation & note-taking |

---

## Fonts

| Package | Type |
|---------|------|
| fonts-noto-core | Core Noto fonts |
| fonts-liberation | MS-compatible |
| fonts-dejavu | Classic fonts |

---

## Packages Installed

```bash
libreoffice libreoffice-gtk3
thunderbird
evince xournalpp
fonts-noto-core fonts-liberation fonts-dejavu
```

---

## Verification

```bash
libreoffice --version
thunderbird --version
evince --version
```
