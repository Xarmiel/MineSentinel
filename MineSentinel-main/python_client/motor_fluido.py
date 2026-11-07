
import logging
import threading
import time
from typing import Optional

import cv2
import numpy as np

logger = logging.getLogger("minesentinel.motor")


class EstadoCompartido:

    def __init__(self):
        self._lock = threading.Lock()
        self._analisis = None
        self._frame = None
        self._version = 0

    def publicar(self, frame: np.ndarray, analisis) -> None:
        with self._lock:
            self._frame = frame
            self._analisis = analisis
            self._version += 1

    def leer(self):
        with self._lock:
            return self._frame, self._analisis, self._version

    def tomar_frame(self) -> Optional[np.ndarray]:
        with self._lock:
            return self._frame


class MotorDosHilos:

    def __init__(self, detector, pipeline, fuente_factory, ancho_maximo: int = 960,
                 calidad_jpeg: int = 70, fps_objetivo: int = 30):
        self.detector = detector
        self.pipeline = pipeline
        self.fuente_factory = fuente_factory
        self.ancho_maximo = ancho_maximo
        self.calidad_jpeg = calidad_jpeg
        self.fps_objetivo = fps_objetivo

        self.estado = EstadoCompartido()
        self.detener = threading.Event()

        self.frames_capturados = 0
        self.frames_analizados = 0
        self.fps_video = 0.0
        self.fps_ia = 0.0
        self._t0_video = time.time()
        self._t0_ia = time.time()
        self.mostrar = False


    def ejecutar(self) -> None:
        hilo_ia = threading.Thread(target=self._hilo_ia, name="ia", daemon=True)
        hilo_ia.start()
        try:
            self._hilo_video()
        except KeyboardInterrupt:
            logger.info("Motor detenido por el usuario.")
        finally:
            self.detener.set()
            hilo_ia.join(timeout=5)
            logger.info("Motor cerrado | video %.1f fps | ia %.1f fps | %d frames capturados",
                        self.fps_video, self.fps_ia, self.frames_capturados)


    def _hilo_video(self) -> None:
        fuente = self.fuente_factory()
        if not fuente.isOpened():
            raise RuntimeError("No se pudo abrir la fuente de video")

        if fuente.isOpened() and hasattr(fuente, "set"):
            fuente.set(cv2.CAP_PROP_BUFFERSIZE, 1)
        try:
            fuente.set(cv2.CAP_PROP_FPS, self.fps_objetivo)
        except Exception:
            pass

        ultimo_envio = 0.0
        intervalo = 1.0 / self.fps_objetivo

        while not self.detener.is_set():
            exito, frame = fuente.read()
            if not exito or frame is None:
                self.detener.set()
                break

            self.frames_capturados += 1
            ahora = time.time()

            if ahora - ultimo_envio < intervalo:
                self._mostrar_si_esta_activo(frame)
                continue
            ultimo_envio = ahora

            imagen = self._anotar(frame)
            jpeg = self._codificar(imagen)
            if jpeg is not None and not self.pipeline.dry_run:
                self.pipeline.cliente.flujo.escribir(jpeg)

            self._mostrar_si_esta_activo(imagen)

            transcurrido = time.time() - self._t0_video
            if transcurrido > 0:
                self.fps_video = self.frames_capturados / transcurrido
            if self.frames_capturados % 150 == 0:
                logger.info("VIDEO %d frames | %.1f fps | IA %.1f fps | %d analizados",
                            self.frames_capturados, self.fps_video, self.fps_ia,
                            self.frames_analizados)

        fuente.release()

    def _mostrar_si_esta_activo(self, imagen) -> None:
        if not self.mostrar:
            return
        try:
            cv2.imshow("MineSentinel - Deteccion de EPP", imagen)
            if cv2.waitKey(1) & 0xFF == ord("q"):
                self.detener.set()
        except cv2.error:
            self.mostrar = False

    def _anotar(self, frame: np.ndarray) -> np.ndarray:
        _, analisis, _ = self.estado.leer()
        if analisis is None:
            return frame
        try:
            return self.detector._anotar(frame, analisis, self.pipeline.linea)
        except Exception as exc:
            logger.debug("No se pudo anotar el frame: %s", exc)
            return frame

    def _codificar(self, imagen: np.ndarray) -> Optional[bytes]:
        if self.ancho_maximo and imagen.shape[1] > self.ancho_maximo:
            escala = self.ancho_maximo / imagen.shape[1]
            imagen = cv2.resize(imagen, (self.ancho_maximo, int(imagen.shape[0] * escala)),
                                interpolation=cv2.INTER_AREA)
        ok, buffer = cv2.imencode(".jpg", imagen,
                                 [int(cv2.IMWRITE_JPEG_QUALITY), self.calidad_jpeg])
        if not ok:
            return None
        return buffer.tobytes()


    def _hilo_ia(self) -> None:
        while not self.detener.is_set() and self.estado.tomar_frame() is None:
            time.sleep(0.01)

        while not self.detener.is_set():
            frame = self.estado.tomar_frame()
            if frame is None:
                time.sleep(0.01)
                continue

            try:
                analisis = self.detector.analizar(frame, rol_id=self.pipeline.rol_id,
                                                  linea=self.pipeline.linea)
            except Exception as exc:
                logger.error("Fallo en la inferencia: %s", exc)
                time.sleep(0.2)
                continue

            self.frames_analizados += 1
            self.estado.publicar(frame, analisis)

            self.pipeline._cruces_de_linea(analisis, frame.shape[0])
            self.pipeline._reportar_infracciones(analisis, incluir_epp=False)
            self.pipeline._sincronizar_episodios(analisis)
            self.pipeline._publicar_cuadro(analisis)

            transcurrido = time.time() - self._t0_ia
            if transcurrido > 0:
                self.fps_ia = self.frames_analizados / transcurrido
