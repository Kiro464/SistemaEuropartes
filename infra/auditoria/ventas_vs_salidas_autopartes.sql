/* ============================================================================
   Auditoría: enlace de cada salida de inventario con su venta (AutoPartes)
   SOLO LECTURA. Complementa ventas_vs_salidas_inventario.sql (DWAutoPartes).

   Existen dos convenciones para SALIDA_VENTA en dbo.MovimientoInventario:
   - ReferenciaTipo = 'VENTA'          -> ReferenciaId = Venta.Id
     (InventarioService.registrar_salida_venta, flujo real de despacho)
   - ReferenciaTipo = 'ORDEN_DESPACHO' -> ReferenciaId = OrdenDespacho.Id
     (comando reconstruir_datos_desarrollo)
   El DW no tiene OrdenDespacho, por eso este enlace se hace aquí.
   ========================================================================== */

DECLARE @Inicio date = DATEADD(DAY, -30, CAST(GETDATE() AS date));
DECLARE @Fin    date = CAST(GETDATE() AS date);

/* Salidas del periodo (por fecha de despacho) clasificadas por la fecha de su venta */
WITH Salidas AS (
    SELECT m.Id, m.Cantidad, m.ProductoId, CAST(m.Fecha AS date) AS FechaSalida, m.ReferenciaTipo,
           CASE WHEN m.ReferenciaTipo = 'VENTA' THEN m.ReferenciaId
                WHEN m.ReferenciaTipo = 'ORDEN_DESPACHO' THEN fv.VentaId END AS VentaId
    FROM dbo.MovimientoInventario AS m
    LEFT JOIN dbo.OrdenDespacho AS od ON m.ReferenciaTipo = 'ORDEN_DESPACHO' AND od.Id = m.ReferenciaId
    LEFT JOIN dbo.FacturaVenta AS fv ON fv.Id = od.FacturaVentaId
    WHERE m.TipoMovimiento = 'SALIDA_VENTA'
)
SELECT
    s.ReferenciaTipo,
    CASE
        WHEN s.VentaId IS NULL OR v.Id IS NULL THEN 'SIN_VENTA'
        WHEN CAST(v.Fecha AS date) < @Inicio THEN 'VENTA_ANTERIOR_AL_PERIODO'
        ELSE 'VENTA_DEL_PERIODO'
    END AS Clasificacion,
    COUNT(*) AS Movimientos,
    SUM(s.Cantidad) AS Unidades
FROM Salidas AS s
LEFT JOIN dbo.Venta AS v ON v.Id = s.VentaId
WHERE s.FechaSalida BETWEEN @Inicio AND @Fin
GROUP BY s.ReferenciaTipo,
    CASE
        WHEN s.VentaId IS NULL OR v.Id IS NULL THEN 'SIN_VENTA'
        WHEN CAST(v.Fecha AS date) < @Inicio THEN 'VENTA_ANTERIOR_AL_PERIODO'
        ELSE 'VENTA_DEL_PERIODO'
    END;

/* Unidades vendidas en el periodo y cuánto de ellas ya salió del almacén */
WITH Vendido AS (
    SELECT d.VentaId, d.ProductoId, SUM(d.Cantidad) AS Vendido
    FROM dbo.DetalleVenta AS d
    JOIN dbo.Venta AS v ON v.Id = d.VentaId
    WHERE CAST(v.Fecha AS date) BETWEEN @Inicio AND @Fin
    GROUP BY d.VentaId, d.ProductoId
),
Despachado AS (
    SELECT CASE WHEN m.ReferenciaTipo = 'VENTA' THEN m.ReferenciaId ELSE fv.VentaId END AS VentaId,
           m.ProductoId, SUM(m.Cantidad) AS Despachado
    FROM dbo.MovimientoInventario AS m
    LEFT JOIN dbo.OrdenDespacho AS od ON m.ReferenciaTipo = 'ORDEN_DESPACHO' AND od.Id = m.ReferenciaId
    LEFT JOIN dbo.FacturaVenta AS fv ON fv.Id = od.FacturaVentaId
    WHERE m.TipoMovimiento = 'SALIDA_VENTA' AND CAST(m.Fecha AS date) <= @Fin
    GROUP BY CASE WHEN m.ReferenciaTipo = 'VENTA' THEN m.ReferenciaId ELSE fv.VentaId END, m.ProductoId
)
SELECT
    SUM(v.Vendido) AS UnidadesVendidas,
    SUM(CASE WHEN ISNULL(d.Despachado, 0) > v.Vendido THEN v.Vendido ELSE ISNULL(d.Despachado, 0) END) AS YaDespachadas,
    SUM(CASE WHEN ISNULL(d.Despachado, 0) < v.Vendido THEN v.Vendido - ISNULL(d.Despachado, 0) ELSE 0 END) AS PendientesDeDespacho,
    SUM(CASE WHEN ISNULL(d.Despachado, 0) > v.Vendido THEN d.Despachado - v.Vendido ELSE 0 END) AS DespachadoDeMas
FROM Vendido AS v
LEFT JOIN Despachado AS d ON d.VentaId = v.VentaId AND d.ProductoId = v.ProductoId;

/* Coherencia de las órdenes: unidades entregadas registradas frente a salidas reales */
SELECT
    (SELECT SUM(CantidadEntregada) FROM dbo.DetalleOrden)                       AS EntregadoSegunOrdenes,
    (SELECT SUM(Cantidad) FROM dbo.MovimientoInventario WHERE TipoMovimiento = 'SALIDA_VENTA') AS SalidasRegistradas,
    (SELECT COUNT(*) FROM dbo.DetalleOrden WHERE EstadoEntrega = 'Entregado' AND CantidadEntregada < Cantidad) AS LineasEntregadasIncompletas;
