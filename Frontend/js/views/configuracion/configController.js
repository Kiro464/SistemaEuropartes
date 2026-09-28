import { ConfigView } from './configView.js';
import { ConfigService } from './configService.js';
import { StateManager } from '../../core/state.js';
import { UI } from '../../components/ui.js';

export const ConfigController = {
    async init() {
        const root = document.getElementById('main-content');
        if (!root) return;
        
        root.innerHTML = ConfigView.render();
        
        await this.loadData();
        this.bindEvents();
    },

    async loadData() {
        UI.showGlobalLoader();
        try {
            // 1. Cargar estado visual
            document.getElementById('cfg_tema').value = localStorage.getItem('europartes_theme') || 'light';
            document.getElementById('cfg_moneda').value = StateManager.moneda || 'NIO';
            document.getElementById('cfg_tc').value = StateManager.tipoCambio || 36.62;

            // 2. Cargar Información de Empresa
            const empresa = ConfigService.getEmpresaInfo();
            document.getElementById('cfg_empresa_nombre').value = empresa.nombre_empresa;
            document.getElementById('cfg_empresa_ruc').value = empresa.ruc;
            document.getElementById('cfg_empresa_telefono').value = empresa.telefono;
            document.getElementById('cfg_empresa_direccion').value = empresa.direccion;

            // 3. Cargar Configuración del Backend (Solo Lectura)
            try {
                const configBackend = await ConfigService.getConfiguracionAPI();
                document.getElementById('cfg_iva').value = configBackend.iva ? `${configBackend.iva}%` : "15.00%";
            } catch (err) {
                console.warn("No se pudo obtener el IVA del servidor", err);
                document.getElementById('cfg_iva').value = "No disponible";
            }
            
        } catch (error) {
            console.error("Error general cargando configuración:", error);
            UI.showToast('Error al cargar la configuración', 'error');
        } finally {
            UI.hideGlobalLoader();
        }
    },

    bindEvents() {
        // EVENTOS INMEDIATOS (No necesitan botón "Guardar")
        document.getElementById('cfg_tema').addEventListener('change', (e) => {
            const nuevoTema = e.target.value;
            localStorage.setItem('europartes_theme', nuevoTema);
            document.documentElement.setAttribute('data-theme', nuevoTema);
        });

        document.getElementById('cfg_moneda').addEventListener('change', (e) => {
            StateManager.setMoneda(e.target.value); // Dispara la actualización visual a todo el sistema
        });

        // BOTÓN GUARDAR (Exclusivo para datos de la empresa)
        document.getElementById('btn-save-config').addEventListener('click', (e) => {
            e.preventDefault();

            const formEmpresa = document.getElementById('form-config-empresa');
            
            // Validar solo el formulario editable (Empresa)
            if (!formEmpresa.checkValidity()) { formEmpresa.reportValidity(); return; }

            UI.showGlobalLoader();

            try {
                // Guardar Datos de la Empresa en LocalStorage
                const infoEmpresa = {
                    nombre_empresa: document.getElementById('cfg_empresa_nombre').value.trim(),
                    ruc: document.getElementById('cfg_empresa_ruc').value.trim(),
                    telefono: document.getElementById('cfg_empresa_telefono').value.trim(),
                    direccion: document.getElementById('cfg_empresa_direccion').value.trim()
                };
                
                ConfigService.saveEmpresaInfo(infoEmpresa);

                UI.showToast('Datos de empresa guardados exitosamente', 'success');
            } catch (error) {
                console.error("Error al guardar:", error);
                UI.showToast('Error al procesar la configuración', 'error');
            } finally {
                UI.hideGlobalLoader();
            }
        });
    }
};

export const init = ConfigController.init.bind(ConfigController);