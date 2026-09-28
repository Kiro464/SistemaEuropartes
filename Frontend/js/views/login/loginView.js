/**
 * js/views/login/loginView.js
 * Genera el HTML estático de la pantalla de inicio de sesión.
 */

export const LoginView = {
    render() {
        return `
            <div style="display: flex; justify-content: center; align-items: center; min-height: 100vh; background-color: var(--bg-color); width: 100vw;">
                <div class="module-card" style="width: 100%; max-width: 420px; padding: 2.5rem 2rem; border-top: 4px solid var(--primary-color); margin: 20px;">
                    
                    <!-- Encabezado con Logo -->
                    <div class="text-center mb-2">
                        <i class='bx bx-wrench logo-icon' style="font-size: 3.5rem;"></i>
                        <h2 class="logo-text" style="font-size: 1.8rem; margin-top: 10px; color: var(--text-primary);">EUROPARTES</h2>
                        <p style="color: var(--text-secondary); margin-top: 5px;">Sistema de Gestión POS</p>
                    </div>

                    <!-- Mensaje de Error Visual (Oculto por defecto) -->
                    <div id="login-error-msg" class="hidden" style="background-color: rgba(220, 53, 69, 0.1); color: var(--danger-color); padding: 10px; border-radius: var(--border-radius); border-left: 4px solid var(--danger-color); margin-bottom: 1.5rem; text-align: center; font-size: 0.9rem; font-weight: 500;">
                        Usuario o contraseña incorrectos.
                    </div>

                    <!-- Formulario de Login -->
                    <form id="login-form" class="euro-form">
                        <div class="form-group">
                            <label for="username">Usuario <span class="req">*</span></label>
                            <div style="position: relative;">
                                <i class='bx bx-user' style="position: absolute; left: 10px; top: 12px; color: var(--text-secondary); font-size: 1.2rem;"></i>
                                <input type="text" id="username" name="username" class="form-control" placeholder="Ingrese su usuario" style="padding-left: 35px;" required autocomplete="username">
                            </div>
                        </div>

                        <div class="form-group" style="margin-top: 1.2rem;">
                            <label for="password">Contraseña <span class="req">*</span></label>
                            <div style="position: relative;">
                                <i class='bx bx-lock-alt' style="position: absolute; left: 10px; top: 12px; color: var(--text-secondary); font-size: 1.2rem;"></i>
                                <input type="password" id="password" name="password" class="form-control" placeholder="Ingrese su contraseña" style="padding-left: 35px;" required autocomplete="current-password">
                            </div>
                        </div>

                        <button type="submit" id="btn-login-submit" class="btn btn-primary" style="width: 100%; margin-top: 1.5rem; padding: 0.8rem; font-size: 1.1rem;">
                            <i class='bx bx-log-in'></i> Iniciar Sesión
                        </button>
                    </form>
                    
                </div>
            </div>
        `;
    }
};