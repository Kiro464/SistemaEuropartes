package com.europartes.mobile.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.europartes.mobile.data.remote.EmployeeDto
import com.europartes.mobile.data.remote.UserAccountDto
import com.europartes.mobile.data.repository.EuropartesRoles
import com.europartes.mobile.data.repository.UserAccountWrite
import com.europartes.mobile.ui.components.ButtonVariant
import com.europartes.mobile.ui.components.ConfirmDialog
import com.europartes.mobile.ui.components.EmptyState
import com.europartes.mobile.ui.components.EuropartesButton
import com.europartes.mobile.ui.components.EuropartesCard
import com.europartes.mobile.ui.components.EuropartesDialog
import com.europartes.mobile.ui.components.EuropartesSearchField
import com.europartes.mobile.ui.components.EuropartesSelect
import com.europartes.mobile.ui.components.EuropartesStatusMessage
import com.europartes.mobile.ui.components.EuropartesTextField
import com.europartes.mobile.ui.components.LoadingState
import com.europartes.mobile.ui.components.MetaGrid
import com.europartes.mobile.ui.components.PageHeader
import com.europartes.mobile.ui.components.PagePadding
import com.europartes.mobile.ui.components.RecordActions
import com.europartes.mobile.ui.components.RecordHeader
import com.europartes.mobile.ui.components.SectionGap
import com.europartes.mobile.ui.components.SelectOption
import com.europartes.mobile.ui.components.StatusPill
import com.europartes.mobile.ui.components.StatusTone
import com.europartes.mobile.ui.theme.EuropartesTheme

private val functionalRoles = listOf(
    EuropartesRoles.ADMIN_GENERAL,
    EuropartesRoles.ASESOR_FINANCIERO,
    EuropartesRoles.VENDEDOR,
    EuropartesRoles.SOPORTE_TI,
    EuropartesRoles.ASESOR_MARKETING,
    EuropartesRoles.COORDINADOR_LOGISTICA,
    EuropartesRoles.OFICIAL_LOGISTICA,
)

@Composable
fun SecurityAdminScreen(
    role: String,
    currentUserId: Int,
    viewModel: SecurityViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var showForm by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Int?>(null) }
    var resetTarget by remember { mutableStateOf<UserAccountDto?>(null) }
    var statusTarget by remember { mutableStateOf<Pair<UserAccountDto, Boolean>?>(null) }
    var versionWhenDialogOpened by remember { mutableStateOf(state.saveVersion) }

    LaunchedEffect(role) { viewModel.loadUsers(role) }
    LaunchedEffect(state.saveVersion) {
        if (showForm && state.saveVersion > versionWhenDialogOpened) showForm = false
        if (resetTarget != null && state.saveVersion > versionWhenDialogOpened) resetTarget = null
    }

    val filteredUsers = remember(state.users, query) {
        val term = query.trim().lowercase()
        if (term.isEmpty()) state.users else state.users.filter { user ->
            listOf(user.username, user.firstName, user.lastName, user.email, user.role.orEmpty()).any { it.lowercase().contains(term) }
        }
    }

    LazyColumn(modifier, contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        item {
            PageHeader(
                title = "Usuarios y roles",
                description = "Administra las cuentas, el rol funcional y su asociación laboral.",
                actions = {
                    EuropartesButton("Nueva cuenta", {
                        viewModel.clearMessage()
                        viewModel.openCreate()
                        editingId = null
                        versionWhenDialogOpened = state.saveVersion
                        showForm = true
                    }, enabled = role == EuropartesRoles.SOPORTE_TI && !state.isWorking && state.isOnline, icon = Icons.Outlined.Add)
                    EuropartesButton(if (state.isLoading) "Actualizando…" else "Actualizar", { viewModel.loadUsers(role, force = true) }, variant = ButtonVariant.Secondary, enabled = !state.isLoading, icon = Icons.Outlined.Refresh)
                },
            )
        }
        item {
            EuropartesCard {
                EuropartesSearchField(query, { query = it }, "Buscar usuario, nombre, correo o rol")
                Text("${filteredUsers.size} cuenta(s)", style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted)
            }
        }
        state.offlineNotice?.let { item { EuropartesStatusMessage(it, isError = false) } }
        state.error?.let { item { EuropartesStatusMessage(it, isError = true) } }
        state.message?.let { item { EuropartesStatusMessage(it, isError = state.messageIsError, onDismiss = viewModel::clearMessage) } }
        when {
            role != EuropartesRoles.SOPORTE_TI -> item { EuropartesStatusMessage("Solo Soporte TI puede consultar cuentas.", true) }
            state.isLoading && !state.hasLoaded -> item { LoadingState("Cargando cuentas…") }
            state.error != null && state.users.isEmpty() -> item {
                EuropartesButton("Reintentar", { viewModel.loadUsers(role, force = true) }, variant = ButtonVariant.Secondary)
            }
            filteredUsers.isEmpty() -> item {
                EmptyState("Sin cuentas", if (state.users.isEmpty()) "No hay cuentas disponibles." else "Ninguna cuenta coincide con la búsqueda.", icon = Icons.Outlined.ManageAccounts)
            }
            else -> items(filteredUsers, key = { it.id }) { user ->
                AccountCard(
                    user = user,
                    employee = state.employees.firstOrNull { it.id == user.employeeId },
                    employeesLoading = state.employeesLoading,
                    employeeError = state.employeeError,
                    isCurrentUser = user.id == currentUserId,
                    isWorking = state.isWorking || !state.isOnline,
                    onEdit = {
                        editingId = user.id
                        versionWhenDialogOpened = state.saveVersion
                        showForm = true
                        viewModel.openEdit(role, user.id)
                    },
                    onResetPassword = {
                        viewModel.clearMessage()
                        versionWhenDialogOpened = state.saveVersion
                        resetTarget = user
                    },
                    onToggleActive = { active -> statusTarget = user to active },
                )
            }
        }
    }

    if (showForm) {
        val account = if (editingId == null) null else state.editingUser?.takeIf { it.id == editingId }
        if (editingId != null && state.editorLoading) {
            EuropartesDialog(title = "Editar cuenta", onDismiss = { showForm = false }) { LoadingState("Cargando cuenta…") }
        } else if (editingId == null || account != null) {
            UserAccountDialog(
                initial = account,
                currentUserId = currentUserId,
                employees = state.employees,
                employeeError = state.employeeError,
                employeesLoading = state.employeesLoading,
                isWorking = state.isWorking,
                error = state.error,
                onDismiss = { showForm = false; viewModel.clearMessage() },
                onSubmit = { input -> viewModel.saveUser(role, input, editingId) },
            )
        }
    }

    resetTarget?.let { user ->
        ResetPasswordDialog(
            username = user.username,
            isWorking = state.isWorking,
            error = state.error,
            onDismiss = { resetTarget = null; viewModel.clearMessage() },
            onSubmit = { password, confirmation -> viewModel.resetPassword(role, user.id, password, confirmation) },
        )
    }

    statusTarget?.let { (user, active) ->
        ConfirmDialog(
            title = if (active) "Activar cuenta" else "Desactivar cuenta",
            message = "${if (active) "Se habilitará" else "Se bloqueará"} el acceso de ${user.username}. El servidor validará las restricciones de seguridad.",
            confirmText = if (active) "Activar" else "Desactivar",
            danger = !active,
            busy = state.isWorking,
            onConfirm = { viewModel.setUserActive(role, user, active); statusTarget = null },
            onDismiss = { statusTarget = null },
        )
    }
}

@Composable
private fun AccountCard(
    user: UserAccountDto,
    employee: EmployeeDto?,
    employeesLoading: Boolean,
    employeeError: String?,
    isCurrentUser: Boolean,
    isWorking: Boolean,
    onEdit: () -> Unit,
    onResetPassword: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
) {
    val employeeSummary = when {
        user.employeeId == null -> "Sin asociación"
        employeesLoading -> "Empleado #${user.employeeId}: cargando…"
        employeeError != null -> "Empleado #${user.employeeId}"
        employee != null -> "${employee.names} ${employee.surnames}".trim() + if (employee.active == false) " · Inactivo" else ""
        else -> "Empleado #${user.employeeId}: no encontrado"
    }
    EuropartesCard {
        RecordHeader(user.username, "${user.firstName} ${user.lastName}".trim().ifBlank { "Sin nombre registrado" }) {
            StatusPill(if (user.isActive) "Activa" else "Inactiva", if (user.isActive) StatusTone.Success else StatusTone.Neutral)
        }
        MetaGrid(
            listOf(
                "Rol funcional" to (user.role ?: "Rol funcional inválido"),
                "Correo" to user.email.ifBlank { "Sin correo" },
                "Empleado" to employeeSummary,
            ),
            columns = 1,
        )
        RecordActions {
            EuropartesButton("Editar", onEdit, variant = ButtonVariant.Secondary, small = true, enabled = !isWorking)
            EuropartesButton("Restablecer contraseña", onResetPassword, variant = ButtonVariant.Secondary, small = true, enabled = !isWorking)
            if (!isCurrentUser) {
                EuropartesButton(if (user.isActive) "Desactivar" else "Activar", { onToggleActive(!user.isActive) }, variant = if (user.isActive) ButtonVariant.Danger else ButtonVariant.Primary, small = true, enabled = !isWorking)
            }
        }
        if (isCurrentUser) Text("Tu cuenta no puede cambiar su propio rol ni desactivarse aquí.", style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted)
    }
}

@Composable
private fun UserAccountDialog(
    initial: UserAccountDto?,
    currentUserId: Int,
    employees: List<EmployeeDto>,
    employeeError: String?,
    employeesLoading: Boolean,
    isWorking: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (UserAccountWrite) -> Unit,
) {
    var username by remember(initial?.id) { mutableStateOf(initial?.username.orEmpty()) }
    var email by remember(initial?.id) { mutableStateOf(initial?.email.orEmpty()) }
    var firstName by remember(initial?.id) { mutableStateOf(initial?.firstName.orEmpty()) }
    var lastName by remember(initial?.id) { mutableStateOf(initial?.lastName.orEmpty()) }
    var role by remember(initial?.id) { mutableStateOf(initial?.role?.takeIf { it in functionalRoles }) }
    var employeeId by remember(initial?.id) { mutableStateOf(initial?.employeeId) }
    var active by remember(initial?.id) { mutableStateOf(initial?.isActive ?: true) }
    var password by remember(initial?.id) { mutableStateOf("") }
    var confirmation by remember(initial?.id) { mutableStateOf("") }
    var localError by remember(initial?.id) { mutableStateOf<String?>(null) }
    val isCreate = initial == null
    val isSelf = initial?.id == currentUserId
    val submit = {
        val selectedRole = role
        when {
            username.isBlank() -> localError = "Ingresa el nombre de usuario."
            selectedRole == null || selectedRole !in functionalRoles -> localError = "Selecciona un rol funcional válido."
            isCreate && password.isBlank() -> localError = "La contraseña inicial es obligatoria."
            isCreate && password != confirmation -> localError = "Las contraseñas no coinciden."
            else -> onSubmit(
                UserAccountWrite(
                    username = username,
                    email = email,
                    firstName = firstName,
                    lastName = lastName,
                    role = selectedRole,
                    employeeId = employeeId,
                    active = active,
                    password = password.takeIf { isCreate },
                    passwordConfirmation = confirmation.takeIf { isCreate },
                ),
            )
        }
    }
    EuropartesDialog(
        title = if (isCreate) "Nueva cuenta" else "Editar cuenta",
        onDismiss = onDismiss,
        dismissEnabled = !isWorking,
        footer = {
            EuropartesButton("Cancelar", onDismiss, variant = ButtonVariant.Secondary, enabled = !isWorking)
            EuropartesButton("Guardar", submit, isLoading = isWorking)
        },
    ) {
        EuropartesTextField(username, { username = it; localError = null }, "Usuario", enabled = !isWorking, required = true)
        EuropartesTextField(email, { email = it; localError = null }, "Correo electrónico", enabled = !isWorking, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        EuropartesTextField(firstName, { firstName = it }, "Nombres", enabled = !isWorking)
        EuropartesTextField(lastName, { lastName = it }, "Apellidos", enabled = !isWorking)
        EuropartesSelect(
            "Rol funcional", functionalRoles.map { SelectOption(it, it) }, role, { role = it },
            enabled = !isWorking && !isSelf, required = true, placeholder = "Seleccionar rol",
            supportingText = when {
                isSelf -> "No puedes cambiar tu propio rol."
                initial != null && role == null -> "El rol actual no se reconoce. Selecciona uno válido antes de guardar."
                else -> null
            },
        )
        val selectedEmployee = employees.firstOrNull { it.id == employeeId }
        val employeeLabel = when {
            employeeId == null -> "Sin empleado asociado"
            employeesLoading -> "Cargando empleado #$employeeId…"
            employeeError != null -> "No se pudo cargar empleado #$employeeId"
            selectedEmployee != null -> "${selectedEmployee.names} ${selectedEmployee.surnames}".trim() + if (selectedEmployee.active == false) " · Inactivo" else ""
            else -> "Empleado #$employeeId no encontrado"
        }
        EuropartesSelect<Int?>(
            label = "Empleado asociado",
            options = listOf(SelectOption<Int?>(null, "Sin empleado asociado")) + employees.filter { it.userId == null || it.id == initial?.employeeId }.map { option ->
                SelectOption<Int?>(option.id, "${option.names} ${option.surnames}${if (!option.active) " · Inactivo" else ""}")
            },
            selected = employeeId,
            onSelect = { employeeId = it },
            enabled = !isWorking && !(employeeId != null && (employeesLoading || employeeError != null)),
            selectedLabel = employeeLabel,
            supportingText = "Opcional. Cada empleado solo puede tener una cuenta.",
        )
        employeeError?.let { EuropartesStatusMessage(it, isError = true) }
        if (isCreate) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = active, onCheckedChange = { active = it }, enabled = !isWorking)
                Text("Cuenta activa", style = MaterialTheme.typography.bodyMedium)
            }
            EuropartesTextField(password, { password = it; localError = null }, "Contraseña inicial", enabled = !isWorking, required = true, visualTransformation = PasswordVisualTransformation())
            EuropartesTextField(confirmation, { confirmation = it; localError = null }, "Confirmar contraseña", enabled = !isWorking, required = true, visualTransformation = PasswordVisualTransformation(), supportingText = "El servidor aplica la política de contraseñas vigente.")
        }
        (localError ?: error)?.let { EuropartesStatusMessage(it, isError = true) }
    }
}

@Composable
private fun ResetPasswordDialog(
    username: String,
    isWorking: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var password by remember(username) { mutableStateOf("") }
    var confirmation by remember(username) { mutableStateOf("") }
    var localError by remember(username) { mutableStateOf<String?>(null) }
    EuropartesDialog(
        title = "Restablecer contraseña",
        subtitle = "Cuenta: $username",
        onDismiss = onDismiss,
        dismissEnabled = !isWorking,
        footer = {
            EuropartesButton("Cancelar", onDismiss, variant = ButtonVariant.Secondary, enabled = !isWorking)
            EuropartesButton("Restablecer", {
                when {
                    password.isBlank() -> localError = "Ingresa la nueva contraseña."
                    password != confirmation -> localError = "Las contraseñas no coinciden."
                    else -> onSubmit(password, confirmation)
                }
            }, isLoading = isWorking)
        },
    ) {
        EuropartesTextField(password, { password = it; localError = null }, "Nueva contraseña", enabled = !isWorking, required = true, visualTransformation = PasswordVisualTransformation())
        EuropartesTextField(confirmation, { confirmation = it; localError = null }, "Confirmar contraseña", enabled = !isWorking, required = true, visualTransformation = PasswordVisualTransformation())
        (localError ?: error)?.let { EuropartesStatusMessage(it, isError = true) }
    }
}

@Composable
fun ChangeOwnPasswordScreen(
    role: String,
    viewModel: SecurityViewModel,
    modifier: Modifier = Modifier,
    isOnline: Boolean = true,
) {
    val state by viewModel.ownPasswordState.collectAsState()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.saveVersion) {
        if (state.saveVersion > 0) {
            current = ""
            next = ""
            confirmation = ""
        }
    }

    Column(modifier.verticalScroll(rememberScrollState()).padding(PagePadding), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        PageHeader(eyebrow = "Mi cuenta", title = "Cambiar contraseña", description = "Se comprobará tu contraseña vigente y el servidor renovará esta sesión de $role.")
        EuropartesCard(verticalSpacing = Arrangement.spacedBy(12.dp)) {
            EuropartesTextField(current, { current = it; localError = null }, "Contraseña actual", enabled = !state.isWorking, required = true, visualTransformation = PasswordVisualTransformation())
            EuropartesTextField(next, { next = it; localError = null }, "Nueva contraseña", enabled = !state.isWorking, required = true, visualTransformation = PasswordVisualTransformation())
            EuropartesTextField(confirmation, { confirmation = it; localError = null }, "Confirmar nueva contraseña", enabled = !state.isWorking, required = true, visualTransformation = PasswordVisualTransformation())
            (localError ?: state.error)?.let { EuropartesStatusMessage(it, isError = true) }
            state.message?.let { EuropartesStatusMessage(it, isError = false) }
            if (!isOnline) EuropartesStatusMessage("Cambiar la contraseña requiere conexión con el servidor.", isError = true)
            EuropartesButton("Actualizar contraseña", {
                when {
                    current.isBlank() || next.isBlank() || confirmation.isBlank() -> localError = "Completa los tres campos de contraseña."
                    next != confirmation -> localError = "Las contraseñas nuevas no coinciden."
                    next == current -> localError = "La nueva contraseña debe ser diferente a la actual."
                    else -> viewModel.changeOwnPassword(current, next, confirmation)
                }
            }, Modifier.fillMaxWidth(), enabled = isOnline, isLoading = state.isWorking)
        }
    }
}
