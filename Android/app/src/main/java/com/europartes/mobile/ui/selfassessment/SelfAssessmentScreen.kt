package com.europartes.mobile.ui.selfassessment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.europartes.mobile.data.local.SelfAssessmentSyncState
import com.europartes.mobile.data.remote.SelfAssessmentCategoryDto
import com.europartes.mobile.data.remote.SelfAssessmentCurrentDto
import com.europartes.mobile.data.remote.SelfAssessmentEvaluationDto
import com.europartes.mobile.data.remote.SelfAssessmentQuestionDto
import com.europartes.mobile.data.remote.SelfAssessmentQuestionnaireDto
import com.europartes.mobile.data.repository.SelfAssessmentRules
import com.europartes.mobile.ui.components.ButtonVariant
import com.europartes.mobile.ui.components.EmptyState
import com.europartes.mobile.ui.components.EuropartesButton
import com.europartes.mobile.ui.components.EuropartesCard
import com.europartes.mobile.ui.components.EuropartesStatusMessage
import com.europartes.mobile.ui.components.EuropartesTabs
import com.europartes.mobile.ui.components.EuropartesTextField
import com.europartes.mobile.ui.components.LoadingState
import com.europartes.mobile.ui.components.MetaGrid
import com.europartes.mobile.ui.components.PageHeader
import com.europartes.mobile.ui.components.StatusPill
import com.europartes.mobile.ui.components.StatusTone
import com.europartes.mobile.ui.components.SummaryRow
import com.europartes.mobile.ui.components.formatDisplayDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SelfAssessmentScreen(
    role: String,
    viewModel: SelfAssessmentViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(role) { viewModel.load(role) }

    when {
        state.isLoading && state.current == null -> LoadingState("Cargando tu autoevaluación…", modifier)
        state.current == null -> EmptyState(
            title = "No se pudo abrir la autoevaluación",
            message = state.message ?: "Inténtalo nuevamente cuando tengas conexión.",
            icon = Icons.Outlined.ErrorOutline,
            action = { EuropartesButton("Reintentar", { viewModel.load(role, force = true) }, icon = Icons.Outlined.Refresh) },
            modifier = modifier.padding(16.dp),
        )
        else -> AssessmentContent(state, viewModel, role, modifier)
    }
}

@Composable
private fun AssessmentContent(
    state: SelfAssessmentUiState,
    viewModel: SelfAssessmentViewModel,
    role: String,
    modifier: Modifier,
) {
    val current = state.current ?: return
    val questionnaire = current.cuestionario
    val open = isPeriodOpen(current)
    val canEdit = open && state.syncState != SelfAssessmentSyncState.SENT && !state.isSubmitting
    val categories = SelfAssessmentRules.categories(questionnaire)
    val progress = SelfAssessmentRules.progress(questionnaire, state.form.answers)
    val status = selfAssessmentStatus(current, state.syncState, state.form.answers, state.isSubmitting)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageHeader(
                title = if (state.step == SelfAssessmentStep.HISTORY) "Historial de autoevaluaciones" else questionnaire?.titulo ?: "Autoevaluación mensual",
                eyebrow = if (state.step == SelfAssessmentStep.HISTORY) "Autoevaluación" else periodLabel(current.periodo)?.let { "Período · $it" },
                description = if (state.step == SelfAssessmentStep.HISTORY) "Tus evaluaciones anteriores, de la más reciente a la más antigua." else null,
            )
        }
        state.message?.let { message ->
            item { EuropartesStatusMessage(message, state.messageIsError, onDismiss = viewModel::clearMessage) }
        }

        when (state.step) {
            SelfAssessmentStep.INTRODUCTION -> {
                item { StatusCard(current, status, progress, state.savingDraft, showProgress = status == SelfAssessmentStatus.IN_PROGRESS || status == SelfAssessmentStatus.PENDING_SYNC) }
                current.evaluacion?.takeIf { status == SelfAssessmentStatus.COMPLETED || status == SelfAssessmentStatus.CLOSED }?.let { evaluation ->
                    item { ResultCard(evaluation) }
                }
                item {
                    Introduction(
                        questionnaire = questionnaire,
                        status = status,
                        open = open,
                        hasQuestions = categories.isNotEmpty(),
                        hasEvaluation = current.evaluacion != null,
                        offline = state.showingOfflineDraft,
                        onBegin = viewModel::begin,
                        onCorrect = viewModel::correct,
                        onReview = viewModel::review,
                        onHistory = { viewModel.openHistory() },
                        onRefresh = { viewModel.load(role, force = true) },
                    )
                }
            }
            SelfAssessmentStep.FORM -> {
                if (categories.isEmpty()) {
                    item { EmptyState("Cuestionario no disponible", "El servidor no proporcionó categorías para este período.") }
                } else {
                    item { StatusCard(current, status, progress, state.savingDraft, showProgress = true) }
                    item {
                        EuropartesTabs(
                            tabs = categories.mapIndexed { index, category -> index to "${index + 1}. ${category.nombre ?: "Categoría"}" },
                            selected = state.form.categoryIndex,
                            onSelect = viewModel::selectCategory,
                        )
                    }
                    val index = state.form.categoryIndex.coerceIn(0, categories.lastIndex)
                    item { CategoryForm(categories[index], index, categories.size, questionnaire, state, canEdit, viewModel) }
                }
            }
            SelfAssessmentStep.REVIEW -> item { Review(categories, state, canEdit, viewModel) }
            SelfAssessmentStep.HISTORY -> historyItems(state.history, viewModel)
        }
    }
}

@Composable
private fun StatusCard(
    current: SelfAssessmentCurrentDto,
    status: SelfAssessmentStatus,
    progress: Pair<Int, Int>,
    saving: Boolean,
    showProgress: Boolean,
) {
    EuropartesCard {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            StatusPill(status.label, statusTone(status))
            if (saving) Text("Guardando borrador…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MetaGrid(
            listOfNotNull(
                periodLabel(current.periodo)?.let { "Período evaluado" to it },
                current.closesAt?.let { (if (isPeriodOpen(current)) "Cierra el" else "Cerró el") to formatDisplayDate(it) },
            ),
        )
        if (showProgress && progress.second > 0) {
            LinearProgressIndicator(
                progress = { progress.first.toFloat() / progress.second },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${progress.first} de ${progress.second} preguntas obligatorias respondidas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Scores exactly as the server returned them; nothing is computed on the phone. */
@Composable
private fun ResultCard(evaluation: SelfAssessmentEvaluationDto) {
    EuropartesCard {
        Text("Resultado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        evaluation.totalScore?.let { SummaryRow("Puntaje general", "${formatScore(it)} / 100", emphasized = true) }
        evaluation.nivel?.let { SummaryRow("Nivel", it) }
        val categories = evaluation.categoryScores.orEmpty().filter { it.puntaje != null }
        if (categories.isNotEmpty()) {
            HorizontalDivider()
            categories.forEach { category -> SummaryRow(category.nombre ?: category.codigo.orEmpty(), formatScore(category.puntaje ?: 0.0)) }
        }
        evaluation.updatedAt?.let {
            Text("Última actualización: ${formatDisplayDate(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Introduction(
    questionnaire: SelfAssessmentQuestionnaireDto?,
    status: SelfAssessmentStatus,
    open: Boolean,
    hasQuestions: Boolean,
    hasEvaluation: Boolean,
    offline: Boolean,
    onBegin: () -> Unit,
    onCorrect: () -> Unit,
    onReview: () -> Unit,
    onHistory: () -> Unit,
    onRefresh: () -> Unit,
) {
    val scale = SelfAssessmentRules.categories(questionnaire).flatMap { it.preguntas.orEmpty() }.firstOrNull { it.tipo == "escala" }?.escala
    EuropartesCard {
        Text("Instrucciones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            questionnaire?.descripcion ?: "Responde cada pregunta según tu desempeño durante el período indicado.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (scale != null) {
            Text("Escala de calificación", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            ((scale.minimo ?: 1)..(scale.maximo ?: 5)).forEach { value ->
                Text("$value · ${scale.etiquetas?.get(value.toString()) ?: ""}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("Cómo responder", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        listOf(
            "Responde pensando en todo el mes evaluado, no solo en los últimos días.",
            "Algunas preguntas aparecen según lo que respondas en otras.",
            "Tus respuestas se guardan en el teléfono mientras avanzas; puedes salir y continuar después.",
            "Antes de enviar podrás revisar todo. El resultado lo calcula el servidor.",
            if (open) "Puedes corregir la evaluación hasta el cierre del período." else "Este período está cerrado y ya no admite cambios.",
        ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        when {
            !hasQuestions -> Text("El servidor no tiene un cuestionario publicado para este período.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            status == SelfAssessmentStatus.CLOSED -> Unit
            status == SelfAssessmentStatus.COMPLETED -> EuropartesButton("Corregir evaluación", onCorrect, Modifier.fillMaxWidth(), ButtonVariant.Secondary, icon = Icons.Outlined.Edit)
            status == SelfAssessmentStatus.PENDING_SYNC -> {
                EuropartesButton("Revisar y enviar", onReview, Modifier.fillMaxWidth(), icon = Icons.Outlined.CheckCircle)
                EuropartesButton("Seguir editando", onBegin, Modifier.fillMaxWidth(), ButtonVariant.Secondary, icon = Icons.Outlined.Edit)
            }
            status == SelfAssessmentStatus.IN_PROGRESS -> EuropartesButton("Continuar autoevaluación", onBegin, Modifier.fillMaxWidth(), icon = Icons.Outlined.Assignment)
            status == SelfAssessmentStatus.SENDING -> Unit
            else -> EuropartesButton(if (hasEvaluation) "Continuar autoevaluación" else "Comenzar autoevaluación", onBegin, Modifier.fillMaxWidth(), icon = Icons.Outlined.Assignment)
        }
        EuropartesButton("Ver historial", onHistory, Modifier.fillMaxWidth(), ButtonVariant.Secondary, icon = Icons.Outlined.History)
        if (offline) EuropartesButton("Reintentar conexión", onRefresh, Modifier.fillMaxWidth(), ButtonVariant.Ghost, icon = Icons.Outlined.Refresh)
    }
}

@Composable
private fun CategoryForm(
    category: SelfAssessmentCategoryDto,
    index: Int,
    total: Int,
    questionnaire: SelfAssessmentQuestionnaireDto?,
    state: SelfAssessmentUiState,
    editable: Boolean,
    viewModel: SelfAssessmentViewModel,
) {
    val questions = SelfAssessmentRules.visibleQuestions(category, questionnaire, state.form.answers)
    EuropartesCard {
        Text("Sección ${index + 1} de $total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(category.nombre ?: "Categoría", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        questions.forEachIndexed { position, question ->
            if (position > 0) HorizontalDivider()
            val value = question.id?.let(state.form.answers::get)
            // An empty required answer is only flagged after trying to move on; a wrong value, at once.
            val error = SelfAssessmentRules.validate(question, value).takeIf { state.showErrors || !value.isNullOrBlank() }
            QuestionInput(question, value, error, editable) { newValue -> question.id?.let { viewModel.answer(it, newValue) } }
        }
        if (questions.isEmpty()) Text("No hay preguntas aplicables en esta categoría.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (index > 0) {
                EuropartesButton("Anterior", viewModel::previousCategory, Modifier.weight(1f), ButtonVariant.Secondary)
            } else {
                EuropartesButton("Instrucciones", viewModel::openIntroduction, Modifier.weight(1f), ButtonVariant.Secondary)
            }
            if (index < total - 1) {
                EuropartesButton("Siguiente", viewModel::nextCategory, Modifier.weight(1f))
            } else {
                EuropartesButton("Revisar", viewModel::review, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuestionInput(question: SelfAssessmentQuestionDto, value: String?, error: String?, enabled: Boolean, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            Text(question.texto ?: "Pregunta", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (question.obligatoria == true) Text(" *", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        }
        when (question.tipo) {
            "escala" -> {
                val min = question.escala?.minimo ?: 1
                val max = question.escala?.maximo ?: 5
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    (min..max).forEach { option ->
                        EuropartesButton(
                            text = option.toString(),
                            onClick = { onValueChange(option.toString()) },
                            modifier = Modifier.weight(1f),
                            variant = if (value == option.toString()) ButtonVariant.Primary else ButtonVariant.Secondary,
                            small = true,
                            enabled = enabled,
                        )
                    }
                }
                Text(
                    question.escala?.etiquetas?.get(value) ?: "${question.escala?.etiquetas?.get(min.toString()) ?: min} → ${question.escala?.etiquetas?.get(max.toString()) ?: max}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            "opcion" -> question.opciones.orEmpty().forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().then(if (enabled) Modifier.clickable { option.valor?.let(onValueChange) } else Modifier),
                ) {
                    RadioButton(selected = value == option.valor, onClick = { option.valor?.let(onValueChange) }, enabled = enabled)
                    Text(option.etiqueta ?: option.valor.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            "numero" -> EuropartesTextField(
                value = value.orEmpty(),
                onValueChange = { text -> onValueChange(text.filter { it.isDigit() || it == '.' || it == ',' }) },
                label = "Cantidad",
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = if (question.entero == true) KeyboardType.Number else KeyboardType.Decimal),
                supportingText = "Entre ${SelfAssessmentRules.formatNumber(question.minimo)} y ${SelfAssessmentRules.formatNumber(question.maximo)}",
            )
            else -> {
                val max = question.maxLength ?: 500
                EuropartesTextField(
                    value = value.orEmpty(),
                    onValueChange = { text -> onValueChange(text.take(max)) },
                    label = "Respuesta",
                    enabled = enabled,
                    supportingText = "${value.orEmpty().length} / $max",
                    multiline = true,
                )
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun Review(
    categories: List<SelfAssessmentCategoryDto>,
    state: SelfAssessmentUiState,
    editable: Boolean,
    viewModel: SelfAssessmentViewModel,
) {
    EuropartesCard {
        Text("Revisa tus respuestas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Comprueba cada respuesta antes de enviar. El puntaje y el nivel los calcula el servidor.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        categories.forEachIndexed { index, category ->
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(category.nombre ?: "Categoría", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (editable) EuropartesButton("Editar", { viewModel.returnToForm(index) }, variant = ButtonVariant.Ghost, small = true)
            }
            SelfAssessmentRules.visibleQuestions(category, state.current?.cuestionario, state.form.answers).forEach { question ->
                Text(question.texto ?: "Pregunta", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(SelfAssessmentRules.answerLabel(question, question.id?.let(state.form.answers::get)), style = MaterialTheme.typography.bodyMedium)
            }
        }
        HorizontalDivider()
        if (editable) {
            EuropartesButton(
                text = if (state.isSubmitting) "Enviando…" else "Enviar evaluación",
                onClick = viewModel::submit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSubmitting,
                isLoading = state.isSubmitting,
                icon = Icons.Outlined.CheckCircle,
            )
        }
        EuropartesButton("Volver al formulario", { viewModel.returnToForm() }, modifier = Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary, enabled = !state.isSubmitting)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.historyItems(history: SelfAssessmentHistoryState, viewModel: SelfAssessmentViewModel) {
    when {
        history.isLoading && history.items.isEmpty() -> item { LoadingState("Cargando tu historial…") }
        history.error != null && history.items.isEmpty() -> item {
            EmptyState(
                title = "No se pudo cargar el historial",
                message = history.error,
                icon = Icons.Outlined.ErrorOutline,
                action = { EuropartesButton("Reintentar", { viewModel.openHistory(force = true) }, icon = Icons.Outlined.Refresh) },
            )
        }
        history.hasLoaded && history.items.isEmpty() -> item {
            EmptyState("Aún no tienes evaluaciones", "Cuando envíes tu primera autoevaluación aparecerá aquí.", icon = Icons.Outlined.History)
        }
        else -> {
            history.error?.let { error -> item { EuropartesStatusMessage(error, isError = true) } }
            items(history.items, key = { it.id ?: it.periodo.orEmpty() }) { evaluation -> HistoryCard(evaluation) }
        }
    }
    item { EuropartesButton("Volver", viewModel::openIntroduction, Modifier.fillMaxWidth(), ButtonVariant.Secondary) }
}

@Composable
private fun HistoryCard(evaluation: SelfAssessmentEvaluationDto) {
    EuropartesCard {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(periodLabel(evaluation.periodo) ?: "Período", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (evaluation.editable == true) StatusPill("Abierta", StatusTone.Info) else StatusPill("Cerrada", StatusTone.Neutral)
        }
        evaluation.totalScore?.let { SummaryRow("Puntaje general", "${formatScore(it)} / 100", emphasized = true) }
        evaluation.nivel?.let { SummaryRow("Nivel", it) }
        evaluation.categoryScores.orEmpty().filter { it.puntaje != null }.forEach { category ->
            SummaryRow(category.nombre ?: category.codigo.orEmpty(), formatScore(category.puntaje ?: 0.0))
        }
        evaluation.updatedAt?.let {
            Text("Actualizada: ${formatDisplayDate(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun statusTone(status: SelfAssessmentStatus): StatusTone = when (status) {
    SelfAssessmentStatus.COMPLETED -> StatusTone.Success
    SelfAssessmentStatus.CLOSED -> StatusTone.Neutral
    SelfAssessmentStatus.PENDING_SYNC, SelfAssessmentStatus.SENDING -> StatusTone.Warning
    SelfAssessmentStatus.IN_PROGRESS, SelfAssessmentStatus.PENDING -> StatusTone.Info
}

/** "2026-11" as "noviembre de 2026"; the raw text if the server ever sends another shape. */
internal fun periodLabel(period: String?): String? {
    if (period.isNullOrBlank()) return null
    return runCatching {
        YearMonth.parse(period).format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale.forLanguageTag("es")))
    }.getOrDefault(period)
}

internal fun formatScore(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
