const ROLES = [
    'Administrador General', 'Asesor Financiero', 'Vendedor', 'Soporte TI',
    'Asesor de Marketing', 'Coordinador de Logística', 'Oficial de Logística'
];

export const UsuariosView = {
    render() {
        return `
        <section class="dashboard-container catalog-page" aria-labelledby="usuarios-title">
            <header class="module-header"><div><h2 id="usuarios-title"><i class="bx bx-user" aria-hidden="true"></i> Usuarios y roles</h2><p class="catalog-description">Administra las cuentas, el rol funcional y su asociación laboral. Los cambios de rol se aplican al renovar la sesión.</p></div><button type="button" id="btn-nuevo-usuario" class="btn btn-primary" data-rbac-action="usuario.gestionar"><i class="bx bx-plus" aria-hidden="true"></i> Nueva cuenta</button></header>
            <div class="module-card"><div class="catalog-toolbar"><label class="catalog-search" for="search-usuario"><i class="bx bx-search" aria-hidden="true"></i><span class="sr-only">Buscar cuentas</span><input type="search" id="search-usuario" class="form-control" placeholder="Buscar por usuario, nombre o correo…"></label><button type="button" id="btn-recargar-usuarios" class="btn btn-secondary"><i class="bx bx-refresh" aria-hidden="true"></i> Actualizar</button></div><div id="usuarios-table-container" class="table-responsive" aria-live="polite"><div class="catalog-state" role="status">Cargando cuentas…</div></div></div>
        </section>
        <div id="usuario-modal" class="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="usuario-modal-title"><div class="modal-box catalog-modal">
            <div class="modal-header"><h3 id="usuario-modal-title">Nueva cuenta</h3><button type="button" id="btn-close-usuario-modal" class="btn-close-modal" aria-label="Cerrar"><i class="bx bx-x" aria-hidden="true"></i></button></div>
            <div class="modal-body"><form id="form-usuario" class="euro-form"><input type="hidden" id="usr_id"><div class="form-grid">
                <div class="form-group"><label for="usr_username">Usuario <span class="req">*</span></label><input id="usr_username" class="form-control" maxlength="150" autocomplete="username" required></div>
                <div class="form-group"><label for="usr_email">Correo electrónico</label><input id="usr_email" type="email" class="form-control" maxlength="254" autocomplete="email"></div>
                <div class="form-group"><label for="usr_first_name">Nombres</label><input id="usr_first_name" class="form-control" maxlength="150" autocomplete="given-name"></div>
                <div class="form-group"><label for="usr_last_name">Apellidos</label><input id="usr_last_name" class="form-control" maxlength="150" autocomplete="family-name"></div>
                <div class="form-group"><label for="usr_rol">Rol funcional <span class="req">*</span></label><select id="usr_rol" class="form-control" required>${ROLES.map(role => `<option value="${role}">${role}</option>`).join('')}</select></div>
                <div class="form-group"><label for="usr_empleado">Empleado asociado</label><select id="usr_empleado" class="form-control"><option value="">Sin empleado asociado</option></select><small class="form-help">Opcional. Cada empleado puede vincularse a una sola cuenta.</small></div>
                <div class="form-group usr-password-field"><label for="usr_password">Contraseña inicial <span class="req">*</span></label><input id="usr_password" type="password" class="form-control" autocomplete="new-password" minlength="8"><small class="form-help">Se almacena mediante el mecanismo de hash de Django.</small></div>
                <div class="form-group usr-password-field"><label for="usr_password2">Confirmar contraseña <span class="req">*</span></label><input id="usr_password2" type="password" class="form-control" autocomplete="new-password" minlength="8"></div>
                <div class="form-group usr-active-field"><label class="catalog-check" for="usr_is_active"><input type="checkbox" id="usr_is_active" checked> Cuenta activa</label></div>
            </div><p id="usuario-form-error" class="form-error" role="alert" hidden></p></form></div>
            <div class="modal-footer"><button type="button" id="btn-cancel-usuario" class="btn btn-secondary">Cancelar</button><button type="submit" form="form-usuario" id="btn-save-usuario" class="btn btn-primary"><i class="bx bx-save" aria-hidden="true"></i> Guardar cuenta</button></div>
        </div></div>`;
    }
};
