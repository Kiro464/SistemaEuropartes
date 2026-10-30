package com.europartes.mobile.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.europartes.mobile.data.remote.UserAccountDto
import com.europartes.mobile.data.remote.EmployeeDto
import com.europartes.mobile.data.repository.EuropartesRoles
import com.europartes.mobile.data.repository.UserAccountWrite

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
            listOf(user.username, user.firstName, user.lastName, user.email, user.role.orEmpty())
                .any { it.lowercase().contains(term) }
        }
    }

    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Cuentas y roles", style = MaterialTheme.typography.headlineSmall)
                Text("Administración reservada a Soporte TI", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { viewModel.loadUsers(role, force = true) }, enabled = !state.isLoading) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar cuentas")
            }
            IconButton(onClick = {
                viewModel.clearMessage()
                viewModel.openCreate()
                editingId = null
                versionWhenDialogOpened = state.saveVersion
                showForm = true
            }, enabled = role == EuropartesRoles.SOPORTE_TI && !state.isWorking) {
                Icon(Icons.Default.Add, contentDescription = "Crear cuenta")
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar usuario, nombre, correo o rol") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )

        state.error?.let { SecurityMessage(it, isError = true) }
        state.message?.let { SecurityMessage(it, isError = state.messageIsError) }

        when {
            role != EuropartesRoles.SOPORTE_TI -> SecurityMessage("Solo Soporte TI puede consultar cuentas.", true)
            state.isLoading && !state.hasLoaded -> Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Cargando cuentas…")
            }
            state.error != null && state.users.isEmpty() -> OutlinedButton(onClick = { viewModel.loadUsers(role, force = true) }) {
                Text("Reintentar")
            }
            filteredUsers.isEmpty() -> Card(Modifier.fillMaxWidth()) {
                Text(
                    if (state.users.isEmpty()) "No hay cuentas disponibles." else "No hay cuentas que coincidan con la búsqueda.",
                    Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filteredUsers, key = { it.id }) { user ->
                    AccountCard(
                        user = user,
                        employee = state.employees.firstOrNull { it.id == user.employeeId },
                        employeesLoading = state.employeesLoading,
                        employeeError = state.employeeError,
                        isCurrentUser = user.id == currentUserId,
                        isWorking = state.isWorking,
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
    }

    if (showForm) {
        val account = if (editingId == null) null else state.editingUser?.takeIf { it.id == editingId }
        if (editingId != null && state.editorLoading) {
            AlertDialog(
                onDismissRequest = { showForm = false },
                confirmButton = {},
                text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Spacer(Modifier.width(12.dp)); Text("Cargando cuenta…") } },
            )
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
        AlertDialog(
            onDismissRequest = { if (!state.isWorking) statusTarget = null },
            title = { Text(if (active) "Activar cuenta" else "Desactivar cuenta") },
            text = { Text("${if (active) "Se habilitará" else "Se bloqueará"} el acceso de ${user.username}. El servidor validará las restricciones de seguridad.") },
            confirmButton = {
                Button(enabled = !state.isWorking, onClick = {
                    viewModel.setUserActive(role, user, active)
                    statusTarget = null
                }) { Text(if (active) "Activar" else "Desactivar") }
            },
            dismissButton = { TextButton(enabled = !state.isWorking, onClick = { statusTarget = null }) { Text("Cancelar") } },
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(user.username, style = MaterialTheme.typography.titleLarge)
            Text("${user.firstName} ${user.lastName}".trim().ifBlank { "Sin nombre registrado" }, style = MaterialTheme.typography.bodyLarge)
            Text(user.email.ifBlank { "Sin correo" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(user.role ?: "Rol funcional inválido", color = if (user.roleValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            val employeeSummary = when {
                user.employeeId == null -> "Empleado: sin asociación"
                employeesLoading -> "Empleado #${user.employeeId}: cargando información…"
                employeeError != null -> "Empleado #${user.employeeId}: no se pudo cargar la información del empleado"
                employee != null -> "Empleado: ${employee.names} ${employee.surnames}".trim() + if (employee.active == false) " · Inactivo" else ""
                else -> "Empleado #${user.employeeId}: no encontrado en el catálogo"
            }
            Text(employeeSummary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (user.isActive) "Estado: Activa" else "Estado: Inactiva", color = if (user.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !isWorking, onClick = onEdit) { Text("Editar") }
                OutlinedButton(enabled = !isWorking, onClick = onResetPassword) { Text("Restablecer contraseña") }
                if (!isCurrentUser) {
                    TextButton(enabled = !isWorking, onClick = { onToggleActive(!user.isActive) }) {
                        Text(if (user.isActive) "Desactivar" else "Activar")
                    }
                }
            }
            if (isCurrentUser) Text("Tu cuenta no puede cambiar su propio rol ni desactivarse aquí.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
    var roleMenu by remember { mutableStateOf(false) }
    var employeeMenu by remember { mutableStateOf(false) }
    var localError by remember(initial?.id) { mutableStateOf<String?>(null) }
    val isCreate = initial == null
    val isSelf = initial?.id == currentUserId

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = { Text(if (isCreate) "Crear cuenta" else "Editar cuenta") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(username, { username = it; localError = null }, label = { Text("Nombre de usuario") }, singleLine = true, enabled = !isWorking, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it; localError = null }, label = { Text("Correo electrónico") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, enabled = !isWorking, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(firstName, { firstName = it }, label = { Text("Nombres") }, singleLine = true, enabled = !isWorking, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(lastName, { lastName = it }, label = { Text("Apellidos") }, singleLine = true, enabled = !isWorking, modifier = Modifier.fillMaxWidth())
                Text("Rol funcional", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(enabled = !isWorking && !isSelf, onClick = { roleMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(role ?: "Seleccionar rol") }
                DropdownMenu(expanded = roleMenu, onDismissRequest = { roleMenu = false }) {
                    functionalRoles.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { role = option; roleMenu = false }) }
                }
                if (isSelf) Text("No puedes cambiar tu propio rol.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (initial != null && role == null) {
                    Text(
                        "El rol actual no se reconoce. Selecciona explícitamente un rol funcional válido antes de guardar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text("Empleado vinculado (opcional)", style = MaterialTheme.typography.labelLarge)
                if (employeesLoading) Text("Cargando empleados…", style = MaterialTheme.typography.bodySmall)
                employeeError?.let { SecurityMessage(it, isError = true) }
                val selectedEmployee = employees.firstOrNull { it.id == employeeId }
                val employeeAssociationUnavailable = employeeId != null && (employeesLoading || employeeError != null)
                val employeeSelectionLabel = when {
                    employeeId == null -> "Sin empleado asociado"
                    employeesLoading -> "Cargando empleado #$employeeId…"
                    employeeError != null -> "No se pudo cargar empleado #$employeeId"
                    selectedEmployee != null -> "${selectedEmployee.names} ${selectedEmployee.surnames}".trim() + if (selectedEmployee.active == false) " · Inactivo" else ""
                    else -> "Empleado #$employeeId no encontrado en el catálogo"
                }
                OutlinedButton(enabled = !isWorking && !employeeAssociationUnavailable, onClick = { employeeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(employeeSelectionLabel)
                }
                DropdownMenu(expanded = employeeMenu, onDismissRequest = { employeeMenu = false }) {
                    DropdownMenuItem(text = { Text("Sin empleado asociado") }, onClick = { employeeId = null; employeeMenu = false })
                    employees.filter { it.userId == null || it.id == initial?.employeeId }.forEach { option ->
                        DropdownMenuItem(
                            text = { Text("${option.names} ${option.surnames}${if (!option.active) " · Inactivo" else ""}") },
                            onClick = { employeeId = option.id; employeeMenu = false },
                        )
                    }
                }
                if (isCreate) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = active, onCheckedChange = { active = it }, enabled = !isWorking)
                        Text("Cuenta activa al crear")
                    }
                }
                if (isCreate) {
                    OutlinedTextField(password, { password = it; localError = null }, label = { Text("Contraseña inicial") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !isWorking, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(confirmation, { confirmation = it; localError = null }, label = { Text("Confirmar contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !isWorking, modifier = Modifier.fillMaxWidth())
                    Text("El servidor aplicará la política de contraseña vigente.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                (localError ?: error)?.let { SecurityMessage(it, isError = true) }
            }
        },
        confirmButton = {
            Button(enabled = !isWorking, onClick = {
                val selectedRole = role
                when {
                    username.isBlank() -> localError = "Ingresa el nombre de usuario."
                    selectedRole == null || selectedRole.orEmpty() !in functionalRoles -> localError = "Selecciona un rol funcional válido."
                    isCreate && password.isBlank() -> localError = "La contraseña inicial es obligatoria."
                    isCreate && password != confirmation -> localError = "Las contraseñas no coinciden."
                    else -> onSubmit(
                        UserAccountWrite(
                            username = username,
                            email = email,
                            firstName = firstName,
                            lastName = lastName,
                            role = selectedRole.orEmpty(),
                            employeeId = employeeId,
                            active = active,
                            password = password.takeIf { isCreate },
                            passwordConfirmation = confirmation.takeIf { isCreate },
                        ),
                    )
                }
            }) { if (isWorking) CircularProgressIndicator() else Text("Guardar") }
        },
        dismissButton = { TextButton(enabled = !isWorking, onClick = onDismiss) { Text("Cancelar") } },
    )
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
    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = { Text("Restablecer contraseña") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Nueva contraseña para $username")
                OutlinedTextField(password, { password = it; localError = null }, label = { Text("Nueva contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !isWorking)
                OutlinedTextField(confirmation, { confirmation = it; localError = null }, label = { Text("Confirmar contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !isWorking)
                (localError ?: error)?.let { SecurityMessage(it, isError = true) }
            }
        },
        confirmButton = {
            Button(enabled = !isWorking, onClick = {
                when {
                    password.isBlank() -> localError = "Ingresa la nueva contraseña."
                    password != confirmation -> localError = "Las contraseñas no coinciden."
                    else -> onSubmit(password, confirmation)
                }
            }) { if (isWorking) CircularProgressIndicator() else Text("Restablecer") }
        },
        dismissButton = { TextButton(enabled = !isWorking, onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun ChangeOwnPasswordScreen(
    role: String,
    viewModel: SecurityViewModel,
    modifier: Modifier = Modifier,
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

    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Seguridad de la cuenta", style = MaterialTheme.typography.headlineSmall)
        Text("Actualiza tu contraseña. Se comprobará la contraseña vigente y el servidor renovará esta sesión.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(current, { current = it; localError = null }, label = { Text("Contraseña actual") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !state.isWorking, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(next, { next = it; localError = null }, label = { Text("Nueva contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !state.isWorking, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(confirmation, { confirmation = it; localError = null }, label = { Text("Confirmar nueva contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !state.isWorking, modifier = Modifier.fillMaxWidth())
        (localError ?: state.error)?.let { SecurityMessage(it, isError = true) }
        state.message?.let { SecurityMessage(it, isError = false) }
        Button(enabled = !state.isWorking, onClick = {
            when {
                current.isBlank() || next.isBlank() || confirmation.isBlank() -> localError = "Completa los tres campos de contraseña."
                next != confirmation -> localError = "Las contraseñas nuevas no coinciden."
                next == current -> localError = "La nueva contraseña debe ser diferente a la actual."
                else -> viewModel.changeOwnPassword(current, next, confirmation)
            }
        }) {
            if (state.isWorking) CircularProgressIndicator() else Text("Actualizar contraseña")
        }
        Text("Sesión de ${role}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SecurityMessage(message: String, isError: Boolean) {
    Text(
        message,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium,
    )
}
