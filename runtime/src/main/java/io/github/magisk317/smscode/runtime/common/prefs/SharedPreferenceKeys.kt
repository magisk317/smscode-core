package io.github.magisk317.smscode.runtime.common.prefs

/**
 * Preference keys whose *meaning* is shared between the smscode-core consumers
 * (XposedSmsCode, xinyi-relay).
 *
 * The store itself is key-agnostic — it accepts string keys from callers — but
 * the shared layer still needs a few names for its own behaviour (the backup
 * exclusion list and the compat tip flag) and for the appearance settings that
 * every consumer exposes with identical defaults.
 *
 * Consumers may keep their own constants for these; the values must match.
 */
object SharedPreferenceKeys {
    /** One-shot flag for the backup-format compatibility tip. */
    const val BACKUP_COMPAT_TIP_SHOWN = "pref_backup_compat_tip_shown"

    /** Entitlement token — never included in a backup snapshot. */
    const val MOBILE_ENTITLEMENT_TOKEN = "entitlement_token"

    /** Entitlement automation switch — never included in a backup snapshot. */
    const val MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED = "mobile_entitlement_automation_allowed"

    /** Keys excluded from [AppPreferencesDataStore.snapshotForBackup] by default. */
    val BACKUP_EXCLUDED: Set<String> = setOf(
        MOBILE_ENTITLEMENT_TOKEN,
        MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED,
    )

    /**
     * Appearance keys — identical names and defaults across consumers, so a
     * backup taken on one app restores meaningfully on another.
     */
    object Appearance {
        /** 0 Compact, 1 Standard, 2 Comfortable. */
        const val LAYOUT_SCALE = "pref_layout_scale"

        /** 0 TonalSpot … 8 Content (`UiKitPaletteStyle`). */
        const val PALETTE_STYLE = "pref_palette_style"

        /** 0 Spec2021, 1 Spec2025 (`UiKitColorSpec`). */
        const val COLOR_SPEC = "pref_color_spec"

        /** Accent color ARGB; 0 means "follow system". */
        const val ACCENT_COLOR = "pref_accent_color"

        const val MONET_ENABLED = "pref_monet_enabled"
        const val SURFACE_BLUR = "pref_surface_blur"
        const val DYNAMIC_COLOR = "pref_dynamic_color"

        /** Navigation group. */
        const val FLOATING_BOTTOM_BAR = "pref_floating_bottom_bar"
        const val BOTTOM_BAR_BLUR = "pref_bottom_bar_blur"
        const val BOTTOM_BAR_BACKDROP = "pref_bottom_bar_backdrop"
        const val NAVIGATION_BADGES = "pref_navigation_badges"

        // Defaults mirror MiPushFramework's appearance defaults.
        const val DEFAULT_LAYOUT_SCALE = 1
        const val DEFAULT_PALETTE_STYLE = 0
        const val DEFAULT_COLOR_SPEC = 1
        const val DEFAULT_ACCENT_COLOR = 0
        const val DEFAULT_MONET_ENABLED = false
        const val DEFAULT_SURFACE_BLUR = false
        const val DEFAULT_DYNAMIC_COLOR = true
        const val DEFAULT_FLOATING_BOTTOM_BAR = true
        const val DEFAULT_BOTTOM_BAR_BLUR = false
        const val DEFAULT_BOTTOM_BAR_BACKDROP = false
        const val DEFAULT_NAVIGATION_BADGES = true
    }
}
