
import os
import unittest

import yaml

from epp_detector import DetectorEPP

CONFIG = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                      "epp_config.yaml")

CLASES_DEL_MODELO = ["gloves", "goggles", "helmet", "vest"]


def cargar_config():
    with open(CONFIG, "r", encoding="utf-8") as fh:
        return yaml.safe_load(fh)


class TestMapeoDeClases(unittest.TestCase):

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
        self.assertFalse(self.cfg["catalogo_epp"]["BOTAS"]["verificable"])

    def test_ningun_rol_exige_un_elemento_no_verificable_silencioso(self):
        for rol, regla in self.cfg["roles"].items():
            sin_verificar = [c for c in (regla.get("obligatorio") or [])
                             if not (self.cfg["catalogo_epp"].get(c) or {}).get("verificable")]
            with self.subTest(rol=rol):
                self.assertEqual(sorted(sin_verificar), ["BOTAS"],
                                 "un elemento no verificable fuera de BOTAS no está "
                                 "advertido por el motor en ninguna parte")


class TestRutasDeModelos(unittest.TestCase):

    class _Falso:
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
