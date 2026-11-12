/*
  Hechos analíticos de autoevaluaciones (MongoDB -> DWAutoPartes).
  Ejecutar una sola vez en DWAutoPartes, después de 001_stg_autoevaluacion.sql.
  Es aditivo e idempotente: no modifica dimensiones, hechos de ventas ni ETL_ControlCarga.

  Clave lógica: EmpleadoKey + CuestionarioCodigo + Periodo (una sola versión vigente).
  La carga la hace `manage.py exportar_autoevaluaciones_dw` (MERGE + reemplazo de categorías).
*/

IF OBJECT_ID(N'dbo.FactAutoevaluacion', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.FactAutoevaluacion (
        AutoevaluacionKey INT IDENTITY(1,1) NOT NULL,
        EmpleadoKey INT NOT NULL,
        CuestionarioCodigo NVARCHAR(100) NOT NULL,
        Periodo CHAR(7) NOT NULL,              -- AAAA-MM
        CuestionarioVersion INT NOT NULL,
        PuntajeTotal DECIMAL(18,4) NOT NULL,   -- calculado por el Backend (0-100)
        Nivel NVARCHAR(100) NOT NULL,          -- nivel del cuestionario según PuntajeTotal
        FechaEnvio DATETIME2 NULL,             -- UTC, fecha_envio del documento
        OrigenEvaluacionId NVARCHAR(48) NOT NULL,  -- _id del documento MongoDB
        VersionEvaluacion INT NOT NULL,        -- version del documento (sube con cada corrección)
        ActualizadoEnOrigen DATETIME2 NULL,    -- UTC, actualizado_en del documento
        CargadoEn DATETIME2 NOT NULL,          -- UTC, última inserción o actualización
        CONSTRAINT PK_FactAutoevaluacion PRIMARY KEY (AutoevaluacionKey),
        CONSTRAINT UQ_FactAutoevaluacion_Clave UNIQUE (EmpleadoKey, CuestionarioCodigo, Periodo),
        CONSTRAINT UQ_FactAutoevaluacion_Origen UNIQUE (OrigenEvaluacionId),
        CONSTRAINT FK_FactAutoevaluacion_DimEmpleado FOREIGN KEY (EmpleadoKey)
            REFERENCES dbo.DimEmpleado (EmpleadoKey)
    );
    CREATE INDEX IX_FactAutoevaluacion_Periodo ON dbo.FactAutoevaluacion (Periodo);
END;
GO

IF OBJECT_ID(N'dbo.FactAutoevaluacionCategoria', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.FactAutoevaluacionCategoria (
        EmpleadoKey INT NOT NULL,
        CuestionarioCodigo NVARCHAR(100) NOT NULL,
        Periodo CHAR(7) NOT NULL,
        CategoriaCodigo NVARCHAR(100) NOT NULL,
        Puntaje DECIMAL(18,4) NOT NULL,        -- puntaje_por_categoria del documento (0-100)
        Peso DECIMAL(18,4) NULL,               -- peso de la categoría en el cuestionario
        CONSTRAINT PK_FactAutoevaluacionCategoria
            PRIMARY KEY (EmpleadoKey, CuestionarioCodigo, Periodo, CategoriaCodigo),
        CONSTRAINT FK_FactAutoevCategoria_Evaluacion FOREIGN KEY (EmpleadoKey, CuestionarioCodigo, Periodo)
            REFERENCES dbo.FactAutoevaluacion (EmpleadoKey, CuestionarioCodigo, Periodo)
    );
END;
GO
