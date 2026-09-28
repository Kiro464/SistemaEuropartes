export const ConfigView = {
    render() {
        return `
            <div class="dashboard-container">
                <div class="module-header">
                    <div>
                        <h2><i class='bx bx-cog'></i> Configuración del Sistema</h2>
                        <p style="color: var(--text-secondary);">Ajustes visuales, moneda e información de la empresa</p>
                    </div>
                    <button id="btn-save-config" class="btn btn-primary">
                        <i class='bx bx-save'></i> Guardar Empresa
                    </button>
                </div>

                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 20px; align-items: start;">
                    
                    <!-- PANEL 1: Preferencias -->
                    <div class="module-card">
                        <h3 style="margin-top: 0; display: flex; align-items: center; gap: 8px;">
                            <i class='bx bx-slider' style="color: var(--primary-color);"></i> Preferencias Visuales
                        </h3>
                        <hr style="border: 0; border-top: 1px solid var(--border-color); margin: 10px 0 20px 0;">
                        
                        <form id="form-config-prefs" class="euro-form">
                            <div class="form-group">
                                <label>Tema Visual</label>
                                <select id="cfg_tema" class="form-control">
                                    <option value="light">Modo Claro</option>
                                    <option value="dark">Modo Oscuro</option>
                                </select>
                            </div>
                            
                            <div class="form-group">
                                <label>Moneda Global</label>
                                <select id="cfg_moneda" class="form-control">
                                    <option value="NIO">Córdobas (C$)</option>
                                    <option value="USD">Dólares ($)</option>
                                </select>
                            </div>
                            
                            <div class="form-group">
                                <label>Tipo de Cambio <small style="color: var(--text-secondary);">(Solo lectura)</small></label>
                                <input type="number" id="cfg_tc" class="form-control" readonly style="background-color: var(--bg-color); cursor: not-allowed;" title="Sincronizado con el sistema central">
                            </div>
                            
                            <div class="form-group">
                                <label>IVA Aplicable (%) <small style="color: var(--text-secondary);">(Solo lectura)</small></label>
                                <input type="text" id="cfg_iva" class="form-control" readonly style="background-color: var(--bg-color); cursor: not-allowed;" title="El IVA es administrado por el backend transaccional">
                            </div>
                        </form>
                    </div>

                    <!-- PANEL 2: Datos de la Empresa -->
                    <div class="module-card">
                        <h3 style="margin-top: 0; display: flex; align-items: center; gap: 8px;">
                            <i class='bx bx-building-house' style="color: var(--primary-color);"></i> Información Comercial
                        </h3>
                        <hr style="border: 0; border-top: 1px solid var(--border-color); margin: 10px 0 20px 0;">
                        
                        <form id="form-config-empresa" class="euro-form">
                            <div class="form-group">
                                <label>Nombre de la Empresa <span class="req">*</span></label>
                                <input type="text" id="cfg_empresa_nombre" class="form-control" required>
                            </div>
                            
                            <div class="form-group">
                                <label>RUC <span class="req">*</span></label>
                                <input type="text" id="cfg_empresa_ruc" class="form-control" required>
                            </div>
                            
                            <div class="form-group">
                                <label>Teléfono de Contacto <span class="req">*</span></label>
                                <input type="text" id="cfg_empresa_telefono" class="form-control" required>
                            </div>
                            
                            <div class="form-group">
                                <label>Dirección Física <span class="req">*</span></label>
                                <textarea id="cfg_empresa_direccion" class="form-control" rows="4" required placeholder="Ingrese la dirección..."></textarea>
                            </div>
                        </form>
                    </div>

                </div>
            </div>
        `;
    }
};