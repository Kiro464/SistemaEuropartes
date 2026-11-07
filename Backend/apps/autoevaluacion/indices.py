"""Colecciones e índices de la autoevaluación en MongoDB.

Las reglas de unicidad viven en la base, no solo en el código: aunque lleguen
dos envíos a la vez desde instancias distintas del backend, MongoDB rechaza el
segundo (DuplicateKeyError).
"""

from dataclasses import dataclass, field

from pymongo import ASCENDING, DESCENDING


CUESTIONARIOS = "cuestionarios"
EMPLEADOS = "empleados"
RESPUESTAS = "respuestas_autoevaluacion"

COLECCIONES = (CUESTIONARIOS, EMPLEADOS, RESPUESTAS)


@dataclass(frozen=True)
class Indice:
    coleccion: str
    nombre: str
    claves: tuple
    opciones: dict = field(default_factory=dict)

    def describir(self):
        claves = ", ".join(f"{campo}:{orden}" for campo, orden in self.claves)
        extras = []
        if self.opciones.get("unique"):
            extras.append("único")
        if "partialFilterExpression" in self.opciones:
            extras.append("parcial")
        sufijo = f" ({', '.join(extras)})" if extras else ""
        return f"{self.coleccion}.{self.nombre} {{{claves}}}{sufijo}"


# Solo documentos donde el campo es texto: los nulos o ausentes no compiten
# por la unicidad (p. ej. empleados sin usuario o envíos sin id_cliente).
def _solo_texto(campo):
    return {"partialFilterExpression": {campo: {"$type": "string"}}}


INDICES = (
    # ---------------- cuestionarios ----------------
    # Cada versión de un cuestionario es única.
    Indice(CUESTIONARIOS, "ux_codigo_version",
           (("codigo", ASCENDING), ("version", ASCENDING)),
           {"unique": True}),
    # Buscar el cuestionario publicado vigente para un rol.
    Indice(CUESTIONARIOS, "ix_rol_estado_vigencia",
           (("rol_destino", ASCENDING), ("estado", ASCENDING), ("vigente_desde", DESCENDING))),

    # ---------------- empleados ----------------
    # _id es el Empleado.Id de AutoPartes (único por definición).
    Indice(EMPLEADOS, "ux_username",
           (("username", ASCENDING),),
           {"unique": True, **_solo_texto("username")}),
    Indice(EMPLEADOS, "ix_activo_rol",
           (("activo", ASCENDING), ("rol", ASCENDING))),

    # ---------------- respuestas_autoevaluacion ----------------
    # Regla de negocio: una autoevaluación por empleado, cuestionario y mes.
    Indice(RESPUESTAS, "ux_empleado_cuestionario_periodo",
           (("empleado_id", ASCENDING), ("cuestionario_codigo", ASCENDING), ("periodo", ASCENDING)),
           {"unique": True}),
    # Reintentos de la app: el mismo envío (mismo UUID) no se guarda dos veces.
    Indice(RESPUESTAS, "ux_id_cliente",
           (("id_cliente", ASCENDING),),
           {"unique": True, **_solo_texto("id_cliente")}),
    # Exportación incremental al DW.
    Indice(RESPUESTAS, "ix_enviado_en",
           (("enviado_en", ASCENDING),)),
    # Consultas por mes y vendedor.
    Indice(RESPUESTAS, "ix_periodo_empleado",
           (("periodo", ASCENDING), ("empleado_id", ASCENDING))),
)


# ============================================================
# Aplicación de los índices
# ============================================================

CREADO = "creado"
EXISTENTE = "existente"
PENDIENTE = "pendiente"
CONFLICTO = "conflicto"


def _claves(pares):
    """Claves comparables: 1 y 1.0 son iguales; "text" o "2dsphere" se dejan como texto."""
    normalizadas = []
    for campo, orden in pares:
        try:
            orden = int(orden)
        except (TypeError, ValueError):
            orden = str(orden)
        normalizadas.append((campo, orden))
    return normalizadas


def _coincide(indice, info):
    """El índice existente tiene las mismas claves y las mismas opciones relevantes."""
    if _claves(info.get("key", [])) != _claves(indice.claves):
        return False
    if bool(info.get("unique")) != bool(indice.opciones.get("unique")):
        return False
    return info.get("partialFilterExpression") == indice.opciones.get("partialFilterExpression")


def aplicar_indices(db, simular=False):
    """Crea los índices que faltan. Nunca borra ni modifica índices existentes.

    Devuelve [(Indice, estado, detalle)] con estado creado, existente,
    pendiente (simulación) o conflicto (mismo nombre o claves con otra definición).
    """
    resultados = []
    existentes_por_coleccion = {}

    for indice in INDICES:
        existentes = existentes_por_coleccion.get(indice.coleccion)
        if existentes is None:
            existentes = dict(db[indice.coleccion].index_information())
            existentes_por_coleccion[indice.coleccion] = existentes

        actual = existentes.get(indice.nombre)
        if actual is not None:
            if _coincide(indice, actual):
                resultados.append((indice, EXISTENTE, ""))
            else:
                resultados.append((indice, CONFLICTO, "ya existe con ese nombre pero otra definición"))
            continue

        mismas_claves = [
            nombre for nombre, info in existentes.items()
            if _claves(info.get("key", [])) == _claves(indice.claves)
        ]
        if mismas_claves:
            resultados.append((indice, CONFLICTO, f"las mismas claves ya existen como '{mismas_claves[0]}'"))
            continue

        if simular:
            resultados.append((indice, PENDIENTE, ""))
            continue

        db[indice.coleccion].create_index(list(indice.claves), name=indice.nombre, **indice.opciones)
        existentes[indice.nombre] = {"key": list(indice.claves), **indice.opciones}
        resultados.append((indice, CREADO, ""))

    return resultados
