/*
  Fase 3.2: staging normalizado para autoevaluaciones MongoDB.
  Ejecutar una sola vez en DWAutoPartes, antes de exportar. Es aditivo e
  idempotente; no modifica dimensiones, hechos ni ETL_ControlCarga.
*/
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = N'stg')
    EXEC(N'CREATE SCHEMA stg');
GO

IF OBJECT_ID(N'stg.AutoevaluacionEvaluacion', N'U') IS NULL
BEGIN
    CREATE TABLE stg.AutoevaluacionEvaluacion (
        OrigenEvaluacionId NVARCHAR(48) NOT NULL,
        EmpleadoId INT NOT NULL,
        EmpleadoUsername NVARCHAR(150) NOT NULL CONSTRAINT DF_StgAutoev_Username DEFAULT N'',
        CuestionarioId NVARCHAR(48) NOT NULL,
        CuestionarioCodigo NVARCHAR(100) NOT NULL,
        CuestionarioVersion INT NOT NULL,
        Periodo CHAR(7) NOT NULL,
        FechaEnvio DATETIME2 NULL,
        ActualizadoEn DATETIME2 NULL,
        VersionEvaluacion INT NOT NULL,
        PuntajeTotal DECIMAL(18,4) NULL,
        Nivel NVARCHAR(100) NOT NULL CONSTRAINT DF_StgAutoev_Nivel DEFAULT N'',
        ExportadoEn DATETIME2 NOT NULL,
        CONSTRAINT PK_StgAutoevaluacionEvaluacion PRIMARY KEY (OrigenEvaluacionId)
    );
END;
GO

IF OBJECT_ID(N'stg.AutoevaluacionRespuesta', N'U') IS NULL
BEGIN
    CREATE TABLE stg.AutoevaluacionRespuesta (
        OrigenEvaluacionId NVARCHAR(48) NOT NULL,
        PreguntaId NVARCHAR(100) NOT NULL,
        ValorTexto NVARCHAR(4000) NOT NULL,
        ValorNumero DECIMAL(18,4) NULL,
        TipoValor NVARCHAR(30) NOT NULL,
        CONSTRAINT PK_StgAutoevaluacionRespuesta PRIMARY KEY (OrigenEvaluacionId, PreguntaId),
        CONSTRAINT FK_StgAutoevRespuesta_Evaluacion FOREIGN KEY (OrigenEvaluacionId)
            REFERENCES stg.AutoevaluacionEvaluacion (OrigenEvaluacionId)
    );
END;
GO

IF OBJECT_ID(N'stg.AutoevaluacionPuntajeCategoria', N'U') IS NULL
BEGIN
    CREATE TABLE stg.AutoevaluacionPuntajeCategoria (
        OrigenEvaluacionId NVARCHAR(48) NOT NULL,
        CategoriaId NVARCHAR(100) NOT NULL,
        Puntaje DECIMAL(18,4) NOT NULL,
        CONSTRAINT PK_StgAutoevaluacionPuntajeCategoria PRIMARY KEY (OrigenEvaluacionId, CategoriaId),
        CONSTRAINT FK_StgAutoevCategoria_Evaluacion FOREIGN KEY (OrigenEvaluacionId)
            REFERENCES stg.AutoevaluacionEvaluacion (OrigenEvaluacionId)
    );
END;
GO
