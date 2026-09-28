from django.urls import path

from . import views



app_name = "reportes_dw"


urlpatterns = [

    # ========================================================
    # RESUMEN EJECUTIVO
    # ========================================================

    path(
        "dashboard/resumen/",
        views.ResumenEjecutivoView.as_view(),
        name="resumen_ejecutivo",
    ),

    # ========================================================
    # VENTAS
    # ========================================================

    path(
        "dashboard/ventas-total/",
        views.VentasTotalView.as_view(),
        name="ventas_total",
    ),

    path(
        "dashboard/ventas-mensuales/",
        views.VentasMensualesView.as_view(),
        name="ventas_mensuales",
    ),

    path(
        "dashboard/productos-top/",
        views.ProductosTopView.as_view(),
        name="productos_top",
    ),

    path(
        "dashboard/empleados-top/",
        views.EmpleadosTopView.as_view(),
        name="empleados_top",
    ),

    path(
        "dashboard/clientes-top/",
        views.ClientesTopView.as_view(),
        name="clientes_top",
    ),

    path(
        "dashboard/ventas-por-marca/",
        views.VentasPorMarcaView.as_view(),
        name="ventas_por_marca",
    ),

    # ========================================================
    # COMPRAS
    # ========================================================

    path(
        "dashboard/compras-total/",
        views.ComprasTotalView.as_view(),
        name="compras_total",
    ),

    path(
        "dashboard/compras-mensuales/",
        views.ComprasMensualesView.as_view(),
        name="compras_mensuales",
    ),

    path(
        "dashboard/compras-por-proveedor/",
        views.ComprasPorProveedorView.as_view(),
        name="compras_por_proveedor",
    ),

    path(
        "dashboard/compras-por-estado/",
        views.ComprasPorEstadoView.as_view(),
        name="compras_por_estado",
    ),

    # ========================================================
    # SOLICITUDES DE COMPRA
    # ========================================================

    path(
        "dashboard/solicitudes-resumen/",
        views.SolicitudesCompraResumenView.as_view(),
        name="solicitudes_resumen",
    ),

    path(
        "dashboard/solicitudes-por-empleado/",
        views.SolicitudesPorEmpleadoView.as_view(),
        name="solicitudes_por_empleado",
    ),

    # ========================================================
    # INVENTARIO
    # ========================================================

    path(
        "dashboard/movimientos-inventario/",
        views.MovimientosInventarioResumenView.as_view(),
        name="movimientos_inventario",
    ),

    path(
        "dashboard/stock-critico/",
        views.StockCriticoView.as_view(),
        name="stock_critico",
    ),

    path(
        "dashboard/stock-historico/",
        views.StockHistoricoView.as_view(),
        name="stock_historico",
    ),

    path(
        "dashboard/stock-evolucion/",
        views.StockEvolucionView.as_view(),
        name="stock_evolucion",
    ),

    path(
        "dashboard/stock-resumen/",
        views.StockActualResumenView.as_view(),
        name="stock_resumen",
    ),

    # ========================================================
    # INDICADORES GERENCIALES
    # ========================================================

    path(
        "dashboard/ventas-vs-compras/",
        views.VentasVsComprasView.as_view(),
        name="ventas_vs_compras",
    ),

    # ========================================================
    # MONITOREO DEL DW
    # ========================================================

    path(
        "dashboard/cobertura-dw/",
        views.IndicadoresCoberturaView.as_view(),
        name="cobertura_dw",
    ),

    path(
        "dashboard/estado-etl/",
        views.EstadoETLIndicadorView.as_view(),
        name="estado_etl_indicador",
    ),

    # ========================================================
    # Ejecución del ETL
    # ========================================================

    path(
        "etl/ejecutar/",
        views.EjecutarETLView.as_view(),
        name="etl-ejecutar",
    ),

    path(
        "etl/ejecuciones/<int:ejecucion_key>/",
        views.EstadoETLView.as_view(),
        name="etl-estado",
    ),

    path(
        "etl/ultima/",
        views.UltimaEjecucionETLView.as_view(),
        name="etl-ultima",
    ),
]