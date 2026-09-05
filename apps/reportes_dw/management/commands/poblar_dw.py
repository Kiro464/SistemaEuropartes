import random
from datetime import timedelta
from django.utils import timezone
from django.core.management.base import BaseCommand
from django.db import transaction
from apps.reportes_dw.models import DimCliente, DimEmpleado, DimProducto, DimTiempo, FactDetalleVenta


class Command(BaseCommand):
    help = 'Inyecta datos históricos de prueba en el Data Warehouse (OLAP)'

    @transaction.atomic(using='dw')
    def handle(self, *args, **kwargs):
        self.stdout.write(self.style.WARNING('Iniciando inyección de datos en el Data Warehouse...'))

        # 1. Limpiar datos existentes (Opcional, ten cuidado si ya tienes datos reales)
        FactDetalleVenta.objects.using('dw').all().delete()
        DimCliente.objects.using('dw').all().delete()
        DimEmpleado.objects.using('dw').all().delete()
        DimProducto.objects.using('dw').all().delete()
        DimTiempo.objects.using('dw').all().delete()

        # 2. Crear Clientes
        clientes_data = [
            {'nombres': 'Carlos', 'apellidos': 'Mendoza', 'telefono': '8888-1111', 'direccion': 'Managua'},
            {'nombres': 'Taller', 'apellidos': 'Los Motores', 'telefono': '8888-2222', 'direccion': 'Masaya'},
            {'nombres': 'Ana', 'apellidos': 'García', 'telefono': '8888-3333', 'direccion': 'León'},
            {'nombres': 'Transportes', 'apellidos': 'Rápidos', 'telefono': '8888-4444', 'direccion': 'Managua'},
            {'nombres': 'Luis', 'apellidos': 'Martínez', 'telefono': '8888-5555', 'direccion': 'Estelí'},
        ]
        clientes = [DimCliente.objects.using('dw').create(**c) for c in clientes_data]
        self.stdout.write(self.style.SUCCESS(f'✅ {len(clientes)} Clientes creados.'))

        # 3. Crear Empleados
        empleados_data = [
            {'nombres': 'Roberto', 'apellidos': 'Gómez', 'cargo': 'Vendedor Senior', 'telefono': '7777-1111',
             'correo': 'roberto@europartes.com'},
            {'nombres': 'María', 'apellidos': 'Pérez', 'cargo': 'Vendedora Junior', 'telefono': '7777-2222',
             'correo': 'maria@europartes.com'},
            {'nombres': 'José', 'apellidos': 'López', 'cargo': 'Gerente de Tienda', 'telefono': '7777-3333',
             'correo': 'jose@europartes.com'},
        ]
        empleados = [DimEmpleado.objects.using('dw').create(**e) for e in empleados_data]
        self.stdout.write(self.style.SUCCESS(f'✅ {len(empleados)} Empleados creados.'))

        # 4. Crear Productos
        productos_data = [
            {'nombre': 'Bujía Iridium', 'descripcion': 'Bujía de alto rendimiento', 'precio_unitario': 150.00,
             'estado': True},
            {'nombre': 'Filtro de Aceite', 'descripcion': 'Filtro estándar', 'precio_unitario': 250.00, 'estado': True},
            {'nombre': 'Pastillas de Freno', 'descripcion': 'Cerámica', 'precio_unitario': 850.00, 'estado': True},
            {'nombre': 'Batería 12V', 'descripcion': 'Batería libre de mantenimiento', 'precio_unitario': 2500.00,
             'estado': True},
            {'nombre': 'Amortiguador Gas', 'descripcion': 'Delantero', 'precio_unitario': 1200.00, 'estado': True},
            {'nombre': 'Aceite Sintético 5W30', 'descripcion': 'Galón', 'precio_unitario': 1800.00, 'estado': True},
            {'nombre': 'Filtro de Aire', 'descripcion': 'Filtro de cabina', 'precio_unitario': 300.00, 'estado': True},
        ]
        productos = [DimProducto.objects.using('dw').create(**p) for p in productos_data]
        self.stdout.write(self.style.SUCCESS(f'✅ {len(productos)} Productos creados.'))

        # 5. Crear DimTiempo (Últimos 6 meses)
        hoy = timezone.now().date()
        fecha_inicio = hoy - timedelta(days=180)
        tiempos = []
        dias_espanol = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo']
        meses_espanol = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre',
                         'Octubre', 'Noviembre', 'Diciembre']

        for i in range(181):
            fecha_actual = fecha_inicio + timedelta(days=i)
            trimestre = (fecha_actual.month - 1) // 3 + 1
            t = DimTiempo(
                fechacompleta=fecha_actual,
                dia=fecha_actual.day,
                mes=fecha_actual.month,
                anio=fecha_actual.year,
                diasemana=dias_espanol[fecha_actual.weekday()],
                nombremes=meses_espanol[fecha_actual.month - 1],
                trimestre=trimestre
            )
            tiempos.append(t)

        DimTiempo.objects.using('dw').bulk_create(tiempos)
        tiempos_guardados = list(DimTiempo.objects.using('dw').all())
        self.stdout.write(self.style.SUCCESS(f'✅ {len(tiempos_guardados)} Días históricos creados.'))

        # 6. Crear Facturas (Simulando ventas aleatorias)
        facturas_creadas = 0
        iva_rate = 0.15

        ventas_bulk = []
        for tiempo in tiempos_guardados:
            # Simular entre 0 y 5 ventas por día
            num_ventas = random.randint(0, 5)
            for _ in range(num_ventas):
                producto = random.choice(productos)
                cliente = random.choice(clientes)
                empleado = random.choice(empleados)

                cantidad = random.randint(1, 4)
                subtotal = float(producto.precio_unitario) * cantidad
                iva = subtotal * iva_rate
                total = subtotal + iva

                venta = FactDetalleVenta(
                    cantidad=cantidad,
                    subtotal=subtotal,
                    iva=iva,
                    total=total,
                    producto=producto,
                    cliente=cliente,
                    empleado=empleado,
                    tiempo=tiempo
                )
                ventas_bulk.append(venta)
                facturas_creadas += 1

        # Insertar todas las ventas de golpe para mayor velocidad
        FactDetalleVenta.objects.using('dw').bulk_create(ventas_bulk)

        self.stdout.write(self.style.SUCCESS(f'✅ {facturas_creadas} Facturas/Ventas inyectadas con éxito.'))
        self.stdout.write(self.style.SUCCESS('¡Data Warehouse poblado correctamente! Ve a ver tu Dashboard.'))
