package com.europartes.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.europartes.mobile.ui.login.LoginScreen
import com.europartes.mobile.ui.login.LoginViewModel
import com.europartes.mobile.ui.navigation.AuthenticatedShell
import com.europartes.mobile.ui.products.LocalProductsViewModel
import com.europartes.mobile.ui.products.ProductsViewModel
import com.europartes.mobile.ui.theme.EuropartesMobileTheme
import com.europartes.mobile.ui.theme.ThemeMode
import com.europartes.mobile.ui.theme.ThemePreference

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by ThemePreference.mode(this).collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }
            EuropartesMobileTheme(darkTheme = isDarkTheme) {
                val loginViewModel: LoginViewModel = viewModel()
                val loginState by loginViewModel.uiState.collectAsState()
                val productsViewModel: ProductsViewModel = viewModel()
                val accountScope = loginState.authenticatedUser?.userId?.toString() ?: "anonymous"
                val localProductsViewModel: LocalProductsViewModel = viewModel(key = "local_products_$accountScope")
                val localProductsState by localProductsViewModel.uiState.collectAsState()

                LaunchedEffect(loginState.authenticatedUser?.userId) {
                    if (loginState.authenticatedUser != null) productsViewModel.loadProducts() else productsViewModel.reset()
                }
                LaunchedEffect(localProductsState.message) {
                    if (localProductsState.message?.contains("confirmados por el servidor") == true) productsViewModel.loadProducts()
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
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = { ThemePreference.set(this, if (isDarkTheme) ThemeMode.LIGHT else ThemeMode.DARK) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        AuthenticatedShell(
                            user = user,
                            productsViewModel = productsViewModel,
                            localProductsViewModel = localProductsViewModel,
                            isDarkTheme = isDarkTheme,
                            onLogout = loginViewModel::logout,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
