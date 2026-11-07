from django.apps import AppConfig


class AutoevaluacionConfig(AppConfig):
    """Autoevaluación mensual de vendedores, almacenada en MongoDB Atlas.

    No tiene modelos ORM ni migraciones: sus datos viven en MongoDB.
    """

    default_auto_field = 'django.db.models.BigAutoField'
    name = 'apps.autoevaluacion'
    verbose_name = 'Autoevaluación de vendedores'
