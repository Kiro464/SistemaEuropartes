"""Acceso a MongoDB de la autoevaluación.

Garantías que no dependen solo del código:
- Una evaluación por empleado, cuestionario y periodo: índice único
  ux_empleado_cuestionario_periodo. Una segunda inserción simultánea recibe
  DuplicateKeyError y se trata como conflicto.
- Concurrencia optimista: cada evaluación tiene un campo `version`; una
  actualización solo se aplica si la versión coincide (filtro atómico en
  find_one_and_update), así dos dispositivos no se pisan en silencio.

Los documentos también cumplen el esquema de referencia del equipo
(Validacion_Schema.json): django_username/estado en empleados y
empleado_username/cuestionario_id/respuestas[{pregunta_id, valor}]/estado/
fecha_envio en las respuestas.
"""

from bson import ObjectId
from bson.errors import InvalidId
from pymongo import DESCENDING, ReturnDocument, UpdateOne
from pymongo.errors import DuplicateKeyError

from .indices import CUESTIONARIOS, EMPLEADOS, RESPUESTAS


class EvaluacionDuplicada(Exception):
    """Otra petición creó la evaluación del mismo periodo al mismo tiempo."""


class Repositorio:

    def __init__(self, db):
        self.db = db

    # ---------------- cuestionarios ----------------

    def cuestionario_vigente(self, rol, periodo):
        """Último cuestionario publicado para el rol, vigente en ese periodo."""
        cursor = (
            self.db[CUESTIONARIOS]
            .find({"rol_destino": rol, "estado": "publicado", "vigente_desde": {"$lte": periodo}})
            .sort([("vigente_desde", DESCENDING), ("version", DESCENDING)])
            .limit(1)
        )
        return next(iter(cursor), None)

    def cuestionario_por_id(self, cuestionario_id):
        try:
            oid = cuestionario_id if isinstance(cuestionario_id, ObjectId) else ObjectId(str(cuestionario_id))
        except (InvalidId, TypeError):
            return None
        return self.db[CUESTIONARIOS].find_one({"_id": oid})

    def cuestionario_por_version(self, codigo, version):
        return self.db[CUESTIONARIOS].find_one({"codigo": codigo, "version": version})

    def insertar_cuestionario(self, documento):
        return self.db[CUESTIONARIOS].insert_one(documento).inserted_id

    # ---------------- empleados ----------------

    def sincronizar_empleado(self, perfil, momento):
        """Crea o actualiza el perfil (clave: Empleado.Id de AutoPartes).

        Si el username pasó a otro empleado (cuenta reasignada), se libera
        primero en el perfil anterior para respetar ux_username.
        """
        empleados = self.db[EMPLEADOS]
        username = perfil.get("username")
        if username:
            empleados.update_many(
                {"username": username, "_id": {"$ne": perfil["_id"]}},
                {"$unset": {"username": ""}, "$set": {"actualizado_en": momento}},
            )
        cambios = {clave: valor for clave, valor in perfil.items() if clave != "_id"}
        cambios["actualizado_en"] = momento
        empleados.update_one(
            {"_id": perfil["_id"]},
            {"$set": cambios, "$setOnInsert": {"fecha_creacion": momento}},
            upsert=True,
        )

    def sincronizar_empleados(self, perfiles, momento):
        """Upsert perfiles en lote y desactiva vendedores ausentes sin borrarlos."""
        empleados = self.db[EMPLEADOS]
        ids_activos = [p["_id"] for p in perfiles]
        # Libera usernames reasignados antes de aplicar los upserts individuales.
        for perfil in perfiles:
            username = perfil.get("username")
            if username:
                empleados.update_many(
                    {"username": username, "_id": {"$ne": perfil["_id"]}},
                    {"$unset": {"username": ""}, "$set": {"actualizado_en": momento}},
                )
        operaciones = []
        for perfil in perfiles:
            cambios = {k: v for k, v in perfil.items() if k != "_id"}
            cambios.update({"actualizado_en": momento, "fecha_sincronizacion": momento})
            operaciones.append(UpdateOne(
                {"_id": perfil["_id"]},
                {"$set": cambios, "$setOnInsert": {"fecha_creacion": momento}},
                upsert=True,
            ))
        if operaciones:
            empleados.bulk_write(operaciones, ordered=False)
        filtro = {"rol": "Vendedor", "activo": True}
        if ids_activos:
            filtro["_id"] = {"$nin": ids_activos}
        desactivar = empleados.update_many(filtro, {"$set": {
            "activo": False, "estado": "inactivo", "fecha_sincronizacion": momento,
            "actualizado_en": momento,
        }})
        return {"sincronizados": len(perfiles), "desactivados": desactivar.modified_count, "errores": 0}

    # ---------------- respuestas ----------------

    def evaluacion(self, empleado_id, cuestionario_codigo, periodo):
        return self.db[RESPUESTAS].find_one({
            "empleado_id": empleado_id,
            "cuestionario_codigo": cuestionario_codigo,
            "periodo": periodo,
        })

    def evaluacion_del_periodo(self, empleado_id, periodo):
        """Evaluación del empleado en un periodo, sea cual sea el cuestionario."""
        cursor = (
            self.db[RESPUESTAS]
            .find({"empleado_id": empleado_id, "periodo": periodo})
            .sort([("actualizado_en", DESCENDING)])
            .limit(1)
        )
        return next(iter(cursor), None)

    def crear_evaluacion(self, documento):
        try:
            documento["_id"] = self.db[RESPUESTAS].insert_one(documento).inserted_id
        except DuplicateKeyError as exc:
            raise EvaluacionDuplicada() from exc
        return documento

    def actualizar_evaluacion(self, evaluacion_id, version_esperada, cambios):
        """Aplica los cambios solo si la versión coincide. None = conflicto."""
        return self.db[RESPUESTAS].find_one_and_update(
            {"_id": evaluacion_id, "version": version_esperada},
            {"$set": cambios, "$inc": {"version": 1}},
            return_document=ReturnDocument.AFTER,
        )

    def historial(self, empleado_id, limite):
        cursor = (
            self.db[RESPUESTAS]
            .find({"empleado_id": empleado_id})
            .sort([("periodo", DESCENDING)])
            .limit(limite)
        )
        return list(cursor)
