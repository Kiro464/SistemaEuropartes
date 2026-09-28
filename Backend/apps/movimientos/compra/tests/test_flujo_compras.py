from decimal import Decimal

from django.contrib.auth.models import Group
from django.test import TestCase
from django.urls import reverse

from rest_framework.test import APIClient

from apps.catalogos.cliente.models import Cliente
from apps.catalogos.departamento.models import Departamento
from apps.catalogos.marca.models import Marca
from apps.catalogos.pais.models import Pais
from apps.catalogos.producto.models import Producto
from apps.catalogos.proveedor.models import Proveedor

from apps.movimientos.compra.models import (
    Compra,
    DetalleCompra,
    DetalleSolicitudCompra,
    FacturaCompra,
    MovimientoInventario,
    SolicitudCompra,
)

from apps.movimientos.venta.models import ConfiguracionGlobal
from apps.seguridad.usuarios.models import User
from apps.catalogos.empleado.models import Empleado


class FlujoComprasE2ETestCase(TestCase):

    @classmethod
    def setUpTestData(cls):
        """
        Crea la estructura mínima necesaria para ejecutar
        el flujo completo de compras.
        """

        # --------------------------------------------------
        # Roles
        # --------------------------------------------------

        cls.grupo_vendedor, _ = Group.objects.get_or_create(
            name='Vendedor'
        )

        cls.grupo_logistica, _ = Group.objects.get_or_create(
            name='Coordinador de Logística'
        )

        cls.grupo_finanzas, _ = Group.objects.get_or_create(
            name='Asesor Financiero'
        )

        cls.grupo_oficial, _ = Group.objects.get_or_create(
            name='Oficial de Logística'
        )

        # --------------------------------------------------
        # Departamentos
        # --------------------------------------------------

        cls.departamento = Departamento.objects.create(
            nombre='Logística E2E'
        )

        # --------------------------------------------------
        # Usuarios
        # --------------------------------------------------

        cls.usuario_vendedor = User.objects.create_user(
            username='test_vendedor_e2e',
            password='Europartes123'
        )

        cls.usuario_coordinador = User.objects.create_user(
            username='test_coordinador_e2e',
            password='Europartes123'
        )

        cls.usuario_finanzas = User.objects.create_user(
            username='test_finanzas_e2e',
            password='Europartes123'
        )

        cls.usuario_oficial = User.objects.create_user(
            username='test_oficial_e2e',
            password='Europartes123'
        )

        cls.usuario_vendedor.groups.add(
            cls.grupo_vendedor
        )

        cls.usuario_coordinador.groups.add(
            cls.grupo_logistica
        )

        cls.usuario_finanzas.groups.add(
            cls.grupo_finanzas
        )

        cls.usuario_oficial.groups.add(
            cls.grupo_oficial
        )

        # --------------------------------------------------
        # Empleados
        # --------------------------------------------------

        cls.empleado_vendedor = Empleado.objects.create(
            nombres='Vendedor',
            apellidos='Prueba E2E',
            cargo='Vendedor',
            departamento=cls.departamento,
            estado=True
        )

        cls.empleado_coordinador = Empleado.objects.create(
            nombres='Coordinador',
            apellidos='Prueba E2E',
            cargo='Coordinador de Logística',
            departamento=cls.departamento,
            estado=True
        )

        cls.empleado_finanzas = Empleado.objects.create(
            nombres='Finanzas',
            apellidos='Prueba E2E',
            cargo='Asesor Financiero',
            departamento=cls.departamento,
            estado=True
        )

        cls.empleado_oficial = Empleado.objects.create(
            nombres='Oficial',
            apellidos='Prueba E2E',
            cargo='Oficial de Logística',
            departamento=cls.departamento,
            estado=True
        )

        cls.empleado_vendedor.usuario = cls.usuario_vendedor
        cls.empleado_vendedor.save(
            update_fields=['usuario']
        )

        cls.empleado_coordinador.usuario = cls.usuario_coordinador
        cls.empleado_coordinador.save(
            update_fields=['usuario']
        )

        cls.empleado_finanzas.usuario = cls.usuario_finanzas
        cls.empleado_finanzas.save(
            update_fields=['usuario']
        )

        cls.empleado_oficial.usuario = cls.usuario_oficial
        cls.empleado_oficial.save(
            update_fields=['usuario']
        )

        # --------------------------------------------------
        # Catálogos mínimos
        # --------------------------------------------------

        cls.pais = Pais.objects.create(
            nombre='País E2E'
        )

        cls.marca = Marca.objects.create(
            nombre='Marca E2E',
        )

        cls.proveedor = Proveedor.objects.create(
            nombre='Proveedor E2E',
            pais=cls.pais
        )

        cls.producto = Producto.objects.create(
            nombre='Producto E2E',
            marca=cls.marca,
            descripcion='Producto utilizado en pruebas E2E',
            precio_unitario=Decimal('500.00'),
            stock_actual=10,
            proveedor=cls.proveedor,
            estado=True
        )

        # --------------------------------------------------
        # Configuración financiera
        # --------------------------------------------------

        ConfiguracionGlobal.objects.update_or_create(
            id=1,
            defaults={
                'iva': Decimal('15.00'),
                'tipo_cambio': Decimal('36.62'),
            }
        )

    def setUp(self):
        self.client = APIClient()

    # ======================================================
    # 1. CREAR SOLICITUD
    # ======================================================

    def test_flujo_completo_compra(self):
        """
        Prueba integral:

        Vendedor
        -> Solicitud PENDIENTE
        -> Coordinador ACEPTA
        -> Coordinador genera Compra
        -> REALIZADA
        -> EN_PROCESO
        -> Finanzas aprueba
        -> FacturaCompra
        -> PROCESADA
        -> Oficial recibe
        -> Stock +
        -> Kardex
        -> ENTREGADA_AL_ALMACEN
        """

        # --------------------------------------------------
        # Vendedor autenticado
        # --------------------------------------------------

        self.client.force_authenticate(
            user=self.usuario_vendedor
        )

        response = self.client.post(
            '/movimientos/compra/solicitudes/',
            {
                'observaciones': 'Solicitud E2E',
                'detalles': [
                    {
                        'producto_id': self.producto.id,
                        'cantidad': 5,
                        'observaciones': 'Compra E2E',
                    }
                ]
            },
            format='json'
        )

        self.assertEqual(
            response.status_code,
            201
        )

        solicitud_id = response.data['id']

        solicitud = SolicitudCompra.objects.get(
            pk=solicitud_id
        )

        self.assertEqual(
            solicitud.estado,
            'PENDIENTE'
        )

        self.assertEqual(
            solicitud.empleado_id,
            self.empleado_vendedor.id
        )

        # --------------------------------------------------
        # Coordinador acepta
        # --------------------------------------------------

        self.client.force_authenticate(
            user=self.usuario_coordinador
        )

        response = self.client.post(
            f'/movimientos/compra/solicitudes/'
            f'{solicitud.id}/aceptar/'
        )

        self.assertEqual(
            response.status_code,
            200
        )

        solicitud.refresh_from_db()

        self.assertEqual(
            solicitud.estado,
            'ACEPTADA'
        )

        # --------------------------------------------------
        # Generar compra desde solicitud
        # --------------------------------------------------

        response = self.client.post(
            '/movimientos/compra/desde-solicitud/',
            {
                'proveedor_id': self.proveedor.id,
                'solicitud_id': solicitud.id,
                'costos': [
                    {
                        'producto_id': self.producto.id,
                        'costo_unitario': '200.00',
                    }
                ]
            },
            format='json'
        )

        self.assertEqual(
            response.status_code,
            201
        )

        compra_id = response.data['id']

        compra = Compra.objects.get(
            pk=compra_id
        )

        self.assertEqual(
            compra.estado,
            'REALIZADA'
        )

        self.assertEqual(
            compra.solicitud_id,
            solicitud.id
        )

        detalle = DetalleCompra.objects.get(
            compra=compra
        )

        self.assertEqual(
            detalle.cantidad,
            5
        )

        self.assertEqual(
            detalle.costo_unitario,
            Decimal('200.00')
        )

        # --------------------------------------------------
        # El stock NO debe haber cambiado
        # --------------------------------------------------

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            10
        )

        # --------------------------------------------------
        # REALIZADA -> EN_PROCESO
        # --------------------------------------------------

        response = self.client.post(
            f'/movimientos/compra/'
            f'{compra.id}/iniciar/'
        )

        self.assertEqual(
            response.status_code,
            200
        )

        compra.refresh_from_db()

        self.assertEqual(
            compra.estado,
            'EN_PROCESO'
        )

        # --------------------------------------------------
        # Finanzas aprueba
        # --------------------------------------------------

        self.client.force_authenticate(
            user=self.usuario_finanzas
        )

        response = self.client.post(
            f'/movimientos/compra/'
            f'{compra.id}/aprobar/'
        )

        self.assertEqual(
            response.status_code,
            200
        )

        compra.refresh_from_db()

        self.assertEqual(
            compra.estado,
            'PROCESADA'
        )

        # --------------------------------------------------
        # Factura creada
        # --------------------------------------------------

        factura = FacturaCompra.objects.get(
            compra=compra
        )

        self.assertEqual(
            factura.total,
            Decimal('1000.00')
        )

        self.assertEqual(
            factura.iva,
            Decimal('150.00')
        )

        self.assertEqual(
            factura.total_con_iva,
            Decimal('1150.00')
        )

        # --------------------------------------------------
        # Stock todavía no cambia
        # --------------------------------------------------

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            10
        )

        # --------------------------------------------------
        # Oficial recibe
        # --------------------------------------------------

        self.client.force_authenticate(
            user=self.usuario_oficial
        )

        response = self.client.post(
            f'/movimientos/compra/'
            f'{compra.id}/recibir/'
        )

        self.assertEqual(
            response.status_code,
            200
        )

        compra.refresh_from_db()
        self.producto.refresh_from_db()

        # --------------------------------------------------
        # Estado final
        # --------------------------------------------------

        self.assertEqual(
            compra.estado,
            'ENTREGADA_AL_ALMACEN'
        )

        # --------------------------------------------------
        # Stock
        # --------------------------------------------------

        self.assertEqual(
            self.producto.stock_actual,
            15
        )

        # --------------------------------------------------
        # Kardex
        # --------------------------------------------------

        movimiento = MovimientoInventario.objects.get(
            referencia_tipo='COMPRA',
            referencia_id=compra.id,
            producto=self.producto
        )

        self.assertEqual(
            movimiento.tipo_movimiento,
            'ENTRADA_COMPRA'
        )

        self.assertEqual(
            movimiento.cantidad,
            5
        )

        self.assertEqual(
            movimiento.stock_anterior,
            10
        )

        self.assertEqual(
            movimiento.stock_posterior,
            15
        )

        self.assertEqual(
            movimiento.empleado_id,
            self.empleado_oficial.id
        )

    def test_vendedor_no_puede_aprobar_compra(self):
        compra = Compra.objects.create(
            fecha='2026-09-05',
            empleado=self.empleado_coordinador,
            proveedor=self.proveedor,
            estado='EN_PROCESO',
        )

        DetalleCompra.objects.create(
            compra=compra,
            producto=self.producto,
            cantidad=2,
            costo_unitario=Decimal('100.00')
        )

        self.client.force_authenticate(
            user=self.usuario_vendedor
        )

        response = self.client.post(
            f'/movimientos/compra/'
            f'{compra.id}/aprobar/'
        )

        self.assertEqual(
            response.status_code,
            403
        )

        compra.refresh_from_db()

        self.assertEqual(
            compra.estado,
            'EN_PROCESO'
        )
    
    def test_finanzas_no_puede_recibir_compra(self):
        compra = Compra.objects.create(
            fecha='2026-09-05',
            empleado=self.empleado_coordinador,
            proveedor=self.proveedor,
            estado='PROCESADA',
        )

        DetalleCompra.objects.create(
            compra=compra,
            producto=self.producto,
            cantidad=2,
            costo_unitario=Decimal('100.00')
        )

        self.client.force_authenticate(
            user=self.usuario_finanzas
        )

        response = self.client.post(
            f'/movimientos/compra/'
            f'{compra.id}/recibir/'
        )

        self.assertEqual(
            response.status_code,
            403
        )

    def test_no_se_puede_aprobar_compra_realizada(self):
        compra = Compra.objects.create(
            fecha='2026-09-05',
            empleado=self.empleado_coordinador,
            proveedor=self.proveedor,
            estado='REALIZADA',
        )

        DetalleCompra.objects.create(
            compra=compra,
            producto=self.producto,
            cantidad=2,
            costo_unitario=Decimal('100.00')
        )

        self.client.force_authenticate(
            user=self.usuario_finanzas
        )

        response = self.client.post(
            f'/movimientos/compra/'
            f'{compra.id}/aprobar/'
        )

        self.assertEqual(
            response.status_code,
            400
        )

        compra.refresh_from_db()

        self.assertEqual(
            compra.estado,
            'REALIZADA'
        )

        self.assertFalse(
            FacturaCompra.objects.filter(
                compra=compra
            ).exists()
        )

    def test_no_se_puede_recibir_dos_veces(self):
        compra = Compra.objects.create(
            fecha='2026-09-05',
            empleado=self.empleado_coordinador,
            proveedor=self.proveedor,
            estado='PROCESADA',
        )

        DetalleCompra.objects.create(
            compra=compra,
            producto=self.producto,
            cantidad=4,
            costo_unitario=Decimal('100.00')
        )

        FacturaCompra.objects.create(
            total=Decimal('400.00'),
            iva=Decimal('60.00'),
            total_con_iva=Decimal('460.00'),
            compra=compra,
        )

        self.client.force_authenticate(
            user=self.usuario_oficial
        )

        # Primera recepción
        response = self.client.post(
            f'/movimientos/compra/'
            f'{compra.id}/recibir/'
        )

        self.assertEqual(
            response.status_code,
            200
        )

        self.producto.refresh_from_db()

        self.assertEqual(
            self.producto.stock_actual,
            14
        )

        movimientos = MovimientoInventario.objects.filter(
            referencia_tipo='COMPRA',
            referencia_id=compra.id
        )

        self.assertEqual(
            movimientos.count(),
            1
        )

        # Segunda recepción
        response = self.client.post(
            f'/movimientos/compra/'
            f'{compra.id}/recibir/'
        )

        self.assertEqual(
            response.status_code,
            400
        )

        self.producto.refresh_from_db()

        # No debe volver a aumentar
        self.assertEqual(
            self.producto.stock_actual,
            14
        )

        movimientos = MovimientoInventario.objects.filter(
            referencia_tipo='COMPRA',
            referencia_id=compra.id
        )

        self.assertEqual(
            movimientos.count(),
            1
        )

    def test_compra_directa(self):
        self.client.force_authenticate(
            user=self.usuario_coordinador
        )

        response = self.client.post(
            '/movimientos/compra/',
            {
                'proveedor_id': self.proveedor.id,
                'detalles': [
                    {
                        'producto_id': self.producto.id,
                        'cantidad': 3,
                        'costo_unitario': '300.00',
                    }
                ]
            },
            format='json'
        )

        self.assertEqual(
            response.status_code,
            201
        )

        compra = Compra.objects.get(
            pk=response.data['id']
        )

        self.assertIsNone(
            compra.solicitud_id
        )

        self.assertEqual(
            compra.estado,
            'REALIZADA'
        )

        detalle = DetalleCompra.objects.get(
            compra=compra
        )

        self.assertEqual(
            detalle.cantidad,
            3
        )

        self.assertEqual(
            detalle.costo_unitario,
            Decimal('300.00')
        )

        self.producto.refresh_from_db()

        # Crear la compra no modifica inventario
        self.assertEqual(
            self.producto.stock_actual,
            10
        )

    def test_no_se_puede_generar_dos_compras_desde_una_solicitud(self):
        solicitud = SolicitudCompra.objects.create(
            empleado=self.empleado_vendedor,
            estado='ACEPTADA',
            observaciones='Solicitud duplicada E2E'
        )

        DetalleSolicitudCompra.objects.create(
            solicitud=solicitud,
            producto=self.producto,
            cantidad=2
        )

        self.client.force_authenticate(
            user=self.usuario_coordinador
        )

        payload = {
            'proveedor_id': self.proveedor.id,
            'solicitud_id': solicitud.id,
            'costos': [
                {
                    'producto_id': self.producto.id,
                    'costo_unitario': '100.00',
                }
            ]
        }

        # Primera
        response = self.client.post(
            '/movimientos/compra/desde-solicitud/',
            payload,
            format='json'
        )

        self.assertEqual(
            response.status_code,
            201
        )

        # Segunda
        response = self.client.post(
            '/movimientos/compra/desde-solicitud/',
            payload,
            format='json'
        )

        self.assertEqual(
            response.status_code,
            400
        )

        self.assertEqual(
            Compra.objects.filter(
                solicitud=solicitud
            ).count(),
            1
        )