"""Periodo pendiente y cierre: día 3 del mes siguiente a las 23:59:59 (Managua)."""

from django.test import SimpleTestCase, override_settings

from apps.autoevaluacion import periodos

from .utilidades import momento


@override_settings(AUTOEVALUACION={"dias_gracia": 3}, TIME_ZONE="America/Managua")
class PeriodosTests(SimpleTestCase):

    def test_cierre_por_defecto_dia_3_a_las_235959(self):
        self.assertEqual(periodos.local_sin_zona(periodos.cierre("2026-11")), "2026-12-03T23:59:59")
        self.assertEqual(periodos.local_sin_zona(periodos.cierre("2026-12")), "2027-01-03T23:59:59")
        self.assertEqual(periodos.local_sin_zona(periodos.cierre("2027-02")), "2027-03-03T23:59:59")

    def test_pendiente_dentro_del_mes(self):
        self.assertEqual(periodos.pendiente(momento(2026, 11, 20)), "2026-11")

    def test_pendiente_durante_la_gracia_es_el_mes_anterior(self):
        self.assertEqual(periodos.pendiente(momento(2026, 12, 1, 0, 0, 0)), "2026-11")
        self.assertEqual(periodos.pendiente(momento(2026, 12, 3, 23, 59, 59)), "2026-11")

    def test_pendiente_tras_el_cierre_es_el_mes_en_curso(self):
        self.assertEqual(periodos.pendiente(momento(2026, 12, 4, 0, 0, 0)), "2026-12")

    def test_cambio_de_anio(self):
        self.assertEqual(periodos.pendiente(momento(2027, 1, 2)), "2026-12")
        self.assertEqual(periodos.anterior("2027-01"), "2026-12")
        self.assertEqual(periodos.siguiente("2026-12"), "2027-01")

    def test_abierto_y_cerrado(self):
        self.assertTrue(periodos.esta_abierto("2026-11", momento(2026, 11, 1, 0, 0, 0)))
        self.assertTrue(periodos.esta_abierto("2026-11", momento(2026, 12, 3, 23, 59, 59)))
        self.assertFalse(periodos.esta_abierto("2026-11", momento(2026, 12, 4, 0, 0, 0)))
        self.assertFalse(periodos.esta_abierto("2027-01", momento(2026, 12, 15)))

    @override_settings(AUTOEVALUACION={"dias_gracia": 0})
    def test_sin_gracia_cierra_el_ultimo_dia_del_mes(self):
        self.assertEqual(periodos.local_sin_zona(periodos.cierre("2026-11")), "2026-11-30T23:59:59")
        self.assertEqual(periodos.pendiente(momento(2026, 12, 1, 0, 0, 0)), "2026-12")

    @override_settings(AUTOEVALUACION={"dias_gracia": 7})
    def test_gracia_configurable(self):
        self.assertEqual(periodos.local_sin_zona(periodos.cierre("2026-11")), "2026-12-07T23:59:59")
        self.assertEqual(periodos.pendiente(momento(2026, 12, 6)), "2026-11")

    def test_formato_de_periodo(self):
        self.assertTrue(periodos.es_periodo("2026-11"))
        for invalido in ("2026-13", "2026-1", "26-11", None, 202611):
            self.assertFalse(periodos.es_periodo(invalido))

    def test_fechas_utc_se_muestran_en_hora_de_managua(self):
        from datetime import datetime, timezone

        self.assertEqual(
            periodos.local_sin_zona(datetime(2026, 11, 21, 2, 30, tzinfo=timezone.utc)),
            "2026-11-20T20:30:00",
        )
