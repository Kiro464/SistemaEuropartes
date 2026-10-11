from django.db import transaction
from django.utils import timezone

from apps.catalogos.empleado.models import Empleado
from apps.catalogos.producto.models import Producto
from apps.movimientos.compra.services import (
    InventarioError,
    InventarioService,
)
from .models import DetalleOrden, OrdenDespacho


class DespachoVentaError(Exception):
    """Error de negocio relacionado con el despacho de una venta."""
    pass


class DespachoVentaService:

    @staticmethod
    @transaction.atomic
    def validar_despacho(
        orden_despacho_id,
        empleado,
        cantidades=None,
    ):
        """
        Valida un despacho de venta.

        cantidades=None:
            Despacha toda la cantidad pendiente de todos los detalles.

        cantidades={detalle_id: cantidad}:
            Permite realizar un despacho parcial.

        La cantidad entregada se controla mediante
        DetalleOrden.cantidad_entregada.

        El inventario se modifica exclusivamente mediante
        InventarioService.registrar_salida_venta().
        """

        if not isinstance(empleado, Empleado):
            raise DespachoVentaError(
                "El empleado responsable no es válido."
            )

        try:
            orden = (
                OrdenDespacho.objects
                .select_for_update()
                .select_related("factura_venta__venta")
                .get(pk=orden_despacho_id)
            )
        except OrdenDespacho.DoesNotExist:
            raise DespachoVentaError(
                f"La orden de despacho con ID "
                f"{orden_despacho_id} no existe."
            )

        if orden.estado:
            raise DespachoVentaError(
                "La orden de despacho ya fue completada."
            )

        detalles = list(
            orden.detalles
            .select_related("producto")
            .order_by("id")
        )

        if not detalles:
            raise DespachoVentaError(
                "La orden de despacho no tiene detalles."
            )

        # ----------------------------------------------------------
        # Validación y normalización de cantidades solicitadas
        # ----------------------------------------------------------

        if cantidades is not None:
            try:
                cantidades = {
                    int(detalle_id): int(cantidad)
                    for detalle_id, cantidad in cantidades.items()
                }
            except (AttributeError, TypeError, ValueError):
                raise DespachoVentaError(
                    "El formato de las cantidades de despacho "
                    "no es válido."
                )

            ids_detalles = {
                detalle.id
                for detalle in detalles
            }

            ids_invalidos = (
                set(cantidades.keys()) - ids_detalles
            )

            if ids_invalidos:
                raise DespachoVentaError(
                    "Se recibieron detalles que no pertenecen "
                    "a la orden de despacho."
                )

        movimientos_generados = []
        detalles_procesados = []

        # ----------------------------------------------------------
        # Procesamiento de cada detalle
        # ----------------------------------------------------------

        for detalle in detalles:

            cantidad_total = detalle.cantidad
            cantidad_entregada = detalle.cantidad_entregada

            if cantidad_total <= 0:
                raise DespachoVentaError(
                    f"La cantidad del detalle {detalle.id} "
                    "debe ser mayor que cero."
                )

            if cantidad_entregada < 0:
                raise DespachoVentaError(
                    f"La cantidad entregada del detalle "
                    f"{detalle.id} no puede ser negativa."
                )

            if cantidad_entregada > cantidad_total:
                raise DespachoVentaError(
                    f"La cantidad entregada del detalle "
                    f"{detalle.id} no puede superar "
                    "la cantidad solicitada."
                )

            cantidad_pendiente = (
                cantidad_total - cantidad_entregada
            )

            # Si no se especifican cantidades, se despacha
            # todo lo pendiente.
            if cantidades is None:
                cantidad_a_despachar = cantidad_pendiente
            else:
                cantidad_a_despachar = cantidades.get(
                    detalle.id,
                    0,
                )

            if cantidad_a_despachar < 0:
                raise DespachoVentaError(
                    f"La cantidad a despachar del detalle "
                    f"{detalle.id} no puede ser negativa."
                )

            if cantidad_a_despachar > cantidad_pendiente:
                raise DespachoVentaError(
                    f"La cantidad a despachar del detalle "
                    f"{detalle.id} supera la cantidad pendiente."
                )

            # ------------------------------------------------------
            # No hay nueva cantidad que entregar
            # ------------------------------------------------------

            if cantidad_a_despachar == 0:

                if cantidad_entregada == cantidad_total:
                    detalle.estado_entrega = "Entregado"
                    detalle.fecha_registro = timezone.now()

                    detalle.save(
                        update_fields=[
                            "estado_entrega",
                            "fecha_registro",
                        ]
                    )

                detalles_procesados.append(detalle)
                continue

            # ------------------------------------------------------
            # Consumir la reserva comprometida
            # ------------------------------------------------------
            #
            # Las órdenes creadas con la nueva lógica tienen una reserva
            # equivalente a la cantidad pendiente. Se admite temporalmente
            # una orden histórica sin reserva para no romper datos de prueba
            # existentes: en ese caso se descuenta únicamente la reserva
            # disponible (cero) y se mantiene la validación de stock físico.
            producto = (
                Producto.objects
                .select_for_update()
                .get(pk=detalle.producto_id)
            )
            reserva_a_consumir = min(
                producto.stock_reservado or 0,
                cantidad_a_despachar,
            )

            if reserva_a_consumir:
                producto.stock_reservado -= reserva_a_consumir
                producto.save(update_fields=["stock_reservado"])

            # ------------------------------------------------------
            # Salida de inventario
            # ------------------------------------------------------

            try:
                movimiento = (
                    InventarioService.registrar_salida_venta(
                        producto_id=detalle.producto_id,
                        cantidad=cantidad_a_despachar,
                        empleado=empleado,
                        referencia_id=orden.factura_venta.venta_id,
                        observaciones=(
                            f"Despacho de venta. "
                            f"Orden de despacho #{orden.id}."
                        ),
                    )
                )
            except InventarioError as exc:
                raise DespachoVentaError(
                    str(exc)
                ) from exc

            # ------------------------------------------------------
            # Actualizar cantidad entregada
            # ------------------------------------------------------

            nueva_cantidad_entregada = (
                cantidad_entregada
                + cantidad_a_despachar
            )

            detalle.cantidad_entregada = (
                nueva_cantidad_entregada
            )

            if nueva_cantidad_entregada == cantidad_total:
                detalle.estado_entrega = "Entregado"
            else:
                detalle.estado_entrega = "Pendiente"

            detalle.fecha_registro = timezone.now()

            detalle.save(
                update_fields=[
                    "cantidad_entregada",
                    "estado_entrega",
                    "fecha_registro",
                ]
            )

            movimientos_generados.append(movimiento)
            detalles_procesados.append(detalle)

        # ----------------------------------------------------------
        # Determinar si la orden quedó completamente entregada
        # ----------------------------------------------------------

        todos_entregados = all(
            detalle.cantidad_entregada == detalle.cantidad
            for detalle in detalles
        )

        orden.estado = todos_entregados
        orden.fecha_registro = timezone.now()

        orden.save(
            update_fields=[
                "estado",
                "fecha_registro",
            ]
        )

        return {
            "orden": orden,
            "detalles": detalles_procesados,
            "movimientos": movimientos_generados,
            "completa": todos_entregados,
        }