"""Doble en memoria de MongoDB para las pruebas de la autoevaluación.

Implementa solo las operaciones que usa el repositorio y aplica los índices
únicos definidos en indices.py (incluidos los parciales sobre texto), para que
las pruebas de duplicados y concurrencia reflejen las reglas reales.
"""

import copy

from bson import ObjectId
from pymongo import ReturnDocument
from pymongo.errors import DuplicateKeyError, ServerSelectionTimeoutError

from apps.autoevaluacion.indices import INDICES


class _Resultado:
    def __init__(self, inserted_id=None, modified_count=0):
        self.inserted_id = inserted_id
        self.modified_count = modified_count


def _coincide(doc, filtro):
    for campo, condicion in filtro.items():
        valor = doc.get(campo)
        if isinstance(condicion, dict) and any(k.startswith("$") for k in condicion):
            for operador, referencia in condicion.items():
                if operador == "$ne" and valor == referencia:
                    return False
                if operador == "$lte" and not (valor is not None and valor <= referencia):
                    return False
                if operador == "$in" and valor not in referencia:
                    return False
        elif valor != condicion:
            return False
    return True


class _Cursor:
    def __init__(self, docs):
        self.docs = docs

    def sort(self, claves):
        for campo, orden in reversed(claves):
            self.docs.sort(key=lambda d: (d.get(campo) is None, d.get(campo)), reverse=orden < 0)
        return self

    def limit(self, n):
        self.docs = self.docs[:n]
        return self

    def __iter__(self):
        return iter(copy.deepcopy(self.docs))


class FakeColeccion:
    def __init__(self, nombre, db):
        self.nombre = nombre
        self.db = db
        self.docs = []
        self.unicos = [i for i in INDICES if i.coleccion == nombre and i.opciones.get("unique")]

    def _comprobar_unicidad(self, nuevo, excluir=None):
        for indice in self.unicos:
            campos = [c for c, _ in indice.claves]
            if "partialFilterExpression" in indice.opciones and not all(isinstance(nuevo.get(c), str) for c in campos):
                continue
            clave = tuple(nuevo.get(c) for c in campos)
            for doc in self.docs:
                if doc is excluir:
                    continue
                if "partialFilterExpression" in indice.opciones and not all(isinstance(doc.get(c), str) for c in campos):
                    continue
                if tuple(doc.get(c) for c in campos) == clave:
                    raise DuplicateKeyError(f"E11000 duplicate key {indice.nombre}")

    def _comprobar_disponible(self):
        if self.db.caida:
            raise ServerSelectionTimeoutError("cluster.ejemplo.mongodb.net: timed out")

    def insert_one(self, documento):
        self._comprobar_disponible()
        nuevo = copy.deepcopy(documento)
        nuevo.setdefault("_id", ObjectId())
        self._comprobar_unicidad(nuevo)
        self.docs.append(nuevo)
        return _Resultado(inserted_id=nuevo["_id"])

    def find_one(self, filtro):
        self._comprobar_disponible()
        for doc in self.docs:
            if _coincide(doc, filtro):
                return copy.deepcopy(doc)
        return None

    def find(self, filtro):
        self._comprobar_disponible()
        return _Cursor([doc for doc in self.docs if _coincide(doc, filtro)])

    def _aplicar(self, doc, cambios, insertando=False):
        actualizado = copy.deepcopy(doc)
        for campo, valor in cambios.get("$set", {}).items():
            actualizado[campo] = copy.deepcopy(valor)
        if insertando:
            for campo, valor in cambios.get("$setOnInsert", {}).items():
                actualizado[campo] = copy.deepcopy(valor)
        for campo, valor in cambios.get("$inc", {}).items():
            actualizado[campo] = actualizado.get(campo, 0) + valor
        for campo in cambios.get("$unset", {}):
            actualizado.pop(campo, None)
        return actualizado

    def _reemplazar(self, doc, nuevo):
        self._comprobar_unicidad(nuevo, excluir=doc)
        self.docs[self.docs.index(doc)] = nuevo

    def update_one(self, filtro, cambios, upsert=False):
        self._comprobar_disponible()
        for doc in self.docs:
            if _coincide(doc, filtro):
                self._reemplazar(doc, self._aplicar(doc, cambios))
                return _Resultado(modified_count=1)
        if upsert:
            base = {k: v for k, v in filtro.items() if not isinstance(v, dict)}
            nuevo = self._aplicar(base, cambios, insertando=True)
            nuevo.setdefault("_id", ObjectId())
            self._comprobar_unicidad(nuevo)
            self.docs.append(nuevo)
        return _Resultado()

    def update_many(self, filtro, cambios):
        self._comprobar_disponible()
        afectados = [doc for doc in self.docs if _coincide(doc, filtro)]
        for doc in afectados:
            self._reemplazar(doc, self._aplicar(doc, cambios))
        return _Resultado(modified_count=len(afectados))

    def find_one_and_update(self, filtro, cambios, return_document=ReturnDocument.BEFORE):
        self._comprobar_disponible()
        for doc in self.docs:
            if _coincide(doc, filtro):
                nuevo = self._aplicar(doc, cambios)
                self._reemplazar(doc, nuevo)
                return copy.deepcopy(nuevo if return_document == ReturnDocument.AFTER else doc)
        return None


class FakeMongoDB:
    def __init__(self):
        self.colecciones = {}
        self.caida = False

    def __getitem__(self, nombre):
        if nombre not in self.colecciones:
            self.colecciones[nombre] = FakeColeccion(nombre, self)
        return self.colecciones[nombre]

    def command(self, nombre):
        if self.caida:
            raise ServerSelectionTimeoutError("cluster.ejemplo.mongodb.net: timed out")
        return {"ok": 1}
