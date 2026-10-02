"""
Pruebas del contrato entre el modelo de EPP y las reglas de cumplimiento.
=======================================================================
Sin dependencias externas: se ejecuta con la stdlib. Desde `python_client/`:

    python -m unittest tests.test_reglas_epp -v

No uses `unittest discover` sobre la raíz: `runs/` tiene miles de artefactos de
Ultralytics y el descubrimiento se arrastra por ellos.

Por qué son pruebas y no una comprobación manual: `verificable: false` no es un
ajuste de estilo, es un interruptor que apaga la alerta DE SILENCIO (ver
DetectorEPP.epp_obligatorio y _evaluar_hallazgos). Basta con que alguien lo
vuelva a poner en `false` para que el sistema deje de avisar de un casco o un
chaleco sin que nada falle: no hay excepción, ni log de error, ni test roto.
Estas pruebas son el candado de esa decisión.
"""

import os
import unittest

import yaml

from epp_detector import DetectorEPP

CONFIG = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                      "epp_config.yaml")

# Clases que expone `best_sentinel_v2.pt`. Si se reentrena el modelo, esta
# lista es lo primero que hay que actualizar, y el fallo sale aquí en vez de
# en production, donde el síntoma es "no le faltan cascos a nadie".
CLASES_DEL_MODELO = ["gloves", "goggles", "helmet", "vest"]


def cargar_config():
    with open(CONFIG, "r", encoding="utf-8") as fh:
        return yaml.safe_load(fh)


class TestMapeoDeClases(unittest.TestCase):
    """Cada clase del modelo tiene que aterrizar en una clave canónica."""

    def setUp(self):
        self.cfg = cargar_config()
        self.indice = {}
        for clave, mapeo in (self.cfg.get("mapeo_clases") or {}).items():
            for clase in mapeo.get("conforme") or []:
                self.indice[clase] = clave

    def test_todas_las_clases_del_modelo_estan_mapeadas(self):
        sin_mapeo = [c for c in CLASES_DEL_MODELO if c not in self.indice]
        self.assertEqual(sin_mapeo, [],
                         f"clases del modelo sin mapeo en 'mapeo_clases': {sin_mapeo}")

    def test_cada_clase_mapeada_es_verificable(self):
        # Este es el test que importa: una clase correctamente mapeada pero con
        # verificable=false se detecta, se pinta en el cuadro, y no genera
        # ninguna alerta. El fallo es invisible salvo por la ausencia de avisos.
        no_verificables = {}
        for clase in CLASES_DEL_MODELO:
            clave = self.indice.get(clase)
            if clave and not (self.cfg["catalogo_epp"].get(clave) or {}).get("verificable"):
                no_verificables[clase] = clave
        self.assertEqual(no_verificables, {},
                         "detectadas pero sin alerta posible: "
                         "pon 'verificable: true' en el catálogo o quita la clase")

    def test_los_cuatro_elementos_clave_estan_verificables(self):
        for clave in ("CASCO", "CHALECO", "GAFAS", "GUANTES"):
            with self.subTest(elemento=clave):
                self.assertTrue(
                    (self.cfg["catalogo_epp"].get(clave) or {}).get("verificable"),
                    f"{clave} es obligatorio en varios roles pero nadie lo verifica")

    def test_el_calzado_no_se_promete(self):
        # El modelo no tiene clase de calzado. Mantenerlo en false es
        # deliberado: es preferible no avisar a decir "falta botas" sin mirarla.
        self.assertFalse(self.cfg["catalogo_epp"]["BOTAS"]["verificable"])

    def test_ningun_rol_exige_un_elemento_no_verificable_silencioso(self):
        # Los roles sí pueden exigirlo (BOTAS lo es), pero el motor lo descarta
        # con un aviso por consola. Que el requisito exista no implica que se
        # pueda cumplir: lo que no debe pasar es que quede en silencio.
        for rol, regla in self.cfg["roles"].items():
            sin_verificar = [c for c in (regla.get("obligatorio") or [])
                             if not (self.cfg["catalogo_epp"].get(c) or {}).get("verificable")]
            with self.subTest(rol=rol):
                self.assertEqual(sorted(sin_verificar), ["BOTAS"],
                                 "un elemento no verificable fuera de BOTAS no está "
                                 "advertido por el motor en ninguna parte")


class TestRutasDeModelos(unittest.TestCase):
    """Las rutas del config se resuelven contra el config, no contra el CWD."""

    class _Falso:
        """Stub: _ruta_local solo usa self._base_dir, no hace falta cargar YOLO."""
        def __init__(self, base):
            self._base_dir = base

    def setUp(self):
        self.base = os.path.dirname(os.path.abspath(CONFIG))

    def _ruta_local(self, ruta):
        return DetectorEPP._ruta_local(self._Falso(self.base), ruta)

    def test_ruta_relativa_se_ancla_al_config(self):
        self.assertEqual(
            os.path.normcase(self._ruta_local("best_sentinel_v2.pt")),
            os.path.normcase(os.path.join(self.base, "best_sentinel_v2.pt")))

    def test_ruta_absoluta_se_respeta(self):
        absoluta = os.path.join(self.base, "best.pt")
        self.assertEqual(os.path.normcase(self._ruta_local(absoluta)),
                         os.path.normcase(absoluta))

    def test_el_pesos_declarado_existe_de_verdad(self):
        # Regresión: el config declaraba una ruta absoluta a una carpeta del
        # equipo que lo entrenó, así que en cualquier otra máquina el cliente
        # moría con FileNotFoundError al arrancar.
        pesos = self._ruta_local(cargar_config()["modelo"]["principal"]["ruta"])
        self.assertTrue(os.path.isfile(pesos), f"pesos declarados no existen: {pesos}")

    def test_no_hay_rutas_absolutas_que_aten_al_config(self):
        with open(CONFIG, "r", encoding="utf-8") as fh:
            crude = fh.read()
        for linea in crude.splitlines():
            if "ruta:" in linea and ":" in linea.split("ruta:", 1)[1]:
                valor = linea.split("ruta:", 1)[1].split("#")[0].strip()
                with self.subTest(ruta=valor):
                    self.assertFalse(
                        os.path.isabs(valor),
                        f"ruta absoluta atada a una máquina: {valor}")


if __name__ == "__main__":
    unittest.main()