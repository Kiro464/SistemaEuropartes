package com.europartes.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.europartes.mobile.ui.login.LoginViewModel
import com.europartes.mobile.ui.theme.EuropartesMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EuropartesMobileTheme {
                val viewModel: LoginViewModel = viewModel()
                val state by viewModel.uiState.collectAsState()
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (state.authenticatedUsername != null) {
                            Text("Sesión iniciada", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(12.dp))
                            Text("Usuario: ${state.authenticatedUsername}")
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = viewModel::logout) { Text("Cerrar sesión") }
                        } else {
                            LoginForm(
                                username = state.username,
                                password = state.password,
                                isLoading = state.isLoading,
                                errorMessage = state.errorMessage,
                                onUsernameChange = viewModel::onUsernameChange,
                                onPasswordChange = viewModel::onPasswordChange,
                                onLogin = viewModel::login,
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
        if (errorMessage != null) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error)
        }
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
