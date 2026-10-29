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
                        <h2>Del almacén al mostrador.</h2>
                        <p>Catálogo, existencias, compras y despachos conectados en un solo flujo de trabajo.</p>
                        <figure class="login-part-figure" aria-hidden="true">
                            <svg viewBox="0 0 440 220" role="presentation" focusable="false">
                                <g class="part-outline">
                                    <circle cx="138" cy="110" r="77"></circle>
                                    <circle cx="138" cy="110" r="55"></circle>
                                    <circle cx="138" cy="110" r="27"></circle>
                                    <circle cx="138" cy="64" r="5"></circle>
                                    <circle cx="170.5" cy="77.5" r="5"></circle>
                                    <circle cx="184" cy="110" r="5"></circle>
                                    <circle cx="170.5" cy="142.5" r="5"></circle>
                                    <circle cx="138" cy="156" r="5"></circle>
                                    <circle cx="105.5" cy="142.5" r="5"></circle>
                                    <circle cx="92" cy="110" r="5"></circle>
                                    <circle cx="105.5" cy="77.5" r="5"></circle>
                                    <path d="M138 34v21M214 110h-21M138 186v-21M62 110h21M84 56l16 16M192 56l-16 16M192 164l-16-16M84 164l16-16"></path>
                                    <path d="M265 77h99l21 16v34l-21 16h-99l-17-18V95z"></path>
                                    <path d="M265 77v66M364 77v66M282 87v46M347 87v46"></path>
                                    <path class="part-accent" d="M231 110h25M331 59v18M331 143v18M229 84l15 13M229 136l15-13"></path>
                                    <path class="part-accent" d="M138 83v54M111 110h54"></path>
                                </g>
                            </svg>
                        </figure>
                        <ul class="login-features">
                            <li><i class='bx bx-package'></i> Existencias y movimientos de inventario</li>
                            <li><i class='bx bx-cart'></i> Compras y abastecimiento</li>
                            <li><i class='bx bx-receipt'></i> Ventas y control de despachos</li>
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
                            <h1>Iniciar sesión</h1>
                            <p>Accede con tus credenciales de Europartes.</p>
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
