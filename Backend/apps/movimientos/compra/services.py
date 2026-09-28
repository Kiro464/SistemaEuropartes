from django.db import transaction
from django.utils import timezone

from apps.catalogos.producto.models import Producto
from apps.catalogos.empleado.models import Empleado
from .models import MovimientoInventario


class InventarioError(Exception):
    """Error de negocio relacionado con operaciones de inventario."""
    pass


class InventarioService:

    @staticmethod
    @transaction.atomic
    def registrar_entrada_compra(
        producto_id,
        cantidad,
        empleado,
        referencia_id,
        observaciones=None,
    ):
        """
        Registra una entrada de inventario proveniente de una compra.

        Regla:
            StockPosterior = StockAnterior + Cantidad

        La cantidad del movimiento siempre se almacena como positiva.
        """

        if cantidad <= 0:
            raise InventarioError(
                "La cantidad de entrada debe ser mayor que cero."
            )

        if not isinstance(empleado, Empleado):
            raise InventarioError(
                "El empleado responsable no es válido."
            )

        try:
            producto = (
                Producto.objects
                .select_for_update()
                .get(pk=producto_id)
            )
        except Producto.DoesNotExist:
            raise InventarioError(
                f"El producto con ID {producto_id} no existe."
            )

        stock_anterior = producto.stock_actual
        stock_posterior = stock_anterior + cantidad

        producto.stock_actual = stock_posterior
        producto.save(update_fields=["stock_actual"])

        movimiento = MovimientoInventario.objects.create(
            producto=producto,
            tipo_movimiento="ENTRADA_COMPRA",
            cantidad=cantidad,
            stock_anterior=stock_anterior,
            stock_posterior=stock_posterior,
            referencia_tipo="COMPRA",
            referencia_id=referencia_id,
            empleado=empleado,
            fecha=timezone.now(),
            observaciones=observaciones,
        )

        return movimiento

    @staticmethod
    @transaction.atomic
    def registrar_salida_venta(
        producto_id,
        cantidad,
        empleado,
        referencia_id,
        observaciones=None,
    ):
        """
        Registra una salida de inventario por una venta.

        Esta operación se utilizará en Fase 4, cuando
        el Oficial de Logística valide el despacho.

        Regla:
            StockPosterior = StockAnterior - Cantidad
        """

        if cantidad <= 0:
            raise InventarioError(
                "La cantidad de salida debe ser mayor que cero."
            )

        if not isinstance(empleado, Empleado):
            raise InventarioError(
                "El empleado responsable no es válido."
            )

        try:
            producto = (
                Producto.objects
                .select_for_update()
                .get(pk=producto_id)
            )
        except Producto.DoesNotExist:
            raise InventarioError(
                f"El producto con ID {producto_id} no existe."
            )

        stock_anterior = producto.stock_actual

        if stock_anterior < cantidad:
            raise InventarioError(
                f"Stock insuficiente para el producto "
                f"'{producto.nombre}'. "
                f"Stock disponible: {stock_anterior}. "
                f"Cantidad solicitada: {cantidad}."
            )

        stock_posterior = stock_anterior - cantidad

        producto.stock_actual = stock_posterior
        producto.save(update_fields=["stock_actual"])

        movimiento = MovimientoInventario.objects.create(
            producto=producto,
            tipo_movimiento="SALIDA_VENTA",
            cantidad=cantidad,
            stock_anterior=stock_anterior,
            stock_posterior=stock_posterior,
            referencia_tipo="VENTA",
            referencia_id=referencia_id,
            empleado=empleado,
            fecha=timezone.now(),
            observaciones=observaciones,
        )

        return movimiento

    @staticmethod
    @transaction.atomic
    def registrar_ajuste(
        producto_id,
        stock_nuevo,
        empleado,
        observaciones=None,
    ):
        """
        Ajusta manualmente el stock de un producto.

        La cantidad almacenada en el Kardex siempre es positiva.
        La dirección del ajuste se determina comparando el stock
        anterior con el nuevo stock.

        No permite stock negativo.
        """

        if stock_nuevo < 0:
            raise InventarioError(
                "El stock nuevo no puede ser negativo."
            )

        if not isinstance(empleado, Empleado):
            raise InventarioError(
                "El empleado responsable no es válido."
            )

        try:
            producto = (
                Producto.objects
                .select_for_update()
                .get(pk=producto_id)
            )
        except Producto.DoesNotExist:
            raise InventarioError(
                f"El producto con ID {producto_id} no existe."
            )

        stock_anterior = producto.stock_actual

        if stock_nuevo == stock_anterior:
            raise InventarioError(
                "El nuevo stock debe ser diferente al stock actual."
            )

        cantidad = abs(stock_nuevo - stock_anterior)

        if stock_nuevo > stock_anterior:
            direccion = "AUMENTO"
        else:
            direccion = "DISMINUCION"

        producto.stock_actual = stock_nuevo
        producto.save(update_fields=["stock_actual"])

        movimiento = MovimientoInventario.objects.create(
            producto=producto,
            tipo_movimiento="AJUSTE",
            cantidad=cantidad,
            stock_anterior=stock_anterior,
            stock_posterior=stock_nuevo,
            referencia_tipo="AJUSTE",
            referencia_id=None,
            empleado=empleado,
            fecha=timezone.now(),
            observaciones=(
                f"{direccion}. "
                f"{observaciones or 'Ajuste manual de inventario.'}"
            ),
        )

        return movimiento