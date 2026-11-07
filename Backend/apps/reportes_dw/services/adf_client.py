"""Cliente mínimo de la API REST de Azure Data Factory.

Se autentica con la identidad administrada del App Service (Microsoft Entra ID),
sin secretos: el token se pide para Azure Resource Manager y azure-identity lo
mantiene en caché hasta que caduca.
"""

import logging
import re
from datetime import datetime, timedelta, timezone

import requests
from django.conf import settings


logger = logging.getLogger(__name__)

ARM_URL = "https://management.azure.com"
ARM_SCOPE = "https://management.azure.com/.default"
API_VERSION = "2018-06-01"

# Tiempo máximo de cada llamada: el inicio del ETL debe responder rápido y el
# backend nunca espera a que el paquete termine.
TIMEOUT = (5, 20)

RUN_ID = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

ESTADOS_ACTIVOS = {"Queued", "InProgress", "Canceling"}
ESTADOS_FALLIDOS = {"Failed", "Cancelled"}

MAXIMO_PAGINAS = 20

_credential = None


class DataFactoryError(Exception):
    """Fallo al hablar con Azure; el mensaje es apto para mostrarse al usuario."""


def _token():
    global _credential
    try:
        if _credential is None:
            # Import diferido: el entorno local que no usa ADF no necesita el SDK.
            from azure.identity import ManagedIdentityCredential

            _credential = ManagedIdentityCredential()
        return _credential.get_token(ARM_SCOPE).token
    except Exception as exc:
        logger.exception("No se obtuvo el token de la identidad administrada para Azure Data Factory.")
        raise DataFactoryError(
            "El backend no pudo autenticarse en Azure con su identidad administrada."
        ) from exc


class DataFactoryClient:

    def __init__(self, config=None):
        config = config or settings.AZURE_ETL
        self.pipeline = config["pipeline_name"]
        self.factory_url = (
            f"{ARM_URL}/subscriptions/{config['subscription_id']}"
            f"/resourceGroups/{config['resource_group']}"
            f"/providers/Microsoft.DataFactory/factories/{config['factory_name']}"
        )

    # --------------------------------------------------------
    # Operaciones de Data Factory
    # --------------------------------------------------------

    def crear_run(self, parametros):
        """Pipelines - Create Run. Devuelve el runId sin esperar a la ejecución."""
        data = self._request(
            "POST",
            f"/pipelines/{self.pipeline}/createRun",
            json=parametros,
        )
        return data["runId"]

    def obtener_run(self, run_id):
        """Pipeline Runs - Get. None si el runId no existe en la factoría."""
        if not RUN_ID.match(run_id or ""):
            return None
        return self._request("GET", f"/pipelineruns/{run_id}", not_found_ok=True)

    def runs_desde(self, desde):
        """Pipeline Runs - Query By Factory, solo de este pipeline, más recientes primero."""
        ahora = datetime.now(timezone.utc)
        # ADF conserva las ejecuciones 45 días.
        desde = max(desde, ahora - timedelta(days=45))
        cuerpo = {
            "lastUpdatedAfter": _iso(desde),
            "lastUpdatedBefore": _iso(ahora + timedelta(hours=1)),
            "filters": [
                {"operand": "PipelineName", "operator": "Equals", "values": [self.pipeline]},
            ],
            "orderBy": [{"orderBy": "RunStart", "order": "DESC"}],
        }
        runs = []
        for _ in range(MAXIMO_PAGINAS):
            data = self._request("POST", "/queryPipelineRuns", json=cuerpo)
            runs.extend(data.get("value", []))
            token = data.get("continuationToken")
            if not token:
                return runs
            cuerpo["continuationToken"] = token
        # Una lista incompleta podría hacer creer que un run no existe.
        logger.error("La consulta de ejecuciones de %s superó %s páginas.", self.pipeline, MAXIMO_PAGINAS)
        raise DataFactoryError("Azure Data Factory devolvió demasiadas ejecuciones para revisarlas.")

    # --------------------------------------------------------
    # HTTP
    # --------------------------------------------------------

    def _request(self, method, path, json=None, not_found_ok=False):
        url = f"{self.factory_url}{path}"
        try:
            response = requests.request(
                method,
                url,
                params={"api-version": API_VERSION},
                json=json,
                headers={"Authorization": f"Bearer {_token()}"},
                timeout=TIMEOUT,
            )
        except requests.RequestException as exc:
            logger.exception("Error de red llamando a Azure Data Factory: %s %s", method, path)
            raise DataFactoryError("No fue posible comunicarse con Azure Data Factory.") from exc

        if response.status_code == 404 and not_found_ok:
            return None

        if response.status_code >= 400:
            logger.error(
                "Azure Data Factory respondió %s a %s %s: %s",
                response.status_code, method, path, response.text[:2000],
            )
            if response.status_code in (401, 403):
                raise DataFactoryError(
                    "La identidad administrada del backend no tiene permisos sobre Azure Data Factory."
                )
            if response.status_code == 404:
                raise DataFactoryError(
                    f"No existe el pipeline {self.pipeline} o la factoría configurada en Azure Data Factory."
                )
            raise DataFactoryError(
                f"Azure Data Factory rechazó la operación (HTTP {response.status_code})."
            )

        return response.json() if response.content else {}


def _iso(fecha):
    return fecha.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")


def _parse(valor):
    """Fechas ISO de ADF, con fracciones de hasta 7 dígitos y sufijo Z."""
    if not valor:
        return None
    texto = valor.replace("Z", "+00:00")
    texto = re.sub(r"\.(\d{6})\d+", r".\1", texto)
    try:
        fecha = datetime.fromisoformat(texto)
    except ValueError:
        return None
    return fecha if fecha.tzinfo else fecha.replace(tzinfo=timezone.utc)
