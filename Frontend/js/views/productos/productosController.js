import { ProductosView } from './productosView.js';
import { ProductosService } from './productosService.js';
import { UI } from '../../components/ui.js';
import { ProveedoresController } from '../proveedores/proveedoresController.js';
import { MarcasController } from '../marcas/marcasController.js';
import { StateManager } from '../../core/state.js';
import { AuthService } from '../../core/auth.js';
import { RBAC } from '../../core/rbac.js';
import { escapeHtml } from '../../utils/escapeHtml.js';

export const ProductosController = {
    debounceTimer: null,
    marcas: [],
    proveedores: [],
    records: [],
    filters: { search: '', stock: 'todos', estado: 'todos' },
    _onMonedaCambiada: null,

    getRole() {
        return AuthService.getRole();
    },

    async init() {
        const root = document.getElementById('main-content');
        if (!root) return;

        root.innerHTML = ProductosView.render();
        this.applyActionVisibility();
        await this.loadDependencias();
        await this.loadTable();
        this.bindEvents();

        if (this._onMonedaCambiada) {
            window.removeEventListener('monedaCambiada', this._onMonedaCambiada);
        }
        this._onMonedaCambiada = () => {
            if (document.getElementById('productos-table-container')) {
                this.renderTable();
            }
        };
        window.addEventListener('monedaCambiada', this._onMonedaCambiada);
    },

    applyActionVisibility() {
        const role = this.getRole();
        document.querySelectorAll('[data-rbac-action]').forEach(element => {
            const allowed = RBAC.can(role, element.dataset.rbacAction);
            element.classList.toggle('hidden', !allowed);
            element.setAttribute('aria-hidden', allowed ? 'false' : 'true');
        });
    },

    async loadDependencias() {
        const role = this.getRole();
        const canManageCatalogs = RBAC.canAny(role, ['marca.gestionar', 'proveedor.gestionar']);

        // Solo los roles que pueden gestionar estos catálogos tienen autorización
        // para consultarlos desde el frontend. El resto puede consultar productos
        // sin provocar llamadas 403 innecesarias.
        if (!canManageCatalogs) {
            this.marcas = [];
            this.proveedores = [];
            return;
        }

        try {
            const [marcasData, provData] = await Promise.all([
                ProductosService.getMarcas(),
                ProductosService.getProveedores()
            ]);

            this.marcas = marcasData.results || marcasData || [];
            this.proveedores = provData.results || provData || [];
            this.populateCatalogSelects();
        } catch (error) {
            console.error('Error obteniendo catálogos relacionados:', error);
            this.marcas = [];
            this.proveedores = [];
        }
    },

    ensureCatalogOption(selectId, value, catalog, label, displayName = '') {
        const select = document.getElementById(selectId);
        if (!select || value === null || value === undefined || value === '') return;

        const normalizedValue = String(value);
        const exists = Array.from(select.options).some(option => option.value === normalizedValue);
        if (exists) {
            select.value = normalizedValue;
            return;
        }

        const item = (catalog || []).find(entry => String(entry.id) === normalizedValue);
        const option = document.createElement('option');
        option.value = normalizedValue;
        option.textContent = item?.nombre ?? displayName ?? `${label} #${normalizedValue}`;
        select.appendChild(option);
        select.value = normalizedValue;
    },

    populateCatalogSelects() {
        const marcaSelect = document.getElementById('prod_marca');
        const proveedorSelect = document.getElementById('prod_proveedor');
        if (!marcaSelect || !proveedorSelect) return;

        marcaSelect.innerHTML = '<option value="">Seleccione...</option>' +
            this.marcas.map(m => `<option value="${m.id}">${escapeHtml(m.nombre)}</option>`).join('');
        proveedorSelect.innerHTML = '<option value="">Seleccione...</option>' +
            this.proveedores.map(p => `<option value="${p.id}">${escapeHtml(p.nombre)}</option>`).join('');
    },

    async loadTable(search = this.filters.search) {
        const container = document.getElementById('productos-table-container');
        if (!container) return;

        this.filters.search = search;
        container.innerHTML = '<div class="product-loading-state"><i class="bx bx-loader-alt bx-spin"></i><span>Cargando productos...</span></div>';

        try {
            const data = await ProductosService.getAll(search, true);
            this.records = data.results || data || [];
            this.renderTable();
        } catch (error) {
            console.error('Error cargando productos:', error);
            container.innerHTML = `<div class="product-empty-state product-error-state"><i class='bx bx-error-circle'></i><strong>No fue posible cargar el catálogo</strong><span>${escapeHtml(this.getErrorMessage(error))}</span><button class="btn btn-secondary" id="btn-retry-productos">Reintentar</button></div>`;
            document.getElementById('btn-retry-productos')?.addEventListener('click', () => this.loadTable(this.filters.search));
        }
    },

    getFilteredRecords() {
        return this.records.filter(product => {
            const stock = Number(product.stock_actual ?? 0);
            const estado = Boolean(product.estado);
            const stockMatch = this.filters.stock === 'todos'
                || (this.filters.stock === 'critico' && stock <= 5)
                || (this.filters.stock === 'sin_stock' && stock === 0)
                || (this.filters.stock === 'disponible' && stock > 5);
            const estadoMatch = this.filters.estado === 'todos'
                || (this.filters.estado === 'activo' && estado)
                || (this.filters.estado === 'inactivo' && !estado);
            return stockMatch && estadoMatch;
        });
    },

    updateSummary(records) {
        const total = records.length;
        const critical = records.filter(p => Number(p.stock_actual ?? 0) <= 5).length;
        const out = records.filter(p => Number(p.stock_actual ?? 0) === 0).length;
        const active = records.filter(p => Boolean(p.estado)).length;

        document.getElementById('product-summary-total').textContent = total;
        document.getElementById('product-summary-critical').textContent = critical;
        document.getElementById('product-summary-out').textContent = out;
        document.getElementById('product-summary-active').textContent = active;
    },

    renderTable() {
        const container = document.getElementById('productos-table-container');
        if (!container) return;

        const records = this.getFilteredRecords().sort((a, b) => {
            const stockA = Number(a.stock_actual ?? 0);
            const stockB = Number(b.stock_actual ?? 0);
            if ((stockA > 0) !== (stockB > 0)) return stockA > 0 ? -1 : 1;

            const activeA = Boolean(a.estado);
            const activeB = Boolean(b.estado);
            if (activeA !== activeB) return activeA ? -1 : 1;

            return String(a.nombre ?? '').localeCompare(String(b.nombre ?? ''), 'es', { sensitivity: 'base' });
        });
        this.updateSummary(this.records);

        const meta = document.getElementById('productos-list-meta');
        if (meta) {
            const total = this.records.length;
            meta.textContent = `${records.length} de ${total} producto${total === 1 ? '' : 's'} mostrado${total === 1 ? '' : 's'}`;
        }

        if (!records.length) {
            container.innerHTML = `<div class="product-empty-state"><i class='bx bx-package'></i><strong>No hay productos para los filtros seleccionados</strong><span>Pruebe otra búsqueda o cambie los filtros.</span></div>`;
            return;
        }

        const simbolo = StateManager.getSimbolo();
        const role = this.getRole();

        const rows = records.map(product => {
            const stock = Number(product.stock_actual ?? 0);
            const canEdit = RBAC.canManageProduct(role, product);
            const canDelete = RBAC.canManageProduct(role, product);
            const precioBase = Number.parseFloat(product.precio_unitario) || 0;
            const precioVisual = StateManager.getPrecioVisual(precioBase);
            const stockClass = stock === 0 ? 'stock-badge stock-out' : stock <= 5 ? 'stock-badge stock-critical' : 'stock-badge stock-ok';
            const estadoClass = product.estado ? 'status-badge status-active' : 'status-badge status-inactive';
            const estadoText = product.estado ? 'Activo' : 'Inactivo';
            const marca = product.marca_nombre || this.marcas.find(m => String(m.id) === String(product.marca))?.nombre;
            const proveedor = product.proveedor_nombre || this.proveedores.find(p => String(p.id) === String(product.proveedor))?.nombre;
            const referencia = `${marca ? escapeHtml(marca) : 'Sin marca'} · ${proveedor ? escapeHtml(proveedor) : 'Sin proveedor'}`;

            return `
                <tr class="${product.estado ? '' : 'row-inactive'}">
                    <td><span class="product-id">#${escapeHtml(String(product.id))}</span></td>
                    <td>
                        <div class="product-name-cell">
                            <strong>${escapeHtml(product.nombre ?? 'Sin nombre')}</strong>
                            <small>${escapeHtml(product.descripcion ?? '')}</small>
                        </div>
                    </td>
                    <td><span class="product-reference">${referencia}</span></td>
                    <td><span class="${stockClass}">${stock}</span></td>
                    <td><strong>${simbolo} ${precioVisual.toFixed(2)}</strong></td>
                    <td><span class="${estadoClass}">${estadoText}</span></td>
                    <td>
                        <div class="table-actions">
                            <button class="btn-icon btn-view-producto" data-id="${product.id}" title="Ver detalle" aria-label="Ver detalle"><i class='bx bx-show'></i></button>
                            ${canEdit ? `<button class="btn-icon btn-edit-producto" data-id="${product.id}" title="Editar" aria-label="Editar"><i class='bx bx-edit'></i></button>` : ''}
                            ${canDelete && product.estado ? `<button class="btn-icon btn-delete-producto" data-id="${product.id}" title="Inactivar" aria-label="Inactivar"><i class='bx bx-trash'></i></button>` : ''}
                            ${canDelete && !product.estado ? `<button class="btn-icon btn-activate-producto" data-id="${product.id}" title="Activar" aria-label="Activar"><i class='bx bx-check-circle'></i></button>` : ''}
                        </div>
                    </td>
                </tr>`;
        }).join('');

        container.innerHTML = `
            <table class="euro-table product-table">
                <thead><tr>
                    <th>ID</th><th>Producto</th><th>Marca / proveedor</th><th>Stock</th><th>Precio</th><th>Estado</th><th>Acciones</th>
                </tr></thead>
                <tbody>${rows}</tbody>
            </table>`;

        container.querySelectorAll('.btn-view-producto').forEach(button => button.addEventListener('click', () => this.openModal(button.dataset.id, true)));
        container.querySelectorAll('.btn-edit-producto').forEach(button => button.addEventListener('click', () => this.openModal(button.dataset.id, false)));
        container.querySelectorAll('.btn-delete-producto').forEach(button => button.addEventListener('click', () => this.toggleStatus(button.dataset.id, false)));
        container.querySelectorAll('.btn-activate-producto').forEach(button => button.addEventListener('click', () => this.toggleStatus(button.dataset.id, true)));
    },

    bindEvents() {
        document.getElementById('search-producto')?.addEventListener('input', event => {
            clearTimeout(this.debounceTimer);
            this.debounceTimer = setTimeout(() => this.loadTable(event.target.value.trim()), 400);
        });

        document.getElementById('filter-stock-producto')?.addEventListener('change', event => {
            this.filters.stock = event.target.value;
            this.renderTable();
        });
        document.getElementById('filter-estado-producto')?.addEventListener('change', event => {
            this.filters.estado = event.target.value;
            this.renderTable();
        });

        const modal = document.getElementById('producto-modal');
        const closeModal = () => {
            modal?.classList.remove('show');
            document.getElementById('form-producto')?.reset();
        };

        document.getElementById('btn-nuevo-producto')?.addEventListener('click', () => {
            if (!RBAC.can(this.getRole(), 'producto.crear')) {
                UI.showToast('No tiene permisos para crear productos.', 'warning');
                return;
            }
            this.resetFormForCreate();
            document.getElementById('producto-modal-title').textContent = 'Nuevo producto';
            modal?.classList.add('show');
        });

        document.getElementById('btn-close-producto-modal')?.addEventListener('click', closeModal);
        document.getElementById('btn-cancel-producto')?.addEventListener('click', closeModal);

        document.getElementById('btn-add-marca')?.addEventListener('click', () => {
            if (!RBAC.can(this.getRole(), 'marca.gestionar')) return UI.showToast('No tiene permisos para gestionar marcas.', 'warning');
            MarcasController.onSuccess = async newId => { await this.loadDependencias(); document.getElementById('prod_marca').value = newId; };
            MarcasController.openModal();
        });
        document.getElementById('btn-view-marca')?.addEventListener('click', () => {
            const id = document.getElementById('prod_marca').value;
            if (!id) return UI.showToast('Seleccione una marca.', 'warning');
            MarcasController.openModal(id, true);
        });
        document.getElementById('btn-edit-marca')?.addEventListener('click', () => {
            if (!RBAC.can(this.getRole(), 'marca.gestionar')) return UI.showToast('No tiene permisos para gestionar marcas.', 'warning');
            const id = document.getElementById('prod_marca').value;
            if (!id) return UI.showToast('Seleccione una marca.', 'warning');
            MarcasController.onSuccess = () => this.loadDependencias();
            MarcasController.openModal(id, false);
        });

        document.getElementById('btn-add-proveedor')?.addEventListener('click', () => {
            if (!RBAC.can(this.getRole(), 'proveedor.gestionar')) return UI.showToast('No tiene permisos para gestionar proveedores.', 'warning');
            ProveedoresController.onSuccess = async newId => { await this.loadDependencias(); document.getElementById('prod_proveedor').value = newId; };
            ProveedoresController.openModal();
        });
        document.getElementById('btn-view-proveedor')?.addEventListener('click', () => {
            const id = document.getElementById('prod_proveedor').value;
            if (!id) return UI.showToast('Seleccione un proveedor.', 'warning');
            ProveedoresController.openModal(id, true);
        });
        document.getElementById('btn-edit-proveedor')?.addEventListener('click', () => {
            if (!RBAC.can(this.getRole(), 'proveedor.gestionar')) return UI.showToast('No tiene permisos para gestionar proveedores.', 'warning');
            const id = document.getElementById('prod_proveedor').value;
            if (!id) return UI.showToast('Seleccione un proveedor.', 'warning');
            ProveedoresController.onSuccess = () => this.loadDependencias();
            ProveedoresController.openModal(id, false);
        });

        document.getElementById('btn-save-producto')?.addEventListener('click', async () => {
            const form = document.getElementById('form-producto');
            if (!form.checkValidity()) { form.reportValidity(); return; }

            const id = document.getElementById('prod_id').value;
            const role = this.getRole();
            const action = id ? 'producto.editar' : 'producto.crear';
            if (!RBAC.can(role, action)) {
                UI.showToast('No tiene permisos para realizar esta operación.', 'warning');
                return;
            }

            const product = this.records.find(item => String(item.id) === String(id));
            if (id && (!product || !RBAC.canManageProduct(role, product))) {
                UI.showToast('El Coordinador de Logística solo puede modificar productos con stock 0.', 'warning');
                return;
            }

            const payload = {
                nombre: document.getElementById('prod_nombre').value.trim(),
                descripcion: document.getElementById('prod_descripcion').value.trim(),
                marca: Number.parseInt(document.getElementById('prod_marca').value, 10),
                proveedor: Number.parseInt(document.getElementById('prod_proveedor').value, 10),
                precio_unitario: Number.parseFloat(document.getElementById('prod_precio_unitario').value)
            };
            if (!id) payload.estado = true;

            UI.showGlobalLoader();
            try {
                if (id) await ProductosService.update(id, payload);
                else await ProductosService.create(payload);
                UI.showToast('Producto guardado correctamente.', 'success');
                closeModal();
                await this.loadTable(document.getElementById('search-producto')?.value || '');
            } catch (error) {
                UI.showToast(this.getErrorMessage(error), 'error');
            } finally {
                UI.hideGlobalLoader();
            }
        });
    },

    resetFormForCreate() {
        document.getElementById('form-producto')?.reset();
        document.getElementById('prod_id').value = '';
        document.getElementById('prod_stock_actual').value = '0';
        document.getElementById('prod_stock_actual').title = 'El stock inicial se mantiene en 0; las entradas se registran mediante inventario.';
        this.setFormReadOnly(false);
        this.setCatalogControls(true);
    },

    setCatalogControls(canEdit) {
        // Cada botón se muestra según el permiso del endpoint que invoca.
        const role = this.getRole();
        const buttons = {
            'btn-add-marca': !canEdit ? null : 'marca.crear',
            'btn-edit-marca': !canEdit ? null : 'marca.editar',
            'btn-view-marca': 'marca.ver',
            'btn-add-proveedor': !canEdit ? null : 'proveedor.crear',
            'btn-edit-proveedor': !canEdit ? null : 'proveedor.editar',
            'btn-view-proveedor': 'proveedor.ver'
        };
        Object.entries(buttons).forEach(([id, action]) => {
            const button = document.getElementById(id);
            if (button) button.classList.toggle('hidden', !action || !RBAC.can(role, action));
        });
    },

    setFormReadOnly(isReadOnly) {
        const form = document.getElementById('form-producto');
        if (!form) return;
        Array.from(form.elements).forEach(element => {
            if (element.type !== 'hidden' && element.id !== 'prod_stock_actual') {
                element.disabled = isReadOnly;
            }
        });
        document.getElementById('prod_stock_actual').disabled = true;
        document.getElementById('btn-save-producto').classList.toggle('hidden', isReadOnly);
        this.setCatalogControls(!isReadOnly);
    },

    async openModal(id, readOnly = false) {
        const role = this.getRole();
        if (!RBAC.can(role, 'producto.ver')) {
            UI.showToast('No tiene permisos para consultar productos.', 'warning');
            return;
        }
        if (!readOnly && !RBAC.can(role, 'producto.editar')) {
            UI.showToast('No tiene permisos para editar productos.', 'warning');
            return;
        }

        UI.showGlobalLoader();
        try {
            const product = await ProductosService.getById(id);
            document.getElementById('prod_id').value = product.id;
            document.getElementById('prod_nombre').value = product.nombre ?? '';
            document.getElementById('prod_descripcion').value = product.descripcion ?? '';
            this.ensureCatalogOption('prod_marca', product.marca, this.marcas, 'Marca', product.marca_nombre);
            this.ensureCatalogOption('prod_proveedor', product.proveedor, this.proveedores, 'Proveedor', product.proveedor_nombre);
            document.getElementById('prod_precio_unitario').value = product.precio_unitario ?? '';
            document.getElementById('prod_stock_actual').value = product.stock_actual ?? 0;

            const effectiveReadOnly = readOnly || !RBAC.canManageProduct(role, product);
            this.setFormReadOnly(effectiveReadOnly);
            document.getElementById('producto-stock-notice').classList.toggle('hidden', !effectiveReadOnly);
            document.getElementById('producto-modal-title').textContent = effectiveReadOnly ? 'Detalle de producto' : 'Editar producto';
            document.getElementById('producto-modal').classList.add('show');
        } catch (error) {
            UI.showToast(this.getErrorMessage(error), 'error');
        } finally {
            UI.hideGlobalLoader();
        }
    },

    async toggleStatus(id, newStatus) {
        const product = this.records.find(item => String(item.id) === String(id));
        if (!product || !RBAC.canManageProduct(this.getRole(), product)) {
            UI.showToast('No tiene permisos para modificar este producto.', 'warning');
            return;
        }

        const actionText = newStatus ? 'activar' : 'inactivar';
        if (!confirm(`¿Desea ${actionText} este producto?`)) return;

        UI.showGlobalLoader();
        try {
            await ProductosService.updateStatus(id, newStatus);
            UI.showToast(`Producto ${newStatus ? 'activado' : 'inactivado'} correctamente.`, 'success');
            await this.loadTable(this.filters.search);
        } catch (error) {
            UI.showToast(this.getErrorMessage(error), 'error');
        } finally {
            UI.hideGlobalLoader();
        }
    },

    getErrorMessage(error) {
        if (error?.status === 403) return 'No tiene permisos para realizar esta operación.';
        if (error?.status === 404) return 'El producto solicitado no existe.';
        const data = error?.data;
        if (data && typeof data === 'object') {
            const firstValue = Object.values(data)[0];
            if (Array.isArray(firstValue) && firstValue[0]) return String(firstValue[0]);
            if (typeof firstValue === 'string') return firstValue;
            if (data.detail) return String(data.detail);
        }
        return 'No fue posible completar la operación.';
    }
};
