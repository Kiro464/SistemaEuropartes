package com.europartes.mobile.ui.components

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Currency the amounts are shown in, chosen from the top bar or Configuración, like the web. */
object DisplayCurrency {
    const val PREFERENCES = "europartes_sales_display"
    const val KEY = "currency"
    private var state: MutableStateFlow<String>? = null

    fun flow(context: Context): StateFlow<String> = stateFor(context)

    fun set(context: Context, currency: String) {
        if (currency != "NIO" && currency != "USD") return
        stateFor(context).value = currency
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(KEY, currency).apply()
    }

    private fun stateFor(context: Context): MutableStateFlow<String> = state ?: run {
        val saved = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY, "NIO")
        MutableStateFlow(saved?.takeIf { it == "NIO" || it == "USD" } ?: "NIO").also { state = it }
    }
}

/** Formats amounts stored in córdobas for the selected currency. */
data class MoneyFormat(val currency: String = "NIO", val exchangeRate: String? = null) {
    private val rate: BigDecimal? get() = exchangeRate?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

    /** USD needs a rate from the server; without one amounts stay in córdobas instead of being invented. */
    val effectiveCurrency: String get() = if (currency == "USD" && rate != null) "USD" else "NIO"
    val symbol: String get() = if (effectiveCurrency == "USD") "US$" else "C$"
    val rateUnavailable: Boolean get() = currency == "USD" && rate == null

    fun format(value: String?): String = value?.toBigDecimalOrNull()?.let(::format) ?: "—"

    fun format(value: BigDecimal): String {
        val shown = if (effectiveCurrency == "USD") value.divide(rate, 2, RoundingMode.HALF_UP) else value.setScale(2, RoundingMode.HALF_UP)
        return "$symbol ${DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US)).format(shown)}"
    }
}

val LocalMoney = compositionLocalOf { MoneyFormat() }

/** Dates arrive as ISO text; unknown shapes are shown as received rather than hidden. */
fun formatDisplayDate(value: String?): String {
    if (value.isNullOrBlank()) return "—"
    val pattern = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("es"))
    runCatching { return java.time.OffsetDateTime.parse(value).format(pattern) }
    runCatching { return java.time.LocalDateTime.parse(value.trim().replace(" ", "T")).format(pattern) }
    runCatching { return java.time.LocalDate.parse(value).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es"))) }
    return value
}

