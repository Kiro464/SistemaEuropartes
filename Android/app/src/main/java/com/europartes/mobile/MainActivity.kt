package com.europartes.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.europartes.mobile.data.remote.ProductDto
import com.europartes.mobile.ui.login.LoginViewModel
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

                LaunchedEffect(loginState.authenticatedUsername) {
                    if (loginState.authenticatedUsername != null) {
                        productsViewModel.loadProducts()
                    } else {
                        productsViewModel.reset()
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    if (loginState.authenticatedUsername != null) {
                        ProductsScreen(
                            username = loginState.authenticatedUsername.orEmpty(),
                            isLoading = productsState.isLoading,
                            products = productsState.products,
                            errorMessage = productsState.errorMessage,
                            hasLoaded = productsState.hasLoaded,
                            onRefresh = productsViewModel::loadProducts,
                            onLogout = loginViewModel::logout,
                            modifier = Modifier.fillMaxSize().padding(padding),
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            LoginForm(
                                username = loginState.username,
                                password = loginState.password,
                                isLoading = loginState.isLoading,
                                errorMessage = loginState.errorMessage,
                                onUsernameChange = loginViewModel::onUsernameChange,
                                onPasswordChange = loginViewModel::onPasswordChange,
                                onLogin = loginViewModel::login,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginForm(
    username: String,
    password: String,
    isLoading: Boolean,
    errorMessage: String?,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Europartes", style = MaterialTheme.typography.headlineLarge)
        Text("Inicia sesión con tu cuenta empresarial")
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("Usuario") },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Contraseña") },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        if (errorMessage != null) Text(errorMessage, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = onLogin,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) CircularProgressIndicator()
            else Text("Iniciar sesión")
        }
    }
}

@Composable
private fun ProductsScreen(
    username: String,
    isLoading: Boolean,
    products: List<ProductDto>,
    errorMessage: String?,
    hasLoaded: Boolean,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text("Productos e inventario", style = MaterialTheme.typography.headlineMedium)
        Text("Sesión: $username", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onRefresh, enabled = !isLoading) {
                Text("Actualizar")
            }
            Button(onClick = onLogout) { Text("Cerrar sesión") }
        }
        Spacer(Modifier.height(12.dp))
        if (isLoading && products.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Conectando con el servicio de productos…")
            }
        } else if (errorMessage != null && products.isEmpty()) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRefresh, enabled = !isLoading) { Text("Reintentar") }
        } else if (hasLoaded && products.isEmpty()) {
            Text("No hay productos activos para mostrar.")
        } else {
            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(products, key = { it.id }) { product ->
                    ProductCard(product)
                }
            }
        }
    }
}

@Composable
private fun ProductCard(product: ProductDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(product.nombre, style = MaterialTheme.typography.titleMedium)
            val brand = product.marcaNombre ?: product.marca?.toString() ?: "Sin marca"
            Text("Marca: $brand", style = MaterialTheme.typography.bodyMedium)
            if (!product.descripcion.isNullOrBlank()) {
                Text(product.descripcion, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(6.dp))
            Text("Precio unitario: ${product.precioUnitario ?: "No disponible"}")
            Text("Stock disponible: ${product.stockDisponible ?: "No disponible"}")
            if (!product.proveedorNombre.isNullOrBlank()) {
                Text("Proveedor: ${product.proveedorNombre}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Preview estática del formulario de acceso.
 * No realiza llamadas HTTP ni inicia sesión.
 */
@Preview(showBackground = true, name = "Inicio de sesión", widthDp = 393, heightDp = 852)
@Composable
private fun LoginFormPreview() {
    EuropartesMobileTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LoginForm(
                username = "usuario.demo",
                password = "",
                isLoading = false,
                errorMessage = null,
                onUsernameChange = {},
                onPasswordChange = {},
                onLogin = {},
            )
        }
    }
}

/**
 * Preview estática del listado de inventario con datos de ejemplo.
 * Estos datos existen solo para visualizar el diseño en Android Studio;
 * no representan una respuesta de la API de Azure.
 */
@Preview(showBackground = true, name = "Productos e inventario - muestra", widthDp = 393, heightDp = 852)
@Composable
private fun ProductsScreenPreview() {
    EuropartesMobileTheme {
        ProductsScreen(
            username = "usuario.demo",
            isLoading = false,
            products = listOf(
                ProductDto(
                    id = 101,
                    nombre = "Filtro de aceite",
                    marca = 1,
                    marcaNombre = "Bosch",
                    descripcion = "Filtro para motor",
                    precioUnitario = "425.00",
                    stockActual = 12,
                    stockReservado = 2,
                    stockDisponible = 10,
                    proveedor = 1,
                    proveedorNombre = "Proveedor de ejemplo",
                    fechaRegistro = "2026-10-23",
                    estado = true,
                ),
                ProductDto(
                    id = 102,
                    nombre = "Pastillas de freno",
                    marca = 2,
                    marcaNombre = "Brembo",
                    descripcion = "Juego de pastillas de freno",
                    precioUnitario = "895.00",
                    stockActual = 8,
                    stockReservado = 1,
                    stockDisponible = 7,
                    proveedor = 2,
                    proveedorNombre = "Proveedor de ejemplo",
                    fechaRegistro = "2026-10-23",
                    estado = true,
                ),
            ),
            errorMessage = null,
            hasLoaded = true,
            onRefresh = {},
            onLogout = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

