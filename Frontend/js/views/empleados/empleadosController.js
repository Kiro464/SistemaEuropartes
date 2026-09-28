import { EmpleadosView } from './empleadosView.js';
import { EmpleadosService } from './empleadosService.js';
import { UI } from '../../components/ui.js';
import { DepartamentosController } from '../departamentos/departamentosController.js';

export const EmpleadosController = {
    debounceTimer: null,
    departamentos: [],

    async init() {
        const root = document.getElementById('main-content');
        if (!root) return;
        root.innerHTML = EmpleadosView.render();
        await this.loadDependencias(); 
        await this.loadTable();
        this.bindEvents();
    },

    async loadDependencias() {
        try {
            const deptoData = await EmpleadosService.getDepartamentos();
            this.departamentos = deptoData.results || deptoData;

            document.getElementById('emp_departamento').innerHTML = '<option value="">Seleccione...</option>' + 
                this.departamentos.map(d => `<option value="${d.id}">${d.nombre}</option>`).join('');
        } catch (error) { console.error("Error cargando departamentos", error); }
    },

    async loadTable(search = '') {
        const container = document.getElementById('empleados-table-container');
        try {
            const data = await EmpleadosService.getAll(search);
            const records = data.results || data;
            if (!records || records.length === 0) return container.innerHTML = `<div class="text-center" style="padding: 2rem;">No hay empleados.</div>`;

            let rows = records.map(e => {
                const deptoObj = this.departamentos.find(d => d.id === e.departamento);
                return `
                <tr style="opacity: ${e.estado ? '1' : '0.5'}">
                    <td><span style="font-family: monospace; color: var(--primary-color);">#${e.id}</span></td>
                    <td><strong>${e.nombres} ${e.apellidos}</strong></td>
                    <td>${e.cargo}</td>
                    <td>${deptoObj ? deptoObj.nombre : '-'}</td>
                    <td>${e.estado ? '<span style="color:var(--success-color);">Activo</span>' : '<span style="color:var(--text-secondary);">Inactivo</span>'}</td>
                    <td>
                        <div class="table-actions">
                            <button class="btn-icon btn-view" data-id="${e.id}" title="Ver Detalle"><i class='bx bx-show'></i></button>
                            <button class="btn-icon btn-edit" data-id="${e.id}" title="Editar"><i class='bx bx-edit'></i></button>
                            ${e.estado ? `<button class="btn-icon btn-delete" data-id="${e.id}" title="Desactivar"><i class='bx bx-trash' style="color: var(--danger-color);"></i></button>` : `<button class="btn-icon btn-activate" data-id="${e.id}" title="Activar"><i class='bx bx-check-circle' style="color: var(--success-color);"></i></button>`}
                        </div>
                    </td>
                </tr>`;
            }).join('');
            container.innerHTML = `<table class="euro-table"><thead><tr><th>ID</th><th>Empleado</th><th>Cargo</th><th>Departamento</th><th>Estado</th><th>Acciones</th></tr></thead><tbody>${rows}</tbody></table>`;

            document.querySelectorAll('.btn-view').forEach(b => b.addEventListener('click', (ev) => this.openModal(ev.currentTarget.dataset.id, true)));
            document.querySelectorAll('.btn-edit').forEach(b => b.addEventListener('click', (ev) => this.openModal(ev.currentTarget.dataset.id, false)));
            document.querySelectorAll('.btn-delete').forEach(b => b.addEventListener('click', (ev) => this.toggleStatus(ev.currentTarget.dataset.id, false)));
            document.querySelectorAll('.btn-activate').forEach(b => b.addEventListener('click', (ev) => this.toggleStatus(ev.currentTarget.dataset.id, true)));
        } catch (error) { container.innerHTML = 'Error de red.'; }
    },

    bindEvents() {
        document.getElementById('search-empleado').addEventListener('input', (e) => {
            clearTimeout(this.debounceTimer);
            this.debounceTimer = setTimeout(() => this.loadTable(e.target.value.trim()), 500);
        });

        const modal = document.getElementById('empleado-modal');
        const closeModal = () => { modal.classList.remove('show'); document.getElementById('form-empleado').reset(); };
        
        document.getElementById('btn-nuevo-empleado').addEventListener('click', () => {
            document.getElementById('emp_id').value = '';
            this.setFormReadOnly(false);
            document.getElementById('empleado-modal-title').innerText = 'Nuevo Empleado';
            modal.classList.add('show');
        });
        
        document.getElementById('btn-close-empleado-modal').addEventListener('click', closeModal);
        document.getElementById('btn-cancel-empleado').addEventListener('click', closeModal);

        /* ========================================================= */
        /* == INTEGRACIÓN MODULAR EXACTA: DEPARTAMENTOS ============ */
        /* ========================================================= */
        document.getElementById('btn-add-departamento')?.addEventListener('click', () => {
            DepartamentosController.onSuccess = async (newId) => { await this.loadDependencias(); document.getElementById('emp_departamento').value = newId; };
            DepartamentosController.openModal();
        });
        document.getElementById('btn-view-departamento').addEventListener('click', () => {
            const id = document.getElementById('emp_departamento').value;
            if(!id) return UI.showToast('Seleccione un departamento', 'error');
            DepartamentosController.openModal(id, true);
        });
        document.getElementById('btn-edit-departamento').addEventListener('click', () => {
            const id = document.getElementById('emp_departamento').value;
            if(!id) return UI.showToast('Seleccione un departamento', 'error');
            DepartamentosController.onSuccess = () => this.loadDependencias();
            DepartamentosController.openModal(id, false);
        });

        document.getElementById('btn-save-empleado').addEventListener('click', async () => {
            const form = document.getElementById('form-empleado');
            if (!form.checkValidity()) { form.reportValidity(); return; }

            const id = document.getElementById('emp_id').value;
            const payload = {
                nombres: document.getElementById('emp_nombres').value.trim(),
                apellidos: document.getElementById('emp_apellidos').value.trim(),
                cargo: document.getElementById('emp_cargo').value.trim(),
                telefono: document.getElementById('emp_telefono').value.trim(),
                correo: document.getElementById('emp_correo').value.trim(),
                departamento: parseInt(document.getElementById('emp_departamento').value)
            };
            if(!id) payload.estado = true;

            UI.showGlobalLoader();
            try {
                if (id) await EmpleadosService.update(id, payload);
                else await EmpleadosService.create(payload);
                UI.showToast(`Empleado guardado`, 'success');
                closeModal();
                this.loadTable(document.getElementById('search-empleado').value);
            } catch (error) { UI.showToast('Error', 'error'); } finally { UI.hideGlobalLoader(); }
        });
    },

    setFormReadOnly(isReadOnly) {
        const form = document.getElementById('form-empleado');
        Array.from(form.elements).forEach(el => { if(el.type !== 'hidden') el.disabled = isReadOnly; });
        document.getElementById('btn-save-empleado').style.display = isReadOnly ? 'none' : 'block';
        
        document.getElementById('btn-view-departamento').disabled = false;
        document.querySelectorAll('#btn-add-departamento, #btn-edit-departamento').forEach(b => {
            b.style.display = isReadOnly ? 'none' : 'inline-flex';
            b.disabled = false;
        });
    },

    async openModal(id, readOnly = false) {
        UI.showGlobalLoader();
        try {
            const emp = await EmpleadosService.getById(id);
            document.getElementById('emp_id').value = emp.id;
            document.getElementById('emp_nombres').value = emp.nombres;
            document.getElementById('emp_apellidos').value = emp.apellidos;
            document.getElementById('emp_cargo').value = emp.cargo;
            document.getElementById('emp_telefono').value = emp.telefono;
            document.getElementById('emp_correo').value = emp.correo;
            document.getElementById('emp_departamento').value = emp.departamento;
            
            this.setFormReadOnly(readOnly);
            document.getElementById('empleado-modal-title').innerText = readOnly ? 'Detalle de Empleado' : 'Editar Empleado';
            document.getElementById('empleado-modal').classList.add('show');
        } catch (error) { UI.showToast('Error', 'error'); } finally { UI.hideGlobalLoader(); }
    },

    async toggleStatus(id, newStatus) {
        if (!confirm(`¿Desea ${newStatus ? 'ACTIVAR' : 'INACTIVAR'} este empleado?`)) return;
        UI.showGlobalLoader();
        try {
            await EmpleadosService.update(id, { estado: newStatus }, 'PATCH');
            UI.showToast(`Empleado actualizado`, 'success');
            this.loadTable();
        } catch (error) { UI.showToast('Error', 'error'); } finally { UI.hideGlobalLoader(); }
    }
};