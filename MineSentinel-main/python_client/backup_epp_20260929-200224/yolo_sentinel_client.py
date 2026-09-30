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
import sys
import time
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
                          descripcion: str = "", track_id: Optional[int] = None) -> bool:
        """Registra una infracción de EPP (FALTANTE o IMPOSTOR) detectada por YOLOv8."""
        if epp_id is None:
            logger.warning("Infracción de tipo %s descartada: el elemento '%s' no existe en "
                           "el catálogo del backend", tipo_infraccion, descripcion)
            return False

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
                return True
            logger.warning("Error al registrar infracción: %s - %s", res.status_code, res.text)
        except Exception as e:
            logger.error("Fallo en envío de infracción: %s", e)
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
        self.lado_por_track: Dict[int, str] = {}
        self.total_movimientos = 0
        self.total_infracciones = 0

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
                self.cliente.enviar_movimiento_aforo(tipo, persona.track_id, self.rol_id)

        for track_id in list(self.lado_por_track):
            if track_id not in {p.track_id for p in analisis.personas}:
                del self.lado_por_track[track_id]

    def _reportar_infracciones(self, analisis) -> None:
        if not analisis.infracciones:
            return

        snapshot_url = ""
        if not self.dry_run and analisis.imagen_anotada is not None:
            snapshot_url = self.cliente.subir_evidencia(
                analisis.imagen_anotada, f"epp_track_{analisis.fotograma}.jpg"
            )

        for infraccion in analisis.infracciones:
            self.total_infracciones += 1
            if self.dry_run:
                logger.info("[DRY-RUN] %s | %s | conf %.2f | track %s",
                            infraccion.tipo, infraccion.descripcion, infraccion.confianza, infraccion.track_id)
                continue

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
        import cv2

        imagen = analisis.imagen_anotada
        if imagen is None:
            return

        ancho = int(getattr(self.detector, "cuadro_ancho_maximo", 960))
        if ancho and imagen.shape[1] > ancho:
            escala = ancho / imagen.shape[1]
            imagen = cv2.resize(imagen, (ancho, int(imagen.shape[0] * escala)),
                                interpolation=cv2.INTER_AREA)

        ok, buffer = cv2.imencode(".jpg", imagen, [int(cv2.IMWRITE_JPEG_QUALITY), 75])
        if not ok:
            logger.warning("No se pudo codificar el frame a JPEG")
            return

        self.cliente.enviar_frame(buffer.tobytes())

    def procesar_fuente(self, fuente) -> None:
        """Lee la fuente de video frame a frame hasta agotarla o Ctrl+C."""
        import cv2

        processed = 0
        try:
            while True:
                exito, frame = fuente.read()
                if not exito or frame is None:
                    break

                analisis = self.detector.analizar(frame, rol_id=self.rol_id, linea=self.linea)
                processed += 1
                self._cruces_de_linea(analisis, frame.shape[0])
                self._reportar_infracciones(analisis)
                self._publicar_cuadro(analisis)

                if processed % 30 == 0:
                    logger.info("Frame %s | %d personas | %d movimientos | %d infracciones | %.1f fps",
                                analisis.fotograma, len(analisis.personas), self.total_movimientos,
                                self.total_infracciones, analisis.fps)
                if self.mostrar:
                    cv2.imshow("MineSentinel - Deteccion de EPP", analisis.imagen_anotada)
                    if cv2.waitKey(1) & 0xFF == ord("q"):
                        break
        except KeyboardInterrupt:
            logger.info("Captura detenida por el usuario.")
        finally:
            if self.mostrar:
                cv2.destroyAllWindows()

        logger.info("Procesados %d frames | %d movimientos de aforo | %d infracciones reportadas",
                    processed, self.total_movimientos, self.total_infracciones)

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
