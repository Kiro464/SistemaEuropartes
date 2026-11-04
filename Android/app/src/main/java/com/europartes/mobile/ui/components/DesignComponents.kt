package com.europartes.mobile.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.europartes.mobile.ui.theme.EuropartesTheme
import com.europartes.mobile.ui.theme.tabular

// Geometry taken from Frontend/css/brand.css.
val CardShape = RoundedCornerShape(10.dp)
val ControlShape = RoundedCornerShape(7.dp)
val ControlHeight = 44.dp
val SmallControlHeight = 38.dp
val PagePadding = 16.dp
val SectionGap = 12.dp

/** Strong ease-out: starts fast so the interface feels like it answered at once. */
val EaseOutStrong = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** Pressable feedback shared by every control, the web's `:active { transform: scale(.98) }`. */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = 0.98f): Modifier {
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, tween(120, easing = EaseOutStrong), label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

enum class ButtonVariant { Primary, Secondary, Danger, Success, Ghost }

@Composable
fun EuropartesButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    small: Boolean = false,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    val extended = EuropartesTheme.colors
    val container: Color
    val content: Color
    val border: BorderStroke?
    when (variant) {
        ButtonVariant.Primary -> { container = colors.primary; content = colors.onPrimary; border = null }
        ButtonVariant.Secondary -> { container = colors.surface; content = colors.onSurface; border = BorderStroke(1.dp, colors.outlineVariant) }
        ButtonVariant.Danger -> { container = colors.surface; content = colors.error; border = BorderStroke(1.dp, colors.error.copy(alpha = 0.45f)) }
        ButtonVariant.Success -> { container = extended.success; content = colors.surface; border = null }
        ButtonVariant.Ghost -> { container = Color.Transparent; content = colors.primary; border = null }
    }
    val active = enabled && !isLoading
    val source = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        enabled = active,
        modifier = modifier.heightIn(min = if (small) SmallControlHeight else ControlHeight).pressScale(source).graphicsLayer { alpha = if (active) 1f else 0.5f },
        shape = ControlShape,
        color = container,
        contentColor = content,
        border = border,
        interactionSource = source,
    ) {
        Row(
            Modifier.padding(horizontal = if (small) 12.dp else 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), color = content, strokeWidth = 2.dp)
            else if (icon != null) Icon(icon, contentDescription = null, Modifier.size(18.dp))
            Text(text, style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun EuropartesPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) = EuropartesButton(text, onClick, modifier.fillMaxWidth(), enabled = enabled, isLoading = isLoading)

/** Label above the control, as in the web forms. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier, required: Boolean = false) {
    Row(modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (required) Text(" *", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EuropartesTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
    supportingText: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    required: Boolean = false,
    placeholder: String? = null,
    multiline: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.outlineVariant,
        disabledBorderColor = colors.outline,
        focusedContainerColor = colors.surface,
        unfocusedContainerColor = colors.surface,
        disabledContainerColor = colors.surfaceVariant,
        cursorColor = colors.primary,
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (label.isNotEmpty()) FieldLabel(label, required = required)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = ControlHeight),
            enabled = enabled,
            singleLine = !multiline,
            minLines = if (multiline) 3 else 1,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = if (enabled) colors.onSurface else colors.onSurfaceVariant),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            interactionSource = source,
            decorationBox = { inner ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = inner,
                    enabled = enabled,
                    singleLine = !multiline,
                    visualTransformation = visualTransformation,
                    interactionSource = source,
                    placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyMedium, color = EuropartesTheme.colors.muted) } },
                    leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, Modifier.size(18.dp), tint = EuropartesTheme.colors.muted) } },
                    trailingIcon = trailingContent,
                    colors = fieldColors,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = enabled,
                            isError = false,
                            interactionSource = source,
                            colors = fieldColors,
                            shape = ControlShape,
                            focusedBorderThickness = 1.5.dp,
                            unfocusedBorderThickness = 1.dp,
                        )
                    },
                )
            },
        )
        supportingText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted) }
    }
}

@Composable
fun EuropartesSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    EuropartesTextField(
        value = value,
        onValueChange = onValueChange,
        label = "",
        modifier = modifier,
        enabled = enabled,
        leadingIcon = Icons.Outlined.Search,
        placeholder = placeholder,
        trailingContent = if (value.isEmpty()) null else {
            { Icon(Icons.Outlined.Close, contentDescription = "Limpiar búsqueda", Modifier.size(18.dp).clip(CircleShape).clickable { onValueChange("") }) }
        },
    )
}

data class SelectOption<T>(val value: T, val label: String, val detail: String? = null)

/**
 * The web's `<select>`. Short lists open anchored to the field; long ones open as a searchable
 * sheet, because scrolling a dropdown of a whole catalog is not workable on a phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> EuropartesSelect(
    label: String,
    options: List<SelectOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Seleccionar",
    enabled: Boolean = true,
    required: Boolean = false,
    selectedLabel: String? = null,
    supportingText: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val current = selectedLabel ?: options.firstOrNull { it.value == selected }?.label
    val source = remember { MutableInteractionSource() }
    val searchable = options.size > 7
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (label.isNotEmpty()) FieldLabel(label, required = required)
        Box {
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
                        current ?: placeholder,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (current == null) EuropartesTheme.colors.muted else if (enabled) colors.onSurface else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Outlined.ExpandMore, contentDescription = null, Modifier.size(20.dp), tint = EuropartesTheme.colors.muted)
                }
            }
            if (!searchable) DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = CardShape, border = BorderStroke(1.dp, colors.outline), shadowElevation = 6.dp, containerColor = EuropartesTheme.colors.raised) {
                if (options.isEmpty()) DropdownMenuItem(text = { Text("No hay opciones disponibles", style = MaterialTheme.typography.bodyMedium) }, onClick = { open = false }, enabled = false)
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { SelectOptionText(option) },
                        trailingIcon = if (option.value == selected) { { Icon(Icons.Outlined.Check, contentDescription = "Seleccionado", Modifier.size(18.dp), tint = colors.primary) } } else null,
                        onClick = { onSelect(option.value); open = false },
                    )
                }
            }
        }
        supportingText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted) }
    }
    if (searchable && open) {
        var query by remember { mutableStateOf("") }
        val visible = remember(options, query) {
            options.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) || it.detail.orEmpty().contains(query.trim(), ignoreCase = true) }
        }
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface,
            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = PagePadding).padding(bottom = 8.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(label.ifEmpty { placeholder }, style = MaterialTheme.typography.titleLarge)
                EuropartesSearchField(query, { query = it }, "Buscar")
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    if (visible.isEmpty()) item { Text("Sin coincidencias", Modifier.padding(vertical = 18.dp), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
                    items(visible) { option ->
                        Row(
                            Modifier.fillMaxWidth().clip(ControlShape).clickable { onSelect(option.value); open = false }.heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.weight(1f)) { SelectOptionText(option) }
                            if (option.value == selected) Icon(Icons.Outlined.Check, contentDescription = "Seleccionado", Modifier.size(18.dp), tint = colors.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectOptionText(option: SelectOption<*>) {
    Column {
        Text(option.label, style = MaterialTheme.typography.bodyMedium)
        option.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted) }
    }
}

enum class StatusTone { Success, Warning, Danger, Info, Neutral }

/** The web's `.status-badge`: small dot and a soft fill. */
@Composable
fun StatusPill(text: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val extended = EuropartesTheme.colors
    val colors = MaterialTheme.colorScheme
    val (background, foreground) = when (tone) {
        StatusTone.Success -> extended.successSoft to extended.success
        StatusTone.Warning -> extended.warningSoft to extended.warning
        StatusTone.Danger -> colors.errorContainer to colors.error
        StatusTone.Info -> extended.infoSoft to extended.info
        StatusTone.Neutral -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    Row(
        modifier.background(background, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(foreground, CircleShape))
        Text(text, style = MaterialTheme.typography.labelSmall, color = foreground, maxLines = 1)
    }
}

@Composable
fun EuropartesStateBadge(text: String, isPositive: Boolean, modifier: Modifier = Modifier) =
    StatusPill(text, if (isPositive) StatusTone.Success else StatusTone.Danger, modifier)

@Composable
fun EuropartesNeutralBadge(text: String, modifier: Modifier = Modifier) = StatusPill(text, StatusTone.Neutral, modifier)

/** Tone shared by every purchase and request state, with the labels the web uses. */
fun purchaseStatusLabel(status: String): String = when (status) {
    "PENDIENTE" -> "Pendiente"
    "ACEPTADA" -> "Aceptada"
    "RECHAZADA" -> "Rechazada"
    "REALIZADA" -> "Realizada"
    "EN_PROCESO" -> "En proceso"
    "PROCESADA" -> "Procesada"
    "ENTREGADA_AL_ALMACEN" -> "Recibida en almacén"
    "CANCELADA" -> "Cancelada"
    else -> status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

fun purchaseStatusTone(status: String): StatusTone = when (status) {
    "ACEPTADA", "PROCESADA", "ENTREGADA_AL_ALMACEN" -> StatusTone.Success
    "RECHAZADA", "CANCELADA" -> StatusTone.Danger
    "PENDIENTE", "EN_PROCESO" -> StatusTone.Warning
    "REALIZADA" -> StatusTone.Info
    else -> StatusTone.Neutral
}

/** Inline alert. `isError = false` is the informative variant. */
@Composable
fun EuropartesStatusMessage(
    message: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val background = if (isError) colors.errorContainer else EuropartesTheme.colors.infoSoft
    val foreground = if (isError) colors.error else EuropartesTheme.colors.info
    // A long form keeps its messages below the fold; an error nobody sees looks like a dead button.
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(message) { if (isError) requester.bringIntoView() }
    Row(
        modifier.fillMaxWidth().bringIntoViewRequester(requester).background(background, ControlShape).border(1.dp, foreground.copy(alpha = 0.2f), ControlShape).padding(start = 11.dp, end = if (onDismiss == null) 11.dp else 5.dp, top = 9.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.Info, contentDescription = null, Modifier.padding(top = 1.dp).size(18.dp), tint = foreground)
        Text(message, Modifier.weight(1f), color = if (isError) colors.onErrorContainer else colors.onSurface, style = MaterialTheme.typography.bodySmall.copy(lineHeight = MaterialTheme.typography.bodyMedium.lineHeight))
        if (onDismiss != null) Icon(Icons.Outlined.Close, contentDescription = "Cerrar mensaje", Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onDismiss).padding(2.dp), tint = foreground)
    }
}

/** Result messages sit above the list; returns there so an action taken far down still gets its answer. */
@Composable
fun rememberScrollToMessage(message: String?): LazyListState {
    val state = rememberLazyListState()
    LaunchedEffect(message) { if (message != null) state.animateScrollToItem(0) }
    return state
}

/** Flat bordered surface, no shadow: the web's `.module-card`. */
@Composable
fun EuropartesCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    verticalSpacing: Arrangement.Vertical = Arrangement.spacedBy(10.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(CardShape).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline, CardShape).padding(contentPadding),
        verticalArrangement = verticalSpacing,
        content = content,
    )
}

@Composable
fun EuropartesOutlinedCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    EuropartesCard(modifier) { content() }
}

@Composable
fun EuropartesSectionCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    EuropartesCard(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.invoke()
        }
        content()
    }
}

/** Eyebrow, title, one line of description and the page's actions, as `.module-header` does. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    description: String? = null,
    actions: (@Composable FlowRowScope.() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        eyebrow?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (actions != null) {
            Spacer(Modifier.height(9.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}

data class KpiItem(val label: String, val value: String, val detail: String? = null, val valueColor: Color? = null)

/** One bordered group with inner rules and no icons: the web's `.catalog-summary-grid`. */
@Composable
fun KpiGrid(items: List<KpiItem>, modifier: Modifier = Modifier, columns: Int = 2) {
    val colors = MaterialTheme.colorScheme
    val rows = items.chunked(columns)
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(colors.surface).border(1.dp, colors.outline, RoundedCornerShape(9.dp))) {
        rows.forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) HorizontalDivider(color = colors.outline)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEachIndexed { index, item ->
                    if (index > 0) VerticalDivider(Modifier.fillMaxHeight(), color = colors.outline)
                    Column(Modifier.weight(1f).padding(horizontal = 13.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(item.label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 2)
                        Text(item.value, style = MaterialTheme.typography.titleLarge.tabular, color = item.valueColor ?: colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        item.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted) }
                    }
                }
                repeat(columns - row.size) {
                    VerticalDivider(Modifier.fillMaxHeight(), color = colors.outline)
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun EuropartesMetricPill(label: String, value: String, modifier: Modifier = Modifier) = LabeledValue(label, value, modifier)

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted)
        Text(value, style = MaterialTheme.typography.labelLarge.tabular, color = valueColor ?: MaterialTheme.colorScheme.onSurface)
    }
}

/** Evenly spaced label and value pairs: what a table row becomes at phone width. */
@Composable
fun MetaGrid(items: List<Pair<String, String>>, modifier: Modifier = Modifier, columns: Int = 2) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value) -> LabeledValue(label, value, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Header of a record: name, secondary line and its state. */
@Composable
fun RecordHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing?.invoke(this)
    }
}

/** Small actions at the foot of a record, separated by a rule so they read as a group. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordActions(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun SummaryRow(label: String, value: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = (if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge).tabular, color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Info,
    action: (@Composable () -> Unit)? = null,
) {
    EuropartesCard(modifier, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 26.dp), verticalSpacing = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, Modifier.size(28.dp), tint = EuropartesTheme.colors.muted)
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            if (action != null) {
                Spacer(Modifier.height(6.dp))
                action()
            }
        }
    }
}

@Composable
fun LoadingState(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Underlined tabs for the sections of one page. */
@Composable
fun <T> EuropartesTabs(tabs: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEach { (value, label) ->
                val active = value == selected
                Column(Modifier.clip(ControlShape).clickable { onSelect(value) }.width(IntrinsicSize.Max)) {
                    Text(label, Modifier.padding(horizontal = 12.dp, vertical = 11.dp), style = MaterialTheme.typography.labelLarge, color = if (active) colors.primary else colors.onSurfaceVariant)
                    Box(Modifier.fillMaxWidth().height(2.dp).background(if (active) colors.primary else Color.Transparent))
                }
            }
        }
        HorizontalDivider(color = colors.outline)
    }
}

/** Mutually exclusive options that all fit on one line: theme, currency. */
@Composable
fun <T> SegmentedControl(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().clip(ControlShape).background(colors.surfaceVariant).border(1.dp, colors.outline, ControlShape).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(5.dp)).background(if (active) EuropartesTheme.colors.raised else Color.Transparent).clickable { onSelect(value) }.heightIn(min = 36.dp).padding(horizontal = 6.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = MaterialTheme.typography.labelMedium, color = if (active) colors.onSurface else colors.onSurfaceVariant, maxLines = 1) }
        }
    }
}

/** The web's modal: header with a close control, scrolling body, fixed footer. */
@Composable
fun EuropartesDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    dismissEnabled: Boolean = true,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = { if (dismissEnabled) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().imePadding().navigationBarsPadding().padding(14.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier.fillMaxWidth().heightIn(max = maxHeight).clip(RoundedCornerShape(12.dp)).background(colors.surface).border(1.dp, colors.outline, RoundedCornerShape(12.dp)),
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                    }
                    Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = dismissEnabled, onClick = onDismiss), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, contentDescription = "Cerrar", Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = colors.outline)
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
                if (footer != null) {
                    HorizontalDivider(color = colors.outline)
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically, content = footer)
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
    busy: Boolean = false,
    dismissText: String = "Cancelar",
) {
    EuropartesDialog(
        title = title,
        onDismiss = onDismiss,
        dismissEnabled = !busy,
        footer = {
            EuropartesButton(dismissText, onDismiss, variant = ButtonVariant.Secondary, enabled = !busy)
            EuropartesButton(confirmText, onConfirm, variant = if (danger) ButtonVariant.Danger else ButtonVariant.Primary, isLoading = busy)
        },
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Initials on a soft circle, like the web's user chip. */
@Composable
fun UserAvatar(name: String, modifier: Modifier = Modifier, background: Color = MaterialTheme.colorScheme.primaryContainer, foreground: Color = MaterialTheme.colorScheme.primary) {
    val initials = name.trim().split(Regex("\\s+")).filter(String::isNotEmpty).take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(modifier.size(34.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Text(initials, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = foreground)
    }
}

/** Keeps status bar icons legible over what is behind them: the blue hero or sidebar, or a surface. */
@Composable
fun StatusBarIcons(lightIcons: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !lightIcons
        }
    }
}
