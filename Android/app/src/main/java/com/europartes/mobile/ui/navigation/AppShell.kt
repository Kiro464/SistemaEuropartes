package com.europartes.mobile.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Factory
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.ShoppingCartCheckout
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.europartes.mobile.data.local.ProductEntity
import com.europartes.mobile.data.local.SyncOperationEntity
import com.europartes.mobile.data.remote.AuthenticatedUser
import com.europartes.mobile.data.repository.EuropartesRoles
import com.europartes.mobile.data.sync.SyncCoordinator
import com.europartes.mobile.ui.catalogs.CatalogsScreen
import com.europartes.mobile.ui.components.ConfirmDialog
import com.europartes.mobile.ui.components.ControlShape
import com.europartes.mobile.ui.components.DisplayCurrency
import com.europartes.mobile.ui.components.EmptyState
import com.europartes.mobile.ui.components.LocalMoney
import com.europartes.mobile.ui.components.MoneyFormat
import com.europartes.mobile.ui.components.UserAvatar
import com.europartes.mobile.ui.dashboard.DashboardScreen
import com.europartes.mobile.ui.dashboard.DashboardViewModel
import com.europartes.mobile.ui.inventory.KardexScreen
import com.europartes.mobile.ui.login.BrandLockup
import com.europartes.mobile.ui.products.LocalProductsScreen
import com.europartes.mobile.ui.products.LocalProductsViewModel
import com.europartes.mobile.ui.products.ProductsScreen
import com.europartes.mobile.ui.products.ProductsViewModel
import com.europartes.mobile.ui.purchasing.PurchaseHistoryScreen
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
import com.europartes.mobile.ui.sync.ConfigurationScreen
import com.europartes.mobile.ui.sync.ConnectivityBanner
import com.europartes.mobile.ui.sync.SyncScreen
import com.europartes.mobile.ui.sync.SyncViewModel
import com.europartes.mobile.ui.theme.EuropartesTheme
import com.europartes.mobile.ui.theme.ThemeMode
import com.europartes.mobile.ui.theme.ThemePreference
import kotlinx.coroutines.launch

private fun navigationIcon(destination: MobileDestination): ImageVector = when (destination) {
    MobileDestination.DASHBOARD -> Icons.Outlined.Dashboard
    MobileDestination.SALES -> Icons.Outlined.PointOfSale
    MobileDestination.PRODUCTS, MobileDestination.LOCAL_DRAFTS -> Icons.Outlined.Inventory2
    MobileDestination.DISPATCHES -> Icons.Outlined.LocalShipping
    MobileDestination.PURCHASING -> Icons.Outlined.ShoppingCart
    MobileDestination.CUSTOMERS -> Icons.Outlined.Groups
    MobileDestination.BRANDS -> Icons.Outlined.Sell
    MobileDestination.SUPPLIERS -> Icons.Outlined.Factory
    MobileDestination.COUNTRIES -> Icons.Outlined.Public
    MobileDestination.DEPARTMENTS -> Icons.Outlined.Apartment
    MobileDestination.EMPLOYEES -> Icons.Outlined.Badge
    MobileDestination.SALES_HISTORY -> Icons.Outlined.ReceiptLong
    MobileDestination.PURCHASE_HISTORY -> Icons.Outlined.ShoppingCartCheckout
    MobileDestination.KARDEX -> Icons.Outlined.Assessment
    MobileDestination.CONFIGURATION -> Icons.Outlined.Settings
    MobileDestination.SYNC -> Icons.Outlined.Sync
    MobileDestination.USER_ADMIN -> Icons.Outlined.ManageAccounts
    MobileDestination.ACCOUNT_SECURITY -> Icons.Outlined.Key
}

private fun AuthenticatedUser.displayName() = listOf(nombres, apellidos).filter(String::isNotBlank).joinToString(" ").ifBlank { username }

/** Sidebar, top bar and content area, arranged as in the web app. */
@Composable
fun AuthenticatedShell(
    user: AuthenticatedUser,
    productsViewModel: ProductsViewModel,
    localProductsViewModel: LocalProductsViewModel,
    isDarkTheme: Boolean,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigation = remember(user.grupo) { navigationForRole(user.grupo) }
    val purchasingViewModel: PurchasingViewModel = viewModel(key = "purchasing_user_${user.userId}")
    val salesViewModel: SalesViewModel = viewModel(key = "sales_user_${user.userId}")
    val securityViewModel: SecurityViewModel = viewModel(key = "security_user_${user.userId}")
    val dashboardViewModel: DashboardViewModel = viewModel(key = "dashboard_user_${user.userId}")
    val syncViewModel: SyncViewModel = viewModel(key = "sync_user_${user.userId}")
    val syncState by syncViewModel.state.collectAsState()
    val salesState by salesViewModel.state.collectAsState()
    val context = LocalContext.current
    val currency by DisplayCurrency.flow(context).collectAsState()
    var confirmLogout by remember { mutableStateOf(false) }
    val requestLogout = { if (syncState.operations.isNotEmpty()) confirmLogout = true else onLogout() }

    // Sends anything queued and refreshes the local copy of every module for this role.
    LaunchedEffect(user.userId) {
        SyncCoordinator.request(context, forceDownload = true)
        salesViewModel.loadConfiguration(user.grupo)
    }
    DisposableEffect(user.userId) {
        onDispose {
            salesViewModel.reset()
            dashboardViewModel.reset()
        }
    }

    val start = remember(user.userId, navigation) {
        defaultDestination(user.grupo).takeIf { target -> navigation.any { it.destination == target } } ?: navigation.firstOrNull()?.destination
    }
    var destination by remember(user.userId, navigation) { mutableStateOf(start) }
    // System Back walks through the screens opened in this session instead of leaving the app.
    val history = remember(user.userId) { mutableStateListOf<MobileDestination>() }
    val open: (MobileDestination) -> Unit = { target ->
        destination?.takeIf { it != target }?.let { current ->
            history.remove(target)
            history.add(current)
        }
        destination = target
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    com.europartes.mobile.ui.components.StatusBarIcons(lightIcons = isDarkTheme || drawerState.targetValue == DrawerValue.Open)
    val scope = rememberCoroutineScope()
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = drawerState.isClosed && history.isNotEmpty()) { destination = history.removeAt(history.lastIndex) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = navigation.isNotEmpty(),
        drawerContent = {
            Sidebar(
                user = user,
                navigation = navigation,
                current = destination,
                onSelect = { open(it); scope.launch { drawerState.close() } },
                onLogout = { scope.launch { drawerState.close() }; requestLogout() },
            )
        },
        modifier = modifier,
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopBar(
                        destination = destination,
                        user = user,
                        currency = currency,
                        isDarkTheme = isDarkTheme,
                        onMenu = { scope.launch { drawerState.open() } },
                        onToggleCurrency = { DisplayCurrency.set(context, if (currency == "USD") "NIO" else "USD") },
                        onToggleTheme = { ThemePreference.set(context, if (isDarkTheme) ThemeMode.LIGHT else ThemeMode.DARK) },
                        onOpen = open,
                        onLogout = requestLogout,
                    )
                    ConnectivityBanner(syncState, onOpenSync = { open(MobileDestination.SYNC) })
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            CompositionLocalProvider(LocalMoney provides MoneyFormat(currency, salesState.configuration?.exchangeRate)) {
                // The web's view enter: a quick fade with a small rise, no exit motion to wait for.
                AnimatedContent(
                    targetState = destination,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    transitionSpec = {
                        (fadeIn(tween(190, easing = com.europartes.mobile.ui.components.EaseOutStrong)) + slideInVertically(tween(190, easing = com.europartes.mobile.ui.components.EaseOutStrong)) { 12 })
                            .togetherWith(fadeOut(tween(70)))
                    },
                    label = "view",
                ) { target ->
                    Destination(
                        target = target,
                        user = user,
                        productsViewModel = productsViewModel,
                        localProductsViewModel = localProductsViewModel,
                        purchasingViewModel = purchasingViewModel,
                        salesViewModel = salesViewModel,
                        securityViewModel = securityViewModel,
                        dashboardViewModel = dashboardViewModel,
                        syncViewModel = syncViewModel,
                        isOnline = syncState.isOnline,
                        open = open,
                    )
                }
            }
        }
    }
    if (confirmLogout) {
        ConfirmDialog(
            title = "Tienes cambios sin enviar",
            message = "Quedarán guardados en este dispositivo y se enviarán cuando vuelvas a iniciar sesión con esta cuenta. Para volver a entrar necesitarás conexión a internet.",
            confirmText = "Cerrar sesión",
            onConfirm = { confirmLogout = false; onLogout() },
            onDismiss = { confirmLogout = false },
        )
    }
}

@Composable
private fun Destination(
    target: MobileDestination?,
    user: AuthenticatedUser,
    productsViewModel: ProductsViewModel,
    localProductsViewModel: LocalProductsViewModel,
    purchasingViewModel: PurchasingViewModel,
    salesViewModel: SalesViewModel,
    securityViewModel: SecurityViewModel,
    dashboardViewModel: DashboardViewModel,
    syncViewModel: SyncViewModel,
    isOnline: Boolean,
    open: (MobileDestination) -> Unit,
) {
    val fill = Modifier.fillMaxSize()
    val refreshProducts = { productsViewModel.loadProducts() }
    val salesState by salesViewModel.state.collectAsState()
    val kind = target?.catalogKind
    when {
        target == null -> NoAuthorizedDestination()
        kind != null -> CatalogsScreen(kind, user.grupo, user.userId, fill)
        else -> when (target) {
            MobileDestination.DASHBOARD -> DashboardScreen(user.grupo, listOf(user.nombres, user.apellidos).filter(String::isNotBlank).joinToString(" ").ifBlank { user.username }, fill, dashboardViewModel)
            MobileDestination.SALES -> PointOfSaleScreen(user, salesViewModel, refreshProducts, fill)
            MobileDestination.PRODUCTS -> ProductsRoute(user, productsViewModel, localProductsViewModel, onOpenLocalProducts = { open(MobileDestination.LOCAL_DRAFTS) }, fill)
            MobileDestination.LOCAL_DRAFTS -> LocalProductsRoute(user, localProductsViewModel, onBack = { open(MobileDestination.PRODUCTS) }, fill)
            MobileDestination.DISPATCHES -> DispatchesScreen(user.grupo, user.userId, salesViewModel, refreshProducts, fill)
            MobileDestination.PURCHASING -> PurchasingScreen(purchasingViewModel, user.grupo, user.userId, fill)
            MobileDestination.PURCHASE_HISTORY -> PurchaseHistoryScreen(purchasingViewModel, user.grupo, user.userId, fill)
            MobileDestination.CUSTOMERS -> CustomersScreen(user.grupo, user.userId, salesViewModel, fill)
            MobileDestination.SALES_HISTORY -> SalesHistoryScreen(user.grupo, user.userId, salesViewModel, fill)
            MobileDestination.KARDEX -> KardexScreen(user.grupo, user.userId, fill)
            MobileDestination.USER_ADMIN -> SecurityAdminScreen(user.grupo, user.userId, securityViewModel, fill)
            MobileDestination.ACCOUNT_SECURITY -> ChangeOwnPasswordScreen(user.grupo, securityViewModel, fill, isOnline = isOnline)
            MobileDestination.CONFIGURATION -> ConfigurationScreen(
                user = user,
                configuration = salesState.configuration,
                onLoadConfiguration = { salesViewModel.loadConfiguration(user.grupo) },
                onOpenPassword = { open(MobileDestination.ACCOUNT_SECURITY) },
                modifier = fill,
            )
            MobileDestination.SYNC -> SyncScreen(syncViewModel, fill)
            else -> NoAuthorizedDestination()
        }
    }
}

@Composable
private fun ProductsRoute(
    user: AuthenticatedUser,
    viewModel: ProductsViewModel,
    localViewModel: LocalProductsViewModel,
    onOpenLocalProducts: () -> Unit,
    modifier: Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val localProducts by localViewModel.localProducts.collectAsState()
    ProductsScreen(
        products = state.products,
        isLoading = state.isLoading,
        errorMessage = state.errorMessage,
        hasLoaded = state.hasLoaded,
        onRefresh = { viewModel.loadProducts() },
        canManage = user.grupo in EuropartesRoles.canManageProductsInBackend,
        isCoordinator = user.grupo == EuropartesRoles.COORDINADOR_LOGISTICA,
        isWorking = state.isWorking,
        message = state.message,
        messageIsError = state.messageIsError,
        brands = state.brands,
        suppliers = state.suppliers,
        onLoadCatalogs = { viewModel.loadCatalogs(user.grupo) },
        onSave = viewModel::save,
        onChangeState = viewModel::changeActive,
        onClearMessage = viewModel::clearMessage,
        role = user.grupo,
        sessionKey = user.userId,
        modifier = modifier,
        isShowingOfflineCache = state.isShowingOfflineCache,
        lastSuccessfulSyncAtEpochMs = state.lastSuccessfulSyncAtEpochMs,
        localProductCount = if (user.grupo in EuropartesRoles.canManageProductsInBackend) localProducts.size else 0,
        pendingProductIds = state.pendingProductIds,
        onOpenLocalProducts = onOpenLocalProducts,
    )
}

@Composable
private fun LocalProductsRoute(user: AuthenticatedUser, viewModel: LocalProductsViewModel, onBack: () -> Unit, modifier: Modifier) {
    if (user.grupo !in EuropartesRoles.canManageProductsInBackend) {
        NoAuthorizedDestination()
        return
    }
    val products by viewModel.localProducts.collectAsState()
    val operations: List<SyncOperationEntity> by viewModel.syncOperations.collectAsState()
    val legacyCount by viewModel.unclaimedLegacyDraftCount.collectAsState()
    val state by viewModel.uiState.collectAsState()
    LocalProductsScreen(
        products = products as List<ProductEntity>,
        syncOperations = operations,
        unclaimedLegacyDraftCount = legacyCount,
        isWorking = state.isWorking,
        message = state.message,
        isError = state.isError,
        onCreate = viewModel::create,
        onUpdate = viewModel::update,
        onDelete = viewModel::delete,
        onSyncPending = viewModel::syncPendingCreates,
        onDiscardSyncOperation = viewModel::discardSyncOperation,
        onClaimLegacyDrafts = viewModel::claimLegacyDrafts,
        onClearMessage = viewModel::clearMessage,
        onBackToProducts = onBack,
        modifier = modifier,
    )
}

@Composable
private fun NoAuthorizedDestination() {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        EmptyState("Sin módulos disponibles", "Tu rol no tiene módulos habilitados. Contacta al administrador si consideras que es incorrecto.")
    }
}

@Composable
private fun Sidebar(
    user: AuthenticatedUser,
    navigation: List<NavigationItem>,
    current: MobileDestination?,
    onSelect: (MobileDestination) -> Unit,
    onLogout: () -> Unit,
) {
    val colors = EuropartesTheme.colors
    ModalDrawerSheet(
        drawerContainerColor = colors.sidebar,
        drawerContentColor = colors.sidebarText,
        drawerShape = RoundedCornerShape(0.dp),
        modifier = Modifier.width(290.dp),
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) {
        Column(Modifier.fillMaxHeight()) {
            Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp)) {
                BrandLockup(subtitle = "Sistema de gestión")
            }
            HorizontalDivider(color = colors.sidebarBorder)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp)) {
                NavigationSection.entries.forEach { section ->
                    val items = navigation.filter { it.destination.section == section }
                    if (items.isNotEmpty()) {
                        Text(section.label, Modifier.padding(start = 10.dp, top = 12.dp, bottom = 5.dp), style = MaterialTheme.typography.labelSmall, color = colors.sidebarLabel)
                        items.forEach { item -> SidebarItem(item, current == item.destination) { onSelect(item.destination) } }
                    }
                }
            }
            HorizontalDivider(color = colors.sidebarBorder)
            Row(Modifier.navigationBarsPadding().padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(user.displayName(), background = colors.accent, foreground = Color(0xFF1C2738))
                Column(Modifier.weight(1f)) {
                    Text(user.displayName(), style = MaterialTheme.typography.labelMedium, color = colors.sidebarText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(user.grupo, style = MaterialTheme.typography.bodySmall, color = colors.sidebarLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onLogout), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "Cerrar sesión", Modifier.size(20.dp), tint = colors.sidebarTextSoft)
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(item: NavigationItem, selected: Boolean, onClick: () -> Unit) {
    val colors = EuropartesTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(ControlShape)
            .background(if (selected) colors.sidebarActive else Color.Transparent)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Inset yellow bar and icon mark the current page, exactly as the web's sidebar does.
        Box(Modifier.width(3.dp).height(44.dp).background(if (selected) colors.accent else Color.Transparent))
        Icon(navigationIcon(item.destination), contentDescription = null, Modifier.padding(start = 9.dp).size(20.dp), tint = if (selected) colors.accent else colors.sidebarTextSoft)
        Text(item.label, Modifier.padding(start = 11.dp, end = 10.dp), style = MaterialTheme.typography.labelLarge, color = if (selected) colors.sidebarText else colors.sidebarTextSoft)
    }
}

@Composable
private fun TopBar(
    destination: MobileDestination?,
    user: AuthenticatedUser,
    currency: String,
    isDarkTheme: Boolean,
    onMenu: () -> Unit,
    onToggleCurrency: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpen: (MobileDestination) -> Unit,
    onLogout: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    Column(Modifier.background(colors.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TopBarIcon(Icons.Outlined.Menu, "Abrir menú de navegación", onMenu)
            Column(Modifier.weight(1f).padding(start = 2.dp)) {
                Text(destination?.section?.label ?: "Europartes", style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted, maxLines = 1)
                Text(destination?.label ?: "Inicio", style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.padding(end = 2.dp).clip(ControlShape).border(1.dp, colors.outlineVariant, ControlShape).clickable(onClick = onToggleCurrency).heightIn(min = 34.dp).padding(horizontal = 9.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (currency == "USD") "US$" else "C$", style = MaterialTheme.typography.labelMedium, color = colors.onSurface) }
            TopBarIcon(if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode, "Cambiar tema", onToggleTheme)
            Box {
                Box(Modifier.size(42.dp).clip(CircleShape).clickable { menuOpen = true }, contentAlignment = Alignment.Center) {
                    UserAvatar(user.displayName())
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(10.dp),
                    containerColor = EuropartesTheme.colors.raised,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.outline),
                    shadowElevation = 6.dp,
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Text(user.displayName(), style = MaterialTheme.typography.titleSmall)
                        Text(user.grupo, style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.muted)
                    }
                    HorizontalDivider(color = colors.outline, modifier = Modifier.padding(vertical = 4.dp))
                    UserMenuItem(Icons.Outlined.Key, "Cambiar contraseña") { menuOpen = false; onOpen(MobileDestination.ACCOUNT_SECURITY) }
                    UserMenuItem(Icons.Outlined.Settings, "Configuración") { menuOpen = false; onOpen(MobileDestination.CONFIGURATION) }
                    HorizontalDivider(color = colors.outline, modifier = Modifier.padding(vertical = 4.dp))
                    UserMenuItem(Icons.AutoMirrored.Outlined.Logout, "Cerrar sesión", tint = colors.error) { menuOpen = false; onLogout() }
                }
            }
        }
        HorizontalDivider(color = colors.outline)
    }
}

@Composable
private fun UserMenuItem(icon: ImageVector, text: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = tint) },
        leadingIcon = { Icon(icon, contentDescription = null, Modifier.size(19.dp), tint = tint) },
        onClick = onClick,
    )
}

@Composable
private fun TopBarIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

