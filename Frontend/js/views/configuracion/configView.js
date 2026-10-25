import { escapeHtml } from '../../utils/escapeHtml.js';

const passwordField = (id, label, autocomplete, hint = '') => `
    <div class="form-group">
        <label for="${id}">${label} <span class="req">*</span></label>
        <div class="input-with-action">
            <input type="password" id="${id}" class="form-control" autocomplete="${autocomplete}" required>
            <button type="button" class="input-action" data-toggle-password="${id}" aria-label="Mostrar contraseña" title="Mostrar contraseña">
                <i class="bx bx-show" aria-hidden="true"></i>
            </button>
        </div>
        ${hint}
        <p class="field-error" id="${id}-error" role="alert" hidden></p>
    </div>`;

export const ConfigView = {
    render({ user = {}, canChangePassword = false } = {}) {
        const name = [user.nombres, user.apellidos].filter(Boolean).join(' ').trim() || user.username || 'Usuario';
        return `
            <section class="dashboard-container config-page" aria-labelledby="config-title">
                <header class="module-header">
                    <div>
                        <span class="eyebrow">Sistema</span>
                        <h2 id="config-title"><i class='bx bx-cog' aria-hidden="true"></i> Configuración</h2>
                        <p class="catalog-description">Preferencias de la interfaz, seguridad de tu cuenta e información comercial.</p>
                    </div>
                </header>

                <div class="config-grid">
                    <div class="module-card config-card config-account">
                        <div class="config-card-head">
                            <span class="config-icon"><i class='bx bx-user-circle' aria-hidden="true"></i></span>
                            <div><h3>Mi cuenta</h3><p>Datos de la sesión activa.</p></div>
                        </div>
                        <dl class="config-identity">
                            <div><dt>Nombre</dt><dd>${escapeHtml(name)}</dd></div>
                            <div><dt>Usuario</dt><dd>${escapeHtml(user.username || '—')}</dd></div>
                            <div><dt>Rol</dt><dd><span class="status-badge status-active">${escapeHtml(user.grupo || 'Sin rol')}</span></dd></div>
                        </dl>
                    </div>

                    ${canChangePassword ? `
                    <div class="module-card config-card config-security">
                        <div class="config-card-head">
                            <span class="config-icon is-accent"><i class='bx bx-lock-alt' aria-hidden="true"></i></span>
                            <div><h3>Cambiar contraseña</h3><p>Por seguridad, confirma tu contraseña actual.</p></div>
                        </div>
                        <form id="form-cambiar-clave" class="euro-form" novalidate>
                            ${passwordField('cfg_clave_actual', 'Contraseña actual', 'current-password')}
                            ${passwordField('cfg_clave_nueva', 'Nueva contraseña', 'new-password', `
                                <div class="password-meter" aria-hidden="true"><span id="cfg-clave-meter"></span></div>
                                <ul class="password-rules" id="cfg-clave-rules">
                                    <li data-rule="length">Al menos 8 caracteres</li>
                                    <li data-rule="numeric">No puede ser solo números</li>
                                    <li data-rule="different">Diferente a la contraseña actual</li>
                                </ul>`)}
                            ${passwordField('cfg_clave_confirmar', 'Confirmar nueva contraseña', 'new-password')}
                            <div class="form-actions">
                                <button type="submit" id="btn-cambiar-clave" class="btn btn-primary">
                                    <i class='bx bx-key' aria-hidden="true"></i> Actualizar contraseña
                                </button>
                            </div>
                        </form>
                    </div>` : ''}

                    <div class="module-card config-card">
                        <div class="config-card-head">
                            <span class="config-icon"><i class='bx bx-palette' aria-hidden="true"></i></span>
                            <div><h3>Apariencia y moneda</h3><p>Se guardan en este navegador.</p></div>
                        </div>
                        <form id="form-config-prefs" class="euro-form">
                            <fieldset class="form-group theme-picker">
                                <legend>Tema</legend>
                                <div class="theme-options" role="radiogroup">
                                    <label class="theme-option"><input type="radio" name="cfg_tema" value="light"><span class="theme-swatch is-light" aria-hidden="true"></span><span>Claro</span></label>
                                    <label class="theme-option"><input type="radio" name="cfg_tema" value="dark"><span class="theme-swatch is-dark" aria-hidden="true"></span><span>Oscuro</span></label>
                                    <label class="theme-option"><input type="radio" name="cfg_tema" value="system"><span class="theme-swatch is-system" aria-hidden="true"></span><span>Sistema</span></label>
                                </div>
                            </fieldset>
                            <div class="form-group">
                                <label for="cfg_moneda">Moneda global</label>
                                <select id="cfg_moneda" class="form-control">
                                    <option value="NIO">Córdobas (C$)</option>
                                    <option value="USD">Dólares ($)</option>
                                </select>
                            </div>
                            <div class="form-grid">
                                <div class="form-group">
                                    <label for="cfg_tc">Tipo de cambio <small>(solo lectura)</small></label>
                                    <input type="number" id="cfg_tc" class="form-control" readonly title="Sincronizado con el sistema central">
                                </div>
                                <div class="form-group">
                                    <label for="cfg_iva">IVA aplicable <small>(solo lectura)</small></label>
                                    <input type="text" id="cfg_iva" class="form-control" readonly title="El IVA es administrado por el backend transaccional">
                                </div>
                            </div>
                        </form>
                    </div>

                    <div class="module-card config-card">
                        <div class="config-card-head">
                            <span class="config-icon"><i class='bx bx-building-house' aria-hidden="true"></i></span>
                            <div><h3>Información comercial</h3><p>Se usa en los tickets impresos.</p></div>
                        </div>
                        <form id="form-config-empresa" class="euro-form">
                            <div class="form-group">
                                <label for="cfg_empresa_nombre">Nombre de la empresa <span class="req">*</span></label>
                                <input type="text" id="cfg_empresa_nombre" class="form-control" required>
                            </div>
                            <div class="form-grid">
                                <div class="form-group">
                                    <label for="cfg_empresa_ruc">RUC <span class="req">*</span></label>
                                    <input type="text" id="cfg_empresa_ruc" class="form-control" required>
                                </div>
                                <div class="form-group">
                                    <label for="cfg_empresa_telefono">Teléfono <span class="req">*</span></label>
                                    <input type="text" id="cfg_empresa_telefono" class="form-control" required>
                                </div>
                            </div>
                            <div class="form-group">
                                <label for="cfg_empresa_direccion">Dirección <span class="req">*</span></label>
                                <textarea id="cfg_empresa_direccion" class="form-control" rows="3" required placeholder="Ingrese la dirección..."></textarea>
                            </div>
                            <div class="form-actions">
                                <button type="submit" id="btn-save-config" class="btn btn-secondary">
                                    <i class='bx bx-save' aria-hidden="true"></i> Guardar datos
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            </section>
        `;
    }
};
