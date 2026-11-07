"""Reglas del cuestionario: formato de la plantilla, preguntas visibles,
validación de respuestas y cálculo de puntajes.

No depende de MongoDB ni de Django, para poder probarse de forma aislada.

Estructura de la plantilla (basada en el diseño del equipo: categorías ->
preguntas con id, tipo, escala/opciones, obligatoria y condiciones):

    {codigo, version, titulo, descripcion, estado, periodicidad: "mensual",
     rol_destino, vigente_desde: "AAAA-MM", niveles: [{minimo, nombre}],
     categorias: [{codigo, nombre, peso, preguntas: [
        {id, texto, tipo: escala|opcion|numero|texto, obligatoria, puntua, peso,
         escala: {minimo, maximo, etiquetas}, opciones: [{valor, etiqueta, puntos}],
         minimo, maximo, entero, min_longitud, max_longitud,
         mostrar_si: {pregunta_id, valores: [...]}}]}]}

Puntuación (siempre en el servidor):
- escala: (valor - minimo) / (maximo - minimo) * 100.
- opcion: los puntos (0-100) de la opción elegida.
- numero y texto no puntúan: son datos de contexto.
- Categoría: media ponderada (por el peso de cada pregunta) de las preguntas
  visibles que puntúan.
- Total: media ponderada de las categorías por su peso.
"""

import hashlib
import json
import re


TIPOS = ("escala", "opcion", "numero", "texto")
ESTADOS = ("borrador", "publicado", "retirado")
RE_CODIGO = re.compile(r"^[A-Z][A-Z0-9_]{1,49}$")
RE_PERIODO = re.compile(r"^\d{4}-(0[1-9]|1[0-2])$")
RE_ID_PREGUNTA = re.compile(r"^[A-Z][A-Z0-9_]{0,19}$")

# Campos que forman la definición (el contenido versionado). Los metadatos que
# añade la carga (fechas, hash, _id) no cuentan para comparar versiones.
CAMPOS_DEFINICION = (
    "codigo", "version", "titulo", "descripcion", "estado", "periodicidad",
    "rol_destino", "vigente_desde", "niveles", "categorias",
)


class CuestionarioInvalido(Exception):
    def __init__(self, errores):
        super().__init__("; ".join(errores))
        self.errores = errores


class RespuestasInvalidas(Exception):
    """errores: {pregunta_id o "respuestas": mensaje}."""

    def __init__(self, errores):
        super().__init__("La autoevaluación tiene respuestas no válidas.")
        self.errores = errores


# ============================================================
# Plantilla
# ============================================================

def definicion(documento):
    return {campo: documento.get(campo) for campo in CAMPOS_DEFINICION}


def hash_definicion(documento):
    texto = json.dumps(definicion(documento), sort_keys=True, ensure_ascii=False, separators=(",", ":"))
    return hashlib.sha256(texto.encode("utf-8")).hexdigest()


def preguntas(cuestionario):
    """(categoria, pregunta) en el orden del cuestionario."""
    for categoria in cuestionario.get("categorias", []):
        for pregunta in categoria.get("preguntas", []):
            yield categoria, pregunta


def _numero(valor):
    return isinstance(valor, (int, float)) and not isinstance(valor, bool)


def _entero(valor):
    return isinstance(valor, int) and not isinstance(valor, bool)


def validar_definicion(cuestionario):
    """Lanza CuestionarioInvalido con todos los problemas encontrados."""
    errores = []

    def error(mensaje):
        errores.append(mensaje)

    if not isinstance(cuestionario, dict):
        raise CuestionarioInvalido(["El cuestionario debe ser un objeto JSON."])

    if not RE_CODIGO.match(str(cuestionario.get("codigo") or "")):
        error("codigo: usa mayúsculas, números y guion bajo (p. ej. AUTOEV_VENDEDOR).")
    if not _entero(cuestionario.get("version")) or cuestionario["version"] < 1:
        error("version: debe ser un entero mayor o igual a 1.")
    for campo in ("titulo", "rol_destino"):
        if not isinstance(cuestionario.get(campo), str) or not cuestionario[campo].strip():
            error(f"{campo}: es obligatorio.")
    if cuestionario.get("estado") not in ESTADOS:
        error(f"estado: debe ser uno de {', '.join(ESTADOS)}.")
    if cuestionario.get("periodicidad") != "mensual":
        error("periodicidad: debe ser 'mensual'.")
    if not RE_PERIODO.match(str(cuestionario.get("vigente_desde") or "")):
        error("vigente_desde: debe tener el formato AAAA-MM.")

    niveles = cuestionario.get("niveles")
    if not isinstance(niveles, list) or not niveles:
        error("niveles: debe ser una lista no vacía.")
    else:
        minimos = []
        for nivel in niveles:
            if not isinstance(nivel, dict) or not _numero(nivel.get("minimo")) or not 0 <= nivel["minimo"] <= 100 \
                    or not isinstance(nivel.get("nombre"), str) or not nivel["nombre"].strip():
                error("niveles: cada nivel necesita 'minimo' (0-100) y 'nombre'.")
                break
            minimos.append(nivel["minimo"])
        else:
            if minimos != sorted(minimos, reverse=True) or len(set(minimos)) != len(minimos):
                error("niveles: ordénalos de mayor a menor 'minimo', sin repetir.")
            if 0 not in minimos:
                error("niveles: debe existir un nivel con minimo 0.")

    categorias = cuestionario.get("categorias")
    if not isinstance(categorias, list) or not categorias:
        error("categorias: debe ser una lista no vacía.")
        raise CuestionarioInvalido(errores)

    codigos_categoria = set()
    vistas = {}  # id -> pregunta, solo las ya recorridas (las condiciones miran hacia atrás)
    for indice, categoria in enumerate(categorias, start=1):
        prefijo = f"categorias[{indice}]"
        if not isinstance(categoria, dict):
            error(f"{prefijo}: debe ser un objeto.")
            continue
        codigo = categoria.get("codigo")
        if not RE_CODIGO.match(str(codigo or "")):
            error(f"{prefijo}.codigo: no es válido.")
        elif codigo in codigos_categoria:
            error(f"{prefijo}.codigo: '{codigo}' está repetido.")
        codigos_categoria.add(codigo)
        if not isinstance(categoria.get("nombre"), str) or not categoria["nombre"].strip():
            error(f"{prefijo}.nombre: es obligatorio.")
        if not _numero(categoria.get("peso")) or categoria["peso"] <= 0:
            error(f"{prefijo}.peso: debe ser un número mayor que 0.")

        lista = categoria.get("preguntas")
        if not isinstance(lista, list) or not lista:
            error(f"{prefijo}.preguntas: debe ser una lista no vacía.")
            continue

        puntuables_fijas = 0
        for pregunta in lista:
            _validar_pregunta(pregunta, vistas, error)
            if isinstance(pregunta, dict):
                if pregunta.get("puntua") and not pregunta.get("mostrar_si"):
                    puntuables_fijas += 1
                if RE_ID_PREGUNTA.match(str(pregunta.get("id") or "")):
                    vistas.setdefault(pregunta["id"], pregunta)
        if puntuables_fijas == 0:
            error(f"{prefijo}: necesita al menos una pregunta que puntúe y no dependa de otra.")

    if errores:
        raise CuestionarioInvalido(errores)


def _validar_pregunta(pregunta, vistas, error):
    if not isinstance(pregunta, dict):
        error("preguntas: cada pregunta debe ser un objeto.")
        return
    pid = pregunta.get("id")
    if not RE_ID_PREGUNTA.match(str(pid or "")):
        error(f"pregunta '{pid}': id no válido (p. ej. V01).")
        return
    if pid in vistas:
        error(f"{pid}: id repetido.")
    if not isinstance(pregunta.get("texto"), str) or not pregunta["texto"].strip():
        error(f"{pid}.texto: es obligatorio.")
    tipo = pregunta.get("tipo")
    if tipo not in TIPOS:
        error(f"{pid}.tipo: debe ser uno de {', '.join(TIPOS)}.")
        return
    if not isinstance(pregunta.get("obligatoria"), bool):
        error(f"{pid}.obligatoria: debe ser true o false.")
    puntua = pregunta.get("puntua", False)
    if not isinstance(puntua, bool):
        error(f"{pid}.puntua: debe ser true o false.")
    if puntua:
        if tipo not in ("escala", "opcion"):
            error(f"{pid}: solo las preguntas de escala u opción pueden puntuar.")
        if not _numero(pregunta.get("peso")) or pregunta["peso"] <= 0:
            error(f"{pid}.peso: debe ser un número mayor que 0 si la pregunta puntúa.")

    if tipo == "escala":
        escala = pregunta.get("escala") or {}
        minimo, maximo = escala.get("minimo"), escala.get("maximo")
        if not (_entero(minimo) and _entero(maximo) and maximo > minimo):
            error(f"{pid}.escala: minimo y maximo deben ser enteros con maximo > minimo.")
        else:
            etiquetas = escala.get("etiquetas") or {}
            if not isinstance(etiquetas, dict) or any(
                not str(k).lstrip("-").isdigit() or not minimo <= int(k) <= maximo for k in etiquetas
            ):
                error(f"{pid}.escala.etiquetas: las claves deben ser valores de la escala.")
    elif tipo == "opcion":
        opciones = pregunta.get("opciones")
        if not isinstance(opciones, list) or len(opciones) < 2:
            error(f"{pid}.opciones: necesita al menos dos opciones.")
        else:
            valores = set()
            for opcion in opciones:
                valor = opcion.get("valor") if isinstance(opcion, dict) else None
                if not isinstance(valor, str) or not valor.strip() or not isinstance(opcion.get("etiqueta"), str):
                    error(f"{pid}.opciones: cada opción necesita 'valor' y 'etiqueta' de texto.")
                    break
                if valor in valores:
                    error(f"{pid}.opciones: el valor '{valor}' está repetido.")
                valores.add(valor)
                puntos = opcion.get("puntos")
                if puntua and (not _numero(puntos) or not 0 <= puntos <= 100):
                    error(f"{pid}.opciones: '{valor}' necesita 'puntos' entre 0 y 100.")
                if not puntua and puntos is not None:
                    error(f"{pid}.opciones: '{valor}' tiene puntos pero la pregunta no puntúa.")
    elif tipo == "numero":
        minimo, maximo = pregunta.get("minimo"), pregunta.get("maximo")
        if not (_numero(minimo) and _numero(maximo) and maximo >= minimo):
            error(f"{pid}: minimo y maximo deben ser números con maximo >= minimo.")
        if not isinstance(pregunta.get("entero", False), bool):
            error(f"{pid}.entero: debe ser true o false.")
    elif tipo == "texto":
        maximo = pregunta.get("max_longitud", 500)
        minimo = pregunta.get("min_longitud", 1 if pregunta.get("obligatoria") else 0)
        if not (_entero(maximo) and 1 <= maximo <= 2000):
            error(f"{pid}.max_longitud: debe ser un entero entre 1 y 2000.")
        elif not (_entero(minimo) and 0 <= minimo <= maximo):
            error(f"{pid}.min_longitud: debe ser un entero entre 0 y max_longitud.")

    condicion = pregunta.get("mostrar_si")
    if condicion is not None:
        padre_id = condicion.get("pregunta_id") if isinstance(condicion, dict) else None
        padre = vistas.get(padre_id)
        if padre is None:
            error(f"{pid}.mostrar_si: debe referirse a una pregunta anterior.")
        elif padre.get("tipo") != "opcion":
            error(f"{pid}.mostrar_si: solo puede depender de una pregunta de opción.")
        else:
            valores = condicion.get("valores")
            permitidos = {o.get("valor") for o in padre.get("opciones", []) if isinstance(o, dict)}
            if not isinstance(valores, list) or not valores or not set(valores) <= permitidos:
                error(f"{pid}.mostrar_si.valores: deben ser opciones de {padre_id}.")


# ============================================================
# Respuestas
# ============================================================

def es_visible(pregunta, valores, por_id):
    """Una pregunta condicional se muestra si su pregunta padre es visible y
    se respondió con uno de los valores indicados."""
    condicion = pregunta.get("mostrar_si")
    if not condicion:
        return True
    padre = por_id.get(condicion["pregunta_id"])
    if padre is None or not es_visible(padre, valores, por_id):
        return False
    return valores.get(padre["id"]) in condicion["valores"]


def _vacia(valor):
    return valor is None or (isinstance(valor, str) and not valor.strip())


def _validar_valor(pregunta, valor):
    """Devuelve (valor_normalizado, puntos) o lanza ValueError con el motivo."""
    tipo = pregunta["tipo"]
    if tipo == "escala":
        escala = pregunta["escala"]
        if isinstance(valor, float) and valor.is_integer():
            valor = int(valor)
        if not _entero(valor):
            raise ValueError("Debe ser un número entero de la escala.")
        if not escala["minimo"] <= valor <= escala["maximo"]:
            raise ValueError(f"Debe estar entre {escala['minimo']} y {escala['maximo']}.")
        puntos = (valor - escala["minimo"]) / (escala["maximo"] - escala["minimo"]) * 100
        return valor, puntos
    if tipo == "opcion":
        opciones = {o["valor"]: o for o in pregunta["opciones"]}
        if not isinstance(valor, str) or valor not in opciones:
            raise ValueError("No es una de las opciones disponibles.")
        return valor, opciones[valor].get("puntos")
    if tipo == "numero":
        if isinstance(valor, float) and valor.is_integer() and pregunta.get("entero"):
            valor = int(valor)
        if not _numero(valor) or (pregunta.get("entero") and not _entero(valor)):
            raise ValueError("Debe ser un número entero." if pregunta.get("entero") else "Debe ser un número.")
        if not pregunta["minimo"] <= valor <= pregunta["maximo"]:
            raise ValueError(f"Debe estar entre {pregunta['minimo']} y {pregunta['maximo']}.")
        return valor, None
    # texto
    if not isinstance(valor, str):
        raise ValueError("Debe ser texto.")
    texto = valor.strip()
    maximo = pregunta.get("max_longitud", 500)
    minimo = pregunta.get("min_longitud", 1 if pregunta.get("obligatoria") else 0)
    if len(texto) > maximo:
        raise ValueError(f"Admite como máximo {maximo} caracteres.")
    if len(texto) < minimo:
        raise ValueError(f"Escribe al menos {minimo} caracteres.")
    return texto, None


def evaluar(cuestionario, entradas):
    """Valida las respuestas enviadas y calcula los puntajes.

    entradas: [{"pregunta_id": "V01", "valor": ...}, ...].
    Devuelve (respuestas, resumen). Las respuestas a preguntas que no aplican
    (condición no cumplida) se descartan. Lanza RespuestasInvalidas.
    """
    if not isinstance(entradas, list):
        raise RespuestasInvalidas({"respuestas": "Debe ser una lista de respuestas."})

    por_id = {pregunta["id"]: pregunta for _, pregunta in preguntas(cuestionario)}
    errores = {}
    valores = {}
    for indice, entrada in enumerate(entradas, start=1):
        pid = entrada.get("pregunta_id") if isinstance(entrada, dict) else None
        if not isinstance(pid, str) or not pid:
            errores[f"respuestas[{indice}]"] = "Cada respuesta necesita 'pregunta_id' y 'valor'."
        elif pid not in por_id:
            errores[pid] = "La pregunta no existe en este cuestionario."
        elif pid in valores:
            errores[pid] = "La pregunta está respondida más de una vez."
        else:
            valores[pid] = entrada.get("valor")
    if errores:
        raise RespuestasInvalidas(errores)

    respuestas = []
    for categoria, pregunta in preguntas(cuestionario):
        pid = pregunta["id"]
        if not es_visible(pregunta, valores, por_id):
            continue
        valor = valores.get(pid)
        if _vacia(valor):
            if pregunta["obligatoria"]:
                errores[pid] = "Esta pregunta es obligatoria."
            continue
        try:
            normalizado, puntos = _validar_valor(pregunta, valor)
        except ValueError as exc:
            errores[pid] = str(exc)
            continue
        if not pregunta.get("puntua"):
            puntos = None
        respuestas.append({
            "pregunta_id": pid,
            "valor": normalizado,
            "categoria": categoria["codigo"],
            "puntos": round(puntos, 2) if puntos is not None else None,
        })
    if errores:
        raise RespuestasInvalidas(errores)

    return respuestas, calcular_resumen(cuestionario, respuestas)


def calcular_resumen(cuestionario, respuestas):
    por_pregunta = {r["pregunta_id"]: r for r in respuestas}
    categorias = []
    suma, suma_pesos = 0.0, 0.0
    for categoria in cuestionario["categorias"]:
        puntos, pesos = 0.0, 0.0
        for pregunta in categoria["preguntas"]:
            respuesta = por_pregunta.get(pregunta["id"])
            if pregunta.get("puntua") and respuesta and respuesta["puntos"] is not None:
                puntos += respuesta["puntos"] * pregunta["peso"]
                pesos += pregunta["peso"]
        puntaje = round(puntos / pesos, 2) if pesos else None
        categorias.append({
            "codigo": categoria["codigo"],
            "nombre": categoria["nombre"],
            "peso": categoria["peso"],
            "puntaje": puntaje,
        })
        if puntaje is not None:
            suma += puntaje * categoria["peso"]
            suma_pesos += categoria["peso"]
    total = round(suma / suma_pesos, 2) if suma_pesos else 0.0
    return {
        "puntaje_total": total,
        "nivel": nivel(cuestionario, total),
        "puntaje_por_categoria": categorias,
    }


def nivel(cuestionario, puntaje):
    for item in cuestionario["niveles"]:
        if puntaje >= item["minimo"]:
            return item["nombre"]
    return cuestionario["niveles"][-1]["nombre"]


# ============================================================
# Representación para la app
# ============================================================

def para_cliente(cuestionario):
    """Plantilla sin puntos ni pesos de pregunta: el puntaje lo calcula el
    servidor y mostrarlo podría sesgar la autoevaluación."""
    categorias = []
    for categoria in cuestionario["categorias"]:
        lista = []
        for pregunta in categoria["preguntas"]:
            item = {
                "id": pregunta["id"],
                "texto": pregunta["texto"],
                "tipo": pregunta["tipo"],
                "obligatoria": pregunta["obligatoria"],
            }
            if pregunta["tipo"] == "escala":
                item["escala"] = pregunta["escala"]
            elif pregunta["tipo"] == "opcion":
                item["opciones"] = [{"valor": o["valor"], "etiqueta": o["etiqueta"]} for o in pregunta["opciones"]]
            elif pregunta["tipo"] == "numero":
                item.update(minimo=pregunta["minimo"], maximo=pregunta["maximo"], entero=pregunta.get("entero", False))
            else:
                item.update(
                    max_longitud=pregunta.get("max_longitud", 500),
                    min_longitud=pregunta.get("min_longitud", 1 if pregunta["obligatoria"] else 0),
                )
            if pregunta.get("mostrar_si"):
                item["mostrar_si"] = pregunta["mostrar_si"]
            lista.append(item)
        categorias.append({"codigo": categoria["codigo"], "nombre": categoria["nombre"], "preguntas": lista})
    return {
        "id": str(cuestionario.get("_id")) if cuestionario.get("_id") is not None else None,
        "codigo": cuestionario["codigo"],
        "version": cuestionario["version"],
        "titulo": cuestionario["titulo"],
        "descripcion": cuestionario.get("descripcion", ""),
        "categorias": categorias,
    }
