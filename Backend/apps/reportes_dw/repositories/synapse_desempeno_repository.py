"""Consultas del reporte de desempeño de vendedores sobre Azure Synapse (SQL Serverless).

Solo lectura, por cursor y sin ORM. Reutiliza la conexión del Resumen Ejecutivo
(alias `synapse` y manejo de errores de SynapseResumenRepository) sin modificarla.

Vistas de `europartes_analytics` y columnas usadas (las de las tablas del DW):

    dbo.vw_DimEmpleado                 EmpleadoKey, EmpleadoID, Nombres, Apellidos, Cargo, EsActual  (vigente o, si no hay, la más reciente)
    dbo.vw_DimTiempo                   TiempoKey, Fecha
    dbo.vw_FactVenta                   EmpleadoKey, FechaVentaKey, Total, Cantidad
    dbo.vw_FactAutoevaluacion          EmpleadoKey, CuestionarioCodigo, CuestionarioVersion,
                                       Periodo, PuntajeTotal, Nivel, FechaEnvio
    dbo.vw_FactAutoevaluacionCategoria EmpleadoKey, CuestionarioCodigo, Periodo,
                                       CategoriaCodigo, Puntaje, Peso

Reglas para no duplicar hechos:
- Las ventas se agregan en SQL por EmpleadoID y mes antes de cruzarlas con nada más.
- Evaluaciones y categorías se consultan por separado y nunca se unen a las ventas;
  el servicio las relaciona por (EmpleadoID, Periodo) ya agregadas.
- DimEmpleado es de tipo 2 (varias EmpleadoKey por EmpleadoID). Las ventas apuntan a la
  versión vigente al vender y las evaluaciones a la vigente al exportar, así que todo se
  agrupa por EmpleadoID. El join por EmpleadoKey no multiplica filas porque es la clave.

Periodos, fechas y empleado viajan siempre como parámetros.
"""

from .synapse_resumen_repository import SynapseResumenRepository, _decimal


# Ventas por vendedor y mes. El filtro de fechas es [inicio, fin).
SQL_VENTAS = """
    SELECT
        e.EmpleadoID,
        YEAR(t.Fecha) AS anio,
        MONTH(t.Fecha) AS mes,
        SUM(f.Total) AS total_ventas,
        SUM(CAST(f.Cantidad AS bigint)) AS unidades_vendidas
    FROM dbo.vw_FactVenta AS f
    INNER JOIN dbo.vw_DimTiempo AS t ON t.TiempoKey = f.FechaVentaKey
    INNER JOIN dbo.vw_DimEmpleado AS e ON e.EmpleadoKey = f.EmpleadoKey
    WHERE t.Fecha >= %s AND t.Fecha < %s{filtro_empleado}
    GROUP BY e.EmpleadoID, YEAR(t.Fecha), MONTH(t.Fecha)
"""

# Una fila por evaluación (clave EmpleadoKey + CuestionarioCodigo + Periodo).
SQL_EVALUACIONES = """
    SELECT
        e.EmpleadoID,
        a.EmpleadoKey,
        a.CuestionarioCodigo,
        a.CuestionarioVersion,
        a.Periodo,
        a.PuntajeTotal,
        a.Nivel,
        a.FechaEnvio
    FROM dbo.vw_FactAutoevaluacion AS a
    INNER JOIN dbo.vw_DimEmpleado AS e ON e.EmpleadoKey = a.EmpleadoKey
    WHERE a.Periodo >= %s AND a.Periodo <= %s{filtro_empleado}
"""

# Una fila por categoría de evaluación; no toca las ventas.
SQL_CATEGORIAS = """
    SELECT
        c.EmpleadoKey,
        c.CuestionarioCodigo,
        c.Periodo,
        c.CategoriaCodigo,
        c.Puntaje,
        c.Peso
    FROM dbo.vw_FactAutoevaluacionCategoria AS c
    INNER JOIN dbo.vw_DimEmpleado AS e ON e.EmpleadoKey = c.EmpleadoKey
    WHERE c.Periodo >= %s AND c.Periodo <= %s{filtro_empleado}
"""

# Todas las versiones de cada empleado; el repositorio elige la vigente o, si ya no
# hay ninguna (p. ej. dado de baja), la de EmpleadoKey más alta.
SQL_EMPLEADOS = """
    SELECT
        e.EmpleadoID,
        e.EmpleadoKey,
        e.EsActual,
        e.Nombres,
        e.Apellidos,
        e.Cargo
    FROM dbo.vw_DimEmpleado AS e
    WHERE e.EmpleadoID IN ({marcadores})
"""

FILTRO_EMPLEADO = " AND e.EmpleadoID = %s"
TAMANIO_LOTE = 500


def _filtro(empleado_id):
    if empleado_id is None:
        return "", []
    return FILTRO_EMPLEADO, [empleado_id]


def _decimal_o_none(valor):
    return None if valor is None else _decimal(valor)


def _texto(valor):
    return valor.strip() if isinstance(valor, str) else valor


class SynapseDesempenoRepository(SynapseResumenRepository):
    """Hereda solo la conexión y el control de errores (`_ejecutar`)."""

    def ventas_por_periodo(self, fecha_inicio, fecha_fin_exclusiva, empleado_id=None):
        filtro, extra = _filtro(empleado_id)
        filas = self._ejecutar(
            SQL_VENTAS.format(filtro_empleado=filtro), [fecha_inicio, fecha_fin_exclusiva, *extra]
        )
        return [
            {
                "empleado_id": int(empleado),
                "periodo": f"{int(anio):04d}-{int(mes):02d}",
                "total_ventas": _decimal(total),
                "unidades_vendidas": int(unidades or 0),
            }
            for empleado, anio, mes, total, unidades in filas
            if empleado is not None and anio is not None and mes is not None
        ]

    def evaluaciones(self, periodo_desde, periodo_hasta, empleado_id=None):
        filtro, extra = _filtro(empleado_id)
        filas = self._ejecutar(
            SQL_EVALUACIONES.format(filtro_empleado=filtro), [periodo_desde, periodo_hasta, *extra]
        )
        return [
            {
                "empleado_id": int(empleado),
                "empleado_key": int(empleado_key),
                "cuestionario_codigo": _texto(codigo),
                "cuestionario_version": int(version),
                "periodo": _texto(periodo),
                "puntaje_total": _decimal(puntaje),
                "nivel": _texto(nivel),
                "fecha_envio": fecha_envio,
            }
            for empleado, empleado_key, codigo, version, periodo, puntaje, nivel, fecha_envio in filas
        ]

    def categorias(self, periodo_desde, periodo_hasta, empleado_id=None):
        filtro, extra = _filtro(empleado_id)
        filas = self._ejecutar(
            SQL_CATEGORIAS.format(filtro_empleado=filtro), [periodo_desde, periodo_hasta, *extra]
        )
        return [
            {
                "empleado_key": int(empleado_key),
                "cuestionario_codigo": _texto(codigo),
                "periodo": _texto(periodo),
                "categoria_codigo": _texto(categoria),
                "puntaje": _decimal(puntaje),
                "peso": _decimal_o_none(peso),
            }
            for empleado_key, codigo, periodo, categoria, puntaje, peso in filas
        ]

    def empleados(self, empleado_ids):
        """{EmpleadoID: datos} de la versión vigente, o de la más reciente si no hay vigente."""
        ids = sorted(set(empleado_ids))
        elegidas = {}  # EmpleadoID -> ((EsActual, EmpleadoKey), datos)
        for i in range(0, len(ids), TAMANIO_LOTE):
            lote = ids[i:i + TAMANIO_LOTE]
            sql = SQL_EMPLEADOS.format(marcadores=", ".join(["%s"] * len(lote)))
            for empleado, empleado_key, es_actual, nombres, apellidos, cargo in self._ejecutar(sql, lote):
                prioridad = (bool(es_actual), int(empleado_key))
                actual = elegidas.get(int(empleado))
                if actual is None or prioridad > actual[0]:
                    elegidas[int(empleado)] = (prioridad, {
                        "nombres": _texto(nombres),
                        "apellidos": _texto(apellidos),
                        "cargo": _texto(cargo),
                    })
        return {empleado: datos for empleado, (_, datos) in elegidas.items()}
