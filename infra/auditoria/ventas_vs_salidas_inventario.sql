/* ============================================================================
   Auditoría: unidades vendidas frente a salidas de inventario (DWAutoPartes)
   SOLO LECTURA. Ninguna consulta modifica datos.

   Definiciones confirmadas en el código:
   - Ventas:  dbo.FactVenta, fecha = FechaVentaKey -> DimTiempo.Fecha
              (apps/reportes_dw/services/ventas_service.py, dashboard_service.py)
   - Salidas: dbo.FactMovimientoInventario con DimTipoMovimiento.TipoMovimientoCodigo
              = 'SALIDA_VENTA', fecha = FechaMovimientoKey -> DimTiempo.Fecha
              (apps/reportes_dw/services/inventario_service.py; la web toma la
              cantidad de ese tipo en dashboardController.renderMovimientos)
   - Origen OLTP de la salida: InventarioService.registrar_salida_venta(), llamada al
     VALIDAR UN DESPACHO (OrdenDespacho), con ReferenciaTipo='VENTA',
     ReferenciaID=VentaID y fecha = momento del despacho.

   Ajusta @Inicio y @Fin. "Últimos 30 días" del dashboard = hoy-30 .. hoy (ambos
   incluidos), comparando DimTiempo.Fecha (fecha local de Managua).
   ========================================================================== */

DECLARE @Inicio date = DATEADD(DAY, -30, CAST(GETDATE() AS date));
DECLARE @Fin    date = CAST(GETDATE() AS date);

/* 1 y 4. Unidades vendidas y cantidad de ventas en el periodo (por fecha de venta) */
SELECT
    SUM(fv.Cantidad)               AS UnidadesVendidas,
    COUNT(DISTINCT fv.VentaID)     AS Ventas,
    COUNT(*)                       AS LineasDeVenta
FROM dbo.FactVenta AS fv
JOIN dbo.DimTiempo AS t ON t.TiempoKey = fv.FechaVentaKey
WHERE t.Fecha BETWEEN @Inicio AND @Fin;

/* 2, 3 y 7. Movimientos por tipo en el periodo (por fecha de movimiento) */
SELECT
    tm.TipoMovimientoCodigo,
    COUNT(*)              AS Movimientos,
    SUM(fm.Cantidad)      AS Unidades,
    SUM(fm.CantidadNeta)  AS UnidadesNetas
FROM dbo.FactMovimientoInventario AS fm
JOIN dbo.DimTipoMovimiento AS tm ON tm.TipoMovimientoKey = fm.TipoMovimientoKey
JOIN dbo.DimTiempo AS t ON t.TiempoKey = fm.FechaMovimientoKey
WHERE t.Fecha BETWEEN @Inicio AND @Fin
GROUP BY tm.TipoMovimientoCodigo
ORDER BY tm.TipoMovimientoCodigo;

/* 5 y 6. Salidas del periodo según la venta que las origina:
   - VENTA_EN_PERIODO: la venta se facturó dentro del periodo
   - VENTA_ANTERIOR:   la venta se facturó antes (despacho posterior)
   - SIN_VENTA_EN_DW:  ReferenciaID no existe en FactVenta */
WITH Salidas AS (
    SELECT fm.MovimientoInventarioID, fm.ReferenciaTipo, fm.ReferenciaID, fm.Cantidad
    FROM dbo.FactMovimientoInventario AS fm
    JOIN dbo.DimTipoMovimiento AS tm ON tm.TipoMovimientoKey = fm.TipoMovimientoKey
    JOIN dbo.DimTiempo AS t ON t.TiempoKey = fm.FechaMovimientoKey
    WHERE tm.TipoMovimientoCodigo = 'SALIDA_VENTA'
      AND t.Fecha BETWEEN @Inicio AND @Fin
),
FechaVenta AS (
    SELECT fv.VentaID, MIN(t.Fecha) AS FechaVenta
    FROM dbo.FactVenta AS fv
    JOIN dbo.DimTiempo AS t ON t.TiempoKey = fv.FechaVentaKey
    GROUP BY fv.VentaID
)
SELECT
    CASE
        WHEN s.ReferenciaTipo <> 'VENTA' OR s.ReferenciaID IS NULL THEN 'SIN_REFERENCIA_VENTA'
        WHEN v.VentaID IS NULL THEN 'SIN_VENTA_EN_DW'
        WHEN v.FechaVenta < @Inicio THEN 'VENTA_ANTERIOR'
        WHEN v.FechaVenta > @Fin THEN 'VENTA_POSTERIOR'
        ELSE 'VENTA_EN_PERIODO'
    END                       AS Clasificacion,
    COUNT(*)                  AS Movimientos,
    SUM(s.Cantidad)           AS Unidades
FROM Salidas AS s
LEFT JOIN FechaVenta AS v ON v.VentaID = s.ReferenciaID
GROUP BY
    CASE
        WHEN s.ReferenciaTipo <> 'VENTA' OR s.ReferenciaID IS NULL THEN 'SIN_REFERENCIA_VENTA'
        WHEN v.VentaID IS NULL THEN 'SIN_VENTA_EN_DW'
        WHEN v.FechaVenta < @Inicio THEN 'VENTA_ANTERIOR'
        WHEN v.FechaVenta > @Fin THEN 'VENTA_POSTERIOR'
        ELSE 'VENTA_EN_PERIODO'
    END;

/* Ventas del periodo según su despacho (unidades vendidas frente a despachadas,
   por venta y producto; el producto se compara por ProductoID porque DimProducto
   es SCD2 y la clave puede cambiar entre la venta y el despacho). */
WITH Vendido AS (
    SELECT fv.VentaID, dp.ProductoID, SUM(fv.Cantidad) AS Vendido
    FROM dbo.FactVenta AS fv
    JOIN dbo.DimTiempo AS t ON t.TiempoKey = fv.FechaVentaKey
    JOIN dbo.DimProducto AS dp ON dp.ProductoKey = fv.ProductoKey
    WHERE t.Fecha BETWEEN @Inicio AND @Fin
    GROUP BY fv.VentaID, dp.ProductoID
),
Despachado AS (
    SELECT fm.ReferenciaID AS VentaID, dp.ProductoID,
           SUM(fm.Cantidad) AS Despachado,
           SUM(CASE WHEN t.Fecha <= @Fin THEN fm.Cantidad ELSE 0 END) AS DespachadoHastaFin
    FROM dbo.FactMovimientoInventario AS fm
    JOIN dbo.DimTipoMovimiento AS tm ON tm.TipoMovimientoKey = fm.TipoMovimientoKey
    JOIN dbo.DimTiempo AS t ON t.TiempoKey = fm.FechaMovimientoKey
    JOIN dbo.DimProducto AS dp ON dp.ProductoKey = fm.ProductoKey
    WHERE tm.TipoMovimientoCodigo = 'SALIDA_VENTA' AND fm.ReferenciaTipo = 'VENTA'
    GROUP BY fm.ReferenciaID, dp.ProductoID
)
SELECT
    SUM(v.Vendido)                                                        AS UnidadesVendidas,
    SUM(COALESCE(d.DespachadoHastaFin, 0))                                AS DespachadasHastaFin,
    SUM(CASE WHEN d.VentaID IS NULL THEN v.Vendido ELSE 0 END)            AS SinNingunDespacho,
    SUM(CASE WHEN COALESCE(d.Despachado, 0) > v.Vendido THEN d.Despachado - v.Vendido ELSE 0 END) AS DespachadoDeMas
FROM Vendido AS v
LEFT JOIN Despachado AS d ON d.VentaID = v.VentaID AND d.ProductoID = v.ProductoID;

/* 8. Días entre la fecha de venta y la fecha de su salida (todas las salidas por venta) */
WITH Pares AS (
    SELECT fm.MovimientoInventarioID, fm.Cantidad,
           tm2.Fecha AS FechaSalida,
           (SELECT MIN(t.Fecha) FROM dbo.FactVenta AS fv
            JOIN dbo.DimTiempo AS t ON t.TiempoKey = fv.FechaVentaKey
            WHERE fv.VentaID = fm.ReferenciaID) AS FechaVenta
    FROM dbo.FactMovimientoInventario AS fm
    JOIN dbo.DimTipoMovimiento AS tm ON tm.TipoMovimientoKey = fm.TipoMovimientoKey
    JOIN dbo.DimTiempo AS tm2 ON tm2.TiempoKey = fm.FechaMovimientoKey
    WHERE tm.TipoMovimientoCodigo = 'SALIDA_VENTA' AND fm.ReferenciaTipo = 'VENTA'
)
SELECT
    CASE
        WHEN FechaVenta IS NULL THEN 'sin venta en DW'
        WHEN DATEDIFF(DAY, FechaVenta, FechaSalida) < 0 THEN 'salida antes de la venta'
        WHEN DATEDIFF(DAY, FechaVenta, FechaSalida) = 0 THEN 'mismo día'
        WHEN DATEDIFF(DAY, FechaVenta, FechaSalida) <= 7 THEN '1 a 7 días'
        WHEN DATEDIFF(DAY, FechaVenta, FechaSalida) <= 30 THEN '8 a 30 días'
        ELSE 'más de 30 días'
    END AS Desfase,
    COUNT(*) AS Movimientos,
    SUM(Cantidad) AS Unidades
FROM Pares
GROUP BY
    CASE
        WHEN FechaVenta IS NULL THEN 'sin venta en DW'
        WHEN DATEDIFF(DAY, FechaVenta, FechaSalida) < 0 THEN 'salida antes de la venta'
        WHEN DATEDIFF(DAY, FechaVenta, FechaSalida) = 0 THEN 'mismo día'
        WHEN DATEDIFF(DAY, FechaVenta, FechaSalida) <= 7 THEN '1 a 7 días'
        WHEN DATEDIFF(DAY, FechaVenta, FechaSalida) <= 30 THEN '8 a 30 días'
        ELSE 'más de 30 días'
    END;

/* Integridad: duplicados de hechos y de tipos (afectan a las sumas del dashboard) */
SELECT 'FactMovimientoInventario' AS Tabla, COUNT(*) AS Filas, COUNT(DISTINCT MovimientoInventarioID) AS IDsDistintos FROM dbo.FactMovimientoInventario
UNION ALL
SELECT 'FactVenta', COUNT(*), COUNT(DISTINCT DetalleVentaID) FROM dbo.FactVenta;

SELECT TipoMovimientoCodigo, COUNT(*) AS FilasEnDimension
FROM dbo.DimTipoMovimiento
GROUP BY TipoMovimientoCodigo;
