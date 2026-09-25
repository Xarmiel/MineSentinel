# 🐍 MineSentinel — Cliente de Integración de Visión (YOLOv8 & ByteTrack)

Este módulo contiene el script de Python para capturar video en tiempo real desde cámaras de seguridad en boca-mina o socavón, realizar el tracking de personal mediante **ByteTrack** y la detección de elementos de protección personal (EPP) mediante **YOLOv8**, transmitiendo los eventos automáticamente al backend Spring Boot.

---

## 🚀 Instalación y Requisitos

1. **Instalar dependencias**:
   ```bash
   pip install -r requirements.txt
   ```

---

## 💻 Modos de Ejecución

### 1. Modo Simulación Industrial (Ideal para demostraciones sin cámara física)
Genera cruces realistas de personal, faltas de EPP y anomalías con subida de snapshots fotográficos al servidor:
```bash
python yolo_sentinel_client.py --mode simulation --interval 3
```

### 2. Modo Cámara Web / Stream RTSP
Procesa el flujo de video en vivo:
```bash
python yolo_sentinel_client.py --mode webcam --src 0
```

### 3. Modo Archivo de Video Local
```bash
python yolo_sentinel_client.py --mode video --src video_mina.mp4
```

---

## 📡 Endpoints Consumidos en Spring Boot
- `POST /api/v1/evidencias/upload`: Carga de evidencias fotográficas (`multipart/form-data`).
- `POST /api/v1/aforo/movimiento`: Cruces de aforo (entradas y salidas).
- `POST /api/v1/seguridad/faltas-epp`: Registro de infracciones de EPP con confianza y snapshot.
- `POST /api/v1/seguridad/anomalias`: Registro de anomalías de movimiento.
