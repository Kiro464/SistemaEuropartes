from django.db import connections


class MonitoreoRepository:
    """Consultas técnicas de auditoría del DW/ETL."""

    def conteos_hechos(self):
        tablas = {
            "ventas": "FactVenta",
            "compras": "FactCompra",
            "solicitudes": "FactSolicitudCompra",
            "movimientos_inventario": "FactMovimientoInventario",
            "stock_diario": "FactStockDiario",
        }
        with connections["dw"].cursor() as cursor:
            resultado = {}
            for nombre, tabla in tablas.items():
                cursor.execute(f"SELECT COUNT(*) FROM dbo.[{tabla}]")
                resultado[nombre] = cursor.fetchone()[0]
        return resultado


class MonitoreoETLRepository:
    """Lectura de las tablas de control del ETL en DWAutoPartes."""

    def ultima_ejecucion(self):
        with connections["dw"].cursor() as cursor:
            cursor.execute("""
                SELECT TOP 1
                    EjecucionKey, Paquete, FechaInicio, FechaFin, Estado,
                    RegistrosExtraidos, RegistrosInsertados,
                    RegistrosActualizados, RegistrosRechazados, Mensaje
                FROM dbo.ETL_Ejecucion
                ORDER BY EjecucionKey DESC
            """)
            row = cursor.fetchone()
        if not row:
            return None
        fields = [
            "ejecucion_key", "paquete", "fecha_inicio", "fecha_fin", "estado",
            "registros_extraidos", "registros_insertados", "registros_actualizados",
            "registros_rechazados", "mensaje",
        ]
        return dict(zip(fields, row))
