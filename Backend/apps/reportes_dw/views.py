from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework import status

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


class EjecutarETLView(APIView):
    permission_classes = [PuedeActualizarDashboard]

    def post(self, request):
        service = ETLService()

        try:
            resultado = service.ejecutar_master()

        except ETLOrchestrationError as exc:
            return Response(
                {
                    "detail": str(exc),
                },
                status=status.HTTP_409_CONFLICT,
            )

        return Response(
            resultado,
            status=status.HTTP_202_ACCEPTED,
        )


class EstadoETLView(APIView):
    permission_classes = [PuedeActualizarDashboard]

    def get(self, request, ejecucion_key):
        service = ETLService()

        resultado = service.consultar_ejecucion(
            ejecucion_key
        )

        if resultado is None:
            return Response(
                {
                    "detail": "Ejecución ETL no encontrada."
                },
                status=status.HTTP_404_NOT_FOUND,
            )

        return Response(resultado)


class UltimaEjecucionETLView(APIView):
    permission_classes = [PuedeActualizarDashboard]

    def get(self, request):
        service = ETLService()

        resultado = service.ultima_ejecucion()

        if resultado is None:
            return Response(
                {
                    "detail": "No existen ejecuciones ETL."
                },
                status=status.HTTP_404_NOT_FOUND,
            )

        return Response(resultado)


