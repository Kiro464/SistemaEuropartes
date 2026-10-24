package com.europartes.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.europartes.mobile.data.local.ProductEntity
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
import com.europartes.mobile.ui.products.LocalProductsScreen
import com.europartes.mobile.ui.products.LocalProductsViewModel
import com.europartes.mobile.ui.products.ProductsViewModel
import com.europartes.mobile.ui.theme.EuropartesMobileTheme
import kotlinx.coroutines.launch

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
                val localProductsViewModel: LocalProductsViewModel = viewModel()
                val localProducts by localProductsViewModel.localProducts.collectAsState()
                val localProductsState by localProductsViewModel.uiState.collectAsState()

                LaunchedEffect(loginState.authenticatedUser?.userId) {
                    if (loginState.authenticatedUser != null) {
                        productsViewModel.loadProducts()
                    } else {
                        productsViewModel.reset()
                    }
                }

                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
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
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        AuthenticatedShell(
                            user = user,
                            products = productsState.products,
                            isLoading = productsState.isLoading,
                            errorMessage = productsState.errorMessage,
                            hasLoaded = productsState.hasLoaded,
                            onRefresh = productsViewModel::loadProducts,
                            localProducts = localProducts,
                            localWorking = localProductsState.isWorking,
                            localMessage = localProductsState.message,
                            localIsError = localProductsState.isError,
                            onCreateLocal = localProductsViewModel::create,
                            onUpdateLocal = localProductsViewModel::update,
                            onDeleteLocal = localProductsViewModel::delete,
                            onClearLocalMessage = localProductsViewModel::clearMessage,
                            onLogout = loginViewModel::logout,
                            modifier = Modifier.fillMaxSize(),
                        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedShell(
    user: AuthenticatedUser,
    products: List<ProductDto>,
    isLoading: Boolean,
    errorMessage: String?,
    hasLoaded: Boolean,
    onRefresh: () -> Unit,
    localProducts: List<ProductEntity>,
    localWorking: Boolean,
    localMessage: String?,
    localIsError: Boolean,
    onCreateLocal: (String, String?, String?, String?, Int?) -> Unit,
    onUpdateLocal: (ProductEntity, String, String?, String?, String?, Int?) -> Unit,
    onDeleteLocal: (ProductEntity) -> Unit,
    onClearLocalMessage: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigation = remember(user.grupo) { navigationForRole(user.grupo) }
    var destination by remember(user.userId, navigation) {
        mutableStateOf(navigation.firstOrNull()?.destination)
    }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val title = when (destination) {
        MobileDestination.PRODUCTS -> "Productos del servidor"
        MobileDestination.LOCAL_DRAFTS -> "Borradores locales"
        null -> "Europartes"
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = navigation.isNotEmpty(),
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("EUROPARTES", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        listOf(user.nombres, user.apellidos).filter { it.isNotBlank() }.joinToString(" ").ifBlank { user.username },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(user.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    EuropartesMetricPill("Rol autenticado", user.grupo)
                }
                HorizontalDivider()
                navigation.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        selected = destination == item.destination,
                        onClick = {
                            destination = item.destination
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                TextButton(
                    onClick = {
                        scope.launch { drawerState.close() }
                        onLogout()
                    },
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) { Text("Cerrar sesión") }
            }
        },
        modifier = modifier,
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            enabled = navigation.isNotEmpty(),
                        ) {
                            Text("☰", style = MaterialTheme.typography.headlineMedium)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            when (destination) {
                MobileDestination.PRODUCTS -> {
                    if (user.grupo in EuropartesRoles.canReadProducts) {
                        ProductsScreen(
                            products = products,
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            hasLoaded = hasLoaded,
                            onRefresh = onRefresh,
                            modifier = Modifier.fillMaxSize().padding(padding),
                        )
                    } else {
                        NoAuthorizedDestination(Modifier.fillMaxSize().padding(padding))
                    }
                }
                MobileDestination.LOCAL_DRAFTS -> {
                    if (user.grupo in EuropartesRoles.canManageProductsInBackend) {
                        LocalProductsScreen(
                            products = localProducts,
                            isWorking = localWorking,
                            message = localMessage,
                            isError = localIsError,
                            onCreate = onCreateLocal,
                            onUpdate = onUpdateLocal,
                            onDelete = onDeleteLocal,
                            onClearMessage = onClearLocalMessage,
                            modifier = Modifier.fillMaxSize().padding(padding),
                        )
                    } else {
                        NoAuthorizedDestination(Modifier.fillMaxSize().padding(padding))
                    }
                }
                null -> NoAuthorizedDestination(Modifier.fillMaxSize().padding(padding))
            }
        }
    }
}

@Composable
private fun NoAuthorizedDestination(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        EuropartesStatusMessage(
            "Tu rol no tiene destinos móviles habilitados en esta versión. Contacta al administrador si consideras que es incorrecto.",
            isError = false,
        )
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
            "Datos consultados desde la API de Django. El servidor es la fuente autoritativa.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EuropartesMetricPill("Productos", products.size.toString())
            EuropartesMetricPill("Origen", "SERVIDOR")
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
            Text("Origen: SERVIDOR · Solo lectura", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
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
