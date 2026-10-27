"""
MineSentinel — Cliente de Integración de Visión por Computadora (YOLOv8 + ByteTrack)
=====================================================================================
Captura video en tiempo real (cámara web, stream RTSP de mina o video local), rastrea
al personal con ByteTrack, evalúa el cumplimiento de EPP con YOLOv8 y reporta al backend
Spring Boot los cruces de aforo, las faltas de EPP y los instrumentos no aptos.

Modos de ejecución:
  python yolo_sentinel_client.py --mode webcam --src 0
  python yolo_sentinel_client.py --mode video --src video_mina.mp4
  python yolo_sentinel_client.py --mode image --src foto_control.jpg
  python yolo_sentinel_client.py --mode simulation            (demo sin cámara)

Las reglas de cumplimiento por rol y el vocabulario de clases viven en
`epp_config.yaml`; la lógica de detección en `epp_detector.py`.
"""

import argparse
import io
import logging
import os
import random
import struct
import sys
import threading
import time
from concurrent.futures import Future, ThreadPoolExecutor
from datetime import datetime
from typing import Dict, Optional

try:
    import requests
except ImportError:
    print("Error: 'requests' no está instalado. Ejecuta: pip install requests")
    sys.exit(1)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S"
)
logger = logging.getLogger("MineSentinelClient")

BACKEND_URL_DEFAULT = "http://localhost:8080"
CONFIG_DEFAULT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "epp_config.yaml")


class CatalogoResolver:
    """Resuelve nombres de EPP/anomalías del YAML a los ids de la base de datos.

    Así `epp_config.yaml` nunca depende de ids hardcodeados: si el catálogo del
    backend cambia, el cliente sigue funcionando.
    """

    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip("/")
        self.session = requests.Session()
        self.epp_por_nombre: Dict[str, int] = {}
        self.anomalias_por_nombre: Dict[str, int] = {}

    def cargar(self) -> bool:
        try:
            respuesta = self.session.get(f"{self.base_url}/api/v1/catalogos/epp", timeout=5)
            if respuesta.status_code == 200:
                self.epp_por_nombre = {
                    self._normalizar(item.get("nombre")): item.get("eppId")
                    for item in respuesta.json() if item.get("nombre")
                }
            respuesta = self.session.get(f"{self.base_url}/api/v1/catalogos/anomalias", timeout=5)
            if respuesta.status_code == 200:
                self.anomalias_por_nombre = {
                    self._normalizar(item.get("nombre")): item.get("catalogoAnomaliaId")
                    for item in respuesta.json() if item.get("nombre")
                }
        except Exception as e:
            logger.warning("No se pudieron leer los catálogos del backend: %s", e)
            return False

        if self.epp_por_nombre:
            logger.info("Catálogo de EPP resuelto: %d elementos", len(self.epp_por_nombre))
        else:
            logger.warning("Catálogo de EPP no disponible: las infracciones se registrarán con eppId = null")
        return bool(self.epp_por_nombre)

    @staticmethod
    def _normalizar(nombre: str) -> str:
        return str(nombre).strip().lower()

    def epp_id(self, nombre: str) -> Optional[int]:
        return self.epp_por_nombre.get(self._normalizar(nombre))

    def anomalia_id(self, nombre: str) -> Optional[int]:
        return self.anomalias_por_nombre.get(self._normalizar(nombre))


class MineSentinelClient:
    def __init__(self, base_url: str = BACKEND_URL_DEFAULT, catalogo: Optional[CatalogoResolver] = None):
        self.base_url = base_url.rstrip("/")
        self.session = requests.Session()
        self.catalogo = catalogo or CatalogoResolver(self.base_url)
        self.track_counter = 100
        # Último frame pendiente del stream MJPEG; el hilo emisor arranca con el primero.
        self._stream_cond = threading.Condition()
        self._stream_frame: Optional[bytes] = None
        self._stream_hilo: Optional[threading.Thread] = None

    # =========================================================================
    # CONEXIÓN
    # =========================================================================

    def verificar_conexion(self) -> bool:
        try:
            res = self.session.get(f"{self.base_url}/api/v1/aforo/tiempo-real", timeout=3)
            if res.status_code == 200:
                data = res.json()
                logger.info("Conectado a MineSentinel Backend. Aforo actual: %s/%s",
                            data.get("aforoActual"), data.get("aforoMaximo"))
                return True
            logger.warning("Backend respondió con código: %s", res.status_code)
            return False
        except Exception as e:
            logger.error("No se pudo conectar a %s: %s", self.base_url, e)
            return False

    # =========================================================================
    # EVIDENCIAS
    # =========================================================================

    def subir_evidencia(self, imagen_bgr, nombre: str = "evidencia_epp.jpg") -> str:
        """Codifica un frame anotado (numpy BGR) y lo sube como evidencia."""
        try:
            import cv2

            ok, buffer = cv2.imencode(".jpg", imagen_bgr, [int(cv2.IMWRITE_JPEG_QUALITY), 85])
            if not ok:
                return ""
            return self.subir_evidencia_jpeg(buffer.tobytes(), nombre)
        except Exception as e:
            logger.debug("Aviso en codificación de evidencia: %s", e)
            return ""

    def subir_evidencia_jpeg(self, datos: bytes, nombre: str = "evidencia_epp.jpg") -> str:
        try:
            files = {"file": (nombre, io.BytesIO(datos), "image/jpeg")}
            res = self.session.post(f"{self.base_url}/api/v1/evidencias/upload", files=files, timeout=8)
            if res.status_code == 201:
                return res.json().get("snapshotUrl", "")
            logger.debug("El backend rechazó la evidencia: %s", res.status_code)
        except Exception as e:
            logger.debug("Aviso en subida de snapshot: %s", e)
        return ""

    def subir_snapshot_simulado(self, infraccion_texto: str) -> str:
        """Crea una imagen sintética en memoria y la sube al backend."""
        try:
            try:
                from PIL import Image, ImageDraw
                img = Image.new('RGB', (640, 480), color=(20, 24, 33))
                draw = ImageDraw.Draw(img)
                draw.rectangle([10, 10, 630, 470], outline=(245, 158, 11), width=3)
                draw.text((30, 40), "MINESENTINEL - EVIDENCIA DE VISION", fill=(245, 158, 11))
                draw.text((30, 80), f"Fecha/Hora: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}", fill=(255, 255, 255))
                draw.text((30, 120), f"Infraccion: {infraccion_texto}", fill=(239, 68, 68))
                draw.text((30, 160), "Punto de Control: Boca-Mina Principal (CAM-01)", fill=(148, 163, 184))
                buffer = io.BytesIO()
                img.save(buffer, format='JPEG', quality=85)
                buffer.seek(0)
                files = {"file": ("snapshot_infraccion.jpg", buffer, "image/jpeg")}
            except ImportError:
                dummy = b"\xFF\xD8\xFF\xE0\x00\x10JFIF\x00\x01\x01\x01\x00`\x00`\x00\x00\xFF\xDB\x00C\x00\xFF\xD9"
                files = {"file": ("evidence.jpg", io.BytesIO(dummy), "image/jpeg")}

            res = self.session.post(f"{self.base_url}/api/v1/evidencias/upload", files=files, timeout=5)
            if res.status_code == 201:
                return res.json().get("snapshotUrl", "")
        except Exception as e:
            logger.debug(f"Aviso en subida de snapshot: {e}")
        return ""

    # =========================================================================
    # AFORO
    # =========================================================================

    def enviar_movimiento_aforo(self, tipo_movimiento: str, track_id: int, rol_id: int = 1) -> bool:
        """Registra un cruce de línea virtual en el Event Ledger."""
        payload = {"tipoMovimiento": tipo_movimiento, "trackId": track_id, "rolId": rol_id}
        try:
            res = self.session.post(f"{self.base_url}/api/v1/aforo/movimiento", json=payload, timeout=5)
            if res.status_code == 201:
                m = res.json()
                logger.info("Cruce %s registrado -> Movimiento #%s (Track #%s)",
                            tipo_movimiento, m.get("movimientoId"), track_id)
                return True
            logger.warning("Error al registrar movimiento: %s - %s", res.status_code, res.text)
        except Exception as e:
            logger.error("Fallo en envío de movimiento: %s", e)
        return False

    # =========================================================================
    # SEGURIDAD
    # =========================================================================

    def enviar_infraccion(self, epp_id: Optional[int], rol_id: int, confianza: float,
                          snapshot_url: str, tipo_infraccion: str = "FALTANTE",
                          descripcion: str = "", track_id: Optional[int] = None) -> Optional[int]:
        """Registra una infracción de EPP (FALTANTE o IMPOSTOR) detectada por YOLOv8.

        Devuelve el `faltaId` con el que el backend la archivó, o None si no se
        pudo registrar. Ese id es lo que permite cerrarla después con
        `resolver_infraccion` cuando el trabajador vuelve a portar el EPP.
        """
        if epp_id is None:
            logger.warning("Infracción de tipo %s descartada: el elemento '%s' no existe en "
                           "el catálogo del backend", tipo_infraccion, descripcion)
            return None

        payload = {
            "eppId": epp_id,
            "rolId": rol_id,
            "nivelConfianza": round(float(confianza), 2),
            "snapshotUrl": snapshot_url,
            "tipoInfraccion": tipo_infraccion,
            "descripcion": descripcion[:250],
        }
        if track_id is not None:
            payload["trackId"] = track_id

        try:
            res = self.session.post(f"{self.base_url}/api/v1/seguridad/infracciones", json=payload, timeout=5)
            if res.status_code == 201:
                f = res.json()
                icono = "IMPOSTOR" if tipo_infraccion == "IMPOSTOR" else "FALTANTE"
                logger.warning("[%s] %s -> Falta #%s (Confianza: %.1f%%, Track #%s)",
                               icono, descripcion, f.get("faltaId"), confianza * 100, track_id)
                return f.get("faltaId")
            logger.warning("Error al registrar infracción: %s - %s", res.status_code, res.text)
        except Exception as e:
            logger.error("Fallo en envío de infracción: %s", e)
        return None

    def resolver_infraccion(self, falta_id: int, track_id: Optional[int] = None,
                            motivo: str = "EPP repuesto", snapshot_url: str = "") -> bool:
        """Cierra una infracción abierta: el trabajador volvió a portar el EPP.

        El backend la pasa a RESUELTA y el panel admin la recibe por SSE, de modo
        que la alerta se marca cerrada sola, sin intervención humana.
        """
        if not falta_id:
            return False

        payload = {"faltaId": falta_id, "motivo": motivo}
        if track_id is not None:
            payload["trackId"] = track_id
        if snapshot_url:
            payload["snapshotUrl"] = snapshot_url

        try:
            res = self.session.post(f"{self.base_url}/api/v1/seguridad/infracciones/resolucion",
                                    json=payload, timeout=5)
            if res.status_code in (200, 201, 202, 204):
                logger.info("Falta #%s resuelta (Track #%s): %s", falta_id, track_id, motivo)
                return True
            logger.warning("Error al resolver infracción #%s: %s - %s",
                           falta_id, res.status_code, res.text)
        except Exception as e:
            logger.error("Fallo en resolución de infracción: %s", e)
        return False

    def enviar_cuadro(self, cuadro: dict) -> bool:
        """Publica el cuadro de cumplimiento en vivo de la vista de cámara.

        No es una alerta: es estado de pantalla. El backend lo mantiene unos
        segundos y lo reemite por SSE, de modo que un supervisor que abre la
        vista tarde ve el estado actual y no un histórico.
        """
        try:
            res = self.session.post(f"{self.base_url}/api/v1/vision/cuadro", json=cuadro, timeout=5)
            if res.status_code in (200, 201, 202, 204):
                return True
            logger.warning("Error al publicar cuadro: %s - %s", res.status_code, res.text)
        except Exception as e:
            logger.error("Fallo en envío de cuadro: %s", e)
        return False

    def enviar_frame(self, jpeg: bytes) -> bool:
        """Sube el frame ya anotado para que /camara muestre el video del motor.

        Se envía crudo como image/jpeg en lugar de multipart: no hay metadatos
        que adjuntar y así se evita el parseo de multipart del lado Spring, que
        para un POST cada pocas décimas de segundo es trabajo inútil.
        """
        try:
            res = self.session.post(
                f"{self.base_url}/api/v1/vision/frame",
                data=jpeg,
                headers={"Content-Type": "image/jpeg"},
                timeout=5,
            )
            if res.status_code in (200, 201, 202, 204):
                return True
            logger.warning("Error al publicar frame: %s - %s", res.status_code, res.text)
        except Exception as e:
            logger.error("Fallo en envío de frame: %s", e)
        return False

    def publicar_stream(self, jpeg: bytes) -> None:
        """Entrega un frame al hilo que alimenta el MJPEG de /camara.

        No bloquea: si la red va lenta se pisa el frame pendiente en vez de
        encolarlo, porque al panel sólo le interesa el presente y la detección
        no debe esperar a la red.
        """
        with self._stream_cond:
            self._stream_frame = jpeg
            self._stream_cond.notify()
        if self._stream_hilo is None:
            self._stream_hilo = threading.Thread(target=self._bucle_stream, name="stream-mjpeg", daemon=True)
            self._stream_hilo.start()

    def _frames_stream(self):
        while True:
            with self._stream_cond:
                while self._stream_frame is None:
                    self._stream_cond.wait()
                jpeg, self._stream_frame = self._stream_frame, None
            # Formato de VisionStreamService.recibir: longitud big-endian de 4 bytes + JPEG.
            yield struct.pack(">I", len(jpeg)) + jpeg

    def _bucle_stream(self) -> None:
        """Mantiene UNA petición POST abierta (chunked) y la reabre si se corta."""
        while True:
            try:
                requests.post(f"{self.base_url}/api/v1/vision/stream", data=self._frames_stream(),
                              headers={"Content-Type": "application/octet-stream"}, timeout=(5, None))
            except Exception as e:
                logger.warning("Stream de video cortado (%s); reintentando", e)
            time.sleep(1)

    def enviar_falta_epp(self, epp_id: int, rol_id: int, confianza: float, snapshot_url: str) -> bool:
        """Atajo retrocompatible: registra una falta de EPP sin tipo explícito."""
        return self.enviar_infraccion(epp_id, rol_id, confianza, snapshot_url, "FALTANTE", "")

    def enviar_anomalia(self, catalogo_anomalia_id: Optional[int], rol_id: int,
                        confianza: float, snapshot_url: str) -> bool:
        if catalogo_anomalia_id is None:
            logger.warning("Anomalía descartada: no existe en el catálogo del backend")
            return False
        payload = {
            "catalogoAnomaliaId": catalogo_anomalia_id,
            "rolId": rol_id,
            "nivelConfianza": round(float(confianza), 2),
            "snapshotUrl": snapshot_url
        }
        try:
            res = self.session.post(f"{self.base_url}/api/v1/seguridad/anomalias", json=payload, timeout=5)
            if res.status_code == 201:
                a = res.json()
                logger.warning("ANOMALIA de movimiento registrada -> ID #%s", a.get("anomaliaId"))
                return True
        except Exception as e:
            logger.error("Fallo en envío de anomalía: %s", e)
        return False

    # =========================================================================
    # MODO SIMULACIÓN (demo sin cámara)
    # =========================================================================

    def publicar_cuadro_simulacion(self) -> None:
        """Deja constancia en /camara de que no hay motor de visión real.

        La simulación no ejecuta YOLOv8 ni captura vídeo, así que nunca publica
        un frame. Sin este cuadro vacío marcado, la vista se queda en
        "Esperando detections del motor YOLOv8…" y es indistinguible de un
        detector que funciona pero no ve a nadie.
        """
        self.enviar_cuadro({
            "rolId": 0,
            "nombreRol": "SIMULACIÓN",
            "fotograma": 0,
            "fps": 0.0,
            "personas": [],
            "simulacion": True,
        })

    def ejecutar_simulacion(self, intervalo_segundos: float = 4.0):
        """Simulación realista de operaciones en mina para demostraciones."""
        logger.info("Iniciando modo de simulación industrial continua (Ctrl+C para detener)...")
        logger.warning("MODO SIMULACIÓN: no se ejecuta YOLOv8 ni se captura vídeo. "
                       "La vista /camara NO mostrará detecciones reales ni recuadros. "
                       "Para ver la visión en el sitio web usa: --mode webcam --src 0")
        self.publicar_cuadro_simulacion()
        roles = [1, 2, 3, 4]
        nombres_epp = ["Casco de Seguridad con Barbiquejo", "Chaleco Reflectivo de Alta Visibilidad",
                       "Lámpara Minera Frontal", "Gafas de Seguridad / Protectoras", "Guantes de Seguridad"]
        impostores = ["Gorra / Sombrero No Apto como Casco",
                      "Gafas de Sol No Aptas como Gafas de Seguridad",
                      "Calzado Normal / Zapatillas Deportivas"]

        while True:
            try:
                self.track_counter += 1
                rol_id = random.choice(roles)
                tipo = "ENTRADA" if random.random() < 0.75 else "SALIDA"
                tiene_infraccion_epp = (tipo == "ENTRADA" and random.random() < 0.25)
                tiene_anomalia = random.random() < 0.10

                if tiene_infraccion_epp:
                    es_impostor = random.random() < 0.40
                    nombre = (random.choice(impostores) if es_impostor else random.choice(nombres_epp))
                    tipo_infraccion = "IMPOSTOR" if es_impostor else "FALTANTE"
                    conf = random.uniform(0.85, 0.98)
                    snap = self.subir_snapshot_simulado(f"{tipo_infraccion}: {nombre}")
                    self.enviar_infraccion(self.catalogo.epp_id(nombre), rol_id, conf, snap,
                                           tipo_infraccion, nombre, self.track_counter)

                if tiene_anomalia:
                    conf = random.uniform(0.85, 0.95)
                    snap = self.subir_snapshot_simulado("Cruce en Sentido Contrario al Flujo")
                    self.enviar_anomalia(1, rol_id, conf, snap)

                self.enviar_movimiento_aforo(tipo, self.track_counter, rol_id)
                time.sleep(intervalo_segundos)
            except KeyboardInterrupt:
                logger.info("Simulación detenida por el usuario.")
                break
            except Exception as ex:
                logger.error("Error inesperado en ciclo de simulación: %s", ex)
                time.sleep(2)


class PipelineEPP:
    """Bucle de captura: analiza cada frame y reporta al backend lo detectado."""

    LADO_ARRIBA = "ARRIBA"
    LADO_ABAJO = "ABAJO"

    def __init__(self, detector, cliente: MineSentinelClient, rol_id: int = 1,
                 linea: float = 0.5, mostrar: bool = False, dry_run: bool = False,
                 backend_camara=None):
        self.detector = detector
        self.cliente = cliente
        self.rol_id = rol_id
        self.linea = linea
        self.mostrar = mostrar
        self.dry_run = dry_run
        self.backend_camara = backend_camara
        # Ejecutor de las llamadas que tocan la base de datos. Sólo existe dentro de
        # procesar_fuente(); fuera de él (pruebas, imagen única) todo corre en línea.
        self._fondo: Optional[ThreadPoolExecutor] = None
        self.lado_por_track: Dict[int, str] = {}
        self.total_movimientos = 0
        self.total_infracciones = 0
        self.total_resueltas = 0

        # --- Tracker de episodios de EPP (ciclo de vida de la alerta) ---
        # Cada infracción de EPP es un "episodio" que se ABRÍ (crea la alerta en
        # el panel) cuando el trabajador pasa a incumplir y se CIERRA (la marca
        # RESUELTA) cuando vuelve a portar el EPP. Se gobierna por las
        # transiciones del `estado_actual`, no por el enfriamiento, para que el
        # panel de admin refleje al instante quién se quitó y quién se repuso.
        self._abiertas: Dict[tuple, int] = {}      # (track, clave, tipo) -> faltaId
        self._cuenta_abrir: Dict[tuple, int] = {}  # frames consecutivos con falta
        self._cuenta_cerrar: Dict[tuple, int] = {}  # frames consecutivos sin falta
        self._primer_avistamiento: Dict[int, float] = {}  # track_id -> timestamp primer frame
        self._historial_confianzas: Dict[tuple, List[float]] = {}  # (track, clave, tipo) -> [confianzas]
        self.segundos_evaluacion = float(getattr(detector, 'segundos_evaluacion', 0.0))
        self.frames_abrir = max(1, int(getattr(detector, "frames_abrir", 3)))
        self.frames_cerrar = max(1, int(getattr(detector, "frames_cerrar", 8)))

    # -------------------------------------------------------------------------

    def _cruces_de_linea(self, analisis, alto_frame: int) -> None:
        """Detecta el cruce de la línea virtual por el punto de apoyo de cada persona."""
        for persona in analisis.personas:
            y = persona.caja.pie[1] / max(1, alto_frame)
            lado = self.LADO_ABAJO if y >= self.linea else self.LADO_ARRIBA
            anterior = self.lado_por_track.get(persona.track_id)
            self.lado_por_track[persona.track_id] = lado
            if anterior is None or anterior == lado:
                continue

            tipo = "ENTRADA" if (anterior == self.LADO_ARRIBA and lado == self.LADO_ABAJO) else "SALIDA"
            self.total_movimientos += 1
            if self.dry_run:
                logger.info("[DRY-RUN] %s del track #%s", tipo, persona.track_id)
            else:
                self._en_fondo(self.cliente.enviar_movimiento_aforo, tipo, persona.track_id, self.rol_id)

        for track_id in list(self.lado_por_track):
            if track_id not in {p.track_id for p in analisis.personas}:
                del self.lado_por_track[track_id]

    def _reportar_infracciones(self, analisis, incluir_epp: bool = True) -> None:
        """Reporta las infracciones NUEVAS de este frame (ya con enfriamiento).

        En los bucles en vivo `incluir_epp=False`: ahí el EPP se reporta por el
        tracker de episodios (`_sincronizar_episodios`), que gobierna el ciclo
        de vida completo (apertura y cierre). Así no se duplica la alerta. Sólo
        las anomalías de movimiento siguen pasando por aquí (camino por
        enfriamiento, sin resolución automática). La imagen única usa el camino
        completo porque es un análisis de un solo frame sin ciclo de vida.
        """
        # Sólo lo que de verdad se envía: antes se subía un snapshot por cada frame con
        # faltas de EPP aunque en vivo esas faltas no se reporten por aquí.
        enviar = [i for i in analisis.infracciones if i.es_anomalia or incluir_epp]
        if not enviar:
            return

        self.total_infracciones += len(enviar)
        if self.dry_run:
            for infraccion in enviar:
                logger.info("[DRY-RUN] %s | %s | conf %.2f | track %s",
                            infraccion.tipo, infraccion.descripcion, infraccion.confianza, infraccion.track_id)
            return

        self._en_fondo(self._enviar_infracciones, enviar, self._copia_imagen(analisis), analisis.fotograma)

    def _enviar_infracciones(self, infracciones, imagen, fotograma) -> None:
        snapshot_url = ""
        if imagen is not None:
            snapshot_url = self.cliente.subir_evidencia(imagen, f"epp_track_{fotograma}.jpg")

        for infraccion in infracciones:
            if infraccion.es_anomalia:
                self.cliente.enviar_anomalia(
                    self.cliente.catalogo.anomalia_id(infraccion.nombre_catalogo),
                    self.rol_id, infraccion.confianza, snapshot_url,
                )
            else:
                self.cliente.enviar_infraccion(
                    self.cliente.catalogo.epp_id(infraccion.nombre_catalogo),
                    self.rol_id, infraccion.confianza, snapshot_url,
                    infraccion.tipo, infraccion.descripcion, infraccion.track_id,
                )

    # -------------------------------------------------------------------------
    # LLAMADAS AL BACKEND FUERA DEL BUCLE DE VIDEO
    # -------------------------------------------------------------------------

    def _en_fondo(self, tarea, *args) -> Future:
        """Ejecuta una llamada al backend sin frenar el video.

        Cada alta en Supabase tarda cientos de ms; hechas en el bucle congelaban
        el video segundos cuando entraba una persona con varias faltas. Un único
        hilo las procesa en orden, así un cierre nunca adelanta a su apertura.
        Sin ejecutor corre en línea y devuelve un Future ya resuelto.
        """
        if self._fondo is None:
            futuro = Future()
            futuro.set_result(self._tarea_segura(tarea, *args))
            return futuro
        return self._fondo.submit(self._tarea_segura, tarea, *args)

    @staticmethod
    def _tarea_segura(tarea, *args):
        # En un hilo de fondo una excepción se perdería en silencio dentro del Future.
        try:
            return tarea(*args)
        except Exception:
            logger.exception("Fallo en llamada al backend en segundo plano")
            return None

    @staticmethod
    def _copia_imagen(analisis):
        # El envío ocurre más tarde, en otro hilo: se congela la imagen de este frame.
        imagen = analisis.imagen_anotada
        return None if imagen is None else imagen.copy()

    def _abrir_episodio(self, imagen, fotograma, infraccion, confianza) -> Optional[int]:
        snapshot_url = ""
        if imagen is not None:
            snapshot_url = self.cliente.subir_evidencia(imagen, f"epp_abrir_{fotograma}.jpg")
        return self.cliente.enviar_infraccion(
            self.cliente.catalogo.epp_id(infraccion.nombre_catalogo),
            self.rol_id, confianza, snapshot_url,
            infraccion.tipo, infraccion.descripcion, infraccion.track_id,
        )

    def _cerrar_episodio(self, apertura: Future, track_id: int) -> None:
        # La apertura ya terminó: va antes en la misma cola de un solo hilo.
        falta_id = apertura.result()
        if falta_id:
            self.cliente.resolver_infraccion(falta_id, track_id=track_id,
                                             motivo="EPP repuesto por el trabajador")

    def _descartar_si_fallo(self, key, apertura: Future) -> None:
        # Si el alta falló no hay alerta que cerrar: se suelta la clave para reintentar.
        if not apertura.result() and self._abiertas.get(key) is apertura:
            self._abiertas.pop(key, None)

    # -------------------------------------------------------------------------
    # CICLO DE VIDA DE LA INFRACCIÓN (apertura y cierre de episodio)
    # -------------------------------------------------------------------------

    def _sincronizar_episodios(self, analisis) -> None:
        """Abre y cierra episodios de infracción de EPP según las transiciones.

        Un "episodio" es la falta continua de un mismo (track, elemento, tipo).
        Se ABRE cuando el trabajador pasa a incumplir de forma sostenida (N
        frames) y se CIERRA cuando vuelve a portar el EPP (otros N frames sin
        falta). Gobierna el ciclo de vida de la alerta en el panel de admin:
        la crea al quitarse el EPP y la marca RESUELTA al ponérselo.

        A propósito NO cierra cuando el track desaparece de cámara: si el
        trabajador salió del encuadre aún sin EPP, no podemos afirmar que se
        repuso, así que la alerta queda abierta para revisión humana.
        """
        TRACK_SIN_ASIGNAR = -1

        # Claves que este frame marcan como INCUMPLIMIENTO vigente (sólo EPP
        # real: FALTANTE/IMPOSTOR con track asignado; las anomalías de
        # movimiento siguen su propio camino por enfriamiento).
        incumpliendo = {}
        for i in analisis.estado_actual:
            if i.es_anomalia or i.track_id is None or i.track_id == TRACK_SIN_ASIGNAR:
                continue
            incumpliendo[(i.track_id, i.clave, i.tipo)] = i

        # --- Cierre: claves abiertas que dejaron de incumplir -----------------
        for key, falta_id in list(self._abiertas.items()):
            track_id = key[0]
            if key in incumpliendo:
                # Sigue sin EPP: se reinicia el contador de cierre.
                self._cuenta_cerrar.pop(key, None)
                continue
            if track_id not in {p.track_id for p in analisis.personas}:
                # El trabajador salió de cámara: no se cierra solo.
                self._cuenta_cerrar.pop(key, None)
                continue

            cierre = self._cuenta_cerrar.get(key, 0) + 1
            if cierre >= self.frames_cerrar:
                self.total_resueltas += 1
                if self.dry_run:
                    logger.info("[DRY-RUN] RESUELTA track #%s | falta #%s", track_id, falta_id)
                else:
                    self._en_fondo(self._cerrar_episodio, falta_id, track_id)
                self._abiertas.pop(key, None)
                self._cuenta_cerrar.pop(key, None)
            else:
                self._cuenta_cerrar[key] = cierre

        # --- Registro de tiempo y agregación de confianzas por track ---------
        ahora = time.time()
        personas_visibles = {p.track_id for p in analisis.personas}
        for tid in personas_visibles:
            if tid not in self._primer_avistamiento:
                self._primer_avistamiento[tid] = ahora

        # Acumular confianzas por frame para el promedio
        for key, infraccion in incumpliendo.items():
            self._historial_confianzas.setdefault(key, []).append(float(infraccion.confianza))

        # --- Apertura: claves incumpliendo que aún no tienen episodio ---------
        for key, infraccion in incumpliendo.items():
            if key in self._abiertas:
                continue  # ya hay un episodio abierto para esta falta

            track_id = key[0]
            tiempo_visible = ahora - self._primer_avistamiento.get(track_id, ahora)
            # Estrategia Híbrida: si aún no alcanza los segundos_evaluacion (5s), se espera
            # a acumular historial estable, a menos que el frame supere el umbral frames_abrir
            # y ya haya transcurrido el tiempo mínimo.
            if tiempo_visible < self.segundos_evaluacion:
                continue

            cuenta = self._cuenta_abrir.get(key, 0) + 1
            if cuenta < self.frames_abrir:
                self._cuenta_abrir[key] = cuenta
                continue

            self._cuenta_abrir.pop(key, None)
            self.total_infracciones += 1

            # Calcular el promedio de confianza real registrado durante la ventana
            confianzas = self._historial_confianzas.get(key, [infraccion.confianza])
            confianza_promedio = round(sum(confianzas) / max(1, len(confianzas)), 3)

            if self.dry_run:
                logger.info("[DRY-RUN] APERTURA track #%s | %s | %s (conf_promedio=%.2f tras %.1fs)",
                            key[0], infraccion.tipo, infraccion.descripcion, confianza_promedio, tiempo_visible)
                continue

            # Se guarda el Future, no el id: el alta corre en segundo plano y el
            # cierre lo resuelve cuando le toca en la cola.
            apertura = self._en_fondo(self._abrir_episodio, self._copia_imagen(analisis),
                                      analisis.fotograma, infraccion, confianza_promedio)
            self._abiertas[key] = apertura
            apertura.add_done_callback(lambda f, k=key: self._descartar_si_fallo(k, f))

        # Limpieza de contadores de claves que ya no aplican (ni abiertas ni
        # incumpliendo): evita que un contador viejo dispare una apertura
        # espuria si la misma combinación reaparece más tarde.
        for key in list(self._cuenta_abrir):
            if key not in incumpliendo and key not in self._abiertas:
                self._cuenta_abrir.pop(key, None)
                self._historial_confianzas.pop(key, None)
        for key in list(self._cuenta_cerrar):
            if key in incumpliendo or key not in self._abiertas:
                self._cuenta_cerrar.pop(key, None)

        # Limpieza de tracks que ya salieron del rango de visión
        for tid in list(self._primer_avistamiento):
            if tid not in personas_visibles:
                self._primer_avistamiento.pop(tid, None)

    # -------------------------------------------------------------------------

    def _publicar_cuadro(self, analisis) -> None:
        """Envía el cuadro de cumplimiento según el ritmo configurado en el detector."""
        intervalo = max(1, int(getattr(self.detector, "cuadro_intervalo", 10)))
        if analisis.fotograma % intervalo != 0:
            return

        cuadro = self.detector.construir_cuadro(analisis, rol_id=self.rol_id)
        if not cuadro.get("personas"):
            # Sin personas no hay nada que pintar. Se envía igual un cuadro vacío
            # para que la vista limpie los recuadros de quienes ya no están.
            cuadro["personas"] = []

        if self.dry_run:
            for fila in cuadro["personas"]:
                faltan = ", ".join(f["nombre"] for f in fila["faltantes"]) or "-"
                logger.info("[DRY-RUN] CUADRO track #%s -> %s | faltan: %s",
                            fila["trackId"], fila["estado"], faltan)
            return

        self.cliente.enviar_cuadro(cuadro)
        self._publicar_frame(analisis)

    def _publicar_frame(self, analisis) -> None:
        """Sube el frame anotado al backend para que la vista /camara lo muestre.

        Se reutiliza la misma cadencia que el cuadro en lugar de la suya propia:
        el frame es más pesado que el JSON y mandarlo cada frame satura la red
        sin aportar información, ya que entre una publicación y la siguiente la
        imagen apenas cambia.
        """
        if not getattr(self.detector, "cuadro_publicar_frame", False):
            return
        jpeg = self._codificar_frame(analisis)
        if jpeg:
            self.cliente.enviar_frame(jpeg)

    def _publicar_stream(self, analisis) -> None:
        """Publica CADA frame en el MJPEG de /camara; el envío corre en otro hilo."""
        if self.dry_run or not getattr(self.detector, "cuadro_publicar_frame", False):
            return
        jpeg = self._codificar_frame(analisis)
        if jpeg:
            self.cliente.publicar_stream(jpeg)

    def _codificar_frame(self, analisis) -> Optional[bytes]:
        """Imagen anotada reescalada a `ancho_maximo` y codificada a JPEG."""
        import cv2

        imagen = analisis.imagen_anotada
        if imagen is None:
            return None

        ancho = int(getattr(self.detector, "cuadro_ancho_maximo", 960))
        if ancho and imagen.shape[1] > ancho:
            escala = ancho / imagen.shape[1]
            imagen = cv2.resize(imagen, (ancho, int(imagen.shape[0] * escala)),
                                interpolation=cv2.INTER_AREA)

        ok, buffer = cv2.imencode(".jpg", imagen, [int(cv2.IMWRITE_JPEG_QUALITY), 75])
        if not ok:
            logger.warning("No se pudo codificar el frame a JPEG")
            return None
        return buffer.tobytes()

    def procesar_fuente(self, fuente) -> None:
        """Lee la fuente de video frame a frame hasta agotarla o Ctrl+C."""
        import cv2

        processed = 0
        self._fondo = ThreadPoolExecutor(max_workers=1, thread_name_prefix="reportes")
        try:
            while True:
                exito, frame = fuente.read()
                if not exito or frame is None:
                    break

                analisis = self.detector.analizar(frame, rol_id=self.rol_id, linea=self.linea)
                processed += 1
                self._cruces_de_linea(analisis, frame.shape[0])
                self._reportar_infracciones(analisis, incluir_epp=False)
                self._sincronizar_episodios(analisis)
                self._publicar_cuadro(analisis)
                self._publicar_stream(analisis)

                if processed % 30 == 0:
                    logger.info("Frame %s | %d personas | %d movimientos | %d infracciones | %d resueltas | %.1f fps",
                                analisis.fotograma, len(analisis.personas), self.total_movimientos,
                                self.total_infracciones, self.total_resueltas, analisis.fps)
                if self.mostrar:
                    cv2.imshow("MineSentinel - Deteccion de EPP", analisis.imagen_anotada)
                    if cv2.waitKey(1) & 0xFF == ord("q"):
                        break
        except KeyboardInterrupt:
            logger.info("Captura detenida por el usuario.")
        finally:
            if self.mostrar:
                cv2.destroyAllWindows()
            # Se espera a que salgan los reportes pendientes: son alertas reales.
            self._fondo.shutdown(wait=True)
            self._fondo = None

        logger.info("Procesados %d frames | %d movimientos de aforo | %d infracciones abiertas | %d resueltas",
                    processed, self.total_movimientos, self.total_infracciones, self.total_resueltas)

    def _abrir_camara(self, cv2, indice: int):
        """Abre la cámara probando backends, porque el que usa OpenCV por
        defecto (MSMF/Media Foundation) falla en varios equipos con
        'can't grab frame' aunque la cámara esté conectada y no ocupada.

        DSHOW es DirectShow y es mucho más fiable en Windows. Se prueban en
        orden y se cae al primero que devuelva un frame real, no sólo una
        cámara 'abierta': MSMF freakily se abre y luego no entrega imagen.
        """
        if self.backend_camara:
            backends = [self.backend_camara]
        else:
            backends = [cv2.CAP_DSHOW, cv2.CAP_MSMF, cv2.CAP_ANY]

        nombres = {cv2.CAP_DSHOW: "DSHOW", cv2.CAP_MSMF: "MSMF", cv2.CAP_ANY: "ANY"}

        for backend in backends:
            cap = cv2.VideoCapture(indice, backend)
            if not cap.isOpened():
                cap.release()
                continue
            ok, frame = cap.read()
            if ok and frame is not None:
                logger.info("Cámara %d abierta con backend %s (%dx%d)",
                            indice, nombres.get(backend, backend),
                            frame.shape[1], frame.shape[0])
                return cap
            logger.warning("Cámara %d se abrió con %s pero no entrega frames; probando otro backend",
                           indice, nombres.get(backend, backend))
            cap.release()

        # Último recurso: devolver el capturador por defecto para que el error
        # lo emita el llamador con su mensaje habitual.
        return cv2.VideoCapture(indice)

    def ejecutar_webcam(self, indice: int) -> None:
        import cv2

        fuente = self._abrir_camara(cv2, indice)
        if not fuente.isOpened():
            raise RuntimeError(f"No se pudo abrir la cámara {indice}")
        # Sin MJPG, OpenCV pide YUYV y muchas webcams sólo dan 10 fps a 720p en ese formato.
        fuente.set(cv2.CAP_PROP_FOURCC, cv2.VideoWriter_fourcc(*"MJPG"))
        fuente.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
        fuente.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)
        logger.info("Cámara %s abierta. Pulsa 'q' para salir.", indice)
        self.procesar_fuente(fuente)
        fuente.release()

    def ejecutar_video(self, ruta: str) -> None:
        import cv2

        if not os.path.isfile(ruta):
            raise FileNotFoundError(f"No existe el archivo de video: {ruta}")
        fuente = cv2.VideoCapture(ruta)
        if not fuente.isOpened():
            raise RuntimeError(f"No se pudo abrir el video: {ruta}")
        fps = fuente.get(cv2.CAP_PROP_FPS) or 25.0
        logger.info("Procesando %s a %.1f fps. Pulsa 'q' para salir.", ruta, fps)
        self.procesar_fuente(fuente)
        fuente.release()

    def ejecutar_imagen(self, ruta: str) -> None:
        import cv2

        if not os.path.isfile(ruta):
            raise FileNotFoundError(f"No existe la imagen: {ruta}")
        frame = cv2.imread(ruta)
        if frame is None:
            raise RuntimeError(f"No se pudo decodificar la imagen: {ruta}")

        logger.info("Analizando imagen única: %s", ruta)
        analisis = self.detector.analizar(frame, rol_id=self.rol_id, linea=self.linea)
        self._reportar_infracciones(analisis)

        salida = os.path.splitext(ruta)[0] + "_anotada.jpg"
        cv2.imwrite(salida, analisis.imagen_anotada)
        logger.info("%d personas detectadas | %d infracciones | evidencia: %s",
                    len(analisis.personas), len(analisis.infracciones), salida)


def main():
    # La consola de Windows usa cp1252 y aborta al imprimir los símbolos del log.
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

    parser = argparse.ArgumentParser(
        description="MineSentinel - Cliente de Inferencia YOLOv8 / ByteTrack",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("--url", default=BACKEND_URL_DEFAULT, help="URL base del backend Spring Boot")
    parser.add_argument("--mode", default="simulation",
                        choices=["simulation", "webcam", "video", "image"],
                        help="simulation = demo sin cámara; webcam/video/image = visión real")
    parser.add_argument("--src", default="0", help="Índice de webcam, ruta de video o de imagen")
    parser.add_argument("--config", default=CONFIG_DEFAULT, help="Ruta a epp_config.yaml")
    parser.add_argument("--rol", type=int, default=1, help="rol_id de roles_personal (define el EPP obligatorio)")
    parser.add_argument("--linea", type=float, default=0.5,
                        help="Altura de la línea virtual de aforo, como fracción (0.0-1.0)")
    parser.add_argument("--interval", type=float, default=3.5, help="Intervalo de eventos en simulación (segundos)")
    parser.add_argument("--mostrar", action="store_true", help="Abrir ventana con el video anotado")
    parser.add_argument("--dry-run", action="store_true",
                        help="Detectar y registrar en consola sin enviar nada al backend")
    parser.add_argument("--confianza", type=float, default=None, help="Umbral de confianza (sobrescribe el YAML)")
    parser.add_argument("--imgsz", type=int, default=None, help="Resolución de inferencia (sobrescribe el YAML)")
    parser.add_argument("--device", default=None, help="'cpu' o 'cuda:0' (sobrescribe el YAML)")
    parser.add_argument("--backend-camara", default=None, choices=["dshow", "msmf", "any"],
                        help="Backend de captura de la webcam. Por defecto se prueban en orden "
                             "y se usa el primero que entregue frames (dshow suele ser el que "
                             "funciona en Windows cuando MSMF falla)")
    args = parser.parse_args()

    cliente = MineSentinelClient(args.url)
    conectado = cliente.verificar_conexion()
    if not conectado:
        logger.warning("El backend no responde en %s.", args.url)
    cliente.catalogo.cargar()

    if args.mode == "simulation":
        cliente.ejecutar_simulacion(args.interval)
        return

    if not conectado and not args.dry_run:
        logger.error("Sin backend no se pueden registrar eventos. Levanta Spring Boot "
                     "o usa --dry-run para probar sólo la visión.")
        return

    from epp_detector import DetectorEPP

    import cv2 as _cv2
    _BACKENDS = {"dshow": _cv2.CAP_DSHOW, "msmf": _cv2.CAP_MSMF, "any": _cv2.CAP_ANY}

    detector = DetectorEPP(args.config, confianza=args.confianza, imgsz=args.imgsz, device=args.device)
    pipeline = PipelineEPP(
        detector=detector,
        cliente=cliente,
        rol_id=args.rol,
        linea=args.linea,
        mostrar=args.mostrar,
        dry_run=args.dry_run,
        backend_camara=_BACKENDS.get(args.backend_camara),
    )

    logger.info("EPP obligatorio para el rol %s: %s", args.rol, ", ".join(detector.epp_obligatorio(args.rol)[0]))
    try:
        if args.mode == "webcam":
            pipeline.ejecutar_webcam(int(args.src))
        elif args.mode == "video":
            pipeline.ejecutar_video(args.src)
        else:
            pipeline.ejecutar_imagen(args.src)
    except (RuntimeError, FileNotFoundError) as e:
        logger.error("%s", e)
    finally:
        detector.cerrar()


if __name__ == "__main__":
    main()
