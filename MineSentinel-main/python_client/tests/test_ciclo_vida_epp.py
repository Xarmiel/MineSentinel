"""
Pruebas del ciclo de vida de la infracción de EPP (tracker de episodios).
========================================================================
Sin red ni modelos: se ejecuta con la stdlib. Desde `python_client/`:

    python -m unittest tests.test_ciclo_vida_epp -v

No uses `unittest discover` sobre la raíz: `runs/` arrastra miles de artefactos
de Ultralytics (ver tests/test_reglas_epp.py).

Qué protegen: `_sincronizar_episodios` decide cuándo el panel de admin ve una
alerta aparecer (apertura) y cuándo se marca RESUELTA sola (cierre). Si se
rompe, el síntoma es un panel que no refleja quién se puso o se quitó el EPP:
o llena de alertas que nunca se cierran, o cierra antes de tiempo y deja una
falta abierta sin aviso. Ninguno de esos fallos lanza excepción.
"""

import os
import sys
import threading
import time
import unittest
from concurrent.futures import ThreadPoolExecutor
from types import SimpleNamespace

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from epp_detector import Infraccion, Persona, FrameAnalisis, Caja  # noqa: E402
from yolo_sentinel_client import PipelineEPP  # noqa: E402


def _infraccion(track_id=7, clave="CASCO", tipo="FALTANTE"):
    return Infraccion(
        tipo=tipo, clave=clave, nombre_catalogo="Casco de Seguridad con Barbiquejo",
        descripcion=f"Sin {clave}", confianza=0.9, track_id=track_id,
        clase_detectada="no_helmet",
    )


def _persona(track_id=7):
    return Persona(track_id=track_id, caja=Caja(0, 0, 10, 10), confianza=0.9)


def _analisis(infracciones=None, personas=None):
    return FrameAnalisis(
        personas=personas if personas is not None else [_persona()],
        estado_actual=infracciones if infracciones is not None else [],
        fotograma=1,
    )


class _ClienteFalso:
    """Captura las llamadas de apertura/cierre sin tocar la red."""

    def __init__(self, falta_id_base=100):
        self._siguiente = falta_id_base
        self.infracciones_abiertas = []   # kwargs de enviar_infraccion
        self.resueltas = []               # (falta_id, kwargs) de resolver_infraccion
        self.catalogo = SimpleNamespace(epp_id=lambda nombre: 1)

    def enviar_infraccion(self, epp_id, rol_id, confianza, snapshot_url,
                          tipo_infraccion="", descripcion="", track_id=None):
        self.infracciones_abiertas.append({
            "epp_id": epp_id, "track_id": track_id, "tipo": tipo_infraccion,
        })
        falta_id = self._siguiente
        self._siguiente += 1
        return falta_id

    def resolver_infraccion(self, falta_id, track_id=None, motivo="", snapshot_url=""):
        self.resueltas.append((falta_id, {"track_id": track_id, "motivo": motivo}))
        return True

    def subir_evidencia(self, imagen, nombre="e.jpg"):
        return "/evidencias/fake.jpg"


def _pipeline(frames_abrir=3, frames_cerrar=8, dry_run=False):
    detector = SimpleNamespace(frames_abrir=frames_abrir, frames_cerrar=frames_cerrar)
    return PipelineEPP(detector=detector, cliente=_ClienteFalso(), rol_id=1, dry_run=dry_run)


class TestAperturaDeEpisodio(unittest.TestCase):
    """La alerta no debe abrirse por un solo frame parpadeante."""

    def test_no_abre_en_el_primer_frame(self):
        p = _pipeline(frames_abrir=3)
        p._sincronizar_episodios(_analisis(infracciones=[_infraccion()]))
        self.assertEqual(p._abiertas, {})
        self.assertEqual(p.cliente.infracciones_abiertas, [])

    def test_abre_tras_frames_consecutivos(self):
        p = _pipeline(frames_abrir=3)
        for _ in range(3):
            p._sincronizar_episodios(_analisis(infracciones=[_infraccion()]))
        self.assertEqual(len(p._abiertas), 1)
        self.assertEqual(len(p.cliente.infracciones_abiertas), 1)

    def test_no_abre_si_la_falta_parapadea(self):
        # 2 frames con falta, 1 sin falta (resetea), 2 con falta: aún no abre.
        p = _pipeline(frames_abrir=3)
        p._sincronizar_episodios(_analisis(infracciones=[_infraccion()]))
        p._sincronizar_episodios(_analisis(infracciones=[_infraccion()]))
        p._sincronizar_episodios(_analisis(infracciones=[]))   # resetea
        p._sincronizar_episodios(_analisis(infracciones=[_infraccion()]))
        p._sincronizar_episodios(_analisis(infracciones=[_infraccion()]))
        self.assertEqual(p._abiertas, {})

    def test_no_abre_episodio_duplicado_de_una_falta_abierta(self):
        p = _pipeline(frames_abrir=2)
        for _ in range(2):
            p._sincronizar_episodios(_analisis(infracciones=[_infraccion()]))
        self.assertEqual(len(p.cliente.infracciones_abiertas), 1)
        # Sigue incumpliendo: no debe abrir un segundo episodio.
        for _ in range(5):
            p._sincronizar_episodios(_analisis(infracciones=[_infraccion()]))
        self.assertEqual(len(p.cliente.infracciones_abiertas), 1)

    def test_dry_run_lleva_la_cuenta_sin_enviar(self):
        p = _pipeline(frames_abrir=2, dry_run=True)
        for _ in range(2):
            p._sincronizar_episodios(_analisis(infracciones=[_infraccion()]))
        self.assertEqual(p.cliente.infracciones_abiertas, [])
        self.assertEqual(p.total_infracciones, 1)


class TestCierreDeEpisodio(unittest.TestCase):
    """Ponerse el EPP debe resolver la alerta exactamente una vez."""

    def _abrir(self, pipeline, frames=3):
        for _ in range(frames):
            pipeline._sincronizar_episodios(_analisis(infracciones=[_infraccion()]))
        self.assertEqual(len(pipeline._abiertas), 1)

    def test_resuelve_exactamente_una_vez_al_ponerse_el_epp(self):
        p = _pipeline(frames_abrir=3, frames_cerrar=3)
        self._abrir(p)
        falta_id = next(iter(p._abiertas.values())).result()  # Future del alta

        # Los frames_cerrar frames conformes cierran el episodio.
        for _ in range(3):
            p._sincronizar_episodios(_analisis(infracciones=[]))
        self.assertEqual(len(p.cliente.resueltas), 1)
        self.assertEqual(p.cliente.resueltas[0][0], falta_id)
        self.assertEqual(p.cliente.resueltas[0][1]["track_id"], 7)
        self.assertEqual(p._abiertas, {})

        # Frames conformes extra no deben resolver de nuevo.
        for _ in range(5):
            p._sincronizar_episodios(_analisis(infracciones=[]))
        self.assertEqual(len(p.cliente.resueltas), 1)

    def test_no_cierra_antes_de_frames_cerrar(self):
        p = _pipeline(frames_abrir=3, frames_cerrar=8)
        self._abrir(p)
        for _ in range(7):   # un frame menos del umbral
            p._sincronizar_episodios(_analisis(infracciones=[]))
        self.assertEqual(p.cliente.resueltas, [])
        self.assertEqual(len(p._abiertas), 1)

    def test_reincumplir_reinicia_el_contador_de_cierre(self):
        p = _pipeline(frames_abrir=3, frames_cerrar=3)
        self._abrir(p)
        p._sincronizar_episodios(_analisis(infracciones=[]))
        p._sincronizar_episodios(_analisis(infracciones=[]))
        # Vuelve a incumplir antes del umbral: no cierra.
        p._sincronizar_episodios(_analisis(infracciones=[_infraccion()]))
        self.assertEqual(p.cliente.resueltas, [])
        self.assertEqual(len(p._abiertas), 1)

    def test_abre_un_episodio_nuevo_tras_resolver(self):
        # Ciclo completo: sin casco -> casco -> sin casco otra vez.
        p = _pipeline(frames_abrir=2, frames_cerrar=2)
        for _ in range(2):
            p._sincronizar_episodios(_analisis(infracciones=[_infraccion()]))
        for _ in range(2):
            p._sincronizar_episodios(_analisis(infracciones=[]))
        for _ in range(2):
            p._sincronizar_episodios(_analisis(infracciones=[_infraccion()]))
        self.assertEqual(len(p.cliente.infracciones_abiertas), 2)
        self.assertEqual(len(p.cliente.resueltas), 1)
        self.assertEqual(len(p._abiertas), 1)


class TestDesaparicionDeTrack(unittest.TestCase):
    """Salir de cámara con falta abierta no debe cerrar solo."""

    def test_track_fuera_de_camara_no_cierra(self):
        p = _pipeline(frames_abrir=2, frames_cerrar=2)
        for _ in range(2):
            p._sincronizar_episodios(_analisis(infracciones=[_infraccion(track_id=7)]))
        self.assertEqual(len(p._abiertas), 1)

        # La persona desaparece del encuadre (sin personas, sin infracciones).
        for _ in range(10):
            p._sincronizar_episodios(_analisis(infracciones=[], personas=[]))
        self.assertEqual(p.cliente.resueltas, [], "salir de cámara no cierra la alerta")
        self.assertEqual(len(p._abiertas), 1)


class TestFiltradoDeAnomalias(unittest.TestCase):
    """Las anomalías de movimiento no entran al tracker de episodios de EPP."""

    def test_anomalia_no_abre_episodio_epp(self):
        p = _pipeline(frames_abrir=2)
        anomalia = Infraccion(
            tipo="ANOMALIA", clave="CAIDA", nombre_catalogo="Detección de Caída o Postura Anómala",
            descripcion="Caída", confianza=0.8, track_id=7, clase_detectada="fall",
            es_anomalia=True,
        )
        for _ in range(5):
            p._sincronizar_episodios(_analisis(infracciones=[anomalia]))
        self.assertEqual(p._abiertas, {})
        self.assertEqual(p.cliente.infracciones_abiertas, [])

    def test_track_sin_asignar_no_abre_episodio(self):
        p = _pipeline(frames_abrir=2)
        suelta = _infraccion(track_id=-1)
        for _ in range(5):
            p._sincronizar_episodios(_analisis(infracciones=[suelta]))
        self.assertEqual(p._abiertas, {})


class TestReportesEnSegundoPlano(unittest.TestCase):
    """En vivo las altas van a un hilo aparte para no congelar el video."""

    def _con_fondo(self, cliente):
        p = _pipeline(frames_abrir=1, frames_cerrar=1)
        p.cliente = cliente
        p._fondo = ThreadPoolExecutor(max_workers=1)
        return p

    def test_el_cierre_usa_el_id_de_su_apertura(self):
        p = self._con_fondo(_ClienteFalso(falta_id_base=555))
        p._sincronizar_episodios(_analisis(infracciones=[_infraccion()]))  # abre
        p._sincronizar_episodios(_analisis(infracciones=[]))               # cierra
        p._fondo.shutdown(wait=True)
        self.assertEqual([r[0] for r in p.cliente.resueltas], [555])
        self.assertEqual(p._abiertas, {})

    def test_el_bucle_no_espera_a_la_red(self):
        liberar = threading.Event()

        class _ClienteLento(_ClienteFalso):
            def enviar_infraccion(self, *a, **kw):
                liberar.wait(5)
                return super().enviar_infraccion(*a, **kw)

        p = self._con_fondo(_ClienteLento())
        inicio = time.monotonic()
        p._sincronizar_episodios(_analisis(infracciones=[_infraccion()]))
        self.assertLess(time.monotonic() - inicio, 0.5)
        self.assertEqual(len(p._abiertas), 1)  # ya cuenta como abierta: no se duplica
        liberar.set()
        p._fondo.shutdown(wait=True)
        self.assertEqual(len(p.cliente.infracciones_abiertas), 1)

    def test_si_el_alta_falla_se_suelta_la_clave(self):
        class _ClienteCaido(_ClienteFalso):
            def enviar_infraccion(self, *a, **kw):
                return None

        p = self._con_fondo(_ClienteCaido())
        p._sincronizar_episodios(_analisis(infracciones=[_infraccion()]))
        p._fondo.shutdown(wait=True)
        self.assertEqual(p._abiertas, {})


if __name__ == "__main__":
    unittest.main()
