# UI Design System: Obsidian Nexus

---
name: Obsidian Nexus
colors:
  surface: '#10131a'
  surface-dim: '#10131a'
  surface-bright: '#363940'
  surface-container-lowest: '#0b0e14'
  surface-container-low: '#191c22'
  surface-container: '#1d2026'
  surface-container-high: '#272a31'
  surface-container-highest: '#32353c'
  on-surface: '#e1e2eb'
  on-surface-variant: '#b9cacb'
  inverse-surface: '#e1e2eb'
  inverse-on-surface: '#2e3037'
  outline: '#849495'
  outline-variant: '#3b494b'
  surface-tint: '#00dbe9'
  primary: '#dbfcff'
  on-primary: '#00363a'
  primary-container: '#00f0ff'
  on-primary-container: '#006970'
  inverse-primary: '#006970'
  secondary: '#d8b9ff'
  on-secondary: '#450086'
  secondary-container: '#6e06d0'
  on-secondary-container: '#d5b5ff'
  tertiary: '#f2f6ff'
  on-tertiary: '#2b3139'
  tertiary-container: '#d4dae4'
  on-tertiary-container: '#595f68'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#7df4ff'
  primary-fixed-dim: '#00dbe9'
  on-primary-fixed: '#002022'
  on-primary-fixed-variant: '#004f54'
  secondary-fixed: '#eddcff'
  secondary-fixed-dim: '#d8b9ff'
  on-secondary-fixed: '#290055'
  on-secondary-fixed-variant: '#6200bc'
  tertiary-fixed: '#dde3ed'
  tertiary-fixed-dim: '#c1c7d1'
  on-tertiary-fixed: '#161c23'
  on-tertiary-fixed-variant: '#414750'
  background: '#10131a'
  on-background: '#e1e2eb'
  surface-variant: '#32353c'
typography:
  headline-lg:
    fontFamily: Space Grotesk
    fontSize: 32px
    fontWeight: '700'
    lineHeight: '1.2'
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Space Grotesk
    fontSize: 24px
    fontWeight: '600'
    lineHeight: '1.3'
  code-display:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '500'
    lineHeight: '1.5'
    letterSpacing: -0.01em
  body-main:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.6'
  body-sm:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: '1.5'
  label-caps:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '700'
    lineHeight: '1'
    letterSpacing: 0.1em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  base: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 40px
  margin-mobile: 20px
  gutter: 12px
---

## Brand & Style

This design system establishes a high-performance environment for "vibe coding," merging the raw efficiency of a terminal with the polished tactility of a premium mobile OS. The brand personality is hyper-intelligent, focused, and futuristic. It targets "creative engineers" — users who prioritize speed and AI-assisted workflows but demand a sophisticated visual experience.

The design style is **Glass-Terminal Minimalism**. It utilizes deep, multi-layered dark surfaces to create a sense of infinite space, punctuated by high-vibrancy "digital light" accents. Glassmorphism is used sparingly to indicate focus and transient layers, while subtle glows around key elements simulate the flicker of a high-end monitor. The emotional response is one of "flow state" mastery and technical confidence.

## Colors

The palette is optimized for long coding sessions and high visual impact.

- **Primary (Neon Cyan):** Reserved for core execution actions, active code states, and progress indicators.
- **Secondary (Electric Purple):** Exclusively signifies AI intelligence, agentic processes, and predictive features.
- **Neutral/Surface:** The background strategy uses **Obsidian (#0B0E14)** for the lowest layer and **Deep Indigo (#151921)** for interactive surfaces and cards.
- **Borders:** A consistent **#2D333B** provides structural definition without breaking the dark-mode immersion.
- **Glows:** Use 15% opacity versions of the Primary and Secondary colors for soft outer glows on active states.

## Typography

This design system uses a dual-font approach to balance futurism with technical clarity.

- **Headlines:** Use **Space Grotesk** for a geometric, high-tech feel. It provides the "interface" personality.
- **Body & UI:** Use **Inter** for its exceptional readability in dense information environments.
- **Monospace Simulation:** While Inter is used for general UI, ensure code snippets and terminal outputs use a high-quality monospaced font (like JetBrains Mono) for structural alignment.
- **Visual Hierarchy:** Contrast is achieved through weight and letter-spacing rather than excessive size changes. Use the `label-caps` style for category headers and technical metadata.

## Layout & Spacing

The layout philosophy follows a **Tight Technical Grid**. On mobile, we utilize a fluid 4-column system with a generous 20px side margin to prevent content from feeling "trapped."

- **Rhythm:** All spacing must be multiples of 4px.
- **Density:** Elements are packed relatively tightly to mimic a developer environment, using 12px gutters between cards.
- **Safe Areas:** Adhere to system-level safe areas for bottom navigation bars and top notch regions, but extend background gradients and glass blurs into these areas for a seamless look.

## Elevation & Depth

Depth is conveyed through **Z-axis Layering** and **Luminosity**, rather than traditional drop shadows.

1. **Level 0 (Base):** Obsidian (#0B0E14). The "floor" of the app.
2. **Level 1 (Cards/Lists):** Deep Indigo (#151921) with a 1px border (#2D333B).
3. **Level 2 (Modals/Overlays):** Glassmorphic surfaces with a 40px Backdrop Blur and 60% opacity.
4. **Interactive Focus:** Elements "lift" by increasing the border brightness or adding a subtle 8px blur glow using the Primary or Secondary accent color.

Avoid heavy shadows; instead, use inner glows or subtle linear gradients (top-to-bottom, #FFFFFF at 5% opacity to #FFFFFF at 0%) on the top edge of elements to suggest a light source from above.

## Shapes

The shape language is **Technical & Precise**. We avoid overly organic or "bubbly" curves to maintain a professional, tool-like aesthetic.

- **Primary Corners:** 4px (Soft) for buttons, inputs, and small modules.
- **Container Corners:** 8px (rounded-lg) for main cards and content blocks.
- **Large Components:** 12px (rounded-xl) for bottom sheets and modals.
- **Interactive States:** Use sharp, 1px strokes for focus states to emphasize precision.

## Components

### Buttons

- **Primary:** Solid Neon Cyan with black text. No shadow, but a 4px Cyan glow on active states.
- **AI/Agent:** Gradient fill (Electric Purple to a deeper violet).
- **Secondary:** Ghost style with #2D333B borders and white text.

### Input Fields

Obsidian background, 1px #2D333B border. On focus, the border turns Neon Cyan and the label "floats" above.

### Cards

Deep Indigo background. Use a subtle vertical gradient (lighter at the top). Headers within cards should be separated by a thin #2D333B horizontal rule.

### Terminal Snippets

A specialized card with a darker background (#05070A) and a "Copy" icon in the top right.

### Chips / Badges

Small, 4px rounded shapes with low-opacity fills (10%) and high-opacity text of the same color (e.g., Purple for 'AI-Generated').

### Status Indicators

Pulse animations for active "vibe coding" sessions using the Neon Cyan color.

### Agent Drawer

A bottom sheet with a heavy glassmorphic background blur, used exclusively for interacting with the AI agent.

## Implementation Notes

To achieve "Real" blur on Android:

- **Android 12+ (API 31):** Native `RenderEffect.createBlurEffect`.
- **Android 8-11:** Fallback to a high-quality semi-transparent gradient or use `Haze` / `Cloudy` libraries (Toolkit).
- **Library Recommendation:** `dev.chrisbanes.haze:haze` for performant, backward-compatible blur effects in Compose.

## Animations

- **Transitions:** Elements shouldn't just "appear". They should **fade and scale up** (as if coming closer through the glass mist).
- **Parallax:** The background gradient should shift slightly as the user tilts the device (Gyroscope) or scrolls, enhancing the depth effect.
