package com.europartes.mobile.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val label: String) { LIGHT("Claro"), DARK("Oscuro"), SYSTEM("Sistema") }

/** Device-level appearance choice, the same three options the web app offers. */
object ThemePreference {
    private const val PREFERENCES = "europartes_appearance"
    private const val KEY = "theme_mode"
    private var state: MutableStateFlow<ThemeMode>? = null

    fun mode(context: Context): StateFlow<ThemeMode> = stateFor(context)

    fun set(context: Context, mode: ThemeMode) {
        stateFor(context).value = mode
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(KEY, mode.name).apply()
    }

    private fun stateFor(context: Context): MutableStateFlow<ThemeMode> = state ?: run {
        val saved = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY, null)
        MutableStateFlow(ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.SYSTEM).also { state = it }
    }
}
