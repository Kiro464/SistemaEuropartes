# ETL Europartes en Azure Data Factory

> Para la integración en Azure tienen que existir el pipeline `PL_Ejecutar_ETL_Europartes` publicado en `adf-europartes` (paso 1) y el rol `Europartes ETL Operador` asignado a la identidad de `europartes-api` (pasos 2 y 3). Si falta cualquiera de los dos, el botón ETL responde `503` y no inicia nada.

## Flujo

```
Web / app móvil
   │  POST /reportes_dw/etl/ejecutar/  (o /api/etl/execute)   JWT + rol autorizado
   ▼
europartes-api (Django)
   1. sp_getapplock 'Europartes_ETL_00_Master' en DWAutoPartes        ← una sola instancia a la vez
   2. Data Factory: ¿hay un run del pipeline en cola o en curso?       ← si lo hay: 409
   3. INSERT dbo.ETL_Ejecucion (Estado INICIADA) → EjecucionKey
   4. Data Factory createRun { pEjecucionKey: EjecucionKey } → runId    ← identidad administrada (token ARM)
   5. libera el bloqueo y responde 202 { ejecucion_key, run_id }
   ▼
adf-europartes ─ PL_Ejecutar_ETL_Europartes ─ actividad Execute SSIS Package
   ▼
ir-europartes-ssis (Azure-SSIS IR)
   ▼
SSISDB /Europartes/ETL_Europartes/00_Master_ETL.dtsx
   · parámetros del proyecto y connection managers: los que ya están configurados en SSISDB (no se sobrescriben)
   · pEjecucionKey > 0 → reutiliza la fila creada por el backend
   ▼
sql-europartes: AutoPartes (lectura) → DWAutoPartes (carga)
   · al terminar, el paquete actualiza dbo.ETL_Ejecucion a EXITOSA o FALLIDA
```

Mientras corre, la web consulta `GET /reportes_dw/etl/ejecuciones/{ejecucion_key}/` cada 5 segundos, hasta 20 minutos. El backend combina la fila de `ETL_Ejecucion` con el run de Data Factory: muestra `INICIADA` o `EN_PROCESO` según Azure, y el estado final que escribió el paquete. Consultar no inicia nada.

### Cómo se relaciona el runId con la fila

El `runId` no se guarda en la base: no hay tablas ni columnas nuevas. El backend busca los runs del pipeline y asocia uno a una fila solo si se cumplen las dos condiciones:

- su parámetro `pEjecucionKey` es igual a la `EjecucionKey` de la fila, y
- empezó entre 10 minutos antes y un día después del `FechaInicio` de la fila.

`FechaInicio` viene de `SYSDATETIME()`, que en Azure SQL Database está en UTC. Por la segunda condición, un run antiguo con la misma clave (por ejemplo, tras reiniciar la identidad de la tabla) nunca se toma por el actual. Si hay varios runs válidos, por ejemplo un *Rerun* desde Monitor, se usa el más reciente.

### Cuándo escribe el backend en `dbo.ETL_Ejecucion`

| Momento | Escritura |
|---|---|
| Al iniciar | `INSERT` de la fila `INICIADA` con el mensaje de siempre, *Ejecución iniciada desde Django* |
| `createRun` falla y Azure confirma que no existe el run | La fila pasa a `FALLIDA` con *No se pudo iniciar en Azure Data Factory* |
| Azure confirma el run como `Failed` o `Cancelled` y el paquete no cerró la fila | `FALLIDA` con *Fallo…* o *Cancelada en Azure Data Factory* |
| Al iniciar, una fila **creada por el backend** sigue abierta, no tiene run en Azure y supera `ETL_MINUTOS_HUERFANA` | `FALLIDA` con *Sin ejecución en Azure Data Factory* |

Todas las actualizaciones llevan `WHERE Estado IN ('INICIADA','EN_PROCESO')`, así que nunca pisan una fila cerrada por el paquete.

**Nunca** se cierra una fila:

- porque una consulta a Azure falló, tardó demasiado o devolvió una lista incompleta (ahí no se toma ninguna decisión), ni
- si la creó el paquete lanzado a mano (`pEjecucionKey = 0`). Esa fila no bloquea pasado el margen, pero tampoco se modifica.

## Endpoints

Todos exigen JWT y uno de estos roles: Administrador General, Asesor Financiero o Coordinador de Logística. Son los mismos que ya tenía el botón.

| Método | Ruta | Respuesta |
|---|---|---|
| POST | `/reportes_dw/etl/ejecutar/` | **La usan la web y la app.** `202` con `ejecucion_key`, `run_id` y `estado`. `409` si ya hay una en curso; en `ejecucion` va cuál es. `503` si Azure no responde o faltan permisos. |
| GET | `/reportes_dw/etl/ejecuciones/{clave}/` | **La usan la web y la app.** La fila con sus columnas originales (`EjecucionKey`, `Estado`, `Mensaje`…) más los campos de Azure |
| GET | `/reportes_dw/etl/ultima/` | **La usan la web y la app.** La ejecución más reciente |
| POST | `/api/etl/execute` | Igual que `ejecutar/` |
| GET | `/api/etl/status/{runId}` | Estado por `runId`. `404` si no es de este pipeline |
| GET | `/api/etl/latest` | La última ejecución |
| GET | `/api/etl/history?limit=20` | De 1 a 100 ejecuciones, más `azure_disponible` |

Campos añadidos a cada ejecución:

- **Identificación:** `ejecucion_key` y `run_id`.
- **Estado:**
  - `estado`: `INICIADA`, `EN_PROCESO`, `EXITOSA` o `FALLIDA`. Excepcionalmente `SIN_CIERRE`: Azure terminó bien pero el paquete no cerró la fila. No se anuncia `EXITOSA` porque el indicador del Dashboard, que lee la fila, no lo reflejaría; el motivo va en `error`.
  - `status`: el estado de Data Factory (`Queued`, `InProgress`, `Succeeded`, `Failed`, `Canceling` o `Cancelled`).
- **Tiempos:** `fecha_inicio` y `fecha_fin` en hora local (`America/Managua`) sin zona, como el resto de la API, y `duracion_segundos`. Azure SQL guarda `SYSDATETIME()` en UTC, así que el backend lo convierte. También lo hace en el indicador `/reportes_dw/dashboard/estado-etl/`.
- **Resultado:** `mensaje`, `error` y `registros_*`.

## Variables de entorno (App Service → Configuración)

Ninguna es obligatoria ni secreta: todas tienen el valor correcto por defecto.

| Variable | Valor |
|---|---|
| `ETL_EJECUTOR` | `adf` (automático en App Service; en local es `ssisdb`) |
| `AZURE_SUBSCRIPTION_ID` | `1b42caf2-54ff-49f2-a600-5321363eb085` |
| `AZURE_RESOURCE_GROUP` | `rg-europartes` |
| `AZURE_DATA_FACTORY_NAME` | `adf-europartes` |
| `AZURE_ETL_PIPELINE_NAME` | `PL_Ejecutar_ETL_Europartes` |
| `ETL_MINUTOS_HUERFANA` | `30` |
| `DW_ZONA_HORARIA` | `UTC` en App Service (Azure SQL); vacío en local (la base usa la hora del equipo) |

La autenticación usa `ManagedIdentityCredential` de `azure-identity` con la identidad asignada por el sistema de `europartes-api` (object ID `e868f940-0555-4e49-a55b-e9dbe5de6fa6`). No hay client secret. Se eligió en lugar de `DefaultAzureCredential` porque `ManagedIdentityCredential` solo usa la identidad administrada y nunca recurre a credenciales personales de Azure CLI ni a variables de entorno.

---

## Paso 0 · Comprobaciones de solo lectura (antes de tocar Azure)

**a) ¿El proyecto usa un Environment de SSISDB?**

En SSMS, conectado al servidor de SSISDB, ejecuta:

```sql
SELECT r.reference_id, r.environment_folder_name, r.environment_name
FROM SSISDB.catalog.environment_references r
JOIN SSISDB.catalog.projects p ON p.project_id = r.project_id
JOIN SSISDB.catalog.folders  f ON f.folder_id  = p.folder_id
WHERE f.name = N'Europartes' AND p.name = N'ETL_Europartes';

SELECT parameter_name, value_type, referenced_variable_name, sensitive
FROM SSISDB.catalog.object_parameters op
JOIN SSISDB.catalog.projects p ON p.project_id = op.project_id
JOIN SSISDB.catalog.folders  f ON f.folder_id  = p.folder_id
WHERE f.name = N'Europartes' AND p.name = N'ETL_Europartes' AND op.object_type = 20;
```

- **Primera consulta vacía y `value_type = 'V'` en todos:** los valores están en el propio proyecto. **No se configura Environment** en el pipeline, que es lo que trae el JSON.
- **Hay referencias o algún `value_type = 'R'`:** avísame antes de publicar. Habría que añadir `environmentPath` con esa ruta exacta; no se inventa.

Esta consulta no muestra el valor de `pContrasenaSQL`.

**b) Versión del paquete desplegado.**

El backend depende de que el paquete desplegado incluya la tarea *SQL - Iniciar Ejecucion ETL* con `IF @pKey > 0`, que reutiliza la fila creada por el backend. La versión de `C:\Users\Alejandro\source\repos\ETL_Europartes` sí la incluye. Si desplegaste esa misma compilación (`bin\Development\ETL_Europartes.ispac`), no hay nada que hacer.

**c) Permisos SQL de la identidad del backend en `DWAutoPartes`.**

Dar permisos en Data Factory no concede ninguno en SQL. El backend necesita en `dbo.ETL_Ejecucion`:

- `SELECT` e `INSERT`, como ya usaba;
- `UPDATE`, que es nuevo y solo sirve para cerrar filas abandonadas;
- `sp_getapplock`, que cualquier usuario puede ejecutar.

Para revisarlo sin cambiar nada:

```sql
-- En DWAutoPartes, como administrador
SELECT dp.name AS usuario, pe.permission_name, pe.state_desc, OBJECT_NAME(pe.major_id) AS objeto
FROM sys.database_principals dp
LEFT JOIN sys.database_permissions pe ON pe.grantee_principal_id = dp.principal_id
WHERE dp.name = N'europartes-api';

SELECT r.name AS rol
FROM sys.database_role_members m
JOIN sys.database_principals r ON r.principal_id = m.role_principal_id
JOIN sys.database_principals u ON u.principal_id = m.member_principal_id
WHERE u.name = N'europartes-api';
```

Si pertenece a `db_datawriter` o tiene `UPDATE` sobre la tabla, está cubierto. Si no tiene `UPDATE`, el ETL igualmente se inicia y se consulta; solo quedaría registrado en el log que no pudo cerrar una fila abandonada. No concedas nada sin revisarlo antes.

---

## Paso 1 · Crear y publicar el pipeline (ADF Studio)

1. **Prepara el IR:** en Azure Portal → `adf-europartes` → **Launch Studio** → **Manage** → **Integration runtimes**, comprueba que `ir-europartes-ssis` está **Running**. Si está detenido puedes crear el pipeline igual, marcando *Manual entries* en el paso 5. No crees otro IR.
2. **Crea el pipeline:** **Author** (lápiz) → **+** → **Pipeline** → **Pipeline**.
3. **Pega la definición:** pulsa el icono **{}** (*Code*) arriba a la derecha. Reemplaza todo el contenido por el de [`PL_Ejecutar_ETL_Europartes.json`](PL_Ejecutar_ETL_Europartes.json) y pulsa **OK**.
4. **Revisa el pipeline:**
   - el nombre debe ser `PL_Ejecutar_ETL_Europartes`;
   - en la pestaña **Parameters** debe haber `pEjecucionKey`, de tipo `Int` y valor por defecto `0`.
5. **Revisa la actividad:** selecciónala (**Ejecutar 00_Master_ETL**) y comprueba:
   - **Settings:**
     - Azure-SSIS IR = `ir-europartes-ssis`
     - *Windows authentication* desmarcado
     - Package location = `SSISDB`
     - Folder `Europartes`, Project `ETL_Europartes`, Package `00_Master_ETL.dtsx`
     - Environment **vacío** (salvo que el paso 0a diga lo contrario)
     - Logging level `Basic`
   - **SSIS parameters → Package:** una sola fila, `pEjecucionKey` = `@pipeline().parameters.pEjecucionKey` (contenido dinámico).
   - **SSIS parameters → Project, Connection managers y Property overrides:** **vacíos**. Así se usan los valores ya configurados en SSISDB.
6. **Valida:** pulsa **Validate**, que no debe mostrar errores.
7. **Publica:** pulsa **Publish all** → **Publish**. **No** pulses *Debug* ni *Add trigger → Trigger now*: ambos ejecutan el ETL.

`concurrency: 1` deja en cola cualquier segundo run que llegue por otra vía. No se crean triggers: la ejecución es solo manual, desde el backend.

## Paso 2 · Crear el rol personalizado

Usa Azure Portal → **Resource groups** → `rg-europartes` → **Access control (IAM)** → **Add** → **Add custom role**:

1. En **Basics**, elige *Start from JSON*, sube [`rol-europartes-etl-operador.json`](rol-europartes-etl-operador.json) y pulsa **Next**.
2. Revisa que **Permissions** muestre exactamente las 5 acciones del JSON y que **Assignable scopes** sea `rg-europartes`.
3. Pulsa **Review + create** → **Create**.

Para hacerlo desde Cloud Shell (Bash) del portal, primero sube el archivo:

```bash
az role definition create --role-definition rol-europartes-etl-operador.json
```

Acciones del rol y la llamada del backend que cubre cada una:

| Acción | Llamada del backend (`adf_client.py`) |
|---|---|
| `Microsoft.DataFactory/factories/pipelines/createrun/action` | `POST …/pipelines/PL_Ejecutar_ETL_Europartes/createRun` |
| `Microsoft.DataFactory/factories/pipelineruns/read` | `GET …/pipelineruns/{runId}` |
| `Microsoft.DataFactory/factories/pipelines/pipelineruns/read` | Variante de la misma lectura de runs: solo lectura, se incluye para no depender de cuál evalúe ARM |
| `Microsoft.DataFactory/factories/querypipelineruns/action` | `POST …/queryPipelineRuns` |
| `Microsoft.DataFactory/factories/querypipelineruns/read` | Lectura del resultado de esa consulta |

No incluye `factories/read`, ni leer o editar pipelines, ni linked services o integration runtimes: el código no los usa.

Los ámbitos asignables de un rol personalizado deben ser un grupo de recursos, una suscripción o un grupo de administración. Por eso la definición se crea a nivel de `rg-europartes`, y la **asignación** se hace solo sobre la factoría (paso 3).

## Paso 3 · Asignar el rol a la identidad del App Service

Usa Azure Portal → `adf-europartes` (el recurso de Data Factory, **no** el grupo de recursos) → **Access control (IAM)** → **Add** → **Add role assignment**:

1. En **Role**, busca `Europartes ETL Operador` y pulsa **Next**.
2. En **Members**, elige *Managed identity* → **+ Select members** → Subscription *Azure for Students* → Managed identity *App Service* → `europartes-api` → **Select**.
3. Pulsa **Review + assign**.

Desde Cloud Shell:

```bash
az role assignment create --assignee-object-id e868f940-0555-4e49-a55b-e9dbe5de6fa6 --assignee-principal-type ServicePrincipal --role "Europartes ETL Operador" --scope "/subscriptions/1b42caf2-54ff-49f2-a600-5321363eb085/resourceGroups/rg-europartes/providers/Microsoft.DataFactory/factories/adf-europartes"
```

La identidad de `adf-europartes` no se toca. La asignación tarda unos minutos en aplicarse.

## Paso 4 · Desplegar el backend

Con el push a `main`, el flujo `.github/workflows/main_europartes-api.yml` despliega `Backend/`. Instala `azure-identity` y `requests` desde `requirements.txt`. No hay migraciones.

## Paso 5 · Comprobación sin ejecutar el ETL

Con un usuario de rol autorizado, sigue estos pasos:

1. Pide `GET /api/etl/history?limit=5`. Debe responder `200` con `"azure_disponible": true`. Esto confirma que el token de la identidad administrada se obtiene y que el permiso de consulta funciona.
2. Si da `false`, mira el **Log stream** del App Service:
   - un *403* indica que falta el paso 3 o aún no se ha aplicado;
   - *no pudo autenticarse* indica un problema de la identidad administrada.

`createRun` no se puede probar sin ejecutar el ETL.

## Paso 6 · Primera ejecución controlada (requiere tu autorización)

1. **Prepara:** comprueba que `ir-europartes-ssis` está *Running* y que nadie está usando los datos del DW en ese momento.
2. **Lanza:** pulsa **Actualizar** en el dashboard web, o haz `POST /api/etl/execute`.
3. **Sigue el run:** en ADF Studio → **Monitor** → **Pipeline runs**, comprueba que el run tiene el parámetro `pEjecucionKey` igual a la clave nueva. En SSMS → SSISDB → **All Executions**, verás la ejecución de `00_Master_ETL.dtsx`.
4. **Comprueba el resultado:** al terminar, la fila de `dbo.ETL_Ejecucion` debe tener `FechaFin` y el estado final, y la web debe mostrar el resultado.
5. **Repite el inicio:** un segundo intento mientras corre debe devolver `409`.

Si la última fila quedó `INICIADA` por el intento anterior, el que no pudo funcionar en Azure, este primer inicio la cerrará como `FALLIDA` con *Sin ejecución en Azure Data Factory*. Solo pasa si la creó el backend.
