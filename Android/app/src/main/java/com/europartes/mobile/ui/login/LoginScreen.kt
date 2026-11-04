package com.europartes.mobile.ui.login

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.europartes.mobile.R
import com.europartes.mobile.ui.components.ButtonVariant
import com.europartes.mobile.ui.components.EuropartesButton
import com.europartes.mobile.ui.components.EuropartesCard
import com.europartes.mobile.ui.components.EuropartesStatusMessage
import com.europartes.mobile.ui.components.EuropartesTextField
import com.europartes.mobile.ui.theme.EuropartesMobileTheme
import com.europartes.mobile.ui.theme.EuropartesTheme
import java.time.Year

/** The web's login on one column: brand hero above, the sign-in card below. */
@Composable
fun LoginScreen(
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
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
) {
    com.europartes.mobile.ui.components.StatusBarIcons(lightIcons = true)
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val busy = isLoading || isRestoringSession
    Column(modifier.background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).imePadding()) {
        LoginHero(isDarkTheme, onToggleTheme)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 12.dp).navigationBarsPadding()) {
            EuropartesCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp), verticalSpacing = Arrangement.spacedBy(13.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Iniciar sesión", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("Ingresa con tu cuenta empresarial.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                EuropartesTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = "Usuario",
                    enabled = !busy,
                    leadingIcon = Icons.Outlined.Person,
                    placeholder = "Tu nombre de usuario",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                EuropartesTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = "Contraseña",
                    enabled = !busy,
                    leadingIcon = Icons.Outlined.Lock,
                    placeholder = "Tu contraseña",
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingContent = {
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = !busy) { passwordVisible = !passwordVisible }, contentAlignment = Alignment.Center) {
                            Icon(
                                if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                                Modifier.size(19.dp),
                                tint = EuropartesTheme.colors.muted,
                            )
                        }
                    },
                )
                if (errorMessage != null) EuropartesStatusMessage(errorMessage, isError = true)
                EuropartesButton(
                    text = if (isRestoringSession) "Comprobando sesión guardada" else if (isLoading) "Conectando…" else "Iniciar sesión",
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = busy,
                )
                if (canRetrySessionRestore) {
                    EuropartesButton("Reintentar verificación de sesión", onRestoreSession, Modifier.fillMaxWidth(), variant = ButtonVariant.Ghost)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "© ${Year.now().value} Europartes · Sistema de gestión",
                Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.bodySmall,
                color = EuropartesTheme.colors.muted,
            )
        }
    }
}

@Composable
private fun LoginHero(isDarkTheme: Boolean, onToggleTheme: () -> Unit) {
    val colors = EuropartesTheme.colors
    Box(Modifier.fillMaxWidth().background(colors.sidebar)) {
        PartDrawing(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 14.dp).width(190.dp).height(95.dp))
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandLockup(Modifier.weight(1f))
                Box(
                    Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onToggleTheme),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode, contentDescription = "Cambiar tema", Modifier.size(20.dp), tint = colors.sidebarText)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("Del almacén al mostrador.", style = MaterialTheme.typography.headlineLarge, color = colors.sidebarText)
            Spacer(Modifier.height(6.dp))
            Text(
                "Catálogo, existencias, compras y despachos conectados en un solo flujo de trabajo.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.sidebarTextSoft,
            )
            Spacer(Modifier.height(16.dp))
            Column(Modifier.width(210.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                HeroFeature(Icons.Outlined.Inventory2, "Existencias e inventario")
                HeroFeature(Icons.Outlined.ShoppingCart, "Compras y abastecimiento")
                HeroFeature(Icons.Outlined.ReceiptLong, "Ventas y despachos")
            }
        }
    }
}

@Composable
private fun HeroFeature(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, Modifier.size(17.dp), tint = EuropartesTheme.colors.accent)
        Text(text, style = MaterialTheme.typography.bodySmall, color = EuropartesTheme.colors.sidebarTextSoft)
    }
}

/** Logo on its white plate with the product name, used by the login and the drawer. */
@Composable
fun BrandLockup(modifier: Modifier = Modifier, subtitle: String? = null) {
    val colors = EuropartesTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(RoundedCornerShape(7.dp)).background(Color.White).padding(horizontal = 5.dp, vertical = 3.dp)) {
            Image(
                painter = painterResource(R.drawable.europartes_logo),
                contentDescription = "Logotipo de Europartes S.A.",
                modifier = Modifier.width(52.dp).height(30.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Column {
            Text("Europartes", style = MaterialTheme.typography.titleLarge, color = colors.sidebarText)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.sidebarLabel) }
        }
    }
}

// The brake disc and caliper line drawing from the web's login, same 440×220 coordinates.
private val partOutline = listOf(
    "M138 34v21M214 110h-21M138 186v-21M62 110h21M84 56l16 16M192 56l-16 16M192 164l-16-16M84 164l16-16",
    "M265 77h99l21 16v34l-21 16h-99l-17-18V95z",
    "M265 77v66M364 77v66M282 87v46M347 87v46",
)
private val partAccent = listOf(
    "M231 110h25M331 59v18M331 143v18M229 84l15 13M229 136l15-13",
    "M138 83v54M111 110h54",
)
private val partCircles = listOf(
    Triple(138f, 110f, 77f), Triple(138f, 110f, 55f), Triple(138f, 110f, 27f),
    Triple(138f, 64f, 5f), Triple(170.5f, 77.5f, 5f), Triple(184f, 110f, 5f), Triple(170.5f, 142.5f, 5f),
    Triple(138f, 156f, 5f), Triple(105.5f, 142.5f, 5f), Triple(92f, 110f, 5f), Triple(105.5f, 77.5f, 5f),
)

@Composable
private fun PartDrawing(modifier: Modifier = Modifier) {
    val line = Color.White.copy(alpha = 0.3f)
    val accent = EuropartesTheme.colors.accent.copy(alpha = 0.8f)
    Canvas(modifier) {
        val factor = size.width / 440f
        scale(factor, factor, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.5f / factor)
            partCircles.forEach { (x, y, radius) -> drawCircle(line, radius, Offset(x, y), style = stroke) }
            partOutline.forEach { drawPath(PathParser().parsePathString(it).toPath(), line, style = stroke) }
            partAccent.forEach { drawPath(PathParser().parsePathString(it).toPath(), accent, style = stroke) }
        }
    }
}

@Preview(showBackground = true, name = "Acceso · normal", widthDp = 393, heightDp = 852)
@Composable
private fun LoginScreenPreview() {
    EuropartesMobileTheme(darkTheme = false) {
        LoginScreen("", "", false, null, {}, {}, {}, Modifier.fillMaxSize())
    }
}

@Preview(showBackground = true, name = "Acceso · error y escala ampliada", widthDp = 360, heightDp = 740, fontScale = 1.2f)
@Composable
private fun LoginScreenErrorPreview() {
    EuropartesMobileTheme(darkTheme = false) {
        LoginScreen("alejandro", "contraseña-demo", false, "No pudimos iniciar sesión. Revisa tus datos e inténtalo de nuevo.", {}, {}, {}, Modifier.fillMaxSize())
    }
}

@Preview(showBackground = true, name = "Acceso · modo oscuro", widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoginScreenDarkPreview() {
    EuropartesMobileTheme(darkTheme = true) {
        LoginScreen("", "", false, null, {}, {}, {}, Modifier.fillMaxSize(), isDarkTheme = true)
    }
}

