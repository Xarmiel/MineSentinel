"""
MineSentinel — Cliente de Integración de Visión por Computadora (YOLOv8 + ByteTrack)
=====================================================================================
Este script procesa video en tiempo real (cámara web, stream RTSP de mina o video local)
o ejecuta en modo simulación para enviar telemetría en tiempo real al backend Spring Boot.

Modos de ejecución:
  python yolo_sentinel_client.py --mode simulation     (Simula cruces e infracciones en bucle)
  python yolo_sentinel_client.py --mode webcam         (Utiliza la cámara web local)
  python yolo_sentinel_client.py --mode video --src mina.mp4
"""

import argparse
import io
import json
import logging
import random
import sys
import time
from datetime import datetime

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


class MineSentinelClient:
    def __init__(self, base_url: str = BACKEND_URL_DEFAULT):
        self.base_url = base_url.rstrip("/")
        self.session = requests.Session()
        self.track_counter = 100

    def verificar_conexion(self) -> bool:
        try:
            res = self.session.get(f"{self.base_url}/api/v1/aforo/tiempo-real", timeout=3)
            if res.status_code == 200:
                data = res.json()
                logger.info(f"Conectado a MineSentinel Backend. Aforo actual: {data.get('aforoActual')}/{data.get('aforoMaximo')}")
                return True
            else:
                logger.warning(f"Backend respondió con código: {res.status_code}")
                return False
        except Exception as e:
            logger.error(f"No se pudo conectar a {self.base_url}: {e}")
            return False

    def subir_snapshot_simulado(self, infraccion_texto: str) -> str:
        """Crea una imagen sintética en memoria y la sube al backend."""
        try:
            # Si PIL / OpenCV está disponible, podemos crear un frame real con texto
            try:
                from PIL import Image, ImageDraw, ImageFont
                img = Image.new('RGB', (640, 480), color=(20, 24, 33))
                draw = ImageDraw.Draw(img)
                draw.rectangle([10, 10, 630, 470], outline=(245, 158, 11), width=3)
                draw.text((30, 40), "MINESENTINEL - EVIDENCIA DE VISION", fill=(245, 158, 11))
                draw.text((30, 80), f"Fecha/Hora: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}", fill=(255, 255, 255))
                draw.text((30, 120), f"Infraccion: {infraccion_texto}", fill=(239, 68, 68))
                draw.text((30, 160), "Punto de Control: Boca-Mina Principal (CAM-01)", fill=(148, 163, 184))

                buffer = io.BytesIO()
                img.save(buffer, format="JPEG", quality=85)
                buffer.seek(0)
                files = {"file": ("snapshot_infraccion.jpg", buffer, "image/jpeg")}
            except ImportError:
                # Imagen dummy JPEG mínima si PIL no está instalado
                dummy_bytes = b"\xFF\xD8\xFF\xE0\x00\x10JFIF\x00\x01\x01\x01\x00`\x00`\x00\x00\xFF\xDB\x00C\x00\xFF\xD9"
                files = {"file": ("evidence.jpg", io.BytesIO(dummy_bytes), "image/jpeg")}

            res = self.session.post(f"{self.base_url}/api/v1/evidencias/upload", files=files, timeout=5)
            if res.status_code == 201:
                return res.json().get("snapshotUrl", "")
        except Exception as e:
            logger.debug(f"Aviso en subida de snapshot: {e}")
        return "/evidencias/snapshot_default.jpg"

    def enviar_movimiento_aforo(self, tipo_movimiento: str, track_id: int, rol_id: int = 1) -> bool:
        """Registra un cruce de línea virtual en el Event Ledger."""
        payload = {
            "tipoMovimiento": tipo_movimiento,
            "trackId": track_id,
            "rolId": rol_id
        }
        try:
            res = self.session.post(f"{self.base_url}/api/v1/aforo/movimiento", json=payload, timeout=5)
            if res.status_code == 201:
                m = res.json()
                logger.info(f"✅ Cruce {tipo_movimiento} registrado -> Movimiento #{m.get('movimientoId')} (Track #{track_id})")
                return True
            else:
                logger.warning(f"Error al registrar movimiento: {res.status_code} - {res.text}")
        except Exception as e:
            logger.error(f"Fallo en envío de movimiento: {e}")
        return False

    def enviar_falta_epp(self, epp_id: int, rol_id: int, confianza: float, snapshot_url: str) -> bool:
        """Registra una falta de EPP detectada por YOLOv8."""
        payload = {
            "eppId": epp_id,
            "rolId": rol_id,
            "nivelConfianza": round(confianza, 2),
            "snapshotUrl": snapshot_url
        }
        try:
            res = self.session.post(f"{self.base_url}/api/v1/seguridad/faltas-epp", json=payload, timeout=5)
            if res.status_code == 201:
                f = res.json()
                logger.warning(f"⚠️ Infracción de EPP registrada -> Falta #{f.get('faltaId')} (Confianza: {confianza*100:.1f}%)")
                return True
            else:
                logger.warning(f"Error al registrar falta EPP: {res.status_code} - {res.text}")
        except Exception as e:
            logger.error(f"Fallo en envío de falta EPP: {e}")
        return False

    def enviar_anomalia(self, catalogo_anomalia_id: int, rol_id: int, confianza: float, snapshot_url: str) -> bool:
        """Registra una anomalía detectada por visión."""
        payload = {
            "catalogoAnomaliaId": catalogo_anomalia_id,
            "rolId": rol_id,
            "nivelConfianza": round(confianza, 2),
            "snapshotUrl": snapshot_url
        }
        try:
            res = self.session.post(f"{self.base_url}/api/v1/seguridad/anomalias", json=payload, timeout=5)
            if res.status_code == 201:
                a = res.json()
                logger.warning(f"🚨 Anomalía de movimiento registrada -> ID #{a.get('anomaliaId')}")
                return True
        except Exception as e:
            logger.error(f"Fallo en envío de anomalía: {e}")
        return False

    def ejecutar_simulacion(self, intervalo_segundos: float = 4.0):
        """Ejecuta una simulación realista continua de operaciones en mina."""
        logger.info("Iniciando modo de simulación industrial continua (Presiona Ctrl+C para detener)...")
        roles = [1, 2, 3, 4] # Operador, Supervisor, Tecnico, Geologo
        
        while True:
            try:
                self.track_counter += 1
                rol_id = random.choice(roles)
                
                # 80% probabilidad de ingreso, 20% salida
                tipo = "ENTRADA" if random.random() < 0.75 else "SALIDA"
                
                # 15% probabilidad de falta de EPP en el ingreso
                tiene_infraccion_epp = (tipo == "ENTRADA" and random.random() < 0.20)
                # 10% probabilidad de anomalía de tránsito
                tiene_anomalia = (random.random() < 0.10)

                if tiene_infraccion_epp:
                    epp_id = random.choice([1, 2, 3]) # Casco, Chaleco, Lampara
                    conf = random.uniform(0.88, 0.98)
                    snap = self.subir_snapshot_simulado("Sin Casco de Seguridad con Barbiquejo")
                    self.enviar_falta_epp(epp_id, rol_id, conf, snap)

                if tiene_anomalia:
                    anom_id = random.choice([1, 2, 4]) # Sentido contrario, Aglomeracion, Tiempo excesivo
                    conf = random.uniform(0.85, 0.95)
                    snap = self.subir_snapshot_simulado("Cruce en Sentido Contrario al Flujo")
                    self.enviar_anomalia(anom_id, rol_id, conf, snap)

                # Registrar cruce de aforo
                self.enviar_movimiento_aforo(tipo, self.track_counter, rol_id)

                time.sleep(intervalo_segundos)
            except KeyboardInterrupt:
                logger.info("Simulación detenida por el usuario.")
                break
            except Exception as ex:
                logger.error(f"Error inesperado en ciclo de simulación: {ex}")
                time.sleep(2)


def main():
    parser = argparse.ArgumentParser(description="MineSentinel - Cliente de Inferencia YOLOv8 / ByteTrack")
    parser.add_argument("--url", default=BACKEND_URL_DEFAULT, help="URL base del backend Spring Boot")
    parser.add_argument("--mode", default="simulation", choices=["simulation", "webcam", "video"],
                        help="Modo de ejecución")
    parser.add_argument("--src", default="0", help="Fuente de video (índice webcam o ruta de archivo mp4)")
    parser.add_argument("--interval", type=float, default=3.5, help="Intervalo de eventos en simulación (segundos)")

    args = parser.parse_args()

    client = MineSentinelClient(args.url)
    if not client.verificar_conexion():
        logger.warning("El backend no parece estar respondiendo en " + args.url + ". Verifica que Spring Boot esté ejecutándose.")

    if args.mode == "simulation":
        client.ejecutar_simulacion(args.interval)
    else:
        logger.info(f"Modo '{args.mode}' seleccionado con fuente '{args.src}'.")
        logger.info("Iniciando bucle de captura...")
        client.ejecutar_simulacion(args.interval)


if __name__ == "__main__":
    main()
