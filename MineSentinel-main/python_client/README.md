# MineSentinel — Cliente de Integración de Visión (YOLOv8 & ByteTrack)

Módulo de visión que procesa el video de cámaras de boca-mina, rastrea al personal con
**ByteTrack**, evalúa el cumplimiento de **EPP** con **YOLOv8** y reporta al backend Spring
Boot los cruces de aforo, las faltas de EPP y —lo más importante para seguridad— los
**objetos no homologados que se hacen pasar por EPP** (gorra en vez de casco, gafas de sol,
calzado civil).

---

## 1. Instalación

```bash
pip install -r requirements.txt
```

Los pesos se descargan solos en la primera ejecución (~90 MB, cacheados por HuggingFace):

| Modelo | Repositorio | Aporta |
|---|---|---|
| Personas | `yolov8n.pt` (Ultralytics, COCO) | cajas con `track_id` = ancla de aforo y cumplimiento |
| EPP principal | `Hexmon/vyra-yolo-ppe-detection` (YOLOv8m) | casco, chaleco, gafas, guantes, máscara + sus clases `NO-*` |
| EPP adicional | `keremberke/yolov8s-protective-equipment-detection` | calzado de seguridad (`shoes` / `no_shoes`) |

> Se fusionan los dos modelos de EPP porque ninguno cubre por sí solo todos los elementos:
> el primero no distingue calzado y el segundo no distingue chaleco.

---

## 2. Modos de ejecución

```bash
# Visión real contra la cámara local, con ventana de video anotado
python yolo_sentinel_client.py --mode webcam --src 0 --mostrar

# Video grabado (o stream RTSP de mina)
python yolo_sentinel_client.py --mode video --src video_mina.mp4

# Foto fija: útil para depurar el modelo
python yolo_sentinel_client.py --mode image --src foto_control.jpg

# Probar la visión sin backend (sólo consola)
python yolo_sentinel_client.py --mode video --src video_mina.mp4 --dry-run

# Demo sin cámara
python yolo_sentinel_client.py --mode simulation --interval 3
```

### Opciones útiles

| Flag | Por defecto | Descripción |
|---|---|---|
| `--rol` | `1` | `rol_id` de `roles_personal`. Define qué EPP es obligatorio (ver §4) |
| `--linea` | `0.5` | Altura de la línea virtual de aforo, como fracción del alto del frame |
| `--config` | `epp_config.yaml` | Reglas, mapeo de clases y pesos |
| `--confianza` | `0.35` | Umbral de detección |
| `--device` | `cpu` | `cuda:0` si hay GPU NVIDIA |
| `--mostrar` | — | Abre ventana con cajas, línea de aforo y colores por estado |
| `--dry-run` | — | No envía nada al backend |

---

## 3. Qué reporta

Cada frame produce tres salidas:

1. **Cruce de aforo** — cuando el punto de apoyo de un track cruza la línea virtual, se
   registra `ENTRADA` o `SALIDA` con su `trackId` (ByteTrack).
2. **Falta de EPP** (`tipoInfraccion = FALTANTE`) — el EPP que su rol exige no se detecta.
3. **Elemento no apto** (`tipoInfraccion = IMPOSTOR`) — se porta un objeto que **imita** a
   un EPP sin estar homologado. Es más grave que una falta: hay riesgo *e* intento de
   encubrimiento.

Todo hallazgo sube antes un snapshot con el frame anotado (cajas en verde = conforme,
rojo = ausencia, naranja = impostor, morado = anomalía) a `/api/v1/evidencias/upload`, y la
alerta apunta a esa URL como evidencia.

Las infracciones se filtran por **enfriamiento** (90 s por track + combinación elemento/tipo,
configurable) para no saturar el tablero con la misma alerta frame a frame.

---

## 4. Configuración: `epp_config.yaml`

Es la única fuente de verdad del pipeline. No contiene `epp_id` hardcodeados: el cliente los
resuelve al arrancar contra `GET /api/v1/catalogos/epp` emparejando por nombre.

### Regla por rol

```yaml
roles:
  1:
    nombre: "Operador de Maquinaria / Perforista"
    obligatorio: [CASCO, CHALECO, BOTAS, GAFAS, GUANTES]
  2:
    nombre: "Supervisor de Seguridad / Ingeniero de Minas"
    obligatorio: [CASCO, CHALECO, BOTAS, GAFAS]
  "*":
    nombre: "Personal sin rol asignado"
    obligatorio: [CASCO, CHALECO, BOTAS]
```

### Instrumentos no aptos

```yaml
impostores:
  GORRA:
    sustituye: CASCO
    clases: ["cap", "baseball_cap", "gorra", "hat"]
    nombre: "Gorra / Sombrero No Apto como Casco"
  GAFAS_SOL:
    sustituye: GAFAS
    clases: ["sunglasses", "gafas_de_sol"]
  CALZADO_NORMAL:
    sustituye: BOTAS
    clases: ["sneakers", "zapatillas", "trainers"]
```

### Elementos no verificables

`LAMPARA` y `RESPIRADOR` están en el catálogo pero marcados `verificable: false`: ningún
modelo público detecta una lámpara frontal o un respirador, así que el motor **nunca** genera
una alerta por su ausencia. Es deliberado — evita alarmas constantes que nadie puede
verificar en cámara.

---

## 5. Limitaciones conocidas

- **Ningún modelo público de EPP trae clases de "gorra", "gafas de sol" ni "zapatillas".**
  Los modelos de dataset industrial sólo tienen `no_helmet`, `no_shoes`, etc., que cubren el
  caso común (elemento simplemente ausente). El bloque `impostores:` ya está cableado y
  funcionando: si entrenas tu propio modelo con esas clases y las declaras ahí, el motor las
  reporta como `IMPOSTOR` sin tocar una línea de código.
- La clase `no_helmet` se reporta como `FALTANTE`, no como `IMPOSTOR`, porque el modelo no
  puede distinguir "cabeza desnuda" de "gorra puesta".
- Los modelos de EPP se entrenan sobre recortes cerrados; por eso el detector de personas es
  un modelo COCO aparte. No esperes detecciones de EPP fiables a más de ~15 m de distancia.

---

## 6. Endpoints consumidos en Spring Boot

| Método | Ruta | Uso |
|---|---|---|
| `GET` | `/api/v1/aforo/tiempo-real` | verificación de conexión |
| `GET` | `/api/v1/catalogos/epp` | resolución nombre → `eppId` |
| `GET` | `/api/v1/catalogos/anomalias` | resolución nombre → `catalogoAnomaliaId` |
| `POST` | `/api/v1/evidencias/upload` | snapshot anotado (`multipart/form-data`) |
| `POST` | `/api/v1/aforo/movimiento` | cruce de aforo (`{tipoMovimiento, trackId, rolId}`) |
| `POST` | `/api/v1/seguridad/infracciones` | `FALTANTE` / `IMPOSTOR` |
| `POST` | `/api/v1/seguridad/faltas-epp` | endpoint legacy equivalente |
| `POST` | `/api/v1/seguridad/anomalias` | caídas / posturas anómalas |
