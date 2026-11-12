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

const RESULTADOS = {
    EXITOSO: { label: 'Exitoso', badge: 'stock-ok' },
    COMPLETADO_CON_RECHAZOS: { label: 'Completado con rechazos', badge: 'stock-critical' },
    FALLIDO: { label: 'Fallido', badge: 'stock-out' }
};

const filaResultado = (label, value) => `<div><dt>${label}</dt><dd>${escapeHtml(String(value ?? '—'))}</dd></div>`;

export const ConfigView = {
    /** TEMPORAL: tarjeta de exportación anticipada (Soporte TI con la función habilitada). */
    renderExportacionAnticipada({ periodo = '' } = {}) {
        return `
            <div class="module-card config-card config-export">
                <div class="config-card-head">
                    <span class="config-icon is-accent"><i class='bx bx-export' aria-hidden="true"></i></span>
                    <div><h3>Exportación anticipada de autoevaluaciones</h3><p>Temporal · Envía al DW un periodo aunque siga abierto.</p></div>
                </div>
                <form id="form-exportacion-anticipada" class="euro-form" novalidate>
                    <div class="form-group">
                        <label for="cfg_export_periodo">Periodo <span class="req">*</span></label>
                        <input type="month" id="cfg_export_periodo" class="form-control" value="${escapeHtml(periodo)}" required>
                    </div>
                    <p class="config-note">Solo se exportan evaluaciones completadas de vendedores activos. No cierra el periodo ni modifica las evaluaciones; si se repite, una evaluación corregida actualiza su registro en el DW.</p>
                    <div class="form-actions">
                        <button type="submit" id="btn-exportacion-anticipada" class="btn btn-primary">
                            <i class='bx bx-export' aria-hidden="true"></i> Exportar al DW
                        </button>
                    </div>
                </form>
                <div id="cfg-export-resultado" aria-live="polite"></div>
            </div>`;
    },

    renderResultadoExportacion(data = {}) {
        const resultado = RESULTADOS[data.resultado] || RESULTADOS.FALLIDO;
        const filas = data.resultado === 'FALLIDO'
            ? filaResultado('Detalle', data.detail || 'La exportación no se completó.')
            : [
                ['Evaluaciones encontradas', data.encontradas],
                ['Procesadas', data.procesadas],
                ['Omitidas', data.omitidas],
                ['Insertadas', data.insertadas],
                ['Actualizadas', data.actualizadas],
                ['Errores', data.errores],
                ['Hechos DW (nuevos / actualizados / sin cambios)',
                    `${data.hechos_insertados ?? 0} / ${data.hechos_actualizados ?? 0} / ${data.hechos_sin_cambios ?? 0}`]
            ].map(([label, value]) => filaResultado(label, value)).join('');
        const rechazos = (data.rechazos || []).slice(0, 20).map(r =>
            `<li><code>${escapeHtml(r.origen_evaluacion_id)}</code> · ${escapeHtml(r.motivo)}${r.campos?.length ? ` (${escapeHtml(r.campos.join(', '))})` : ''}</li>`).join('');
        return `
            <dl class="config-identity config-export-result">
                <div><dt>Resultado</dt><dd><span class="status-badge ${resultado.badge}">${resultado.label}</span></dd></div>
                ${filaResultado('Periodo', data.periodo)}
                ${filas}
                ${data.auditoria_registrada === false ? filaResultado('Auditoría', 'Solo en el log del servidor') : ''}
            </dl>
            ${rechazos ? `<details class="config-export-rechazos"><summary>No cargadas (${data.rechazos.length})</summary><ul>${rechazos}</ul></details>` : ''}`;
    },

    render({ user = {}, canChangePassword = false, canExportAnticipada = false } = {}) {
        const name = [user.nombres, user.apellidos].filter(Boolean).join(' ').trim() || user.username || 'Usuario';
        return `
            <section class="dashboard-container config-page" aria-labelledby="config-title">
                <header class="module-header">
                    <div>
                        <span class="eyebrow">Sistema</span>
                        <h1 id="config-title"><i class='bx bx-cog' aria-hidden="true"></i> Configuración</h1>
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

                    ${canExportAnticipada ? '<div id="cfg-exportacion-anticipada" class="config-export-slot"></div>' : ''}

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
