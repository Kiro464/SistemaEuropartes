from django.urls import path

from apps.movimientos.compra.views import (
    SolicitudCompraListCreateAPIView,
    SolicitudCompraDetailAPIView,
    AceptarSolicitudCompraAPIView,
    RechazarSolicitudCompraAPIView,
    CompraListCreateAPIView,
    CompraDetailAPIView,
    CrearCompraDesdeSolicitudAPIView,
    CambiarEstadoCompraAPIView,
    AprobarCompraAPIView,
    RecibirCompraAPIView,
    MovimientoInventarioListAPIView,
    HistorialComprasAPIView,
    HistorialCompraDetalleAPIView,
    ExportarHistorialComprasExcelAPIView,
    ExportarKardexExcelAPIView,
)

app_name = 'compra'

urlpatterns = [
    # Solicitudes
    path(
        'solicitudes/',
        SolicitudCompraListCreateAPIView.as_view(),
        name='solicitudes'
    ),
    path(
        'solicitudes/<int:solicitud_id>/',
        SolicitudCompraDetailAPIView.as_view(),
        name='solicitud_detalle'
    ),
    path(
        'solicitudes/<int:solicitud_id>/aceptar/',
        AceptarSolicitudCompraAPIView.as_view(),
        name='solicitud_aceptar'
    ),
    path(
        'solicitudes/<int:solicitud_id>/rechazar/',
        RechazarSolicitudCompraAPIView.as_view(),
        name='solicitud_rechazar'
    ),
    path(
        "historial/exportar/excel/",
        ExportarHistorialComprasExcelAPIView.as_view(),
        name="exportar_historial_compras_excel",
    ),
    path(
        "historial/",
        HistorialComprasAPIView.as_view(),
        name="historial_compras",
    ),
    path(
        "historial/<int:compra_id>/",
        HistorialCompraDetalleAPIView.as_view(),
        name="detalle_historial_compra",
    ),

    # Compras
    path(
        '',
        CompraListCreateAPIView.as_view(),
        name='compras'
    ),
    path(
        '<int:compra_id>/',
        CompraDetailAPIView.as_view(),
        name='compra_detalle'
    ),
    path(
        'desde-solicitud/',
        CrearCompraDesdeSolicitudAPIView.as_view(),
        name='crear_desde_solicitud'
    ),
    path(
        '<int:compra_id>/iniciar/',
        CambiarEstadoCompraAPIView.as_view(),
        {'accion': 'iniciar'},
        name='compra_iniciar'
    ),
    path(
        '<int:compra_id>/aprobar/',
        AprobarCompraAPIView.as_view(),
        name='compra_aprobar'
    ),
    path(
        '<int:compra_id>/recibir/',
        RecibirCompraAPIView.as_view(),
        name='compra_recibir'
    ),
    path(
        '<int:compra_id>/cancelar/',
        CambiarEstadoCompraAPIView.as_view(),
        {'accion': 'cancelar'},
        name='compra_cancelar'
    ),
    path(
        'inventario/exportar/excel/',
        ExportarKardexExcelAPIView.as_view(),
        name='exportar_kardex_excel'
    ),
    path(
        'inventario/',
        MovimientoInventarioListAPIView.as_view(),
        name='inventario'
    ),
]