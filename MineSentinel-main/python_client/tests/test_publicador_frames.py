import os
import sys
import threading
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from yolo_sentinel_client import PublicadorFrames  # noqa: E402


class _ClienteFalso:
    def __init__(self):
        self.frames = []
        self.inicio = threading.Event()
        self.continuar = threading.Event()

    def enviar_frame(self, jpeg, session=None):
        self.frames.append(jpeg)
        if jpeg == b"ocupado":
            self.inicio.set()
            self.continuar.wait(timeout=2)
        return True


class TestPublicadorFrames(unittest.TestCase):
    def test_reemplaza_frames_pendientes_por_el_mas_reciente(self):
        cliente = _ClienteFalso()
        publicador = PublicadorFrames(cliente)
        try:
            publicador.publicar(b"ocupado")
            self.assertTrue(cliente.inicio.wait(timeout=2))
            publicador.publicar(b"obsoleto")
            publicador.publicar(b"reciente")
            cliente.continuar.set()
        finally:
            cliente.continuar.set()
            publicador.cerrar()

        self.assertEqual(cliente.frames, [b"ocupado", b"reciente"])


if __name__ == "__main__":
    unittest.main()
