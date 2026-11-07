"""Datos y ayudas compartidas por las pruebas de la autoevaluación."""

import json
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from apps.autoevaluacion import cuestionario as reglas
from apps.autoevaluacion.repositorio import Repositorio


RUTA_V1 = Path(__file__).resolve().parents[1] / "cuestionarios" / "autoevaluacion_vendedor_v1.json"
MANAGUA = ZoneInfo("America/Managua")


def v1():
    with open(RUTA_V1, encoding="utf-8") as archivo:
        return json.load(archivo)


def momento(anio, mes, dia, hora=12, minuto=0, segundo=0):
    return datetime(anio, mes, dia, hora, minuto, segundo, tzinfo=MANAGUA)


def cargar_v1(db):
    documento = v1()
    return Repositorio(db).insertar_cuestionario({**documento, "contenido_hash": reglas.hash_definicion(documento)})


# Respuestas válidas con un puntaje conocido:
#   VENTAS       (80*3 + 75) / 4            = 78.75
#   ATENCION     (100 + 50 + 75) / 3        = 75.00
#   PRODUCTO     (75 + 100 + 70) / 3        = 81.67
#   SEGUIMIENTO  (25 + 50) / 2              = 37.50
#   ORGANIZACION (100 + 100 + 75) / 3       = 91.67
#   EQUIPO       (75 + 75) / 2              = 75.00
#   Total = (78.75*25 + 75*20 + 81.67*15 + 37.5*15 + 91.67*15 + 75*10) / 100 = 73.81
PUNTAJE_ESPERADO = 73.81


def respuestas_validas(**cambios):
    valores = {
        "V01": "90_99", "V04": 4,
        "A01": 5, "A02": 3, "A03": "SI", "A04": 4,
        "P01": 4, "P02": 5, "P03": "UNA",
        "S01": 12, "S02": 2, "S03": 3,
        "O01": 5, "O02": 5, "O03": 4,
        "E01": 4, "E02": 4, "E03": "",
    }
    valores.update(cambios)
    return [{"pregunta_id": pid, "valor": valor} for pid, valor in valores.items() if valor is not ...]
