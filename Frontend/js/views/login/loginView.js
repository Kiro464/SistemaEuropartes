/**
 * js/views/login/loginView.js
 * Genera el HTML de la pantalla de inicio de sesión.
 */

export const LoginView = {
    render() {
        const year = new Date().getFullYear();
        return `
            <div class="login-shell">
                <section class="login-hero" aria-hidden="true">
                    <div class="login-brand">
                        <span class="brand-mark"><i class='bx bxs-car-mechanic'></i></span>
                        Europartes
                    </div>
                    <div class="login-hero-copy">
                        <h1>Repuestos, ventas e inventario <span>en un solo lugar.</span></h1>
                        <p>Gestiona facturación, compras, despachos y el kardex de tu negocio con información actualizada en tiempo real.</p>
                        <ul class="login-features">
                            <li><i class='bx bx-receipt'></i> Facturación y control de despachos</li>
                            <li><i class='bx bx-package'></i> Inventario y kardex trazable</li>
                            <li><i class='bx bx-line-chart'></i> Indicadores para la toma de decisiones</li>
                        </ul>
                    </div>
                    <div class="login-hero-foot">© ${year} Europartes · Sistema de gestión</div>
                </section>

                <section class="login-panel">
                    <button type="button" id="login-theme-toggle" class="login-theme-toggle" aria-label="Cambiar tema" title="Cambiar tema">
                        <i class='bx bx-moon'></i>
                    </button>

                    <div class="login-card">
                        <div class="login-card-head">
                            <div class="login-brand">
                                <span class="brand-mark"><i class='bx bxs-car-mechanic'></i></span>
                                Europartes
                            </div>
                            <h2>Bienvenido de nuevo</h2>
                            <p>Ingresa tus credenciales para continuar.</p>
                        </div>

                        <!-- Mensaje de error (oculto por defecto) -->
                        <div id="login-error-msg" class="login-alert hidden" role="alert">
                            <i class='bx bx-error-circle' aria-hidden="true"></i>
                            <span id="login-error-text">Usuario o contraseña incorrectos.</span>
                        </div>

                        <form id="login-form" class="euro-form" novalidate>
                            <div class="form-group">
                                <label for="username">Usuario</label>
                                <div class="input-icon">
                                    <i class='bx bx-user' aria-hidden="true"></i>
                                    <input type="text" id="username" name="username" class="form-control" placeholder="Tu nombre de usuario" required autocomplete="username" autocapitalize="none" spellcheck="false">
                                </div>
                            </div>

                            <div class="form-group">
                                <label for="password">Contraseña</label>
                                <div class="input-icon input-with-action">
                                    <i class='bx bx-lock-alt' aria-hidden="true"></i>
                                    <input type="password" id="password" name="password" class="form-control" placeholder="Tu contraseña" required autocomplete="current-password">
                                    <button type="button" id="login-toggle-password" class="input-action" aria-label="Mostrar contraseña" title="Mostrar contraseña">
                                        <i class='bx bx-show' aria-hidden="true"></i>
                                    </button>
                                </div>
                                <p id="login-caps" class="login-caps" aria-live="polite"><i class='bx bx-up-arrow-alt' aria-hidden="true"></i> Bloq Mayús está activado</p>
                            </div>

                            <button type="submit" id="btn-login-submit" class="btn btn-primary login-submit">
                                <i class='bx bx-log-in'></i> Iniciar sesión
                            </button>
                        </form>

                        <p class="login-help">¿Olvidaste tu contraseña? Solicita el restablecimiento a Soporte TI.</p>
                    </div>
                </section>
            </div>
        `;
    }
};
