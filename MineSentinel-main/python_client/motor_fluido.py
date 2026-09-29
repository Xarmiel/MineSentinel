"""Motor de video e inference separados.

El problema que resuelve: la camara entrega 30 fps, pero la inferencia de tres
modelos en CPU se queda en 3-5 fps. Si un solo hilo hiciera ambas cosas, el
video se veria a 3 fps, que es lo que hace que un panel de videovigilancia
parezca congelado aunque el equipo no este haciendo nada mal.

La solucion es separar los dos trabajos:

  hilo VIDEO   lee la camara, pega el ultimo analisis conocido, codifica a JPEG
               y lo envia por el stream. Va a la velocidad de la camara.
  hilo IA      toma el frame mas reciente, corre los modelos y actualiza el
               analisis. Va a la velocidad que permita la CPU.

Cada recuadro refleja siempre el ultimo analisis completo, aunque el frame que
se esta pintando sea mas nuevo. Esa desincronizacion de un instante es
invisible a 3-5 fps de analisis y es el precio de que el video vaya fluido.

El estado compartido es un unico objeto con un lock. No hace falta nada mas
elaborado: la IA publica un analisis completo de forma atomica y el hilo de
video lo lee entero, nunca a medias.
"""

import logging
import threading
import time
from typing import Optional

import cv2
import numpy as np

logger = logging.getLogger("minesentinel.motor")


class EstadoCompartido:
    """Ultimo analisis disponible para el hilo de video.

    Se reemplaza entero, nunca se muta. Asi el hilo de video siempre ve un
    analisis coherente aunque la IA este escribiendo en ese instante.
    """

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
        """Ultimo frame capturado, sin crear uno nuevo si no hay nada nuevo."""
        with self._lock:
            return self._frame


class MotorDosHilos:
    """Orquesta captura continua e inferencia sobre una fuente de video."""

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

    # ---------------------------------------------------------------------

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

    # ---------------------------------------------------------------------

    def _hilo_video(self) -> None:
        """Captura, anota con el ultimo analisis y publica. No detecta nada."""
        fuente = self.fuente_factory()
        if not fuente.isOpened():
            raise RuntimeError("No se pudo abrir la fuente de video")

        # Limitar la espera entre frames: si la camara se atasca, no interesa
        # acumular frames viejo en el buffer, interesa seguir intentando.
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
                # Faster que la camara: se descarta el frame en vez de enviarlo.
                # Enviarlo solo produciria imagenes casi identicas y saturaria
                # el stream sin que el ojo note diferencia.
                self._mostrar_si_esta_activo(frame)
                continue
            ultimo_envio = ahora

            # Se dibuja el ultimo analisis disponible sobre el frame nuevo.
            # Es la clave de la fluidez: el recuadro puede tener 200 ms, pero el
            # fondo se mueve a 30 fps.
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
        """Pega el ultimo analisis sobre el frame actual."""
        _, analisis, _ = self.estado.leer()
        if analisis is None:
            # Todavia no hay analisis: se devuelve el frame limpio en vez de
            # bloquear la espera del primer resultado de la IA.
            return frame
        try:
            return self.detector._anotar(frame, analisis, self.pipeline.linea)
        except Exception as exc:  # pragma: no cover - defensivo
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

    # ---------------------------------------------------------------------

    def _hilo_ia(self) -> None:
        """Corre los modelos sobre el frame mas reciente disponible."""
        # Se espera al primer frame antes de arrancar: sin frame no hay nada que
        # analizar y un intento solo gastaria CPU.
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

            # Todo lo que consume la IA se mantiene aqui, a su ritmo.
            self.pipeline._cruces_de_linea(analisis, frame.shape[0])
            self.pipeline._reportar_infracciones(analisis)
            self.pipeline._publicar_cuadro(analisis)

            transcurrido = time.time() - self._t0_ia
            if transcurrido > 0:
                self.fps_ia = self.frames_analizados / transcurrido
