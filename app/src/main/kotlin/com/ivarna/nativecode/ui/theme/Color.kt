package com.ivarna.nativecode.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// Obsidian Nexus Design System - Color Tokens
// =============================================================================

// --- Primary (Neon Cyan) ---
val ObsidianPrimary = Color(0xFF00dbe9)
val ObsidianOnPrimary = Color(0xFF00363a)
val ObsidianPrimaryContainer = Color(0xFF006970)
val ObsidianOnPrimaryContainer = Color(0xFFdbfcff)
val ObsidianInversePrimary = Color(0xFF006970)

// --- Secondary (Electric Purple) ---
val ObsidianSecondary = Color(0xFFd8b9ff)
val ObsidianOnSecondary = Color(0xFF450086)
val ObsidianSecondaryContainer = Color(0xFF6e06d0)
val ObsidianOnSecondaryContainer = Color(0xFFd5b5ff)

// --- Tertiary ---
val ObsidianTertiary = Color(0xFFc1c7d1)
val ObsidianOnTertiary = Color(0xFF2b3139)
val ObsidianTertiaryContainer = Color(0xFF595f68)
val ObsidianOnTertiaryContainer = Color(0xFFf2f6ff)

// --- Error ---
val ObsidianError = Color(0xFFFFb4ab)
val ObsidianOnError = Color(0xFF690005)
val ObsidianErrorContainer = Color(0xFF93000a)
val ObsidianOnErrorContainer = Color(0xFFffdad6)

// --- Surface (Dark) ---
val ObsidianBackground = Color(0xFF10131a)
val ObsidianOnBackground = Color(0xFFe1e2eb)
val ObsidianSurface = Color(0xFF1d2026)
val ObsidianSurfaceDim = Color(0xFF10131a)
val ObsidianSurfaceBright = Color(0xFF363940)
val ObsidianSurfaceContainerLowest = Color(0xFF0b0e14)
val ObsidianSurfaceContainerLow = Color(0xFF191c22)
val ObsidianSurfaceContainer = Color(0xFF1d2026)
val ObsidianSurfaceContainerHigh = Color(0xFF272a31)
val ObsidianSurfaceContainerHighest = Color(0xFF32353c)
val ObsidianSurfaceVariant = Color(0xFF32353c)
val ObsidianOnSurface = Color(0xFFe1e2eb)
val ObsidianOnSurfaceVariant = Color(0xFFb9cacb)
val ObsidianSurfaceTint = Color(0xFF00dbe9)
val ObsidianInverseSurface = Color(0xFFe1e2eb)
val ObsidianInverseOnSurface = Color(0xFF2e3037)
val ObsidianOutline = Color(0xFF2D333B)
val ObsidianOutlineVariant = Color(0xFF3b494b)

// --- Light Surface ---
val ObsidianLightBackground = Color(0xFFF8F9FC)
val ObsidianLightOnBackground = Color(0xFF10131a)
val ObsidianLightSurface = Color(0xFFFFFFFF)
val ObsidianLightSurfaceDim = Color(0xFFD8DAE0)
val ObsidianLightSurfaceBright = Color(0xFFF8F9FC)
val ObsidianLightSurfaceContainerLowest = Color(0xFFFFFFFF)
val ObsidianLightSurfaceContainerLow = Color(0xFFF1F3F8)
val ObsidianLightSurfaceContainer = Color(0xFFECEEF4)
val ObsidianLightSurfaceContainerHigh = Color(0xFFE6E8EE)
val ObsidianLightSurfaceContainerHighest = Color(0xFFE1E2EB)
val ObsidianLightSurfaceVariant = Color(0xFFE1E2EB)
val ObsidianLightOnSurface = Color(0xFF10131a)
val ObsidianLightOnSurfaceVariant = Color(0xFF595f68)
val ObsidianLightInverseSurface = Color(0xFF2e3037)
val ObsidianLightInverseOnSurface = Color(0xFFF0F2F5)
val ObsidianLightOutline = Color(0xFFc4c7cf)
val ObsidianLightOutlineVariant = Color(0xFFd4dae4)

// --- Light Primary ---
val ObsidianLightPrimary = Color(0xFF006970)
val ObsidianLightOnPrimary = Color(0xFFFFFFFF)
val ObsidianLightPrimaryContainer = Color(0xFF7df4ff)
val ObsidianLightOnPrimaryContainer = Color(0xFF002022)
val ObsidianLightInversePrimary = Color(0xFF00dbe9)

// --- Light Secondary ---
val ObsidianLightSecondary = Color(0xFF6200bc)
val ObsidianLightOnSecondary = Color(0xFFFFFFFF)
val ObsidianLightSecondaryContainer = Color(0xFFeddcff)
val ObsidianLightOnSecondaryContainer = Color(0xFF290055)

// --- Light Tertiary ---
val ObsidianLightTertiary = Color(0xFF414750)
val ObsidianLightOnTertiary = Color(0xFFFFFFFF)
val ObsidianLightTertiaryContainer = Color(0xFFdde3ed)
val ObsidianLightOnTertiaryContainer = Color(0xFF161c23)

// --- Light Error ---
val ObsidianLightError = Color(0xFF93000a)
val ObsidianLightOnError = Color(0xFFFFFFFF)
val ObsidianLightErrorContainer = Color(0xFFffdad6)
val ObsidianLightOnErrorContainer = Color(0xFF690005)

// --- Accent Glows (Theme-agnostic) ---
val NeonCyan = Color(0xFF00dbe9)
val NeonCyan15 = Color(0x2600dbe9)
val ElectricPurple = Color(0xFFd8b9ff)
val ElectricPurple15 = Color(0x26d8b9ff)

// =============================================================================
// Legacy aliases for backward compatibility during migration
// =============================================================================
@Deprecated("Use MaterialTheme.colorScheme.primary instead")
val FluxCream = ObsidianPrimary
@Deprecated("Use MaterialTheme.colorScheme.primary instead")
val FluxCreamPrimary = ObsidianPrimary
@Deprecated("Use MaterialTheme.colorScheme.primary instead")
val BrandCream = ObsidianPrimary
@Deprecated("Use MaterialTheme.colorScheme.surface instead")
val FluxDarkGrey = ObsidianSurface
@Deprecated("Use MaterialTheme.colorScheme.background instead")
val FluxDarkSurface = ObsidianBackground
@Deprecated("Use NeonCyan instead")
val FluxAccentCyan = NeonCyan
@Deprecated("Use ElectricPurple instead")
val FluxAccentMagenta = ElectricPurple
@Deprecated("Use ObsidianBackground instead")
val FluxBackgroundStart = ObsidianBackground
@Deprecated("Use ObsidianSurface instead")
val FluxBackgroundMid = ObsidianSurface
@Deprecated("Use ObsidianSurfaceContainerHighest instead")
val FluxBackgroundEnd = ObsidianSurfaceContainerHighest
@Deprecated("Use MaterialTheme.colorScheme.outlineVariant instead")
val GlassWhiteHigh = Color(0x26FFFFFF)
@Deprecated("Use MaterialTheme.colorScheme.outlineVariant instead")
val GlassWhiteMedium = Color(0x1AFFFFFF)
@Deprecated("Use MaterialTheme.colorScheme.outlineVariant instead")
val GlassWhiteLow = Color(0x0DFFFFFF)
@Deprecated("Use MaterialTheme.colorScheme.outline instead")
val GlassBorder = Color(0x4DFFFFFF)
@Deprecated("Use MaterialTheme.colorScheme.onSurface instead")
val TextWhite = ObsidianOnSurface
@Deprecated("Use MaterialTheme.colorScheme.onSurfaceVariant instead")
val TextGrey = ObsidianOnSurfaceVariant
@Deprecated("Use ObsidianPrimary instead")
val Seed = ObsidianPrimary
