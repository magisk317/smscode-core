package io.github.magisk317.smscode.runtime.common.prefs

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Typed accessors for the appearance settings every consumer exposes.
 *
 * Keys and defaults live in [SharedPreferenceKeys.Appearance] so the three apps
 * (XposedSmsCode, xinyi-relay, and anything else consuming this module) agree
 * on names and defaults — a backup taken by one app restores meaningfully in
 * another.
 *
 * Storage is delegated to [AppPreferencesDataStore], so these reads and writes
 * follow the same DataStore transaction rules as every other preference.
 */
object AppearancePreferences {
    // ---- Layout scale: 0 Compact, 1 Standard, 2 Comfortable ----

    suspend fun layoutScale(context: Context): Int = AppPreferencesDataStore.getInt(
        context,
        SharedPreferenceKeys.Appearance.LAYOUT_SCALE,
        SharedPreferenceKeys.Appearance.DEFAULT_LAYOUT_SCALE,
    )

    suspend fun setLayoutScale(context: Context, value: Int) = AppPreferencesDataStore.setInt(
        context,
        SharedPreferenceKeys.Appearance.LAYOUT_SCALE,
        value,
    )

    fun layoutScaleFlow(context: Context): Flow<Int> = AppPreferencesDataStore.getIntFlow(
        context,
        SharedPreferenceKeys.Appearance.LAYOUT_SCALE,
        SharedPreferenceKeys.Appearance.DEFAULT_LAYOUT_SCALE,
    )

    // ---- Palette style: 0 TonalSpot ... 8 Content ----

    suspend fun paletteStyle(context: Context): Int = AppPreferencesDataStore.getInt(
        context,
        SharedPreferenceKeys.Appearance.PALETTE_STYLE,
        SharedPreferenceKeys.Appearance.DEFAULT_PALETTE_STYLE,
    )

    suspend fun setPaletteStyle(context: Context, value: Int) = AppPreferencesDataStore.setInt(
        context,
        SharedPreferenceKeys.Appearance.PALETTE_STYLE,
        value,
    )

    fun paletteStyleFlow(context: Context): Flow<Int> = AppPreferencesDataStore.getIntFlow(
        context,
        SharedPreferenceKeys.Appearance.PALETTE_STYLE,
        SharedPreferenceKeys.Appearance.DEFAULT_PALETTE_STYLE,
    )

    // ---- Colour spec: 0 Spec2021, 1 Spec2025 ----

    suspend fun colorSpec(context: Context): Int = AppPreferencesDataStore.getInt(
        context,
        SharedPreferenceKeys.Appearance.COLOR_SPEC,
        SharedPreferenceKeys.Appearance.DEFAULT_COLOR_SPEC,
    )

    suspend fun setColorSpec(context: Context, value: Int) = AppPreferencesDataStore.setInt(
        context,
        SharedPreferenceKeys.Appearance.COLOR_SPEC,
        value,
    )

    fun colorSpecFlow(context: Context): Flow<Int> = AppPreferencesDataStore.getIntFlow(
        context,
        SharedPreferenceKeys.Appearance.COLOR_SPEC,
        SharedPreferenceKeys.Appearance.DEFAULT_COLOR_SPEC,
    )

    // ---- Accent colour: ARGB, 0 = follow system ----

    suspend fun accentColor(context: Context): Int = AppPreferencesDataStore.getInt(
        context,
        SharedPreferenceKeys.Appearance.ACCENT_COLOR,
        SharedPreferenceKeys.Appearance.DEFAULT_ACCENT_COLOR,
    )

    suspend fun setAccentColor(context: Context, argb: Int) = AppPreferencesDataStore.setInt(
        context,
        SharedPreferenceKeys.Appearance.ACCENT_COLOR,
        argb,
    )

    fun accentColorFlow(context: Context): Flow<Int> = AppPreferencesDataStore.getIntFlow(
        context,
        SharedPreferenceKeys.Appearance.ACCENT_COLOR,
        SharedPreferenceKeys.Appearance.DEFAULT_ACCENT_COLOR,
    )

    // ---- Colour toggles ----

    suspend fun monetEnabled(context: Context): Boolean = AppPreferencesDataStore.getBoolean(
        context,
        SharedPreferenceKeys.Appearance.MONET_ENABLED,
        SharedPreferenceKeys.Appearance.DEFAULT_MONET_ENABLED,
    )

    suspend fun setMonetEnabled(context: Context, enabled: Boolean) = AppPreferencesDataStore.setBoolean(
        context,
        SharedPreferenceKeys.Appearance.MONET_ENABLED,
        enabled,
    )

    fun monetEnabledFlow(context: Context): Flow<Boolean> = AppPreferencesDataStore.getBooleanFlow(
        context,
        SharedPreferenceKeys.Appearance.MONET_ENABLED,
        SharedPreferenceKeys.Appearance.DEFAULT_MONET_ENABLED,
    )

    suspend fun surfaceBlurEnabled(context: Context): Boolean = AppPreferencesDataStore.getBoolean(
        context,
        SharedPreferenceKeys.Appearance.SURFACE_BLUR,
        SharedPreferenceKeys.Appearance.DEFAULT_SURFACE_BLUR,
    )

    suspend fun setSurfaceBlurEnabled(context: Context, enabled: Boolean) = AppPreferencesDataStore.setBoolean(
        context,
        SharedPreferenceKeys.Appearance.SURFACE_BLUR,
        enabled,
    )

    fun surfaceBlurFlow(context: Context): Flow<Boolean> = AppPreferencesDataStore.getBooleanFlow(
        context,
        SharedPreferenceKeys.Appearance.SURFACE_BLUR,
        SharedPreferenceKeys.Appearance.DEFAULT_SURFACE_BLUR,
    )

    suspend fun dynamicColorEnabled(context: Context): Boolean = AppPreferencesDataStore.getBoolean(
        context,
        SharedPreferenceKeys.Appearance.DYNAMIC_COLOR,
        SharedPreferenceKeys.Appearance.DEFAULT_DYNAMIC_COLOR,
    )

    suspend fun setDynamicColorEnabled(context: Context, enabled: Boolean) = AppPreferencesDataStore.setBoolean(
        context,
        SharedPreferenceKeys.Appearance.DYNAMIC_COLOR,
        enabled,
    )

    fun dynamicColorFlow(context: Context): Flow<Boolean> = AppPreferencesDataStore.getBooleanFlow(
        context,
        SharedPreferenceKeys.Appearance.DYNAMIC_COLOR,
        SharedPreferenceKeys.Appearance.DEFAULT_DYNAMIC_COLOR,
    )

    // ---- Navigation group ----

    suspend fun floatingBottomBarEnabled(context: Context): Boolean = AppPreferencesDataStore.getBoolean(
        context,
        SharedPreferenceKeys.Appearance.FLOATING_BOTTOM_BAR,
        SharedPreferenceKeys.Appearance.DEFAULT_FLOATING_BOTTOM_BAR,
    )

    suspend fun setFloatingBottomBarEnabled(context: Context, enabled: Boolean) = AppPreferencesDataStore.setBoolean(
        context,
        SharedPreferenceKeys.Appearance.FLOATING_BOTTOM_BAR,
        enabled,
    )

    fun floatingBottomBarFlow(context: Context): Flow<Boolean> = AppPreferencesDataStore.getBooleanFlow(
        context,
        SharedPreferenceKeys.Appearance.FLOATING_BOTTOM_BAR,
        SharedPreferenceKeys.Appearance.DEFAULT_FLOATING_BOTTOM_BAR,
    )

    suspend fun bottomBarBlurEnabled(context: Context): Boolean = AppPreferencesDataStore.getBoolean(
        context,
        SharedPreferenceKeys.Appearance.BOTTOM_BAR_BLUR,
        SharedPreferenceKeys.Appearance.DEFAULT_BOTTOM_BAR_BLUR,
    )

    suspend fun setBottomBarBlurEnabled(context: Context, enabled: Boolean) = AppPreferencesDataStore.setBoolean(
        context,
        SharedPreferenceKeys.Appearance.BOTTOM_BAR_BLUR,
        enabled,
    )

    fun bottomBarBlurFlow(context: Context): Flow<Boolean> = AppPreferencesDataStore.getBooleanFlow(
        context,
        SharedPreferenceKeys.Appearance.BOTTOM_BAR_BLUR,
        SharedPreferenceKeys.Appearance.DEFAULT_BOTTOM_BAR_BLUR,
    )

    suspend fun bottomBarBackdropEnabled(context: Context): Boolean = AppPreferencesDataStore.getBoolean(
        context,
        SharedPreferenceKeys.Appearance.BOTTOM_BAR_BACKDROP,
        SharedPreferenceKeys.Appearance.DEFAULT_BOTTOM_BAR_BACKDROP,
    )

    suspend fun setBottomBarBackdropEnabled(context: Context, enabled: Boolean) = AppPreferencesDataStore.setBoolean(
        context,
        SharedPreferenceKeys.Appearance.BOTTOM_BAR_BACKDROP,
        enabled,
    )

    fun bottomBarBackdropFlow(context: Context): Flow<Boolean> = AppPreferencesDataStore.getBooleanFlow(
        context,
        SharedPreferenceKeys.Appearance.BOTTOM_BAR_BACKDROP,
        SharedPreferenceKeys.Appearance.DEFAULT_BOTTOM_BAR_BACKDROP,
    )

    suspend fun navigationBadgesEnabled(context: Context): Boolean = AppPreferencesDataStore.getBoolean(
        context,
        SharedPreferenceKeys.Appearance.NAVIGATION_BADGES,
        SharedPreferenceKeys.Appearance.DEFAULT_NAVIGATION_BADGES,
    )

    suspend fun setNavigationBadgesEnabled(context: Context, enabled: Boolean) = AppPreferencesDataStore.setBoolean(
        context,
        SharedPreferenceKeys.Appearance.NAVIGATION_BADGES,
        enabled,
    )

    fun navigationBadgesFlow(context: Context): Flow<Boolean> = AppPreferencesDataStore.getBooleanFlow(
        context,
        SharedPreferenceKeys.Appearance.NAVIGATION_BADGES,
        SharedPreferenceKeys.Appearance.DEFAULT_NAVIGATION_BADGES,
    )
}
