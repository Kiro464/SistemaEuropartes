package com.europartes.mobile.ui.selfassessment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.europartes.mobile.data.remote.SelfAssessmentCategoryDto
import com.europartes.mobile.data.remote.SelfAssessmentQuestionDto
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
        else -> AssessmentContent(state, viewModel, modifier)
    }
}

@Composable
private fun AssessmentContent(
    state: SelfAssessmentUiState,
    viewModel: SelfAssessmentViewModel,
    modifier: Modifier,
) {
    val current = state.current ?: return
    val questionnaire = current.cuestionario
    val editable = current.abierto == true && current.evaluacion?.editable != false
    val categories = SelfAssessmentRules.categories(questionnaire)
    val progress = SelfAssessmentRules.progress(questionnaire, state.form.answers)
    val status = assessmentStatus(state, editable)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageHeader(
                title = questionnaire?.titulo ?: "Autoevaluación mensual",
                eyebrow = current.periodo?.let { "Período $it" },
                description = if (state.step == SelfAssessmentStep.FORM) "${progress.first} de ${progress.second} preguntas obligatorias respondidas" else questionnaire?.descripcion,
            )
        }
        item {
            EuropartesCard {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    StatusPill(status.first, status.second)
                    if (state.savingDraft) Text("Guardando…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MetaGrid(
                    listOfNotNull(
                        current.periodo?.let { "Período" to it },
                        current.closesAt?.let { "Cierre" to it },
                        current.evaluacion?.estado?.let { "Estado del servidor" to it },
                    ),
                )
            }
        }
        state.message?.let { message ->
            item { EuropartesStatusMessage(message, state.messageIsError, onDismiss = viewModel::clearMessage) }
        }

        when (state.step) {
            SelfAssessmentStep.INTRODUCTION -> item {
                Introduction(
                    description = questionnaire?.descripcion,
                    editable = editable,
                    hasQuestions = categories.isNotEmpty(),
                    onBegin = viewModel::begin,
                )
            }
            SelfAssessmentStep.FORM -> {
                if (categories.isEmpty()) {
                    item { EmptyState("Cuestionario no disponible", "El servidor no proporcionó categorías para este período.") }
                } else {
                    item {
                        EuropartesTabs(
                            tabs = categories.mapIndexed { index, category -> index to (category.nombre ?: "Categoría ${index + 1}") },
                            selected = state.form.categoryIndex,
                            onSelect = viewModel::selectCategory,
                        )
                    }
                    val category = categories[state.form.categoryIndex.coerceIn(0, categories.lastIndex)]
                    item { CategoryForm(category, questionnaire, state, editable, viewModel) }
                }
            }
            SelfAssessmentStep.REVIEW -> item {
                Review(categories, state, editable, viewModel)
            }
        }
    }
}

@Composable
private fun Introduction(description: String?, editable: Boolean, hasQuestions: Boolean, onBegin: () -> Unit) {
    EuropartesCard {
        Text("Antes de comenzar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            description ?: "Responde cada pregunta según tu desempeño durante el período indicado.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Usa la escala de 1 a 5. Las preguntas obligatorias deben estar completas antes de la revisión.", style = MaterialTheme.typography.bodyMedium)
        EuropartesButton(
            text = "Comenzar autoevaluación",
            onClick = onBegin,
            enabled = editable && hasQuestions,
            icon = Icons.Outlined.Assignment,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!editable) Text("Este período ya no admite cambios según el estado informado por el servidor.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CategoryForm(
    category: SelfAssessmentCategoryDto,
    questionnaire: com.europartes.mobile.data.remote.SelfAssessmentQuestionnaireDto?,
    state: SelfAssessmentUiState,
    editable: Boolean,
    viewModel: SelfAssessmentViewModel,
) {
    val questions = SelfAssessmentRules.visibleQuestions(category, questionnaire, state.form.answers)
    val categories = SelfAssessmentRules.categories(questionnaire)
    val errors = SelfAssessmentRules.errors(questionnaire, state.form.answers)
    EuropartesCard {
        Text(category.nombre ?: "Categoría", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        questions.forEachIndexed { index, question ->
            if (index > 0) HorizontalDivider()
            val questionId = question.id
            QuestionInput(question, questionId?.let(state.form.answers::get), questionId?.let(errors::get), editable) { value ->
                question.id?.let { viewModel.answer(it, value) }
            }
        }
        if (questions.isEmpty()) Text("No hay preguntas aplicables en esta categoría.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            EuropartesButton("Anterior", viewModel::previousCategory, Modifier.weight(1f), ButtonVariant.Secondary, enabled = state.form.categoryIndex > 0)
            if (state.form.categoryIndex < categories.lastIndex) {
                EuropartesButton("Siguiente", viewModel::nextCategory, Modifier.weight(1f))
            } else {
                EuropartesButton("Revisar", viewModel::review, Modifier.weight(1f), enabled = editable)
            }
        }
    }
}

@Composable
private fun QuestionInput(question: SelfAssessmentQuestionDto, value: String?, error: String?, enabled: Boolean, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(question.texto ?: "Pregunta", style = MaterialTheme.typography.bodyLarge)
        if (question.tipo == "escala") {
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
            question.escala?.etiquetas?.get(value)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            EuropartesTextField(
                value = value.orEmpty(),
                onValueChange = onValueChange,
                label = "Respuesta",
                enabled = enabled,
                required = question.obligatoria == true,
                supportingText = error,
                multiline = question.tipo !in setOf("numero", "opcion"),
            )
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
        Text("Verifica la información antes del envío. Los puntajes y el nivel serán calculados exclusivamente por el servidor.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        categories.forEach { category ->
            Text(category.nombre ?: "Categoría", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            SelfAssessmentRules.visibleQuestions(category, state.current?.cuestionario, state.form.answers).forEach { question ->
                Text(question.texto ?: "Pregunta", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(SelfAssessmentRules.answerLabel(question, question.id?.let(state.form.answers::get)), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
        }
        EuropartesButton("Volver a editar", viewModel::returnToForm, modifier = Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
        if (editable) {
            Text("La evaluación está lista para envío. La sincronización definitiva se habilitará en la siguiente fase.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun assessmentStatus(state: SelfAssessmentUiState, editable: Boolean): Pair<String, StatusTone> = when {
    state.showingOfflineDraft -> "Borrador local" to StatusTone.Warning
    !editable -> "Período cerrado" to StatusTone.Neutral
    state.current?.evaluacion?.estado == "completada" -> "Evaluación completada" to StatusTone.Success
    state.restoredDraft -> "Evaluación en progreso" to StatusTone.Info
    else -> "Evaluación pendiente" to StatusTone.Info
}
