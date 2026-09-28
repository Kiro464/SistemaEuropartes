from django.core.management.base import BaseCommand
from django.db import connection
from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group

from apps.catalogos.departamento.models import Departamento
from apps.catalogos.empleado.models import Empleado
from apps.catalogos.pais.models import Pais
from apps.catalogos.marca.models import Marca
from apps.catalogos.proveedor.models import Proveedor
from apps.catalogos.producto.models import Producto
from apps.catalogos.cliente.models import Cliente

from apps.movimientos.compra.models import (
    SolicitudCompra,
    DetalleSolicitudCompra,
    Compra,
    DetalleCompra,
    FacturaCompra,
    MovimientoInventario,
)

from apps.movimientos.venta.models import (
    Venta,
    DetalleVenta,
    FacturaVenta,
    OrdenDespacho,
    DetalleOrden,
)


User = get_user_model()


class Command(BaseCommand):
    help = "Valida la integridad de los datos de desarrollo de EuropartesSystem."

    def __init__(self):
        super().__init__()
        self.errores = 0
        self.advertencias = 0

    def ok(self, mensaje):
        self.stdout.write(self.style.SUCCESS(f"✓ {mensaje}"))

    def error(self, mensaje):
        self.errores += 1
        self.stdout.write(self.style.ERROR(f"✗ ERROR: {mensaje}"))

    def advertencia(self, mensaje):
        self.advertencias += 1
        self.stdout.write(self.style.WARNING(f"⚠ ADVERTENCIA: {mensaje}"))

    def validar_cantidad(self, nombre, actual, esperada):
        if actual == esperada:
            self.ok(f"{nombre}: {actual}")
        else:
            self.error(
                f"{nombre}: {actual} (se esperaban {esperada})"
            )

    def handle(self, *args, **options):

        self.stdout.write("")
        self.stdout.write("=" * 60)
        self.stdout.write("   VALIDACIÓN DE INTEGRIDAD - EUROPARTES")
        self.stdout.write("=" * 60)
        self.stdout.write("")

        # ============================================================
        # 1. CATÁLOGOS
        # ============================================================

        self.stdout.write(self.style.HTTP_INFO("1. VALIDACIÓN DE CATÁLOGOS"))
        self.stdout.write("-" * 60)

        self.validar_cantidad(
            "Departamentos",
            Departamento.objects.count(),
            6,
        )

        self.validar_cantidad(
            "Países",
            Pais.objects.count(),
            6,
        )

        self.validar_cantidad(
            "Marcas",
            Marca.objects.count(),
            10,
        )

        self.validar_cantidad(
            "Proveedores",
            Proveedor.objects.count(),
            12,
        )

        self.validar_cantidad(
            "Productos",
            Producto.objects.count(),
            40,
        )

        self.validar_cantidad(
            "Clientes",
            Cliente.objects.count(),
            30,
        )

        # ============================================================
        # 2. USUARIOS Y EMPLEADOS
        # ============================================================

        self.stdout.write("")
        self.stdout.write(
            self.style.HTTP_INFO("2. VALIDACIÓN DE USUARIOS Y ROLES")
        )
        self.stdout.write("-" * 60)

        self.validar_cantidad(
            "Usuarios",
            User.objects.count(),
            12,
        )

        self.validar_cantidad(
            "Empleados",
            Empleado.objects.count(),
            12,
        )

        grupos_esperados = [
            "Administrador General",
            "Asesor Financiero",
            "Vendedor",
            "Soporte TI",
            "Asesor de Marketing",
            "Coordinador de Logística",
            "Oficial de Logística",
        ]

        grupos_existentes = set(
            Group.objects.values_list("name", flat=True)
        )

        for grupo in grupos_esperados:
            if grupo in grupos_existentes:
                self.ok(f"Rol existe: {grupo}")
            else:
                self.error(f"Falta el rol: {grupo}")

        # Cada empleado debe tener usuario
        empleados_sin_usuario = Empleado.objects.filter(
            usuario__isnull=True
        ).count()

        if empleados_sin_usuario == 0:
            self.ok("Todos los empleados tienen usuario asociado.")
        else:
            self.error(
                f"Hay {empleados_sin_usuario} empleados sin usuario."
            )

        # Cada usuario debe tener exactamente un grupo
        usuarios_sin_rol = []
        usuarios_con_multiples_roles = []

        for usuario in User.objects.all():
            cantidad_roles = usuario.groups.count()

            if cantidad_roles == 0:
                usuarios_sin_rol.append(usuario.username)

            elif cantidad_roles > 1:
                usuarios_con_multiples_roles.append(usuario.username)

        if not usuarios_sin_rol:
            self.ok("Todos los usuarios tienen un rol.")
        else:
            self.error(
                "Usuarios sin rol: "
                + ", ".join(usuarios_sin_rol)
            )

        if not usuarios_con_multiples_roles:
            self.ok("Cada usuario tiene un único rol.")
        else:
            self.error(
                "Usuarios con múltiples roles: "
                + ", ".join(usuarios_con_multiples_roles)
            )

        # ============================================================
        # 3. SOLICITUDES DE COMPRA
        # ============================================================

        self.stdout.write("")
        self.stdout.write(
            self.style.HTTP_INFO("3. VALIDACIÓN DE SOLICITUDES DE COMPRA")
        )
        self.stdout.write("-" * 60)

        self.validar_cantidad(
            "Solicitudes de compra",
            SolicitudCompra.objects.count(),
            20,
        )

        self.validar_cantidad(
            "Solicitudes PENDIENTE",
            SolicitudCompra.objects.filter(
                estado="PENDIENTE"
            ).count(),
            4,
        )

        self.validar_cantidad(
            "Solicitudes ACEPTADA",
            SolicitudCompra.objects.filter(
                estado="ACEPTADA"
            ).count(),
            10,
        )

        self.validar_cantidad(
            "Solicitudes RECHAZADA",
            SolicitudCompra.objects.filter(
                estado="RECHAZADA"
            ).count(),
            6,
        )

        detalles_sin_solicitud = DetalleSolicitudCompra.objects.filter(
            solicitud__isnull=True
        ).count()

        if detalles_sin_solicitud == 0:
            self.ok("Todos los detalles de solicitud tienen solicitud.")
        else:
            self.error(
                f"Hay {detalles_sin_solicitud} detalles sin solicitud."
            )

        # ============================================================
        # 4. COMPRAS
        # ============================================================

        self.stdout.write("")
        self.stdout.write(
            self.style.HTTP_INFO("4. VALIDACIÓN DE COMPRAS")
        )
        self.stdout.write("-" * 60)

        self.validar_cantidad(
            "Órdenes de compra",
            Compra.objects.count(),
            12,
        )

        estados_compra = {
            "REALIZADA": 2,
            "EN_PROCESO": 2,
            "PROCESADA": 3,
            "ENTREGADA_AL_ALMACEN": 5,
        }

        for estado, esperado in estados_compra.items():
            self.validar_cantidad(
                f"Compras {estado}",
                Compra.objects.filter(estado=estado).count(),
                esperado,
            )

        self.validar_cantidad(
            "Facturas de compra",
            FacturaCompra.objects.count(),
            5,
        )

        compras_sin_detalles = 0

        for compra in Compra.objects.all():
            if not DetalleCompra.objects.filter(compra=compra).exists():
                compras_sin_detalles += 1

        if compras_sin_detalles == 0:
            self.ok("Todas las compras tienen detalles.")
        else:
            self.error(
                f"Hay {compras_sin_detalles} compras sin detalles."
            )

        # Las facturas de compra deben pertenecer a compras entregadas
        facturas_compra_invalidas = 0

        for factura in FacturaCompra.objects.select_related("compra"):
            if factura.compra.estado != "ENTREGADA_AL_ALMACEN":
                facturas_compra_invalidas += 1

        if facturas_compra_invalidas == 0:
            self.ok(
                "Todas las facturas de compra pertenecen "
                "a compras entregadas al almacén."
            )
        else:
            self.error(
                f"Hay {facturas_compra_invalidas} facturas de compra "
                "con estado de compra incorrecto."
            )

        # ============================================================
        # 5. VENTAS
        # ============================================================

        self.stdout.write("")
        self.stdout.write(
            self.style.HTTP_INFO("5. VALIDACIÓN DE VENTAS")
        )
        self.stdout.write("-" * 60)

        self.validar_cantidad(
            "Ventas",
            Venta.objects.count(),
            40,
        )

        self.validar_cantidad(
            "Detalles de venta",
            DetalleVenta.objects.count(),
            79,
        )

        self.validar_cantidad(
            "Facturas de venta",
            FacturaVenta.objects.count(),
            40,
        )

        self.validar_cantidad(
            "Órdenes de despacho",
            OrdenDespacho.objects.count(),
            40,
        )

        self.validar_cantidad(
            "Detalles de despacho",
            DetalleOrden.objects.count(),
            79,
        )

        ventas_sin_detalles = 0

        for venta in Venta.objects.all():
            if not DetalleVenta.objects.filter(venta=venta).exists():
                ventas_sin_detalles += 1

        if ventas_sin_detalles == 0:
            self.ok("Todas las ventas tienen detalles.")
        else:
            self.error(
                f"Hay {ventas_sin_detalles} ventas sin detalles."
            )

        # ============================================================
        # 6. VALIDACIÓN DE DESPACHOS
        # ============================================================

        self.stdout.write("")
        self.stdout.write(
            self.style.HTTP_INFO("6. VALIDACIÓN DE DESPACHOS")
        )
        self.stdout.write("-" * 60)

        detalles_entregados = DetalleOrden.objects.filter(
            estado_entrega="Entregado"
        ).count()

        detalles_pendientes = DetalleOrden.objects.filter(
            estado_entrega="Pendiente"
        ).count()

        detalles_faltantes = DetalleOrden.objects.filter(
            estado_entrega="Faltante"
        ).count()

        self.stdout.write(
            f"Detalles Entregados: {detalles_entregados}"
        )
        self.stdout.write(
            f"Detalles Pendientes: {detalles_pendientes}"
        )
        self.stdout.write(
            f"Detalles Faltantes: {detalles_faltantes}"
        )

        estados_permitidos = {
            "Entregado",
            "Pendiente",
            "Faltante",
        }

        estados_reales = set(
            DetalleOrden.objects.values_list(
                "estado_entrega",
                flat=True
            ).distinct()
        )

        estados_invalidos = estados_reales - estados_permitidos

        if not estados_invalidos:
            self.ok("Todos los estados de despacho son válidos.")
        else:
            self.error(
                "Estados de despacho inválidos: "
                + ", ".join(estados_invalidos)
            )

        # ============================================================
        # 7. KARDEX / INVENTARIO
        # ============================================================

        self.stdout.write("")
        self.stdout.write(
            self.style.HTTP_INFO("7. VALIDACIÓN DE INVENTARIO Y KARDEX")
        )
        self.stdout.write("-" * 60)

        movimientos = MovimientoInventario.objects.count()

        entradas = MovimientoInventario.objects.filter(
            tipo_movimiento="ENTRADA_COMPRA"
        )

        salidas = MovimientoInventario.objects.filter(
            tipo_movimiento="SALIDA_VENTA"
        )

        ajustes = MovimientoInventario.objects.filter(
            tipo_movimiento="AJUSTE"
        )

        total_entradas = sum(
            abs(m.cantidad) for m in entradas
        )

        total_salidas = sum(
            abs(m.cantidad) for m in salidas
        )

        total_ajustes = sum(
            m.cantidad for m in ajustes
        )

        self.stdout.write(
            f"Movimientos totales: {movimientos}"
        )
        self.stdout.write(
            f"Movimientos de entrada: {entradas.count()}"
        )
        self.stdout.write(
            f"Movimientos de salida: {salidas.count()}"
        )
        self.stdout.write(
            f"Movimientos de ajuste: {ajustes.count()}"
        )

        self.stdout.write(
            f"Unidades ingresadas: {total_entradas}"
        )

        self.stdout.write(
            f"Unidades despachadas: {total_salidas}"
        )

        self.stdout.write(
            f"Unidades ajustadas: {total_ajustes}"
        )

        # Validar stock producto por producto
        productos_con_error_stock = []

        for producto in Producto.objects.all():

            movimientos_producto = (
                MovimientoInventario.objects
                .filter(producto=producto)
                .order_by("fecha", "id")
            )

            stock_calculado = 0
            stock_anterior_esperado = 0
            error_kardex = False

            for movimiento in movimientos_producto:

                # StockAnterior debe coincidir con el saldo
                # resultante del movimiento anterior.
                if movimiento.stock_anterior != stock_anterior_esperado:
                    error_kardex = True

                # El tipo de movimiento determina el efecto
                # sobre el stock.
                if movimiento.tipo_movimiento == "ENTRADA_COMPRA":
                    stock_calculado += movimiento.cantidad

                elif movimiento.tipo_movimiento == "SALIDA_VENTA":
                    stock_calculado -= movimiento.cantidad

                elif movimiento.tipo_movimiento == "AJUSTE":
                    stock_calculado += movimiento.cantidad

                else:
                    error_kardex = True

                # StockPosterior debe coincidir con el saldo
                # calculado después del movimiento.
                if movimiento.stock_posterior != stock_calculado:
                    error_kardex = True

                stock_anterior_esperado = stock_calculado

            # El stock físico del producto debe coincidir
            # con el saldo final del Kardex.
            if producto.stock_actual != stock_calculado:
                error_kardex = True

            if stock_calculado < 0:
                error_kardex = True

            if error_kardex:
                productos_con_error_stock.append(
                    f"{producto.id} - {producto.nombre}"
                )

        if not productos_con_error_stock:
            self.ok(
                "StockActual coincide con el Kardex "
                "para todos los productos."
            )
        else:
            self.error(
                "Productos con inconsistencias de stock/Kardex: "
                + ", ".join(productos_con_error_stock)
            )

        # Stock negativo
        productos_stock_negativo = Producto.objects.filter(
            stock_actual__lt=0
        ).count()

        if productos_stock_negativo == 0:
            self.ok("No existen productos con stock negativo.")
        else:
            self.error(
                f"Hay {productos_stock_negativo} productos "
                "con stock negativo."
            )

        # ============================================================
        # 8. VALIDACIONES ESPECÍFICAS DE KARDEX
        # ============================================================

        self.stdout.write("")
        self.stdout.write(
            self.style.HTTP_INFO(
                "8. VALIDACIONES ESPECÍFICAS DE MOVIMIENTOS"
            )
        )
        self.stdout.write("-" * 60)

        movimientos_invalidos = []

        for movimiento in MovimientoInventario.objects.all():

            # Toda cantidad de movimiento debe ser mayor que cero.
            if movimiento.cantidad <= 0:
                movimientos_invalidos.append(
                    f"Movimiento {movimiento.id}: "
                    "cantidad no positiva"
                )

            if movimiento.tipo_movimiento == "ENTRADA_COMPRA":
                if movimiento.cantidad <= 0:
                    movimientos_invalidos.append(
                        f"Movimiento {movimiento.id}: "
                        "entrada con cantidad no positiva"
                    )

            elif movimiento.tipo_movimiento == "SALIDA_VENTA":
                if movimiento.cantidad <= 0:
                    movimientos_invalidos.append(
                        f"Movimiento {movimiento.id}: "
                        "salida con cantidad no positiva"
                    )

            elif movimiento.tipo_movimiento == "AJUSTE":
                if movimiento.cantidad <= 0:
                    movimientos_invalidos.append(
                        f"Movimiento {movimiento.id}: "
                        "ajuste con cantidad no positiva"
                    )

            else:
                movimientos_invalidos.append(
                    f"Movimiento {movimiento.id}: "
                    f"tipo de movimiento inválido: "
                    f"{movimiento.tipo_movimiento}"
                )

        if not movimientos_invalidos:
            self.ok(
                "Las cantidades y tipos de movimiento son coherentes."
            )
        else:
            for error in movimientos_invalidos:
                self.error(error)

        # ============================================================
        # 9. INTEGRIDAD REFERENCIAL
        # ============================================================

        self.stdout.write("")
        self.stdout.write(
            self.style.HTTP_INFO("9. VALIDACIÓN DE INTEGRIDAD REFERENCIAL")
        )
        self.stdout.write("-" * 60)

        relaciones_invalidas = 0

        relaciones = [
            (
                "Empleados → Departamentos",
                Empleado.objects.filter(
                    departamento__isnull=True
                ).count(),
            ),
            (
                "Productos → Marcas",
                Producto.objects.filter(
                    marca__isnull=True
                ).count(),
            ),
            (
                "Productos → Proveedores",
                Producto.objects.filter(
                    proveedor__isnull=True
                ).count(),
            ),
            (
                "Proveedores → Países",
                Proveedor.objects.filter(
                    pais__isnull=True
                ).count(),
            ),
            (
                "Compras → Empleados",
                Compra.objects.filter(
                    empleado__isnull=True
                ).count(),
            ),
            (
                "Compras → Proveedores",
                Compra.objects.filter(
                    proveedor__isnull=True
                ).count(),
            ),
            (
                "Ventas → Empleados",
                Venta.objects.filter(
                    empleado__isnull=True
                ).count(),
            ),
            (
                "Ventas → Clientes",
                Venta.objects.filter(
                    cliente__isnull=True
                ).count(),
            ),
        ]

        for nombre, cantidad in relaciones:

            if cantidad == 0:
                self.ok(f"{nombre}: OK")
            else:
                self.error(
                    f"{nombre}: {cantidad} registros inválidos."
                )
                relaciones_invalidas += cantidad

        # ============================================================
        # 10. RESUMEN
        # ============================================================

        self.stdout.write("")
        self.stdout.write("=" * 60)
        self.stdout.write("   RESUMEN DE VALIDACIÓN")
        self.stdout.write("=" * 60)

        if self.errores == 0:
            self.stdout.write(
                self.style.SUCCESS(
                    "✓ VALIDACIÓN COMPLETADA SIN ERRORES"
                )
            )
        else:
            self.stdout.write(
                self.style.ERROR(
                    f"✗ VALIDACIÓN COMPLETADA CON "
                    f"{self.errores} ERROR(ES)"
                )
            )

        if self.advertencias > 0:
            self.stdout.write(
                self.style.WARNING(
                    f"⚠ Advertencias: {self.advertencias}"
                )
            )

        self.stdout.write("=" * 60)
        self.stdout.write("")