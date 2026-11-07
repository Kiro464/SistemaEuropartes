from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework import status

import logging

from .services.adf_client import DataFactoryError
from .services.etl_service import (
    ETLService,
    ETLOrchestrationError,
)
from .services.dashboard_service import DashboardService
from .services.ventas_service import VentasService
from .services.compras_service import ComprasService
from .services.solicitudes_service import SolicitudesService
from .services.inventario_service import InventarioService
from .services.monitoreo_service import MonitoreoService


from .permissions import (
    PuedeConsultarDashboard,
    PuedeActualizarDashboard,
)


_dashboard = DashboardService()
_ventas = VentasService()
_compras = ComprasService()
_solicitudes = SolicitudesService()
_inventario = InventarioService()
_monitoreo = MonitoreoService()


class ResumenEjecutivoView(APIView):
    permission_classes = [PuedeConsultarDashboard]

    def get(self, request):
        return Response(_dashboard.resumen(request.query_params))


class VentasTotalView(APIView):
    permission_classes = [PuedeConsultarDashboard]

    def get(self, request):
        return Response(_ventas.totales(request.query_params))


class VentasMensualesView(APIView):
    permission_classes = [PuedeConsultarDashboard]

    def get(self, request):
        return Response(_ventas.mensuales(request.query_params))


class ProductosTopView(APIView):
    permission_classes = [PuedeConsultarDashboard]

    def get(self, request):
        return Response(_ventas.productos_top(request.query_params))


class EmpleadosTopView(APIView):
    permission_classes = [PuedeConsultarDashboard]

    def get(self, request):
        return Response(_ventas.empleados_top(request.query_params))


class ClientesTopView(APIView):
    permission_classes = [PuedeConsultarDashboard]

    def get(self, request):
        return Response(_ventas.clientes_top(request.query_params))


class VentasPorMarcaView(APIView):
    permission_classes = [PuedeConsultarDashboard]

    def get(self, request):
        return Response(_ventas.por_marca(request.query_params))


class ComprasTotalView(APIView):
    permission_classes = [PuedeConsultarDashboard]

    def get(self, request):
        return Response(_compras.totales(request.query_params))


class ComprasMensualesView(APIView):
    permission_classes = [PuedeConsultarDashboard]

    def get(self, request):
        return Response(_compras.mensuales(request.query_params))


class ComprasPorProveedorView(APIView):
    permission_classes = [PuedeConsultarDashboard]

    def get(self, request):
        return Response(_compras.por_proveedor(request.query_params))


class ComprasPorEstadoView(APIView):
    permission_classes = [PuedeConsultarDashboard]

    def get(self, request):
        return Response(_compras.por_estado(request.query_params))


class SolicitudesCompraResumenView(APIView):
    permission_classes = [PuedeConsultarDashboard]

    def get(self, request):
        return Response(_solicitudes.resumen(request.query_params))


class SolicitudesPorEmpleadoView(APIView):
    permission_classes = [PuedeConsultarDashboard]

    def get(self, request):
        return Response(_solicitudes.por_empleado(request.query_params))


class MovimientosInventarioResumenView(APIView):
    permission_classes = [PuedeConsultarDashboard]

    def get(self, request):
        return Response(_inventario.movimientos_resumen(request.query_params))


class StockCriticoView(APIView):
    permission_classes = [PuedeConsultarDashboard]

    def get(self, request):
        return Response(_inventario.stock_critico(request.query_params))


class StockHistoricoView(APIView):
    permission_classes = [PuedeConsultarDashboard]

    def get(self, request):
        return Response(_inventario.stock_historico(request.query_params))


class StockEvolucionView(APIView):
    permission_classes = [PuedeConsultarDashboard]

    def get(self, request):
        return Response(_inventario.stock_evolucion(request.query_params))


class StockActualResumenView(APIView):
    permission_classes = [PuedeConsultarDashboard]

    def get(self, request):
        return Response(_inventario.stock_resumen(request.query_params))


class VentasVsComprasView(APIView):
    permission_classes = [PuedeConsultarDashboard]

    def get(self, request):
        return Response(_inventario.ventas_vs_compras(request.query_params))


class IndicadoresCoberturaView(APIView):
    permission_classes = [PuedeConsultarDashboard]

    def get(self, request):
        return Response(_monitoreo.cobertura())

class EstadoETLIndicadorView(APIView):
    permission_classes = [PuedeConsultarDashboard]

    def get(self, request):
        return Response(_monitoreo.estado_etl())


logger = logging.getLogger(__name__)

HISTORIAL_POR_DEFECTO = 20
HISTORIAL_MAXIMO = 100


def _respuesta_etl(accion):
    """Ejecuta una operación ETL y traduce sus errores a respuestas HTTP estables."""
    try:
        return accion()
    except ETLOrchestrationError as exc:
        cuerpo = {"detail": str(exc)}
        if exc.ejecucion:
            cuerpo["ejecucion"] = exc.ejecucion
        return Response(cuerpo, status=status.HTTP_409_CONFLICT)
    except DataFactoryError as exc:
        # El detalle técnico ya quedó en el log; al cliente solo un mensaje seguro.
        return Response({"detail": str(exc)}, status=status.HTTP_503_SERVICE_UNAVAILABLE)


def _encontrado(resultado, mensaje):
    if resultado is None:
        return Response({"detail": mensaje}, status=status.HTTP_404_NOT_FOUND)
    return Response(resultado)


# ============================================================
# ETL: endpoints usados por la web y la app móvil (reportes_dw/etl/...)
# ============================================================

class EjecutarETLView(APIView):
    permission_classes = [PuedeActualizarDashboard]

    def post(self, request):
        logger.info("Inicio de ETL solicitado por %s", request.user.get_username())
        return _respuesta_etl(
            lambda: Response(ETLService().ejecutar_master(), status=status.HTTP_202_ACCEPTED)
        )


class EstadoETLView(APIView):
    permission_classes = [PuedeActualizarDashboard]

    def get(self, request, ejecucion_key):
        return _respuesta_etl(lambda: _encontrado(
            ETLService().consultar_ejecucion(ejecucion_key),
            "Ejecución ETL no encontrada.",
        ))


class UltimaEjecucionETLView(APIView):
    permission_classes = [PuedeActualizarDashboard]

    def get(self, request):
        return _respuesta_etl(lambda: _encontrado(
            ETLService().ultima_ejecucion(),
            "No existen ejecuciones ETL.",
        ))


# ============================================================
# ETL en Azure Data Factory (api/etl/...)
# ============================================================

class EstadoRunETLView(APIView):
    """Estado de una ejecución por su runId de Azure Data Factory."""

    permission_classes = [PuedeActualizarDashboard]

    def get(self, request, run_id):
        return _respuesta_etl(lambda: _encontrado(
            ETLService().consultar_run(run_id),
            "Ejecución ETL no encontrada en Azure Data Factory.",
        ))


class HistorialETLView(APIView):
    permission_classes = [PuedeActualizarDashboard]

    def get(self, request):
        try:
            limite = int(request.query_params.get("limit", HISTORIAL_POR_DEFECTO))
        except ValueError:
            return Response(
                {"detail": "El parámetro limit debe ser un número entero."},
                status=status.HTTP_400_BAD_REQUEST,
            )
        limite = max(1, min(limite, HISTORIAL_MAXIMO))
        return _respuesta_etl(lambda: Response(ETLService().historial(limite)))
