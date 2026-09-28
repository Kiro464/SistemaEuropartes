from datetime import datetime, timedelta
from decimal import Decimal
from django.core.management.base import BaseCommand
from django.db import transaction
from django.contrib.auth import get_user_model
from django.core.management import call_command

from apps.movimientos.venta.models import (
    DetalleOrden,
    OrdenDespacho,
    FacturaVenta,
    DetalleVenta,
    Venta,
)
from apps.movimientos.compra.models import (
    FacturaCompra,
    DetalleCompra,
    DetalleSolicitudCompra,
    SolicitudCompra,
    MovimientoInventario,
    Compra,
)
from apps.catalogos.producto.models import Producto
from apps.catalogos.cliente.models import Cliente
from apps.catalogos.proveedor.models import Proveedor
from apps.catalogos.marca.models import Marca
from apps.catalogos.pais.models import Pais
from apps.catalogos.empleado.models import Empleado
from apps.catalogos.departamento.models import Departamento

User = get_user_model()


class Command(BaseCommand):
    help = (
        "Limpia los datos transitorios de desarrollo de EuropartesSystem "
        "sin eliminar usuarios, empleados, departamentos ni roles."
    )

    @transaction.atomic
    def handle(self, *args, **options):

        self.stdout.write(
            self.style.WARNING(
                "ADVERTENCIA: esta operación eliminará los datos "
                "operativos y catálogos actuales."
            )
        )

        confirmacion = input(
            "Escribí 'RECONSTRUIR' para continuar: "
        )

        if confirmacion != "RECONSTRUIR":
            self.stdout.write(
                self.style.ERROR(
                    "Operación cancelada. No se modificaron datos."
                )
            )
            return

        # ============================================================
        # 1. DETALLES DE ÓRDENES DE DESPACHO
        # ============================================================

        cantidad = DetalleOrden.objects.count()
        DetalleOrden.objects.all().delete()

        self.stdout.write(
            f"DetalleOrden eliminados: {cantidad}"
        )

        # ============================================================
        # 2. ÓRDENES DE DESPACHO
        # ============================================================

        cantidad = OrdenDespacho.objects.count()
        OrdenDespacho.objects.all().delete()

        self.stdout.write(
            f"OrdenDespacho eliminadas: {cantidad}"
        )

        # ============================================================
        # 3. FACTURAS DE COMPRA
        # ============================================================

        cantidad = FacturaCompra.objects.count()
        FacturaCompra.objects.all().delete()

        self.stdout.write(
            f"FacturaCompra eliminadas: {cantidad}"
        )

        # ============================================================
        # 4. DETALLES DE COMPRA
        # ============================================================

        cantidad = DetalleCompra.objects.count()
        DetalleCompra.objects.all().delete()

        self.stdout.write(
            f"DetalleCompra eliminados: {cantidad}"
        )

        # ============================================================
        # 5. COMPRA
        # ============================================================
        cantidad = Compra.objects.count()
        Compra.objects.all().delete()
        self.stdout.write(
            f"Compra eliminadas: {cantidad}"
        )

        # ============================================================
        # 6. DETALLES DE SOLICITUD DE COMPRA
        # ============================================================

        cantidad = DetalleSolicitudCompra.objects.count()
        DetalleSolicitudCompra.objects.all().delete()

        self.stdout.write(
            f"DetalleSolicitudCompra eliminados: {cantidad}"
        )

        # ============================================================
        # 7. SOLICITUDES DE COMPRA
        # ============================================================

        cantidad = SolicitudCompra.objects.count()
        SolicitudCompra.objects.all().delete()

        self.stdout.write(
            f"SolicitudCompra eliminadas: {cantidad}"
        )

        # ============================================================
        # 8. FACTURAS DE VENTA
        # ============================================================

        cantidad = FacturaVenta.objects.count()
        FacturaVenta.objects.all().delete()

        self.stdout.write(
            f"FacturaVenta eliminadas: {cantidad}"
        )

        # ============================================================
        # 9. DETALLES DE VENTA
        # ============================================================

        cantidad = DetalleVenta.objects.count()
        DetalleVenta.objects.all().delete()

        self.stdout.write(
            f"DetalleVenta eliminados: {cantidad}"
        )

        # ============================================================
        # 10. VENTAS
        # ============================================================

        cantidad = Venta.objects.count()
        Venta.objects.all().delete()

        self.stdout.write(
            f"Venta eliminadas: {cantidad}"
        )

        # ============================================================
        # 11. MOVIMIENTOS DE INVENTARIO
        # ============================================================

        cantidad = MovimientoInventario.objects.count()
        MovimientoInventario.objects.all().delete()

        self.stdout.write(
            f"MovimientoInventario eliminados: {cantidad}"
        )

        # ============================================================
        # 12. PRODUCTOS
        # ============================================================

        cantidad = Producto.objects.count()
        Producto.objects.all().delete()

        self.stdout.write(
            f"Producto eliminados: {cantidad}"
        )

        # ============================================================
        # 13. CLIENTES
        # ============================================================

        cantidad = Cliente.objects.count()
        Cliente.objects.all().delete()

        self.stdout.write(
            f"Cliente eliminados: {cantidad}"
        )

        # ============================================================
        # 14. PROVEEDORES
        # ============================================================

        cantidad = Proveedor.objects.count()
        Proveedor.objects.all().delete()

        self.stdout.write(
            f"Proveedor eliminados: {cantidad}"
        )

        # ============================================================
        # 15. MARCAS
        # ============================================================

        cantidad = Marca.objects.count()
        Marca.objects.all().delete()

        self.stdout.write(
            f"Marca eliminadas: {cantidad}"
        )

        # ============================================================
        # 16. PAÍSES
        # ============================================================

        cantidad = Pais.objects.count()
        Pais.objects.all().delete()

        self.stdout.write(
            f"Pais eliminados: {cantidad}"
        )

        # ============================================================
        # 17. EMPLEADOS ANTIGUOS
        # ============================================================

        cantidad = Empleado.objects.count()
        Empleado.objects.all().delete()

        self.stdout.write(
            f"Empleado eliminados: {cantidad}"
        )

        # ============================================================
        # 18. DEPARTAMENTOS ANTIGUOS
        # ============================================================

        cantidad = Departamento.objects.count()
        Departamento.objects.all().delete()

        self.stdout.write(
            f"Departamento eliminados: {cantidad}"
        )

        # ============================================================
        # 19. USUARIOS ANTIGUOS
        # ============================================================

        usuarios_permitidos = {
            "alejandro.jimenez",
            "carlos.martinez",
            "laura.rodriguez",
            "maria.garcia",
            "jose.lopez",
            "andrea.mendoza",
            "daniel.torres",
            "sofia.herrera",
            "ricardo.castillo",
            "gabriela.martinez",
            "fernando.espinoza",
            "paola.vargas",
        }

        usuarios_antiguos = User.objects.exclude(
            username__in=usuarios_permitidos
        )

        cantidad = usuarios_antiguos.count()

        usuarios_antiguos.delete()

        self.stdout.write(
            f"Usuarios antiguos eliminados: {cantidad}"
        )

        # ============================================================
        # 20. RECREAR USUARIOS, DEPARTAMENTOS Y EMPLEADOS
        # ============================================================

        call_command("crear_usuarios_iniciales")

        # ============================================================
        # 1B.8.3 - GENERACIÓN DE CATÁLOGOS
        # ============================================================

        # ------------------------------------------------------------
        # 1. PAÍSES
        # ------------------------------------------------------------
        paises_data = [
            "Nicaragua",
            "Costa Rica",
            "Honduras",
            "El Salvador",
            "Guatemala",
            "Panamá",
        ]

        paises = []

        for nombre in paises_data:
            pais = Pais.objects.create(
                nombre=nombre
            )
            paises.append(pais)

        self.stdout.write(
            self.style.SUCCESS(
                f"✓ Países creados: {len(paises)}"
            )
        )

        # ------------------------------------------------------------
        # 2. MARCAS
        # ------------------------------------------------------------
        marcas_data = [
            "AutoMax",
            "MotorPlus",
            "Frenotec",
            "PartesPro",
            "CarTech",
            "MecánicaX",
            "Autoforce",
            "RuedaPro",
            "DieselTech",
            "TransAuto",
        ]

        marcas = []

        for nombre in marcas_data:
            marca = Marca.objects.create(
                nombre=nombre
            )
            marcas.append(marca)

        self.stdout.write(
            self.style.SUCCESS(
                f"✓ Marcas creadas: {len(marcas)}"
            )
        )

        # ------------------------------------------------------------
        # 3. PROVEEDORES
        # ------------------------------------------------------------
        proveedores_data = [
            {
                "nombre": "Repuestos Centroamérica S.A.",
                "pais": paises[0],
                "telefono": "+505 2250-1001",
                "email": "ventas@repuestoscentroamerica.com",
                "sitio_web": "https://www.repuestoscentroamerica.com",
            },
            {
                "nombre": "Importadora Motor Nica",
                "pais": paises[0],
                "telefono": "+505 2250-1002",
                "email": "contacto@motornica.com",
                "sitio_web": "https://www.motornica.com",
            },
            {
                "nombre": "Autopartes del Pacífico",
                "pais": paises[0],
                "telefono": "+505 2250-1003",
                "email": "ventas@autopartespacifico.com",
                "sitio_web": "https://www.autopartespacifico.com",
            },
            {
                "nombre": "Distribuidora La Centroamericana",
                "pais": paises[1],
                "telefono": "+506 2200-2001",
                "email": "ventas@centroamericana.cr",
                "sitio_web": "https://www.centroamericana.cr",
            },
            {
                "nombre": "Costa Rica Auto Supply",
                "pais": paises[1],
                "telefono": "+506 2200-2002",
                "email": "contacto@autosupply.cr",
                "sitio_web": "https://www.autosupply.cr",
            },
            {
                "nombre": "Honduras Motor Parts",
                "pais": paises[2],
                "telefono": "+504 2200-3001",
                "email": "ventas@motorparts.hn",
                "sitio_web": "https://www.motorparts.hn",
            },
            {
                "nombre": "Repuestos del Valle",
                "pais": paises[2],
                "telefono": "+504 2200-3002",
                "email": "contacto@repuestosvalle.hn",
                "sitio_web": "https://www.repuestosvalle.hn",
            },
            {
                "nombre": "Importadora El Salvador Auto",
                "pais": paises[3],
                "telefono": "+503 2200-4001",
                "email": "ventas@elsalvadorauto.sv",
                "sitio_web": "https://www.elsalvadorauto.sv",
            },
            {
                "nombre": "Guatemala Repuestos GT",
                "pais": paises[4],
                "telefono": "+502 2200-5001",
                "email": "ventas@repuestosgt.gt",
                "sitio_web": "https://www.repuestosgt.gt",
            },
            {
                "nombre": "Autopartes Chapinas",
                "pais": paises[4],
                "telefono": "+502 2200-5002",
                "email": "contacto@autoparteschapinas.gt",
                "sitio_web": "https://www.autoparteschapinas.gt",
            },
            {
                "nombre": "Panamá Auto Import",
                "pais": paises[5],
                "telefono": "+507 2200-6001",
                "email": "ventas@panamaauto.pa",
                "sitio_web": "https://www.panamaauto.pa",
            },
            {
                "nombre": "Canal Motor Supply",
                "pais": paises[5],
                "telefono": "+507 2200-6002",
                "email": "contacto@canalmotor.pa",
                "sitio_web": "https://www.canalmotor.pa",
            },
        ]

        proveedores = []

        for data in proveedores_data:
            proveedor = Proveedor.objects.create(
                nombre=data["nombre"],
                pais=data["pais"],
                telefono=data["telefono"],
                email=data["email"],
                sitio_web=data["sitio_web"],
                estado=True,
            )
            proveedores.append(proveedor)

        self.stdout.write(
            self.style.SUCCESS(
                f"✓ Proveedores creados: {len(proveedores)}"
            )
        )

        # ------------------------------------------------------------
        # 4. PRODUCTOS
        # ------------------------------------------------------------
        productos_data = [
            # Filtros
            ("Filtro de aceite", "Filtros", "Filtro de aceite para motor"),
            ("Filtro de aire", "Filtros", "Filtro de aire para motor"),
            ("Filtro de combustible", "Filtros", "Filtro de combustible"),
            ("Filtro de cabina", "Filtros", "Filtro de aire acondicionado de cabina"),
            ("Filtro hidráulico", "Filtros", "Filtro para sistemas hidráulicos"),

            # Frenos
            ("Pastillas de freno delanteras", "Frenos", "Juego de pastillas de freno delanteras"),
            ("Pastillas de freno traseras", "Frenos", "Juego de pastillas de freno traseras"),
            ("Disco de freno delantero", "Frenos", "Disco de freno delantero"),
            ("Disco de freno trasero", "Frenos", "Disco de freno trasero"),
            ("Kit de freno de emergencia", "Frenos", "Kit para sistema de freno de emergencia"),

            # Motor
            ("Bujías de encendido", "Motor", "Juego de bujías de encendido"),
            ("Correa de distribución", "Motor", "Correa para sistema de distribución"),
            ("Tensor de correa", "Motor", "Tensor para correa de distribución"),
            ("Bomba de agua", "Motor", "Bomba de agua para sistema de refrigeración"),
            ("Bomba de aceite", "Motor", "Bomba de aceite para motor"),
            ("Empaque de culata", "Motor", "Empaque para culata de motor"),
            ("Sensor de temperatura", "Motor", "Sensor de temperatura del motor"),

            # Suspensión
            ("Amortiguador delantero", "Suspensión", "Amortiguador delantero"),
            ("Amortiguador trasero", "Suspensión", "Amortiguador trasero"),
            ("Resorte de suspensión", "Suspensión", "Resorte para sistema de suspensión"),
            ("Rótula de suspensión", "Suspensión", "Rótula para suspensión"),
            ("Terminal de dirección", "Suspensión", "Terminal para sistema de dirección"),
            ("Buje de suspensión", "Suspensión", "Buje para sistema de suspensión"),

            # Transmisión
            ("Kit de embrague", "Transmisión", "Kit completo de embrague"),
            ("Disco de embrague", "Transmisión", "Disco para sistema de embrague"),
            ("Collarín de embrague", "Transmisión", "Collarín para sistema de embrague"),
            ("Aceite para transmisión", "Transmisión", "Lubricante para transmisión"),
            ("Retén de transmisión", "Transmisión", "Retén para sistema de transmisión"),

            # Eléctrico
            ("Batería automotriz", "Eléctrico", "Batería para vehículo"),
            ("Alternador", "Eléctrico", "Alternador para vehículo"),
            ("Motor de arranque", "Eléctrico", "Motor de arranque"),
            ("Bobina de encendido", "Eléctrico", "Bobina para sistema de encendido"),
            ("Sensor de oxígeno", "Eléctrico", "Sensor de oxígeno"),
            ("Fusibles automotrices", "Eléctrico", "Juego de fusibles automotrices"),

            # Refrigeración
            ("Radiador", "Refrigeración", "Radiador para sistema de refrigeración"),
            ("Ventilador de radiador", "Refrigeración", "Ventilador para radiador"),
            ("Manguera de radiador", "Refrigeración", "Manguera para sistema de refrigeración"),
            ("Refrigerante automotriz", "Refrigeración", "Refrigerante para motor"),

            # Lubricantes
            ("Aceite de motor 5W-30", "Lubricantes", "Aceite sintético para motor 5W-30"),
            ("Aceite de motor 10W-40", "Lubricantes", "Aceite para motor 10W-40"),
        ]

        rangos_precio = {
            "Filtros": (8, 35),
            "Frenos": (25, 120),
            "Motor": (20, 250),
            "Suspensión": (30, 180),
            "Transmisión": (25, 350),
            "Eléctrico": (15, 300),
            "Refrigeración": (20, 180),
            "Lubricantes": (15, 55),
        }

        productos = []

        for i, (nombre, categoria, descripcion) in enumerate(productos_data):
            minimo, maximo = rangos_precio[categoria]

            # Precio determinístico y reproducible dentro del rango
            rango = maximo - minimo
            precio = minimo + ((i * 17) % (rango + 1))

            marca = marcas[i % len(marcas)]

            # Distribución de proveedores no completamente secuencial
            proveedor = proveedores[(i * 3) % len(proveedores)]

            producto = Producto.objects.create(
                nombre=nombre,
                marca=marca,
                descripcion=descripcion,
                precio_unitario=precio,
                stock_actual=0,
                proveedor=proveedor,
                estado=True,
            )

            productos.append(producto)

        self.stdout.write(
            self.style.SUCCESS(
                f"✓ Productos creados: {len(productos)}"
            )
        )

        # ------------------------------------------------------------
        # 5. CLIENTES
        # ------------------------------------------------------------
        nombres = [
            "Carlos",
            "María",
            "José",
            "Ana",
            "Luis",
            "Andrea",
            "Miguel",
            "Gabriela",
            "Fernando",
            "Sofía",
            "Ricardo",
            "Daniel",
            "Laura",
            "Jorge",
            "Valeria",
            "Alejandro",
            "Paola",
            "Roberto",
            "Karla",
            "Oscar",
            "Marvin",
            "Claudia",
            "Diego",
            "Patricia",
            "Enrique",
            "Lucía",
            "Manuel",
            "Verónica",
            "Héctor",
            "Natalia",
        ]

        apellidos = [
            "Martínez",
            "Rodríguez",
            "López",
            "García",
            "Hernández",
            "Mendoza",
            "Torres",
            "Castillo",
            "Vargas",
            "Espinoza",
            "Ramírez",
            "Pérez",
            "González",
            "Díaz",
            "Morales",
            "Jiménez",
            "Sánchez",
            "Rivas",
            "Cruz",
            "Flores",
            "Gutiérrez",
            "Ruiz",
            "Navarro",
            "Solis",
            "Romero",
            "Pineda",
            "Aguilar",
            "Bermúdez",
            "Zamora",
            "Chávez",
        ]

        departamentos_cliente = [
            "Managua",
            "Masaya",
            "Granada",
            "León",
            "Chinandega",
            "Estelí",
            "Matagalpa",
            "Jinotega",
            "Rivas",
            "Carazo",
        ]

        clientes = []

        for i in range(30):
            nombre = nombres[i]
            apellido = apellidos[i]

            cliente = Cliente.objects.create(
                nombres=nombre,
                apellidos=apellido,
                telefono=f"+505 8{5000000 + i:07d}",
                direccion=(
                    f"Barrio {i + 1}, "
                    f"{departamentos_cliente[i % len(departamentos_cliente)]}, "
                    f"Nicaragua"
                ),
                correo=f"cliente{i + 1:02d}@example.com",
                estado=True,
            )

            clientes.append(cliente)

        self.stdout.write(
            self.style.SUCCESS(
                f"✓ Clientes creados: {len(clientes)}"
            )
        )

        # ============================================================
        # RESUMEN DE CATÁLOGOS
        # ============================================================
        self.stdout.write("")
        self.stdout.write(
            self.style.SUCCESS(
                "=============================================="
            )
        )
        self.stdout.write(
            self.style.SUCCESS(
                "   CATÁLOGOS GENERADOS CORRECTAMENTE"
            )
        )
        self.stdout.write(
            self.style.SUCCESS(
                "=============================================="
            )
        )
        self.stdout.write(
            f"  Países:      {Pais.objects.count()}"
        )
        self.stdout.write(
            f"  Marcas:      {Marca.objects.count()}"
        )
        self.stdout.write(
            f"  Proveedores: {Proveedor.objects.count()}"
        )
        self.stdout.write(
            f"  Productos:   {Producto.objects.count()}"
        )
        self.stdout.write(
            f"  Clientes:    {Cliente.objects.count()}"
        )
        self.stdout.write(
            self.style.SUCCESS(
                "=============================================="
            )
        )

        # ============================================================
        # 1B.8.4 - GENERACIÓN DE OPERACIONES HISTÓRICAS
        # ============================================================

        # ============================================================
        # CONFIGURACIÓN GENERAL
        # ============================================================

        ahora = datetime.now()

        # Empleados operativos reconstruidos para el entorno de desarrollo.
        empleados = list(
            Empleado.objects.select_related("departamento").all()
        )

        if not empleados:
            raise ValueError(
                "No existen empleados. Deben existir los 12 empleados "
                "generados por crear_usuarios_iniciales."
            )

        def obtener_empleado_por_cargo(cargo):
            empleado = next(
                (
                    e for e in empleados
                    if e.cargo == cargo
                ),
                None
            )

            if empleado is None:
                raise ValueError(
                    f"No se encontró un empleado con cargo: {cargo}"
                )

            return empleado

        # Empleados principales para las operaciones.
        coordinador_logistica = obtener_empleado_por_cargo(
            "Coordinador de Logística"
        )

        oficial_logistica = obtener_empleado_por_cargo(
            "Oficial de Logística"
        )

        asesores_financieros = [
            e for e in empleados
            if e.cargo == "Asesor Financiero"
        ]

        vendedores = [
            e for e in empleados
            if e.cargo == "Vendedor"
        ]

        if not asesores_financieros:
            raise ValueError(
                "No se encontraron asesores financieros."
            )

        if not vendedores:
            raise ValueError(
                "No se encontraron vendedores."
            )

        # ============================================================
        # 1. SOLICITUDES DE COMPRA
        # ============================================================

        # 20 solicitudes:
        # 10 ACEPTADAS
        # 6 RECHAZADAS
        # 4 PENDIENTES

        solicitudes = []

        estados_solicitud = (
            ["ACEPTADA"] * 10
            + ["RECHAZADA"] * 6
            + ["PENDIENTE"] * 4
        )

        for i, estado in enumerate(estados_solicitud):
            vendedor = vendedores[i % len(vendedores)]

            fecha_solicitud = ahora - timedelta(
                days=60 - i
            )

            solicitud = SolicitudCompra.objects.create(
                empleado=vendedor,
                fecha_solicitud=fecha_solicitud,
                estado=estado,
                observaciones=(
                    "Solicitud generada como parte del historial "
                    "de operaciones de desarrollo."
                ),
            )

            solicitudes.append(solicitud)

            # Cada solicitud contiene entre 1 y 3 productos.
            cantidad_detalles = 1 + (i % 3)

            for j in range(cantidad_detalles):
                producto = productos[
                    (i * 2 + j) % len(productos)
                ]

                cantidad = 5 + ((i + j) % 16)

                DetalleSolicitudCompra.objects.create(
                    solicitud=solicitud,
                    producto=producto,
                    cantidad=cantidad,
                    observaciones=(
                        "Producto solicitado para reposición "
                        "de inventario."
                    ),
                )

        self.stdout.write(
            self.style.SUCCESS(
                f"✓ Solicitudes de compra creadas: {len(solicitudes)}"
            )
        )

        # ============================================================
        # 2. ÓRDENES DE COMPRA
        # ============================================================

        # Las primeras 8 órdenes provienen de solicitudes aceptadas.
        # Las últimas 4 son órdenes directas sin solicitud previa.

        estados_compra = (
            ["REALIZADA"] * 2
            + ["EN_PROCESO"] * 2
            + ["PROCESADA"] * 3
            + ["ENTREGADA_AL_ALMACEN"] * 5
        )

        solicitudes_aceptadas = [
            solicitud
            for solicitud in solicitudes
            if solicitud.estado == "ACEPTADA"
        ]

        compras = []

        # ------------------------------------------------------------
        # 2.1 Órdenes derivadas de solicitudes aceptadas
        # ------------------------------------------------------------

        for i in range(8):
            solicitud = solicitudes_aceptadas[i]

            proveedor = proveedores[
                i % len(proveedores)
            ]

            empleado_responsable = coordinador_logistica

            fecha_compra = ahora - timedelta(
                days=45 - i
            )

            compra = Compra.objects.create(
                fecha=fecha_compra,
                empleado=empleado_responsable,
                proveedor=proveedor,
                solicitud=solicitud,
                estado=estados_compra[i],
            )

            compras.append(compra)

            # Copiamos los productos y cantidades de la solicitud
            # aceptada hacia la orden de compra.
            detalles_solicitud = list(
                solicitud.detalles.all()
            )

            for detalle_solicitud in detalles_solicitud:
                producto = detalle_solicitud.producto

                # El costo de compra es inferior al precio de venta.
                precio_venta = Decimal(
                    producto.precio_unitario
                )

                porcentaje_descuento = Decimal(
                    20 + ((i + producto.id) % 16)
                )

                precio_compra = (
                    precio_venta
                    * (
                        Decimal("100")
                        - porcentaje_descuento
                    )
                    / Decimal("100")
                ).quantize(
                    Decimal("0.01")
                )

                # Evitar costos excesivamente bajos.
                if precio_compra < Decimal("5.00"):
                    precio_compra = Decimal("5.00")

                DetalleCompra.objects.create(
                    compra=compra,
                    producto=producto,
                    cantidad=detalle_solicitud.cantidad,
                    costo_unitario=precio_compra,
                )

        # ------------------------------------------------------------
        # 2.2 Órdenes directas
        # ------------------------------------------------------------

        for i in range(4):
            indice_compra = 8 + i

            proveedor = proveedores[
                (indice_compra * 2) % len(proveedores)
            ]

            fecha_compra = ahora - timedelta(
                days=37 - i
            )

            compra = Compra.objects.create(
                fecha=fecha_compra,
                empleado=coordinador_logistica,
                proveedor=proveedor,
                solicitud=None,
                estado=estados_compra[indice_compra],
            )

            compras.append(compra)

            # Las órdenes directas contienen entre 2 y 4 productos.
            cantidad_detalles = 2 + (i % 3)

            for j in range(cantidad_detalles):
                producto = productos[
                    (indice_compra * 3 + j) % len(productos)
                ]

                cantidad = 8 + ((i + j) % 13)

                precio_venta = Decimal(
                    producto.precio_unitario
                )

                porcentaje_descuento = Decimal(
                    22 + ((indice_compra + j) % 14)
                )

                precio_compra = (
                    precio_venta
                    * (
                        Decimal("100")
                        - porcentaje_descuento
                    )
                    / Decimal("100")
                ).quantize(
                    Decimal("0.01")
                )

                if precio_compra < Decimal("5.00"):
                    precio_compra = Decimal("5.00")

                DetalleCompra.objects.create(
                    compra=compra,
                    producto=producto,
                    cantidad=cantidad,
                    costo_unitario=precio_compra,
                )

        self.stdout.write(
            self.style.SUCCESS(
                f"✓ Órdenes de compra creadas: {len(compras)}"
            )
        )

        # ============================================================
        # 3. FACTURAS DE COMPRA
        # ============================================================

        # Solo las 5 compras que llegaron al almacén tienen
        # factura de compra.
        compras_entregadas = [
            compra
            for compra in compras
            if compra.estado == "ENTREGADA_AL_ALMACEN"
        ]

        facturas_compra = []

        for i, compra in enumerate(compras_entregadas):
            detalles = list(
                compra.detalles.all()
            )

            subtotal = sum(
                (
                    Decimal(detalle.cantidad)
                    * Decimal(detalle.costo_unitario)
                    for detalle in detalles
                ),
                Decimal("0.00")
            )

            iva = (
                subtotal
                * Decimal("0.15")
            ).quantize(
                Decimal("0.01")
            )

            total_con_iva = (
                subtotal + iva
            ).quantize(
                Decimal("0.01")
            )

            factura = FacturaCompra.objects.create(
                fecha_emision=ahora - timedelta(
                    days=18 - i
                ),
                total=subtotal,
                iva=iva,
                total_con_iva=total_con_iva,
                compra=compra,
            )

            facturas_compra.append(factura)

        self.stdout.write(
            self.style.SUCCESS(
                f"✓ Facturas de compra creadas: "
                f"{len(facturas_compra)}"
            )
        )

        # ============================================================
        # 4. VENTAS
        # ============================================================

        ventas = []
        facturas_venta = []
        ordenes_despacho = []

        # ------------------------------------------------------------
        # STOCK DISPONIBLE PARA LAS VENTAS HISTÓRICAS
        # ------------------------------------------------------------
        #
        # Solo las compras ENTREGADA_AL_ALMACEN representan mercancía
        # físicamente disponible antes de realizar las ventas.
        #
        # Importante:
        # Este stock se utiliza para GENERAR operaciones coherentes.
        # 1B.8.5 no tendrá que corregir posteriormente las ventas.
        # ------------------------------------------------------------

        stock_disponible = {
            producto.id: 0
            for producto in productos
        }

        compras_recibidas = (
            Compra.objects
            .filter(
                estado=Compra.ESTADO_ENTREGADA_AL_ALMACEN
            )
            .prefetch_related("detalles")
            .order_by("fecha", "id")
        )

        for compra in compras_recibidas:
            for detalle in compra.detalles.all():
                stock_disponible[detalle.producto_id] += detalle.cantidad

        productos_con_stock = [
            producto
            for producto in productos
            if stock_disponible.get(producto.id, 0) > 0
        ]

        if not productos_con_stock:
            raise ValueError(
                "No existe stock disponible para generar las ventas históricas."
            )


        # ------------------------------------------------------------
        # FUNCIÓN PARA OBTENER UN PRODUCTO CON STOCK
        # ------------------------------------------------------------

        def obtener_producto_disponible(
            cantidad_minima=1,
            posicion_inicial=0,
        ):
            total_productos = len(productos_con_stock)

            for desplazamiento in range(total_productos):
                posicion = (
                    posicion_inicial + desplazamiento
                ) % total_productos

                producto = productos_con_stock[posicion]

                if stock_disponible.get(producto.id, 0) >= cantidad_minima:
                    return producto

            return None


        # ------------------------------------------------------------
        # PLAN DE DESPACHOS
        # ------------------------------------------------------------
        #
        # Cada elemento tendrá:
        #
        # {
        #     "producto_id": ...,
        #     "cantidad_venta": ...,
        #     "cantidad_despachada": ...,
        #     "estado": ...
        # }
        #
        # La cantidad de venta representa lo solicitado/facturado.
        # La cantidad despachada representa lo efectivamente entregado.
        #
        # Por tanto:
        #
        # Venta completa:
        #     venta = 2
        #     despacho = 2
        #
        # Venta parcial:
        #     venta = 2
        #     despacho = 1
        #
        # Venta pendiente:
        #     venta = 2
        #     despacho = 0
        #
        # La venta NUNCA se modifica posteriormente.
        # ------------------------------------------------------------

        plan_despachos = []


        # ============================================================
        # 4.1 GENERACIÓN DE LAS 40 VENTAS
        # ============================================================

        for i in range(40):

            vendedor = vendedores[
                i % len(vendedores)
            ]

            cliente = clientes[
                i % len(clientes)
            ]

            fecha_venta = ahora - timedelta(
                days=30 - (i % 30)
            )

            venta = Venta.objects.create(
                empleado=vendedor,
                cliente=cliente,
            )

            ventas.append(venta)

            cantidad_detalles = 1 + (i % 3)

            subtotal = Decimal("0.00")

            plan_venta = []

            for j in range(cantidad_detalles):

                # ----------------------------------------------------
                # CASOS 1-30: VENTAS COMPLETAMENTE DESPACHABLES
                # ----------------------------------------------------

                if i < 30:

                    cantidad = 1 + ((i + j) % 2)

                    producto = obtener_producto_disponible(
                        cantidad_minima=cantidad,
                        posicion_inicial=(i * 3 + j)
                        % len(productos_con_stock),
                    )

                    if producto is None:
                        raise ValueError(
                            "No existe suficiente stock para generar "
                            f"la venta histórica {i + 1}."
                        )

                    cantidad_despachada = cantidad
                    estado_entrega = "Entregado"

                    stock_disponible[producto.id] -= (
                        cantidad_despachada
                    )

                # ----------------------------------------------------
                # CASOS 31-35: VENTAS PENDIENTES
                # ----------------------------------------------------
                #
                # Estas ventas representan pedidos que todavía no
                # pueden ser atendidos.
                #
                # La venta conserva su cantidad solicitada.
                # El despacho registra 0 unidades entregadas.
                # ----------------------------------------------------

                elif i < 35:

                    # Buscamos un producto con poco stock disponible.
                    producto = min(
                        productos_con_stock,
                        key=lambda p: stock_disponible.get(
                            p.id,
                            0,
                        ),
                    )

                    stock_producto = stock_disponible.get(
                        producto.id,
                        0,
                    )

                    cantidad = stock_producto + 1

                    cantidad_despachada = 0
                    estado_entrega = "Pendiente"

                # ----------------------------------------------------
                # CASOS 36-40: VENTAS PARCIALMENTE DESPACHADAS
                # ----------------------------------------------------
                #
                # El primer detalle de cada venta se genera con una
                # cantidad superior a la disponible.
                #
                # Se entrega únicamente la cantidad disponible.
                # ----------------------------------------------------

                else:

                    if j == 0:

                        producto = max(
                            productos_con_stock,
                            key=lambda p: stock_disponible.get(
                                p.id,
                                0,
                            ),
                        )

                        disponible = stock_disponible.get(
                            producto.id,
                            0,
                        )

                        if disponible <= 0:
                            # Si no queda stock en ningún producto,
                            # esta operación no puede ser parcial.
                            # Utilizamos el siguiente producto con
                            # disponibilidad.
                            producto = obtener_producto_disponible(
                                cantidad_minima=1,
                                posicion_inicial=i,
                            )

                            if producto is None:
                                raise ValueError(
                                    "No existe stock para generar "
                                    "una venta parcialmente entregada."
                                )

                            disponible = stock_disponible[
                                producto.id
                            ]

                        cantidad = disponible + 1
                        cantidad_despachada = disponible

                        if cantidad_despachada <= 0:
                            estado_entrega = "Pendiente"
                        else:
                            estado_entrega = "Entregado"

                        stock_disponible[producto.id] = 0

                    else:

                        # Los detalles adicionales permanecen
                        # completamente pendientes si no hay stock.
                        producto = min(
                            productos_con_stock,
                            key=lambda p: stock_disponible.get(
                                p.id,
                                0,
                            ),
                        )

                        disponible = stock_disponible.get(
                            producto.id,
                            0,
                        )

                        cantidad = disponible + 1
                        cantidad_despachada = 0
                        estado_entrega = "Pendiente"

                # ----------------------------------------------------
                # CREAR DETALLE DE VENTA
                # ----------------------------------------------------

                precio_unitario = Decimal(
                    producto.precio_unitario
                )

                subtotal += (
                    Decimal(cantidad)
                    * precio_unitario
                )

                DetalleVenta.objects.create(
                    venta=venta,
                    producto=producto,
                    cantidad=cantidad,
                    precio_unitario=precio_unitario,
                )

                plan_venta.append(
                    {
                        "producto_id": producto.id,
                        "cantidad_venta": cantidad,
                        "cantidad_despachada": cantidad_despachada,
                        "estado_entrega": estado_entrega,
                    }
                )

            # --------------------------------------------------------
            # FACTURA DE VENTA
            # --------------------------------------------------------

            subtotal = subtotal.quantize(
                Decimal("0.01")
            )

            iva = (
                subtotal
                * Decimal("0.15")
            ).quantize(
                Decimal("0.01")
            )

            total_con_iva = (
                subtotal + iva
            ).quantize(
                Decimal("0.01")
            )

            factura_venta = FacturaVenta.objects.create(
                total=subtotal,
                iva=iva,
                total_con_iva=total_con_iva,
                venta=venta,
            )

            facturas_venta.append(
                factura_venta
            )

            plan_despachos.append(
                plan_venta
            )


        self.stdout.write(
            self.style.SUCCESS(
                f"✓ Ventas creadas: {len(ventas)}"
            )
        )

        self.stdout.write(
            self.style.SUCCESS(
                f"✓ Facturas de venta creadas: "
                f"{len(facturas_venta)}"
            )
        )


        # ============================================================
        # 5. ÓRDENES DE DESPACHO
        # ============================================================

        # ------------------------------------------------------------
        # 30 completamente entregadas
        # 5 pendientes
        # 5 parcialmente entregadas
        #
        # Las cantidades ya fueron determinadas durante la creación
        # de las ventas.
        #
        # IMPORTANTE:
        # Este bloque NO recalcula ni modifica las cantidades de venta.
        # ------------------------------------------------------------

        for i, factura in enumerate(facturas_venta):

            if i < 30:
                estado_orden = True

            else:
                estado_orden = False

            fecha_despacho = ahora - timedelta(
                days=15 - (i % 15)
            )

            orden = OrdenDespacho.objects.create(
                fecha_generacion=fecha_despacho,
                estado=estado_orden,
                factura_venta=factura,
                empleado_responsable=oficial_logistica,
                fecha_registro=fecha_despacho,
            )

            ordenes_despacho.append(orden)

            plan_venta = plan_despachos[i]

            for detalle_plan in plan_venta:

                DetalleOrden.objects.create(
                    orden=orden,
                    producto_id=detalle_plan["producto_id"],
                    cantidad=detalle_plan[
                        "cantidad_despachada"
                    ],
                    estado_entrega=detalle_plan[
                        "estado_entrega"
                    ],
                    fecha_registro=fecha_despacho,
                )


        self.stdout.write(
            self.style.SUCCESS(
                f"✓ Órdenes de despacho creadas: "
                f"{len(ordenes_despacho)}"
            )
        )

        # ============================================================
        # 6. AJUSTE DE FECHAS HISTÓRICAS
        # ============================================================
        #
        # Algunos modelos utilizan auto_now_add. Después de crear
        # los registros, ajustamos sus fechas para que la información
        # tenga una secuencia histórica coherente.
        #
        # No se modifica la fecha de registro original del catálogo.

        for i, venta in enumerate(ventas):
            fecha = ahora - timedelta(
                days=30 - (i % 30)
            )

            Venta.objects.filter(
                pk=venta.pk
            ).update(
                fecha=fecha,
                fecha_registro=fecha,
            )

        for i, factura in enumerate(facturas_venta):
            fecha = ahora - timedelta(
                days=30 - (i % 30)
            )

            FacturaVenta.objects.filter(
                pk=factura.pk
            ).update(
                fecha_emision=fecha,
                fecha_registro=fecha,
            )

        for i, factura in enumerate(facturas_compra):
            fecha = ahora - timedelta(
                days=18 - i
            )

            FacturaCompra.objects.filter(
                pk=factura.pk
            ).update(
                fecha_emision=fecha,
                fecha_registro=fecha,
            )

        # ============================================================
        # RESUMEN DE OPERACIONES
        # ============================================================

        self.stdout.write("")
        self.stdout.write(
            self.style.SUCCESS(
                "=============================================="
            )
        )
        self.stdout.write(
            self.style.SUCCESS(
                "   OPERACIONES HISTÓRICAS GENERADAS"
            )
        )
        self.stdout.write(
            self.style.SUCCESS(
                "=============================================="
            )
        )

        self.stdout.write(
            f"  Solicitudes de compra: "
            f"{SolicitudCompra.objects.count()}"
        )

        self.stdout.write(
            f"    Aceptadas: "
            f"{SolicitudCompra.objects.filter(estado='ACEPTADA').count()}"
        )

        self.stdout.write(
            f"    Rechazadas: "
            f"{SolicitudCompra.objects.filter(estado='RECHAZADA').count()}"
        )

        self.stdout.write(
            f"    Pendientes: "
            f"{SolicitudCompra.objects.filter(estado='PENDIENTE').count()}"
        )

        self.stdout.write(
            f"  Órdenes de compra: "
            f"{Compra.objects.count()}"
        )

        self.stdout.write(
            f"    REALIZADA: "
            f"{Compra.objects.filter(estado='REALIZADA').count()}"
        )

        self.stdout.write(
            f"    EN_PROCESO: "
            f"{Compra.objects.filter(estado='EN_PROCESO').count()}"
        )

        self.stdout.write(
            f"    PROCESADA: "
            f"{Compra.objects.filter(estado='PROCESADA').count()}"
        )

        self.stdout.write(
            f"    ENTREGADA_AL_ALMACEN: "
            f"{Compra.objects.filter(estado='ENTREGADA_AL_ALMACEN').count()}"
        )

        self.stdout.write(
            f"  Facturas de compra: "
            f"{FacturaCompra.objects.count()}"
        )

        self.stdout.write(
            f"  Ventas: "
            f"{Venta.objects.count()}"
        )

        self.stdout.write(
            f"  Facturas de venta: "
            f"{FacturaVenta.objects.count()}"
        )

        self.stdout.write(
            f"  Órdenes de despacho: "
            f"{OrdenDespacho.objects.count()}"
        )

        self.stdout.write(
            f"  Detalles de despacho: "
            f"{DetalleOrden.objects.count()}"
        )

        self.stdout.write(
            self.style.SUCCESS(
                "=============================================="
            )
        )

        # ============================================================
        # 1B.8.5 - RECONSTRUCCIÓN DE STOCK Y KARDEX
        # ============================================================

        self.stdout.write("")
        self.stdout.write(
            self.style.SUCCESS(
                "=============================================="
            )
        )
        self.stdout.write(
            self.style.SUCCESS(
                "   RECONSTRUCCIÓN DE STOCK Y KARDEX"
            )
        )
        self.stdout.write(
            self.style.SUCCESS(
                "=============================================="
            )
        )

        # ------------------------------------------------------------
        # 1. REINICIAR STOCK
        # ------------------------------------------------------------
        #
        # Todos los productos fueron creados con StockActual = 0.
        # Se mantiene ese valor mientras reconstruimos el inventario
        # a partir de las operaciones reales.
        # ------------------------------------------------------------

        Producto.objects.all().update(
            stock_actual=0
        )

        # ------------------------------------------------------------
        # 2. OBTENER COMPRAS EFECTIVAMENTE RECIBIDAS
        # ------------------------------------------------------------
        #
        # Únicamente las compras ENTREGADA_AL_ALMACEN representan
        # una entrada física de mercancía.
        # ------------------------------------------------------------

        compras_recibidas = (
            Compra.objects
            .filter(
                estado=Compra.ESTADO_ENTREGADA_AL_ALMACEN
            )
            .prefetch_related("detalles")
            .order_by("fecha", "id")
        )

        # ------------------------------------------------------------
        # 3. CONSTRUIR INVENTARIO DISPONIBLE
        # ------------------------------------------------------------
        #
        # Este diccionario representa temporalmente el stock físico
        # disponible de cada producto.
        # ------------------------------------------------------------

        stock_disponible = {
            producto.id: 0
            for producto in productos
        }

        entradas_generadas = 0
        cantidad_entrada_total = 0

        for compra in compras_recibidas:

            for detalle in compra.detalles.all():

                producto_id = detalle.producto_id
                cantidad = detalle.cantidad

                stock_disponible[producto_id] += cantidad

                entradas_generadas += 1
                cantidad_entrada_total += cantidad

        self.stdout.write(
            self.style.SUCCESS(
                f"✓ Inventario físico proveniente de compras: "
                f"{cantidad_entrada_total} unidades"
            )
        )

        # ------------------------------------------------------------
        # 4. VALIDAR DESPACHOS HISTÓRICOS
        # ------------------------------------------------------------
        #
        # Las cantidades de los despachos ya fueron determinadas
        # durante 1B.8.4.
        #
        # En esta fase NO se modifican ventas ni despachos.
        #
        # Únicamente verificamos que:
        #
        #   1. Las cantidades despachadas no superen las cantidades
        #      vendidas.
        #   2. Los estados sean coherentes con las cantidades.
        #   3. Los despachos entregados no generen stock negativo.
        # ------------------------------------------------------------

        ordenes = (
            OrdenDespacho.objects
            .select_related("factura_venta")
            .prefetch_related("detalles")
            .order_by(
                "fecha_generacion",
                "id"
            )
        )

        for orden in ordenes:

            detalles_venta = {}

            for detalle_venta in DetalleVenta.objects.filter(
                venta=orden.factura_venta.venta
            ):
                detalles_venta[detalle_venta.producto_id] = (
                    detalles_venta.get(detalle_venta.producto_id, 0)
                    + detalle_venta.cantidad
                )

            for detalle in orden.detalles.all():

                cantidad_despachada = detalle.cantidad

                cantidad_vendida = detalles_venta.get(
                    detalle.producto_id
                )

                if cantidad_vendida is None:
                    raise ValueError(
                        "Inconsistencia de despacho: "
                        f"el producto {detalle.producto_id} "
                        "no existe en la venta."
                    )

                # ----------------------------------------------------
                # Determinar cuánto puede salir realmente.
                # ----------------------------------------------------

                if cantidad_despachada < 0:
                    raise ValueError(
                        "Inconsistencia de despacho: "
                        f"cantidad negativa para producto "
                        f"{detalle.producto_id}."
                    )

                # ----------------------------------------------------
                # Si no existe stock:
                # ----------------------------------------------------

                if cantidad_despachada > cantidad_vendida:
                    raise ValueError(
                        "Inconsistencia de despacho: "
                        f"se intentan despachar "
                        f"{cantidad_despachada} unidades de "
                        f"producto {detalle.producto_id}, "
                        f"pero la venta contiene "
                        f"{cantidad_vendida}."
                    )

                # ----------------------------------------------------
                # Actualizar cantidad del detalle.
                # ----------------------------------------------------

                if (
                    detalle.estado_entrega == "Entregado"
                    and cantidad_despachada <= 0
                ):
                    raise ValueError(
                        "Inconsistencia de despacho: "
                        "un detalle marcado como Entregado "
                        "debe tener cantidad mayor que cero."
                    )

                if (
                    detalle.estado_entrega == "Pendiente"
                    and cantidad_despachada != 0
                ):
                    raise ValueError(
                        "Inconsistencia de despacho: "
                        "un detalle Pendiente debe tener "
                        "cantidad cero."
                    )


        self.stdout.write(
            self.style.SUCCESS(
                "✓ Despachos históricos validados sin modificaciones."
            )
        )

        # ------------------------------------------------------------
        # 5. REINICIAR MOVIMIENTOS DE INVENTARIO
        # ------------------------------------------------------------
        #
        # La limpieza inicial normalmente deja esta tabla vacía,
        # pero la vaciamos explícitamente para que esta fase sea
        # reproducible.
        # ------------------------------------------------------------

        MovimientoInventario.objects.all().delete()

        # ------------------------------------------------------------
        # 6. CREAR MOVIMIENTOS DE ENTRADA
        # ------------------------------------------------------------

        stock_actual = {
            producto.id: 0
            for producto in productos
        }

        movimientos_entrada = 0

        for compra in compras_recibidas:

            for detalle in compra.detalles.all():

                producto_id = detalle.producto_id

                anterior = stock_actual[
                    producto_id
                ]

                cantidad = detalle.cantidad

                posterior = (
                    anterior + cantidad
                )

                MovimientoInventario.objects.create(
                    producto_id=producto_id,
                    tipo_movimiento=(
                        MovimientoInventario
                        .TIPO_ENTRADA_COMPRA
                    ),
                    cantidad=cantidad,
                    stock_anterior=anterior,
                    stock_posterior=posterior,
                    referencia_tipo="COMPRA",
                    referencia_id=compra.id,
                    empleado=compra.empleado,
                    fecha=compra.fecha,
                    observaciones=(
                        "Entrada de mercancía recibida "
                        "en almacén."
                    ),
                )

                stock_actual[producto_id] = posterior

                movimientos_entrada += 1

        # ------------------------------------------------------------
        # 7. CREAR MOVIMIENTOS DE SALIDA
        # ------------------------------------------------------------
        #
        # Solo se generan movimientos por cantidades efectivamente
        # entregadas.
        # ------------------------------------------------------------

        movimientos_salida = 0

        ordenes = (
            OrdenDespacho.objects
            .select_related(
                "empleado_responsable"
            )
            .prefetch_related("detalles")
            .order_by(
                "fecha_generacion",
                "id"
            )
        )

        for orden in ordenes:

            for detalle in orden.detalles.all():

                cantidad = detalle.cantidad

                if (
                    cantidad is None
                    or cantidad <= 0
                    or detalle.estado_entrega
                    != "Entregado"
                ):
                    continue

                producto_id = detalle.producto_id

                anterior = stock_actual[
                    producto_id
                ]

                # La cantidad despachada nunca puede superar
                # el stock físico disponible en ese momento.
                if cantidad > anterior:
                    raise ValueError(
                        "Inconsistencia de inventario: "
                        f"el producto {producto_id} "
                        f"intenta salir con {cantidad} unidades "
                        f"pero solo existen {anterior}."
                    )

                posterior = (
                    anterior - cantidad
                )

                MovimientoInventario.objects.create(
                    producto_id=producto_id,
                    tipo_movimiento=(
                        MovimientoInventario
                        .TIPO_SALIDA_VENTA
                    ),
                    cantidad=cantidad,
                    stock_anterior=anterior,
                    stock_posterior=posterior,
                    referencia_tipo="ORDEN_DESPACHO",
                    referencia_id=orden.id,
                    empleado=(
                        orden.empleado_responsable
                    ),
                    fecha=orden.fecha_generacion,
                    observaciones=(
                        "Salida de mercancía por "
                        "despacho de venta."
                    ),
                )

                stock_actual[producto_id] = posterior

                movimientos_salida += 1

        # ------------------------------------------------------------
        # 8. ACTUALIZAR STOCK ACTUAL DE LOS PRODUCTOS
        # ------------------------------------------------------------

        for producto in productos:

            producto.stock_actual = stock_actual[
                producto.id
            ]

            producto.save(
                update_fields=["stock_actual"]
            )

        # ------------------------------------------------------------
        # 9. VALIDACIÓN DEL STOCK
        # ------------------------------------------------------------

        inconsistencias_stock = []

        for producto in productos:

            movimientos = (
                MovimientoInventario.objects
                .filter(producto=producto)
                .order_by(
                    "fecha",
                    "id"
                )
            )

            stock_calculado = 0

            for movimiento in movimientos:

                if movimiento.stock_anterior != stock_calculado:

                    inconsistencias_stock.append(
                        (
                            producto.id,
                            producto.nombre,
                            "StockAnterior inconsistente"
                        )
                    )

                if (
                    movimiento.tipo_movimiento
                    == MovimientoInventario
                    .TIPO_ENTRADA_COMPRA
                ):
                    stock_calculado += (
                        movimiento.cantidad
                    )

                elif (
                    movimiento.tipo_movimiento
                    == MovimientoInventario
                    .TIPO_SALIDA_VENTA
                ):
                    stock_calculado -= (
                        movimiento.cantidad
                    )

                elif (
                    movimiento.tipo_movimiento
                    == MovimientoInventario
                    .TIPO_AJUSTE
                ):
                    stock_calculado += (
                        movimiento.cantidad
                    )

                if (
                    movimiento.stock_posterior
                    != stock_calculado
                ):
                    inconsistencias_stock.append(
                        (
                            producto.id,
                            producto.nombre,
                            "StockPosterior inconsistente"
                        )
                    )

            if producto.stock_actual != stock_calculado:

                inconsistencias_stock.append(
                    (
                        producto.id,
                        producto.nombre,
                        (
                            "StockActual no coincide "
                            "con Kardex"
                        )
                    )
                )

            if producto.stock_actual < 0:

                inconsistencias_stock.append(
                    (
                        producto.id,
                        producto.nombre,
                        "Stock negativo"
                    )
                )

        if inconsistencias_stock:

            raise ValueError(
                "Se encontraron inconsistencias "
                f"de inventario: {inconsistencias_stock}"
            )

        # ------------------------------------------------------------
        # 10. RESUMEN DE KARDEX
        # ------------------------------------------------------------

        productos_con_stock = (
            Producto.objects
            .filter(stock_actual__gt=0)
            .count()
        )

        stock_total = sum(
            producto.stock_actual
            for producto in productos
        )

        cantidad_entrada_total = sum(
            movimiento.cantidad
            for movimiento in MovimientoInventario.objects.filter(
                tipo_movimiento="ENTRADA_COMPRA"
            )
        )

        cantidad_salida_total = sum(
            movimiento.cantidad
            for movimiento in MovimientoInventario.objects.filter(
                tipo_movimiento="SALIDA_VENTA"
            )
        )

        self.stdout.write(
            self.style.SUCCESS(
                f"✓ Movimientos de entrada: "
                f"{movimientos_entrada}"
            )
        )

        self.stdout.write(
            self.style.SUCCESS(
                f"✓ Movimientos de salida: "
                f"{movimientos_salida}"
            )
        )

        self.stdout.write(
            self.style.SUCCESS(
                f"✓ Unidades ingresadas: "
                f"{cantidad_entrada_total}"
            )
        )

        self.stdout.write(
            self.style.SUCCESS(
                f"✓ Unidades despachadas: "
                f"{cantidad_salida_total}"
            )
        )

        self.stdout.write(
            self.style.SUCCESS(
                f"✓ Stock total actual: "
                f"{stock_total}"
            )
        )

        self.stdout.write(
            self.style.SUCCESS(
                f"✓ Productos con existencia: "
                f"{productos_con_stock}"
            )
        )

        self.stdout.write(
            self.style.SUCCESS(
                "✓ Validación de Kardex completada "
                "sin inconsistencias."
            )
        )

        self.stdout.write(
            self.style.SUCCESS(
                "=============================================="
            )
        )

        # ============================================================
        # FINAL
        # ============================================================

        self.stdout.write("")
        self.stdout.write(
            self.style.SUCCESS(
                "Limpieza de datos de desarrollo completada correctamente."
            )
        )

        self.stdout.write(
            self.style.SUCCESS(
                "Usuarios, empleados, departamentos y roles "
                "fueron reconstruidos correctamente."
            )
        )