package com.europartes.mobile

import android.os.Bundle
import android.content.res.Configuration
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.europartes.mobile.data.local.ProductEntity
import com.europartes.mobile.data.remote.AuthenticatedUser
import com.europartes.mobile.data.remote.ProductDto
import com.europartes.mobile.data.remote.CatalogOptionDto
import com.europartes.mobile.data.repository.EuropartesRoles
import com.europartes.mobile.ui.components.EuropartesBrand
import com.europartes.mobile.ui.components.EuropartesMetricPill
import com.europartes.mobile.ui.components.EuropartesOutlinedCard
import com.europartes.mobile.ui.components.EuropartesPrimaryButton
import com.europartes.mobile.ui.components.EuropartesSectionCard
import com.europartes.mobile.ui.components.EuropartesStateBadge
import com.europartes.mobile.ui.components.EuropartesStatusMessage
import com.europartes.mobile.ui.components.EuropartesTextField
import com.europartes.mobile.ui.login.LoginViewModel
import com.europartes.mobile.ui.catalogs.CatalogsScreen
import com.europartes.mobile.ui.navigation.MobileDestination
import com.europartes.mobile.ui.navigation.DASHBOARD_ROLES
import com.europartes.mobile.ui.inventory.KardexScreen
import com.europartes.mobile.ui.navigation.navigationForRole
import com.europartes.mobile.ui.products.LocalProductsScreen
import com.europartes.mobile.ui.products.LocalProductsViewModel
import com.europartes.mobile.ui.products.ProductsViewModel
import com.europartes.mobile.ui.purchasing.PurchasingScreen
import com.europartes.mobile.ui.purchasing.PurchasingViewModel
import com.europartes.mobile.ui.sales.CustomersScreen
import com.europartes.mobile.ui.sales.DispatchesScreen
import com.europartes.mobile.ui.sales.PointOfSaleScreen
import com.europartes.mobile.ui.sales.SalesHistoryScreen
import com.europartes.mobile.ui.sales.SalesViewModel
import com.europartes.mobile.ui.security.ChangeOwnPasswordScreen
import com.europartes.mobile.ui.security.SecurityAdminScreen
import com.europartes.mobile.ui.security.SecurityViewModel
import com.europartes.mobile.ui.dashboard.DashboardScreen
import com.europartes.mobile.ui.dashboard.DashboardViewModel
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
                            isRestoringSession = loginState.isRestoringSession,
                            canRetrySessionRestore = loginState.canRetrySessionRestore,
                            errorMessage = loginState.errorMessage,
                            onUsernameChange = loginViewModel::onUsernameChange,
                            onPasswordChange = loginViewModel::onPasswordChange,
                            onLogin = loginViewModel::login,
                            onRestoreSession = loginViewModel::retrySessionRestore,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        AuthenticatedShell(
                            user = user,
                            products = productsState.products,
                            isLoading = productsState.isLoading,
                            errorMessage = productsState.errorMessage,
                            hasLoaded = productsState.hasLoaded,
                            onRefresh = { includeInactive -> productsViewModel.loadProducts(includeInactive) },
                            productWorking = productsState.isWorking,
                            productMessage = productsState.message,
                            productMessageIsError = productsState.messageIsError,
                            brands = productsState.brands,
                            suppliers = productsState.suppliers,
                            onLoadCatalogs = productsViewModel::loadCatalogs,
                            onSaveProduct = productsViewModel::save,
                            onChangeProductState = productsViewModel::changeActive,
                            onRefreshProducts = { productsViewModel.loadProducts() },
                            onClearProductMessage = productsViewModel::clearMessage,
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
    isRestoringSession: Boolean = false,
    canRetrySessionRestore: Boolean = false,
    onRestoreSession: () -> Unit = {},
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 24.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = MaterialTheme.colorScheme.secondary, shape = RoundedCornerShape(11.dp)) {
                    Text("EP", Modifier.padding(horizontal = 11.dp, vertical = 6.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondary)
                }
                Text("EUROPARTES", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                Text("Gestión comercial,\nen movimiento.", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimary)
                Text("Inventario y operación empresarial en un solo lugar.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f))
            }
        }
        Spacer(Modifier.height(18.dp))
        EuropartesSectionCard(
            title = "Iniciar sesión",
            subtitle = "Ingresa con tu cuenta empresarial.",
        ) {
            EuropartesTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = "Usuario",
                enabled = !isLoading && !isRestoringSession,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )
            EuropartesTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = "Contraseña",
                visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                enabled = !isLoading && !isRestoringSession,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingContent = {
                    TextButton(onClick = { passwordVisible = !passwordVisible }, enabled = !isLoading && !isRestoringSession) {
                        Text(if (passwordVisible) "Ocultar" else "Mostrar", style = MaterialTheme.typography.labelMedium)
                    }
                },
            )
            if (errorMessage != null) {
                EuropartesStatusMessage(errorMessage, isError = true)
            }
            if (canRetrySessionRestore) {
                TextButton(
                    onClick = onRestoreSession,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reintentar verificación de sesión")
                }
            }
            EuropartesPrimaryButton(
                text = if (isRestoringSession) "Comprobando sesión guardada" else "Iniciar sesión",
                onClick = onLogin,
                enabled = !isLoading && !isRestoringSession,
                isLoading = isLoading || isRestoringSession,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Tu sesión se valida de forma segura con Europartes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
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
    onRefresh: (Boolean) -> Unit,
    productWorking: Boolean,
    productMessage: String?,
    productMessageIsError: Boolean,
    brands: List<CatalogOptionDto>,
    suppliers: List<CatalogOptionDto>,
    onLoadCatalogs: () -> Unit,
    onSaveProduct: (Int?, String, String, Int?, Int?, String) -> Unit,
    onChangeProductState: (ProductDto, Boolean) -> Unit,
    onRefreshProducts: () -> Unit,
    onClearProductMessage: () -> Unit,
    localProducts: List<ProductEntity>,
    localWorking: Boolean,
    localMessage: String?,
    localIsError: Boolean,
    onCreateLocal: (String, String?, String?, String?, Int?) -> Unit,
    onUpdateLocal: (ProductEntity, String, String?, String?, String?, Int?) -> Unit,
    onDeleteLocal: (ProductEntity) -> Unit,
    onClearLocalMessage: () -> Unit,
    onLogout: () -> Unit,
    previewDrawerOpen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val navigation = remember(user.grupo) { navigationForRole(user.grupo) }
    val purchasingViewModel: PurchasingViewModel = viewModel()
    val salesViewModel: SalesViewModel = viewModel()
    val securityViewModel: SecurityViewModel = viewModel()
    val dashboardViewModel: DashboardViewModel = viewModel()
    DisposableEffect(user.userId) {
        onDispose { salesViewModel.reset() }
    }
    var destination by remember(user.userId, navigation) {
        mutableStateOf(navigation.firstOrNull()?.destination)
    }
    val drawerState = rememberDrawerState(initialValue = if (previewDrawerOpen) DrawerValue.Open else DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val title = when (destination) {
        MobileDestination.PRODUCTS -> "Productos del servidor"
        MobileDestination.KARDEX -> "Movimientos y Kardex"
        MobileDestination.LOCAL_DRAFTS -> "Borradores locales"
        MobileDestination.CATALOGS -> "Catálogos"
        MobileDestination.PURCHASING -> "Solicitudes y compras"
        MobileDestination.CUSTOMERS -> "Clientes"
        MobileDestination.SALES -> "Punto de venta"
        MobileDestination.SALES_HISTORY -> "Historial de facturas"
        MobileDestination.DISPATCHES -> "Despachos pendientes"
        MobileDestination.USER_ADMIN -> "Cuentas y roles"
        MobileDestination.ACCOUNT_SECURITY -> "Seguridad de cuenta"
        MobileDestination.DASHBOARD -> "Dashboard analítico"
        null -> "Europartes"
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = navigation.isNotEmpty(),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Surface(color = MaterialTheme.colorScheme.secondary, shape = RoundedCornerShape(10.dp)) {
                        Text("EP", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondary)
                    }
                    Text("Europartes", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
                    Text(
                        listOf(user.nombres, user.apellidos).filter { it.isNotBlank() }.joinToString(" ").ifBlank { user.username },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(user.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f))
                    Spacer(Modifier.height(2.dp))
                    EuropartesStateBadge(user.grupo, isPositive = true)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                navigation.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        selected = destination == item.destination,
                        onClick = {
                            destination = item.destination
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                        colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                TextButton(
                    onClick = {
                        scope.launch { drawerState.close() }
                        onLogout()
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                ) { Text("Cerrar sesión", color = MaterialTheme.colorScheme.error) }
            }
        },
        modifier = modifier,
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        TextButton(
                            onClick = { scope.launch { drawerState.open() } },
                            enabled = navigation.isNotEmpty(),
                            modifier = Modifier.padding(start = 6.dp),
                        ) {
                            Text("Menú", style = MaterialTheme.typography.labelLarge)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
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
                            onRefresh = { includeInactive -> onRefresh(includeInactive) },
                            canManage = user.grupo in EuropartesRoles.canManageProductsInBackend,
                            isCoordinator = user.grupo == EuropartesRoles.COORDINADOR_LOGISTICA,
                            isWorking = productWorking,
                            message = productMessage,
                            messageIsError = productMessageIsError,
                            brands = brands,
                            suppliers = suppliers,
                            onLoadCatalogs = onLoadCatalogs,
                            onSave = onSaveProduct,
                            onChangeState = onChangeProductState,
                            onClearMessage = onClearProductMessage,
                            modifier = Modifier.fillMaxSize().padding(padding),
                        )
                    } else {
                        NoAuthorizedDestination(Modifier.fillMaxSize().padding(padding))
                    }
                }
                MobileDestination.KARDEX -> {
                    if (user.grupo in EuropartesRoles.canReadKardex) {
                        KardexScreen(
                            role = user.grupo,
                            sessionKey = user.userId,
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
                MobileDestination.CATALOGS -> CatalogsScreen(
                    role = user.grupo,
                    sessionKey = user.userId,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
                MobileDestination.PURCHASING -> {
                    if (user.grupo in PurchasingViewModel.requestRoles || user.grupo in PurchasingViewModel.purchaseRoles) {
                        PurchasingScreen(
                            viewModel = purchasingViewModel,
                            role = user.grupo,
                            sessionKey = user.userId,
                            modifier = Modifier.fillMaxSize().padding(padding),
                        )
                    } else {
                        NoAuthorizedDestination(Modifier.fillMaxSize().padding(padding))
                    }
                }
                MobileDestination.CUSTOMERS -> {
                    if (user.grupo in SalesViewModel.CUSTOMER_ROLES) {
                        CustomersScreen(user.grupo, user.userId, salesViewModel, Modifier.fillMaxSize().padding(padding))
                    } else NoAuthorizedDestination(Modifier.fillMaxSize().padding(padding))
                }
                MobileDestination.SALES -> {
                    if (user.grupo == SalesViewModel.VENDEDOR) {
                        PointOfSaleScreen(user.grupo, user.userId, salesViewModel, onRefreshProducts, Modifier.fillMaxSize().padding(padding))
                    } else NoAuthorizedDestination(Modifier.fillMaxSize().padding(padding))
                }
                MobileDestination.SALES_HISTORY -> {
                    if (user.grupo in SalesViewModel.HISTORY_ROLES) {
                        SalesHistoryScreen(user.grupo, user.userId, salesViewModel, Modifier.fillMaxSize().padding(padding))
                    } else NoAuthorizedDestination(Modifier.fillMaxSize().padding(padding))
                }
                MobileDestination.DISPATCHES -> {
                    if (user.grupo == SalesViewModel.OFICIAL_LOGISTICA) {
                        DispatchesScreen(user.grupo, user.userId, salesViewModel, onRefreshProducts, Modifier.fillMaxSize().padding(padding))
                    } else NoAuthorizedDestination(Modifier.fillMaxSize().padding(padding))
                }
                MobileDestination.USER_ADMIN -> {
                    if (user.grupo == "Soporte TI") {
                        SecurityAdminScreen(user.grupo, user.userId, securityViewModel, Modifier.fillMaxSize().padding(padding))
                    } else NoAuthorizedDestination(Modifier.fillMaxSize().padding(padding))
                }
                MobileDestination.ACCOUNT_SECURITY -> {
                    ChangeOwnPasswordScreen(user.grupo, securityViewModel, Modifier.fillMaxSize().padding(padding))
                }
                MobileDestination.DASHBOARD -> {
                    if (user.grupo in DASHBOARD_ROLES) {
                        DashboardScreen(user.grupo, Modifier.fillMaxSize().padding(padding), dashboardViewModel)
                    } else NoAuthorizedDestination(Modifier.fillMaxSize().padding(padding))
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
    products: List<ProductDto>, isLoading: Boolean, errorMessage: String?, hasLoaded: Boolean,
    onRefresh: (Boolean) -> Unit, canManage: Boolean, isCoordinator: Boolean, isWorking: Boolean,
    message: String?, messageIsError: Boolean, brands: List<CatalogOptionDto>, suppliers: List<CatalogOptionDto>,
    onLoadCatalogs: () -> Unit, onSave: (Int?, String, String, Int?, Int?, String) -> Unit,
    onChangeState: (ProductDto, Boolean) -> Unit, onClearMessage: () -> Unit, modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<ProductDto?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var includeInactive by remember { mutableStateOf(false) }
    var confirmStateChange by remember { mutableStateOf<Pair<ProductDto, Boolean>?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(canManage) { if (canManage) onLoadCatalogs() }
    LaunchedEffect(message, messageIsError) {
        if (!messageIsError && message in setOf("Producto creado en el servidor.", "Producto actualizado en el servidor.")) showForm = false
    }
    Column(modifier = modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Productos", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("Catálogo remoto · inventario", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            EuropartesStateBadge("API", isPositive = true)
        }
        Spacer(Modifier.height(14.dp))
        EuropartesTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = "Buscar productos",
            enabled = !isWorking,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canManage) {
                Button(
                    onClick = { editing = null; showForm = true; onLoadCatalogs() },
                    enabled = !isWorking,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                ) { Text("Nuevo producto") }
            }
            OutlinedButton(
                onClick = { onRefresh(includeInactive) },
                enabled = !isLoading && !isWorking,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) { Text(if (isLoading) "Actualizando…" else "Actualizar") }
        }
        if (canManage) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Incluir inactivos", style = MaterialTheme.typography.titleSmall)
                    Text("Para reactivar productos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(
                    checked = includeInactive,
                    onCheckedChange = { includeInactive = it; onRefresh(it) },
                    enabled = !isWorking,
                )
            }
        }
        if (message != null) {
            EuropartesStatusMessage(message, isError = messageIsError)
            TextButton(onClick = onClearMessage) { Text("Cerrar mensaje") }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "${products.size} productos${if (searchQuery.isNotBlank()) " · búsqueda local" else ""}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val visibleProducts = products.filter { product ->
            val query = searchQuery.trim()
            query.isBlank() || listOfNotNull(product.nombre, product.marcaNombre, product.proveedorNombre, product.descripcion, product.id.toString())
                .any { it.contains(query, ignoreCase = true) }
        }
        when {
            isLoading && products.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            errorMessage != null && products.isEmpty() -> Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                EuropartesStatusMessage(errorMessage, isError = true); EuropartesPrimaryButton("Reintentar consulta", { onRefresh(includeInactive) }, enabled = !isLoading)
            }
            hasLoaded && products.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EuropartesSectionCard(
                    title = if (includeInactive) "Catálogo sin productos" else "No hay productos activos",
                    subtitle = "${if (includeInactive) "No hay productos en este catálogo." else "No hay productos activos para mostrar."} Puedes actualizar la consulta para comprobar si hay cambios.",
                ) { OutlinedButton(onClick = { onRefresh(includeInactive) }, enabled = !isLoading) { Text("Actualizar catálogo") } }
            }
            visibleProducts.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EuropartesSectionCard(title = "Sin coincidencias", subtitle = "Prueba con otro nombre, marca, proveedor o código.") {
                    TextButton(onClick = { searchQuery = "" }) { Text("Limpiar búsqueda") }
                }
            }
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visibleProducts, key = { it.id }) { product ->
                    ProductCard(product, canManage, isCoordinator, isWorking,
                        onEdit = { editing = product; showForm = true; onLoadCatalogs() },
                        onStateChange = { active -> confirmStateChange = product to active })
                }
            }
        }
    }
    if (showForm) ProductFormDialog(
        initial = editing, brands = brands, suppliers = suppliers, isWorking = isWorking,
        message = message, messageIsError = messageIsError,
        onDismiss = { if (!isWorking) showForm = false },
        onSubmit = { id, name, description, brand, supplier, price -> onSave(id, name, description, brand, supplier, price) },
    )
    confirmStateChange?.let { (product, active) ->
        AlertDialog(
            onDismissRequest = { if (!isWorking) confirmStateChange = null },
            title = { Text(if (active) "Reactivar producto" else "Desactivar producto") },
            text = { Text(if (active) "¿Reactivar ${product.nombre}? Volverá a aparecer en el catálogo activo." else "¿Desactivar ${product.nombre}? Se conservará en el servidor y dejará de aparecer en la lista habitual.") },
            confirmButton = { Button(enabled = !isWorking, onClick = { onChangeState(product, active); confirmStateChange = null }) { Text(if (active) "Reactivar" else "Desactivar") } },
            dismissButton = { TextButton(enabled = !isWorking, onClick = { confirmStateChange = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ProductFormDialog(
    initial: ProductDto?, brands: List<CatalogOptionDto>, suppliers: List<CatalogOptionDto>, isWorking: Boolean,
    message: String?, messageIsError: Boolean,
    onDismiss: () -> Unit, onSubmit: (Int?, String, String, Int?, Int?, String) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.nombre.orEmpty()) }
    var description by remember(initial?.id) { mutableStateOf(initial?.descripcion.orEmpty()) }
    var price by remember(initial?.id) { mutableStateOf(initial?.precioUnitario.orEmpty()) }
    var brandId by remember(initial?.id, brands) { mutableStateOf(initial?.marca) }
    var supplierId by remember(initial?.id, suppliers) { mutableStateOf(initial?.proveedor) }
    var brandExpanded by remember { mutableStateOf(false) }
    var supplierExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (initial == null) "Nuevo producto" else "Editar producto")
                Text(
                    if (initial == null) "Se registrará en el catálogo del servidor." else "Código #${initial.id} · cambios en el servidor",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nombre *") }, enabled = !isWorking, singleLine = true, shape = RoundedCornerShape(13.dp))
                OutlinedTextField(description, { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Descripción *") }, enabled = !isWorking, minLines = 2, shape = RoundedCornerShape(13.dp))
                Text("CATÁLOGOS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Box {
                    OutlinedButton(onClick = { brandExpanded = true }, enabled = !isWorking, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Text(brands.firstOrNull { it.id == brandId }?.nombre ?: "Seleccionar marca *", modifier = Modifier.fillMaxWidth()) }
                    DropdownMenu(expanded = brandExpanded, onDismissRequest = { brandExpanded = false }) {
                        brands.forEach { option -> DropdownMenuItem(text = { Text(option.nombre) }, onClick = { brandId = option.id; brandExpanded = false }) }
                    }
                }
                Box {
                    OutlinedButton(onClick = { supplierExpanded = true }, enabled = !isWorking, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Text(suppliers.firstOrNull { it.id == supplierId }?.nombre ?: "Seleccionar proveedor *", modifier = Modifier.fillMaxWidth()) }
                    DropdownMenu(expanded = supplierExpanded, onDismissRequest = { supplierExpanded = false }) {
                        suppliers.forEach { option -> DropdownMenuItem(text = { Text(option.nombre) }, onClick = { supplierId = option.id; supplierExpanded = false }) }
                    }
                }
                OutlinedTextField(price, { price = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Precio unitario *") }, enabled = !isWorking, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(13.dp))
                if (initial != null) {
                    EuropartesOutlinedCard {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Existencias informativas", style = MaterialTheme.typography.titleSmall)
                            Text("El stock no se modifica desde este formulario.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Físico ${initial.stockActual ?: 0}   ·   Reservado ${initial.stockReservado ?: 0}   ·   Disponible ${initial.stockDisponible ?: 0}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (brands.isEmpty() || suppliers.isEmpty()) EuropartesStatusMessage("No se pudieron cargar los catálogos. Comprueba permisos de marca/proveedor y vuelve a abrir el formulario.", isError = true)
                if (message != null) Text(message, color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(enabled = !isWorking && brands.isNotEmpty() && suppliers.isNotEmpty(), onClick = { onSubmit(initial?.id, name, description, brandId, supplierId, price) }, shape = RoundedCornerShape(12.dp)) { if (isWorking) CircularProgressIndicator() else Text("Guardar") } },
        dismissButton = { TextButton(enabled = !isWorking, onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ProductCard(product: ProductDto, canManage: Boolean, isCoordinator: Boolean, isWorking: Boolean, onEdit: () -> Unit, onStateChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(product.nombre, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("Código #${product.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        EuropartesStateBadge(if (product.estado == false) "Inactivo" else "Activo", isPositive = product.estado != false)
            }
            Text(product.marcaNombre ?: product.marca?.let { "Marca #$it" } ?: "Marca no especificada", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            if (!product.descripcion.isNullOrBlank()) Text(product.descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EuropartesMetricPill("Precio unitario", product.precioUnitario ?: "—", modifier = Modifier.weight(1f))
                EuropartesMetricPill("Disponible", product.stockDisponible?.toString() ?: "—", modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EuropartesMetricPill("Físico", product.stockActual?.toString() ?: "—", modifier = Modifier.weight(1f))
                EuropartesMetricPill("Reservado", product.stockReservado?.toString() ?: "—", modifier = Modifier.weight(1f))
            }
            if (!product.proveedorNombre.isNullOrBlank()) Text("Proveedor  ·  ${product.proveedorNombre}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Información del servidor · ${product.fechaRegistro ?: "Fecha no disponible"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (canManage && (!isCoordinator || product.stockActual == 0)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !isWorking && product.estado != false, onClick = onEdit) { Text("Editar") }
                    if (product.estado == false) Button(enabled = !isWorking, onClick = { onStateChange(true) }) { Text("Reactivar") }
                    else Button(enabled = !isWorking, onClick = { onStateChange(false) }) { Text("Desactivar") }
                }
            } else if (canManage && isCoordinator && product.stockActual != 0) {
                Text("El Coordinador solo puede modificar productos con stock actual igual a cero.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Preview(showBackground = true, name = "Acceso · normal", widthDp = 393, heightDp = 852)
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

@Preview(showBackground = true, name = "Acceso · compacto", widthDp = 320, heightDp = 640)
@Composable
private fun LoginScreenCompactPreview() {
    EuropartesMobileTheme {
        LoginScreen("alejandro", "", false, "", {}, {}, {}, Modifier.fillMaxSize())
    }
}

@Preview(showBackground = true, name = "Acceso · error y escala ampliada", widthDp = 393, heightDp = 852, fontScale = 1.2f)
@Composable
private fun LoginScreenErrorPreview() {
    EuropartesMobileTheme {
        LoginScreen("alejandro", "contraseña-demo", false, "No pudimos iniciar sesión. Revisa tus datos e inténtalo de nuevo.", {}, {}, {}, Modifier.fillMaxSize())
    }
}

@Preview(showBackground = true, name = "Acceso · modo oscuro", widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoginScreenDarkPreview() {
    EuropartesMobileTheme(darkTheme = true) {
        LoginScreen("", "", false, null, {}, {}, {}, Modifier.fillMaxSize())
    }
}

@Preview(showBackground = true, name = "Productos · lista y estados", widthDp = 393, heightDp = 852)
@Composable
private fun ProductsScreenPreview() {
    EuropartesMobileTheme {
        ProductsScreen(
            products = listOf(
                ProductDto(101, "Filtro de aceite premium", 1, "Bosch", "Filtro para motores de uso diario. Demostración local para Compose Preview.", "425.00", 12, 2, 10, 1, "Repuestos Central", "2026-10-23", true),
                ProductDto(102, "Pastillas de freno", 2, "Brembo", "Juego delantero · referencia de demostración.", "895.00", 8, 1, 7, 2, "Distribuidora Norte", "2026-10-23", false),
            ),
            isLoading = false,
            errorMessage = null,
            hasLoaded = true,
            onRefresh = {}, canManage = true, isCoordinator = false, isWorking = false,
            message = null, messageIsError = false, brands = emptyList(), suppliers = emptyList(),
            onLoadCatalogs = {}, onSave = { _, _, _, _, _, _ -> }, onChangeState = { _, _ -> }, onClearMessage = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(showBackground = true, name = "Productos · sin resultados", widthDp = 393, heightDp = 852)
@Composable
private fun ProductsEmptyPreview() {
    EuropartesMobileTheme {
        ProductsScreen(emptyList(), false, null, true, {}, false, false, false, null, false, emptyList(), emptyList(), {}, { _, _, _, _, _, _ -> }, { _, _ -> }, {}, Modifier.fillMaxSize())
    }
}

@Preview(showBackground = true, name = "Productos · error y reintento", widthDp = 393, heightDp = 852)
@Composable
private fun ProductsErrorPreview() {
    EuropartesMobileTheme {
        ProductsScreen(emptyList(), false, "No fue posible actualizar el catálogo.", false, {}, false, false, false, null, false, emptyList(), emptyList(), {}, { _, _, _, _, _, _ -> }, { _, _ -> }, {}, Modifier.fillMaxSize())
    }
}

@Preview(showBackground = true, name = "Productos · carga", widthDp = 393, heightDp = 852)
@Composable
private fun ProductsLoadingPreview() {
    EuropartesMobileTheme {
        ProductsScreen(emptyList(), true, null, false, {}, false, false, false, null, false, emptyList(), emptyList(), {}, { _, _, _, _, _, _ -> }, { _, _ -> }, {}, Modifier.fillMaxSize())
    }
}

@Preview(showBackground = true, name = "Navegación · menú desplegado", widthDp = 393, heightDp = 852)
@Composable
private fun AuthenticatedShellPreview() {
    val productSamples = listOf(
        ProductDto(101, "Filtro de aceite premium", 1, "Bosch", "Registro de ejemplo para evaluar la interfaz.", "425.00", 12, 2, 10, 1, "Repuestos Central", "2026-10-23", true),
        ProductDto(102, "Pastillas de freno", 2, "Brembo", "Juego delantero · artículo de demostración.", "895.00", 8, 1, 7, 2, "Distribuidora Norte", "2026-10-23", true),
    )
    EuropartesMobileTheme {
        AuthenticatedShell(
            user = AuthenticatedUser(7, "alejandro", "Alejandro", "Pérez", EuropartesRoles.SOPORTE_TI),
            products = productSamples,
            isLoading = false,
            errorMessage = null,
            hasLoaded = true,
            onRefresh = {},
            productWorking = false,
            productMessage = null,
            productMessageIsError = false,
            brands = listOf(CatalogOptionDto(1, "Bosch")),
            suppliers = listOf(CatalogOptionDto(1, "Repuestos Central")),
            onLoadCatalogs = {},
            onSaveProduct = { _, _, _, _, _, _ -> },
            onChangeProductState = { _, _ -> },
            onRefreshProducts = {},
            onClearProductMessage = {},
            localProducts = emptyList(),
            localWorking = false,
            localMessage = null,
            localIsError = false,
            onCreateLocal = { _, _, _, _, _ -> },
            onUpdateLocal = { _, _, _, _, _, _ -> },
            onDeleteLocal = {},
            onClearLocalMessage = {},
            onLogout = {},
            previewDrawerOpen = true,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(showBackground = true, name = "Formulario · producto existente", widthDp = 393, heightDp = 852)
@Composable
private fun ProductFormPreview() {
    EuropartesMobileTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ProductFormDialog(
                initial = ProductDto(101, "Filtro de aceite premium", 1, "Bosch", "Producto de demostración.", "425.00", 12, 2, 10, 1, "Repuestos Central", "2026-10-23", true),
                brands = listOf(CatalogOptionDto(1, "Bosch"), CatalogOptionDto(2, "Brembo")),
                suppliers = listOf(CatalogOptionDto(1, "Repuestos Central")),
                isWorking = false,
                message = null,
                messageIsError = false,
                onDismiss = {},
                onSubmit = { _, _, _, _, _, _ -> },
            )
        }
    }
}
