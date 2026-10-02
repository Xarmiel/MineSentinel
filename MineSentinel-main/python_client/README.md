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

Los pesos se resuelven en este orden: primero el archivo local declarado en
`epp_config.yaml`, y sólo si no existe se descarga de HuggingFace.

| Modelo | Origen | Aporta |
|---|---|---|
| Personas | `yolov8n.pt` (local junto al config, o descarga de Ultralytics) | cajas con `track_id` = ancla de aforo y cumplimiento |
| EPP principal | `best_sentinel_v2.pt` (entrenado en local) | `gloves`, `goggles`, `helmet`, `vest` |
| EPP adicional | — (`adicionales: []`) | placeholder para un modelo de calzado |

> Las rutas de pesos del config son **relativas al propio `epp_config.yaml`**, no al
> directorio desde el que se lance el cliente: se puede ejecutar desde cualquier carpeta.

> `modelo.adicionales` está vacío porque el modelo propio ya cubre las cuatro clases
> vigiladas. Para recuperar el calzado de seguridad hay que descomentar el bloque
> `keremberke` del config y marcar `BOTAS` como `verificable: true`.

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

Sólo generan alerta los elementos que el modelo activo puede ver de verdad. Hoy son
**cuatro**, los que expone `best_sentinel_v2.pt`:

| Elemento | Clase del modelo | Verificable |
|---|---|---|
| `GUANTES` | `gloves` | sí |
| `GAFAS` | `goggles` | sí |
| `CASCO` | `helmet` | sí |
| `CHALECO` | `vest` | sí |

El resto del catálogo (`MASCARA`, `BOTAS`, `LAMPARA`, `RESPIRADOR`) está en
`verificable: false`: ningún modelo cargado los detecta, así que el motor **nunca**
genera una alerta por su ausencia, aunque el rol los exija. Al arrancar, el cliente
avisa por consola de cada rol que exige algo no verificable.

> `verificable: false` **no es cosmético**: apaga la alerta en dos sitios
> (`epp_obligatorio` y `_evaluar_hallazgos`). El elemento se sigue detectando y se
> sigue pintando en el cuadro, pero jamás dispara un aviso. Por eso hay tests que
> lo vigilan (`tests/test_reglas_epp.py`): un `false` puesto por error no da ningún
> error visible en producción, sólo la ausencia de avisos.

Es deliberado — evita alarmas constantes que nadie puede verificar en cámara.

---

## 5. Limitaciones conocidas

- **El modelo propio no trae clases de "gorra", "gafas de sol" ni "zapatillas".**
  Los datasets industriales suelen traer `no_helmet`, `no_shoes`, etc., que cubren el
  caso común (elemento simplemente ausente). El bloque `impostores:` ya está cableado y
  funcionando: si entrenas un modelo con esas clases y las declaras ahí, el motor las
  reporta como `IMPOSTOR` sin tocar una línea de código.
- **El calzado no se vigila.** `BOTAS` es obligatorio en todos los roles, pero sigue en
  `verificable: false` porque el modelo no tiene clase de calzado. Es la brecha conocida
  entre lo que la mina exige y lo que el sistema puede ver.
- Una clase `no_helmet` (o equivalente) se reporta como `FALTANTE`, no como `IMPOSTOR`,
  porque el modelo no puede distinguir "cabeza desnuda" de "gorra puesta".
- Los modelos de EPP se entrenan sobre recortes cerrados; por eso el detector de personas es
  un modelo COCO aparte. No esperes detecciones de EPP fiables a más de ~15 m de distancia.
- Las cajas que se dibujan en `/camara` vienen del frame original mientras que la imagen
  publicada va reescalada, y el `<img>` además recorta con `object-fit: cover`. El overlay
  compensa ambas escalas; si se toca ese mapeo, `src/test/js/prueba-camara.mjs` lo detecta.

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
