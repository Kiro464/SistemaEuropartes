package com.europartes.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.europartes.mobile.data.remote.AuthenticatedUser
import com.europartes.mobile.data.remote.ProductDto
import com.europartes.mobile.data.repository.EuropartesRoles
import com.europartes.mobile.ui.components.EuropartesBrand
import com.europartes.mobile.ui.components.EuropartesMetricPill
import com.europartes.mobile.ui.components.EuropartesPrimaryButton
import com.europartes.mobile.ui.components.EuropartesSectionCard
import com.europartes.mobile.ui.components.EuropartesStatusMessage
import com.europartes.mobile.ui.components.EuropartesTextField
import com.europartes.mobile.ui.login.LoginViewModel
import com.europartes.mobile.ui.navigation.MobileDestination
import com.europartes.mobile.ui.navigation.navigationForRole
import com.europartes.mobile.ui.products.ProductsViewModel
import com.europartes.mobile.ui.theme.EuropartesMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EuropartesMobileTheme {
                val loginViewModel: LoginViewModel = viewModel()
                val loginState by loginViewModel.uiState.collectAsState()
                val productsViewModel: ProductsViewModel = viewModel()
                val productsState by productsViewModel.uiState.collectAsState()

                LaunchedEffect(loginState.authenticatedUser?.userId) {
                    if (loginState.authenticatedUser != null) {
                        productsViewModel.loadProducts()
                    } else {
                        productsViewModel.reset()
                    }
                }

                Surface(color = MaterialTheme.colorScheme.background) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background,
                    ) { padding ->
                        val user = loginState.authenticatedUser
                        if (user == null) {
                            LoginScreen(
                                username = loginState.username,
                                password = loginState.password,
                                isLoading = loginState.isLoading,
                                errorMessage = loginState.errorMessage,
                                onUsernameChange = loginViewModel::onUsernameChange,
                                onPasswordChange = loginViewModel::onPasswordChange,
                                onLogin = loginViewModel::login,
                                modifier = Modifier.fillMaxSize().padding(padding),
                            )
                        } else {
                            AuthenticatedShell(
                                user = user,
                                products = productsState.products,
                                isLoading = productsState.isLoading,
                                errorMessage = productsState.errorMessage,
                                hasLoaded = productsState.hasLoaded,
                                onRefresh = productsViewModel::loadProducts,
                                onLogout = loginViewModel::logout,
                                modifier = Modifier.fillMaxSize().padding(padding),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(
    username: String,
    password: String,
    isLoading: Boolean,
    errorMessage: String?,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.Center,
    ) {
        EuropartesBrand(
            title = "Europartes",
            subtitle = "Accede a tus herramientas de operación, inventario y gestión empresarial.",
        )
        Spacer(Modifier.height(30.dp))
        EuropartesSectionCard(
            title = "Iniciar sesión",
            subtitle = "Utiliza las credenciales de tu cuenta empresarial.",
        ) {
            EuropartesTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = "Usuario",
                supportingText = "Nombre de usuario de Europartes",
                enabled = !isLoading,
                modifier = Modifier.padding(top = 4.dp),
            )
            EuropartesTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = "Contraseña",
                visualTransformation = PasswordVisualTransformation(),
                enabled = !isLoading,
            )
            if (errorMessage != null) {
                EuropartesStatusMessage(errorMessage, isError = true)
            }
            EuropartesPrimaryButton(
                text = "Iniciar sesión",
                onClick = onLogin,
                enabled = !isLoading,
                isLoading = isLoading,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Conexión segura mediante la API de Europartes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AuthenticatedShell(
    user: AuthenticatedUser,
    products: List<ProductDto>,
    isLoading: Boolean,
    errorMessage: String?,
    hasLoaded: Boolean,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigation = remember(user.grupo) { navigationForRole(user.grupo) }
    var destination by remember(user.userId) {
        mutableStateOf(navigation.firstOrNull()?.destination ?: MobileDestination.MODULES)
    }
    Column(modifier = modifier.statusBarsPadding()) {
        AccountHeader(user = user, onLogout = onLogout)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        if (destination == MobileDestination.PRODUCTS && user.grupo in EuropartesRoles.canReadProducts) {
            ProductsScreen(
                products = products,
                isLoading = isLoading,
                errorMessage = errorMessage,
                hasLoaded = hasLoaded,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            ModulesOverview(
                user = user,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
        if (navigation.size > 1) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                navigation.forEach { item ->
                    val selected = destination == item.destination
                    if (selected) {
                        Button(
                            onClick = { destination = item.destination },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(item.label)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { destination = item.destination },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(item.label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountHeader(user: AuthenticatedUser, onLogout: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("EUROPARTES", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            Text(
                listOf(user.nombres, user.apellidos).filter { it.isNotBlank() }.joinToString(" ")
                    .ifBlank { user.username },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(user.grupo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onLogout, shape = RoundedCornerShape(12.dp)) {
            Text("Salir")
        }
    }
}

@Composable
private fun ProductsScreen(
    products: List<ProductDto>,
    isLoading: Boolean,
    errorMessage: String?,
    hasLoaded: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text("Productos e inventario", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Consulta del catálogo activo autorizado para tu rol.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EuropartesMetricPill("Productos", products.size.toString())
            EuropartesMetricPill("Origen", "API Django")
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Catálogo", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onRefresh, enabled = !isLoading, shape = RoundedCornerShape(12.dp)) {
                Text(if (isLoading) "Cargando…" else "Actualizar")
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            isLoading && products.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Conectando con el catálogo…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            errorMessage != null && products.isEmpty() -> Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EuropartesStatusMessage(errorMessage, isError = true)
                Spacer(Modifier.height(12.dp))
                EuropartesPrimaryButton(text = "Reintentar consulta", onClick = onRefresh, enabled = !isLoading)
            }
            hasLoaded && products.isEmpty() -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                EuropartesStatusMessage("No hay productos activos para mostrar.", isError = false)
            }
            else -> {
                if (errorMessage != null) EuropartesStatusMessage(errorMessage, isError = true)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(products, key = { it.id }) { product -> ProductCard(product) }
                }
            }
        }
    }
}

@Composable
private fun ProductCard(product: ProductDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(product.nombre, style = MaterialTheme.typography.titleMedium)
            Text(
                product.marcaNombre ?: product.marca?.let { "Marca #$it" } ?: "Marca no especificada",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            if (!product.descripcion.isNullOrBlank()) {
                Text(product.descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EuropartesMetricPill("Precio", product.precioUnitario ?: "—")
                EuropartesMetricPill("Stock disponible", product.stockDisponible?.toString() ?: "—")
            }
            if (!product.proveedorNombre.isNullOrBlank()) {
                Text("Proveedor: ${product.proveedorNombre}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ModulesOverview(user: AuthenticatedUser, modifier: Modifier = Modifier) {
    val modules = listOf(
        "Productos e inventario" to (user.grupo in EuropartesRoles.canReadProducts),
        "Compras y proveedores" to false,
        "Ventas y despachos" to false,
        "Administración de usuarios" to false,
        "Dashboard y reportería" to false,
    )
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Espacio de trabajo", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Módulos disponibles según la cobertura móvil actual.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EuropartesSectionCard(
            title = "Sesión autenticada",
            subtitle = "El rol se obtuvo de la respuesta real de Django.",
        ) {
            Text(user.username, style = MaterialTheme.typography.titleMedium)
            Text(user.grupo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }
        modules.forEach { (name, implemented) ->
            EuropartesSectionCard(
                title = name,
                subtitle = if (implemented) "Consulta disponible en esta versión." else "Pendiente de una fase posterior.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (implemented) "Disponible" else "Próximamente",
                        color = if (implemented) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        EuropartesStatusMessage(
            "La visibilidad del menú no sustituye la autorización del backend. Django valida cada solicitud.",
            isError = false,
        )
    }
}

@Preview(showBackground = true, name = "Fase 4 · Login definitivo", widthDp = 393, heightDp = 852)
@Composable
private fun LoginScreenPreview() {
    EuropartesMobileTheme {
        LoginScreen(
            username = "",
            password = "",
            isLoading = false,
            errorMessage = null,
            onUsernameChange = {},
            onPasswordChange = {},
            onLogin = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(showBackground = true, name = "Fase 4 · Inventario definitivo", widthDp = 393, heightDp = 852)
@Composable
private fun ProductsScreenPreview() {
    EuropartesMobileTheme {
        ProductsScreen(
            products = listOf(
                ProductDto(101, "Filtro de aceite", 1, "Bosch", "Filtro para motor", "425.00", 12, 2, 10, 1, "Repuestos Central", "2026-10-23", true),
                ProductDto(102, "Pastillas de freno", 2, "Brembo", "Juego de pastillas de freno", "895.00", 8, 1, 7, 2, "Distribuidora Norte", "2026-10-23", true),
            ),
            isLoading = false,
            errorMessage = null,
            hasLoaded = true,
            onRefresh = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(showBackground = true, name = "Fase 4 · Módulos pendientes", widthDp = 393, heightDp = 852)
@Composable
private fun ModulesOverviewPreview() {
    EuropartesMobileTheme {
        ModulesOverview(
            user = AuthenticatedUser(7, "usuario.demo", "Usuario", "Demo", EuropartesRoles.COORDINADOR_LOGISTICA),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
