package com.europartes.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.europartes.mobile.ui.theme.EuropartesTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The web's `<input type="date">`. The value stays in the ISO format the API filters expect. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EuropartesDateField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val parsed = remember(value) { runCatching { LocalDate.parse(value) }.getOrNull() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        FieldLabel(label)
        Surface(
            onClick = { open = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = ControlHeight).pressScale(source, 0.995f),
            shape = ControlShape,
            color = if (enabled) colors.surface else colors.surfaceVariant,
            border = BorderStroke(1.dp, if (enabled) colors.outlineVariant else colors.outline),
            interactionSource = source,
        ) {
            Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (parsed != null) formatDisplayDate(value) else "dd/mm/aaaa",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (parsed != null) colors.onSurface else EuropartesTheme.colors.muted,
                    maxLines = 1,
                )
                if (parsed != null && enabled) Icon(Icons.Outlined.Close, contentDescription = "Quitar fecha", Modifier.size(18.dp).clip(CircleShape).clickable { onChange("") }, tint = EuropartesTheme.colors.muted)
                else Icon(Icons.Outlined.CalendarMonth, contentDescription = null, Modifier.size(18.dp), tint = EuropartesTheme.colors.muted)
            }
        }
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = parsed?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                EuropartesButton("Aceptar", {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    open = false
                }, Modifier.padding(end = 8.dp, bottom = 8.dp), small = true)
            },
            dismissButton = { EuropartesButton("Cancelar", { open = false }, Modifier.padding(end = 8.dp, bottom = 8.dp), variant = ButtonVariant.Secondary, small = true) },
        ) { DatePicker(state, title = null, headline = null, showModeToggle = false) }
    }
}
