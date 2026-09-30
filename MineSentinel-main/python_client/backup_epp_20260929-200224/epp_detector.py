"""
MineSentinel — Motor de detección de EPP sobre YOLOv8
======================================================
Carga uno o varios modelos de detección de EPP, fusiona sus predicciones,
asocia cada elemento a la persona que lo porta (anclada por ByteTrack) y
evalúa el cumplimiento frente a la normativa por rol definida en
`epp_config.yaml`.

Distinción clave del motor:

  CONFORME  el trabajador porta EPP homologado            -> sin alerta
  AUSENTE   el modelo detecta que no lo porta             -> alerta FALTANTE
  IMPOSTOR  porta un objeto no homologado que imita al    -> alerta IMPOSTOR
            EPP (gorra, gafas de sol, zapatillas)

Las clases IMPOSTOR las declara el usuario en `epp_config.yaml`; ningún modelo
público de EPP las trae de fábrica, por lo que se reportan solo cuando el
modelo efectivamente las reconoce.
"""

import logging
import os
import re
import time
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Tuple

import numpy as np

logger = logging.getLogger("MineSentinel.Detector")

# Estados de un elemento de EPP sobre un trabajador
ESTADO_CONFORME = "CONFORME"
ESTADO_AUSENTE = "AUSENTE"
ESTADO_IMPOSTOR = "IMPOSTOR"

GRAVEDAD_GRAVE = "GRAVE"
GRAVEDAD_INTERMEDIA = "INTERMEDIO"
GRAVEDAD_LEVE = "LEVE"

# Estado global del cuadro de una persona: de ahí sale el color del recuadzo.
CUADRO_CONFORME = "CONFORME"
CUADRO_INTERMEDIO = "INTERMEDIO"
CUADRO_CRITICO = "CRITICO"

COLOR_CUADRO = {
    CUADRO_CONFORME: (0, 200, 0),
    CUADRO_INTERMEDIO: (0, 165, 255),
    CUADRO_CRITICO: (0, 0, 235),
}

# Tipos de infracción reportables al backend
TIPO_FALTANTE = "FALTANTE"
TIPO_IMPOSTOR = "IMPOSTOR"

# track_id sintético para hallazgos de EPP que no se pudieron anclar a ninguna persona
TRACK_SIN_ASIGNAR = -1


# =============================================================================
# ESTRUCTURAS DE DATOS
# =============================================================================

@dataclass
class Caja:
    """Caja delimitadora en coordenadas de píxel del frame original."""
    x1: float
    y1: float
    x2: float
    y2: float

    @property
    def ancho(self) -> float:
        return max(0.0, self.x2 - self.x1)

    @property
    def alto(self) -> float:
        return max(0.0, self.y2 - self.y1)

    @property
    def centro(self) -> Tuple[float, float]:
        return ((self.x1 + self.x2) / 2.0, (self.y1 + self.y2) / 2.0)

    @property
    def pie(self) -> Tuple[float, float]:
        """Punto de apoyo (centro inferior): el más estable para el cruce de línea."""
        return ((self.x1 + self.x2) / 2.0, self.y2)

    def contiene(self, x: float, y: float) -> bool:
        return self.x1 <= x <= self.x2 and self.y1 <= y <= self.y2

    def expandida(self, fx: float, fy: float) -> "Caja":
        ex = self.ancho * fx
        ey = self.alto * fy
        return Caja(self.x1 - ex, self.y1 - ey, self.x2 + ex, self.y2 + ey)

    def como_lista(self) -> List[float]:
        return [self.x1, self.y1, self.x2, self.y2]


@dataclass
class Deteccion:
    """Una caja detectada ya interpretada por el vocabulario canónico."""
    clase: str
    modelo: str
    caja: Caja
    confianza: float
    estado: str
    clave: Optional[str] = None
    track_id: Optional[int] = None

    @property
    def es_persona(self) -> bool:
        return self.clave == "__persona__"


@dataclass
class Persona:
    """Un track ByteTrack en el frame actual."""
    track_id: int
    caja: Caja
    confianza: float
    lado: Optional[str] = None          # "ARRIBA" / "ABAJO" respecto a la línea virtual
    elementos: List[Deteccion] = field(default_factory=list)

    @property
    def tiene_infraccion(self) -> bool:
        return any(d.estado in (ESTADO_AUSENTE, ESTADO_IMPOSTOR) for d in self.elementos)


@dataclass
class Infraccion:
    """Infracción de seguridad lista para enviarse al backend."""
    tipo: str                          # FALTANTE | IMPOSTOR | ANOMALIA
    clave: str                         # clave canónica (CASCO, BOTAS, GORRA...)
    nombre_catalogo: str               # debe existir en catalogo_epp
    descripcion: str
    confianza: float
    track_id: int
    clase_detectada: str               # clase del modelo que originó el hallazgo
    gravedad: str = GRAVEDAD_INTERMEDIA  # GRAVE | INTERMEDIO | LEVE
    evidencia_directa: bool = True     # False = ausencia inferida, sin caja propia
    es_anomalia: bool = False


@dataclass
class FrameAnalisis:
    """Resultado completo del análisis de un frame."""
    personas: List[Persona] = field(default_factory=list)
    elementos: List[Deteccion] = field(default_factory=list)
    # Infracciones NUEVAS, ya filtradas por enfriamiento: es lo que se reporta
    # como alerta al backend.
    infracciones: List[Infraccion] = field(default_factory=list)
    # Infracciones VIGENTES de este frame, sin filtrar. El cuadro de la vista de
    # cámara se construye con esta lista: si usara `infracciones`, un trabajador
    # en periodo de enfriamiento parpadearía a "conforme" aun sin casco.
    estado_actual: List[Infraccion] = field(default_factory=list)
    imagen_anotada: Optional[np.ndarray] = None
    fotograma: int = 0
    fps: float = 0.0


# =============================================================================
# UTILIDADES
# =============================================================================

def normalizar_clase(clase: str) -> str:
    """Normaliza el nombre de clase del modelo para compararlo con el mapeo.

    'NO-Safety Vest' -> 'no_safety_vest' ; 'Fall-Detected' -> 'fall_detected'
    """
    return re.sub(r"[\s\-]+", "_", str(clase).strip().lower()).strip("_")


def iou(a: Caja, b: Caja) -> float:
    """Intersección sobre unión entre dos cajas."""
    x1, y1 = max(a.x1, b.x1), max(a.y1, b.y1)
    x2, y2 = min(a.x2, b.x2), min(a.y2, b.y2)
    if x2 <= x1 or y2 <= y1:
        return 0.0
    interseccion = (x2 - x1) * (y2 - y1)
    union = a.ancho * a.alto + b.ancho * b.alto - interseccion
    return interseccion / union if union > 0 else 0.0


def distancia_fuera(x: float, y: float, caja: Caja) -> float:
    """Distancia euclídea del punto (x,y) al rectángulo (0 si está dentro)."""
    dx = max(caja.x1 - x, 0.0, x - caja.x2)
    dy = max(caja.y1 - y, 0.0, y - caja.y2)
    return float(np.hypot(dx, dy))


# =============================================================================
# MOTOR DE DETECCIÓN
# =============================================================================

class DetectorEPP:
    """Carga los modelos, fusiona detecciones y evalúa el cumplimiento de EPP."""

    def __init__(self, config_path: str, confianza: Optional[float] = None,
                imgsz: Optional[int] = None, device: Optional[str] = None):
        import yaml  # import diferido: mensaje de error más claro

        self.config_path = config_path
        with open(config_path, "r", encoding="utf-8") as fh:
            self.cfg: Dict[str, Any] = yaml.safe_load(fh) or {}

        modelo_cfg = self.cfg.get("modelo", {})
        self.imgsz = int(imgsz if imgsz is not None else modelo_cfg.get("imgsz", 640))
        self.confianza = float(confianza if confianza is not None else modelo_cfg.get("confianza", 0.35))
        self.iou_umbral = float(modelo_cfg.get("iou", 0.45))
        self.device = device if device is not None else modelo_cfg.get("device", "cpu")

        asig = self.cfg.get("asignacion", {})
        self.margen_x = float(asig.get("margen_x", 0.15))
        self.margen_y = float(asig.get("margen_y", 0.10))
        self.confianza_suelto = float(asig.get("confianza_suelto", 0.60))

        self.catalogo_epp: Dict[str, Any] = self.cfg.get("catalogo_epp", {}) or {}
        self.roles_cfg: Dict[str, Any] = self.cfg.get("roles", {}) or {}
        self.anomalias_cfg: Dict[str, Any] = self.cfg.get("anomalias", {}) or {}

        emision = self.cfg.get("emision", {}) or {}
        self.enfriamiento = float(emision.get("enfriamiento_segundos", 90.0))
        self.frames_estables = int(emision.get("frames_estables", 5))
        self.confianza_minima_alerta = float(emision.get("confianza_minima_alerta", 0.45))

        cuadro = self.cfg.get("cuadro", {}) or {}
        self.cuadro_intervalo = int(cuadro.get("intervalo_fotogramas", 10))
        self.cuadro_expiracion = float(cuadro.get("expiracion_segundos", 8.0))
        self.cuadro_incluir_cajas = bool(cuadro.get("incluir_cajas", True))
        self.cuadro_publicar_frame = bool(cuadro.get("publicar_frame", True))
        self.cuadro_ancho_maximo = int(cuadro.get("ancho_maximo", 960))

        # clave canónica -> gravedad declarada en el catálogo
        self._gravedad: Dict[str, str] = {}
        for clave, definicion in self.catalogo_epp.items():
            self._gravedad[clave] = str(definicion.get("gravedad", GRAVEDAD_INTERMEDIA)).upper()
        for clave in (self.cfg.get("impostores", {}) or {}).keys():
            # Un impostor no es "falta un EPP": es EPP homologado ausente Y un
            # objeto no apto en su lugar. Siempre se escala al máximo.
            self._gravedad[clave] = GRAVEDAD_GRAVE

        self._indice_clases: Dict[str, Tuple[Optional[str], str]] = self._construir_indice()
        self._modelos: List[Tuple[str, Any]] = []          # (nombre, modelo) de EPP
        self._modelo_personas: Optional[Tuple[str, Any]] = None
        self._clases_persona: set = set()

        # Estado de seguimiento entre frames
        self._estado_tracks: Dict[int, Dict[str, Any]] = {}
        self._track_counter = 0
        self._ultimas_dimensiones: Optional[Tuple[int, int]] = None
        self._tracker_path = os.path.join(
            os.path.dirname(os.path.abspath(config_path)), "bytetrack_epp.yaml"
        )
        self._contador_fotogramas = 0

        self._cargar_modelos(modelo_cfg)
        self._advertir_reglas_no_verificables()

    # -------------------------------------------------------------------------
    # CARGA
    # -------------------------------------------------------------------------

    def _construir_indice(self) -> Dict[str, Tuple[Optional[str], str]]:
        """clase_normalizada -> (clave canónica, estado)."""
        indice: Dict[str, Tuple[Optional[str], str]] = {}

        for clave, mapeo in (self.cfg.get("mapeo_clases", {}) or {}).items():
            for clase in mapeo.get("conforme", []) or []:
                indice[normalizar_clase(clase)] = (clave, ESTADO_CONFORME)
            for clase in mapeo.get("ausencia", []) or []:
                indice[normalizar_clase(clase)] = (clave, ESTADO_AUSENTE)

        for clave, definicion in (self.cfg.get("impostores", {}) or {}).items():
            for clase in definicion.get("clases", []) or []:
                indice[normalizar_clase(clase)] = (clave, ESTADO_IMPOSTOR)

        for clave, definicion in self.anomalias_cfg.items():
            for clase in definicion.get("clases", []) or []:
                indice[normalizar_clase(clase)] = (clave, "ANOMALIA")

        return indice

    def _resolver_pesos(self, definicion: Dict[str, Any]) -> str:
        """Devuelve una ruta local a los pesos, descargándolos de HuggingFace si hace falta."""
        ruta = definicion.get("ruta")
        if ruta:
            if not os.path.isfile(ruta):
                raise FileNotFoundError(f"No se encuentra el modelo local: {ruta}")
            return ruta

        repo = definicion.get("repo_hf")
        if not repo:
            raise ValueError(f"El modelo '{definicion.get('nombre')}' no define 'ruta' ni 'repo_hf'")

        from huggingface_hub import hf_hub_download

        logger.info("⏳ Descargando pesos de HuggingFace: %s (%s)", repo, definicion.get("archivo", "best.pt"))
        return hf_hub_download(repo_id=repo, filename=definicion.get("archivo", "best.pt"))

    def _cargar_modelos(self, modelo_cfg: Dict[str, Any]) -> None:
        from ultralytics import YOLO

        principal = modelo_cfg.get("principal") or {}
        if not principal:
            raise ValueError("epp_config.yaml debe definir 'modelo.principal'")

        definiciones = [principal] + list(modelo_cfg.get("adicionales", []) or [])
        for definicion in definiciones:
            nombre = definicion.get("nombre", "modelo")
            pesos = self._resolver_pesos(definicion)
            modelo = YOLO(pesos)
            nombres = {normalizar_clase(n) for n in modelo.names.values()}
            self._modelos.append((nombre, modelo))
            logger.info("Modelo de EPP '%s' cargado desde %s (%d clases)", nombre, pesos, len(nombres))

        # --- Detector de personas (ancla de tracking y de cumplimiento) -------
        personas_cfg = modelo_cfg.get("personas")
        if personas_cfg:
            nombre = personas_cfg.get("nombre", "personas")
            pesos = personas_cfg.get("ruta") or personas_cfg.get("pesos") or "yolov8n.pt"
            if not os.path.isfile(pesos):
                logger.info("Descargando detector de personas: %s", pesos)
            modelo = YOLO(pesos)
            nombres = {normalizar_clase(n) for n in modelo.names.values()}
            declaradas = {normalizar_clase(c) for c in personas_cfg.get("clases", ["person"])}
            if not (declaradas & nombres):
                raise ValueError(
                    f"El detector de personas '{nombre}' no expone {declaradas}; "
                    "corrige 'modelo.personas.clases' en epp_config.yaml"
                )
            self._modelo_personas = (nombre, modelo)
            self._clases_persona = declaradas
            logger.info("Detector de personas '%s' cargado (%s)", nombre, pesos)
        else:
            # Reserva: se usan las clases persona del propio modelo de EPP.
            declaradas = {normalizar_clase(c) for c in principal.get("clases_persona", ["person"])}
            self._clases_persona = declaradas
            logger.warning(
                "Sin bloque 'modelo.personas:' en epp_config.yaml: se usará la clase persona "
                "del modelo de EPP, que suele ser poco fiable para aforo."
            )

        if not os.path.isfile(self._tracker_path):
            logger.warning("No se encontró %s; se usará el tracker por defecto de Ultralytics", self._tracker_path)
            self._tracker_path = "bytetrack.yaml"

    def _advertir_reglas_no_verificables(self) -> None:
        for clave_rol, regla in self.roles_cfg.items():
            no_verificables = [
                c for c in (regla.get("obligatorio") or [])
                if not (self.catalogo_epp.get(c, {}) or {}).get("verificable", False)
            ]
            if no_verificables:
                logger.warning(
                    "El rol '%s' exige %s, pero ningún modelo cargado puede detectarlo: se omite la alerta.",
                    clave_rol, ", ".join(no_verificables),
                )

    # -------------------------------------------------------------------------
    # INFERENCIA
    # -------------------------------------------------------------------------

    def _convertir(self, resultado: Any, nombre: str, con_track: bool) -> List[Deteccion]:
        """Traduce un resultado de Ultralytics a detecciones con vocabulario canónico."""
        salida: List[Deteccion] = []
        cajas = getattr(resultado, "boxes", None)
        if cajas is None or len(cajas) == 0:
            return salida

        identidades = cajas.id if con_track else None
        for indice in range(len(cajas)):
            clase = normalizar_clase(resultado.names[int(cajas.cls[indice])])
            caja = Caja(
                float(cajas.xyxy[indice][0]), float(cajas.xyxy[indice][1]),
                float(cajas.xyxy[indice][2]), float(cajas.xyxy[indice][3]),
            )
            track_id = int(identidades[indice]) if identidades is not None else None

            if clase in self._clases_persona:
                clave, estado = "__persona__", "PERSONA"
            else:
                clave, estado = self._indice_clases.get(clase, (None, "OTRO"))

            salida.append(Deteccion(
                clase=clase,
                modelo=nombre,
                caja=caja,
                confianza=float(cajas.conf[indice]),
                estado=estado,
                clave=clave,
                track_id=track_id,
            ))
        return salida

    def _inferir_personas(self, frame: np.ndarray) -> List[Deteccion]:
        """Detecta y rasteriza personas. Es el único punto que genera track_id."""
        argumentos = dict(
            conf=0.30,
            iou=self.iou_umbral,
            imgsz=self.imgsz,
            device=self.device,
            verbose=False,
        )
        nombre, modelo = self._modelo_personas
        # Ultralytics exige model.track() explícito para activar ByteTrack;
        # model(frame, persist=...) ya no es una llamada válida en 8.4+.
        resultados = modelo.track(frame, persist=True, tracker=self._tracker_path, **argumentos)
        detecciones: List[Deteccion] = []
        for resultado in resultados or []:
            detecciones.extend(self._convertir(resultado, nombre, con_track=True))
        return [d for d in detecciones if d.clave == "__persona__"]

    def _inferir_epp(self, frame: np.ndarray) -> List[Deteccion]:
        """Detecta elementos de EPP. Sin tracking: solo aportan caja y estado."""
        argumentos = dict(
            conf=self.confianza,
            iou=self.iou_umbral,
            imgsz=self.imgsz,
            device=self.device,
            verbose=False,
        )
        detecciones: List[Deteccion] = []
        for nombre, modelo in self._modelos:
            for resultado in modelo(frame, **argumentos) or []:
                for deteccion in self._convertir(resultado, nombre, con_track=False):
                    if deteccion.clave == "__persona__" or deteccion.estado == "OTRO":
                        continue
                    detecciones.append(deteccion)
        return self._fusionar(detecciones)

    def _inferir(self, frame: np.ndarray) -> Tuple[List[Deteccion], List[Deteccion]]:
        """Devuelve (personas rastreadas, elementos de EPP fusionados)."""
        if self._modelo_personas is not None:
            return self._inferir_personas(frame), self._inferir_epp(frame)

        # Reserva: sin detector dedicado, el propio modelo de EPP hace de ancla.
        argumentos = dict(
            conf=self.confianza, iou=self.iou_umbral, imgsz=self.imgsz,
            device=self.device, verbose=False,
        )
        nombre, modelo = self._modelos[0]
        personas, elementos = [], []
        for resultado in modelo.track(frame, persist=True, tracker=self._tracker_path, **argumentos) or []:
            for deteccion in self._convertir(resultado, nombre, con_track=True):
                (personas if deteccion.clave == "__persona__" else elementos).append(deteccion)
        return [d for d in personas if d.track_id is not None], self._fusionar(
            [d for d in elementos if d.estado != "OTRO"]
        )

    @staticmethod
    def _fusionar(detecciones: List[Deteccion]) -> List[Deteccion]:
        """NMS entre modelos: si dos modelos detectan lo mismo, gana el más confiado."""
        ordenadas = sorted(detecciones, key=lambda d: d.confianza, reverse=True)
        conservadas: List[Deteccion] = []
        for deteccion in ordenadas:
            if any(deteccion.clase == c.clase and iou(deteccion.caja, c.caja) > 0.55 for c in conservadas):
                continue
            conservadas.append(deteccion)
        return conservadas

    # -------------------------------------------------------------------------
    # ASIGNACIÓN ELEMENTO -> PERSONA
    # -------------------------------------------------------------------------

    def _asociar(self, personas: List[Persona], elementos: List[Deteccion]) -> List[Deteccion]:
        """Asigna cada EPP a la persona que lo porta.

        Devuelve los elementos sin dueño. Un elemento sin dueño NO se descarta: si
        es una ausencia explícita ("no_helmet") o un impostor, se reporta igual con
        track_id = TRACK_SIN_ASIGNAR, porque en una mina ese hallazgo crítico puede
        llegar de un trabajador lejano que el detector de personas no resolvió.
        """
        sueltos: List[Deteccion] = []
        for elemento in elementos:
            cx, cy = elemento.caja.centro
            mejor_persona, mejor_score = None, 0.0
            for persona in personas:
                puntaje = iou(elemento.caja, persona.caja)
                if puntaje == 0.0:
                    margen = persona.caja.expandida(self.margen_x, self.margen_y)
                    distancia = distancia_fuera(cx, cy, margen)
                    escala = max(persona.caja.ancho, persona.caja.alto, 1.0)
                    puntaje = max(0.0, 1.0 - (distancia / (escala * 0.5)))
                    if not margen.contiene(cx, cy) and puntaje < 0.30:
                        puntaje = 0.0
                if puntaje > mejor_score:
                    mejor_persona, mejor_score = persona, puntaje

            if mejor_persona is not None:
                elemento.track_id = mejor_persona.track_id
                mejor_persona.elementos.append(elemento)
            elif elemento.confianza >= self.confianza_suelto:
                elemento.track_id = TRACK_SIN_ASIGNAR
                sueltos.append(elemento)
        return sueltos

    # -------------------------------------------------------------------------
    # REGLAS DE CUMPLIMIENTO
    # -------------------------------------------------------------------------

    def epp_obligatorio(self, rol_id: int) -> Tuple[List[str], str]:
        """Devuelve (claves obligatorias, nombre del rol) según la configuración."""
        # PyYAML entrega claves numéricas de `roles:` como enteros, pero un rol_id
        # puede llegar como string desde la CLI o el backend.
        regla = self.roles_cfg.get(rol_id) or self.roles_cfg.get(str(rol_id)) or self.roles_cfg.get("*") or {}
        claves = [c for c in (regla.get("obligatorio") or [])
                  if (self.catalogo_epp.get(c, {}) or {}).get("verificable", False)]
        return claves, regla.get("nombre", f"Rol {rol_id}")

    def _evaluar_hallazgos(self, elementos: List[Deteccion], track_id: int,
                        obligatorios: List[str]) -> List[Infraccion]:
        """Convierte detecciones de EPP en infracciones evaluando el cumplimiento.

        Cubre dos casos con la misma lógica:
          - los elementos asignados a una persona (track_id real), y
          - los elementos sin persona dueña (track_id = TRACK_SIN_ASIGNAR).
        """
        infracciones: List[Infraccion] = []
        detectados = {d.clave for d in elementos if d.clave and d.estado == ESTADO_CONFORME}

        # --- 1. EPP homologado ausente -------------------------------------
        for clave in obligatorios:
            meta = self.catalogo_epp.get(clave, {}) or {}
            if not meta.get("verificable", False):
                continue
            if clave in detectados:
                continue
            ausencias = [d for d in elementos if d.clave == clave and d.estado == ESTADO_AUSENTE]
            if ausencias:
                mejor = max(ausencias, key=lambda d: d.confianza)
                infracciones.append(Infraccion(
                    tipo=TIPO_FALTANTE,
                    clave=clave,
                    nombre_catalogo=meta.get("nombre", clave),
                    descripcion=f"Sin {meta.get('nombre', clave)} (detectado como {mejor.clase})",
                    confianza=mejor.confianza,
                    track_id=track_id,
                    clase_detectada=mejor.clase,
                    gravedad=self._gravedad.get(clave, GRAVEDAD_INTERMEDIA),
                    evidencia_directa=True,
                ))
            elif track_id != TRACK_SIN_ASIGNAR:
                # Sin caja que lo respalde sólo se infiere si el EPP es obligatorio;
                # para un hallazgo suelto no hay evidencia de a quién corresponde.
                infracciones.append(Infraccion(
                    tipo=TIPO_FALTANTE,
                    clave=clave,
                    nombre_catalogo=meta.get("nombre", clave),
                    descripcion=f"Sin {meta.get('nombre', clave)} detectado en la persona",
                    confianza=self.confianza_minima_alerta,
                    track_id=track_id,
                    clase_detectada="",
                    gravedad=self._gravedad.get(clave, GRAVEDAD_INTERMEDIA),
                    evidencia_directa=False,
                ))

        # --- 2. Instrumentos no aptos (gorra, gafas de sol, zapatillas) -------
        # Se reportan aunque el EPP sustituido no sea obligatorio para el rol.
        impostores_cfg = self.cfg.get("impostores", {}) or {}
        for deteccion in elementos:
            if deteccion.estado == ESTADO_IMPOSTOR:
                definicion = impostores_cfg.get(deteccion.clave, {})
                infracciones.append(Infraccion(
                    tipo=TIPO_IMPOSTOR,
                    clave=deteccion.clave,
                    nombre_catalogo=definicion.get("nombre", deteccion.clave),
                    descripcion=definicion.get("descripcion", f"Uso de {deteccion.clase} no apto para seguridad"),
                    confianza=deteccion.confianza,
                    track_id=track_id,
                    clase_detectada=deteccion.clase,
                    gravedad=self._gravedad.get(deteccion.clave, GRAVEDAD_GRAVE),
                    evidencia_directa=True,
                ))
            elif deteccion.estado == "ANOMALIA":
                definicion = self.anomalias_cfg.get(deteccion.clave, {})
                infracciones.append(Infraccion(
                    tipo="ANOMALIA",
                    clave=deteccion.clave,
                    nombre_catalogo=definicion.get("nombre", deteccion.clave),
                    descripcion=definicion.get("descripcion", "Anomalía de comportamiento"),
                    confianza=deteccion.confianza,
                    track_id=track_id,
                    clase_detectada=deteccion.clase,
                    evidencia_directa=True,
                    es_anomalia=True,
                ))

        return infracciones

    # -------------------------------------------------------------------------
    # FILTRADO POR ENFRIAMIENTO
    # -------------------------------------------------------------------------

    def _debe_emitir(self, track_id: int, infraccion: Infraccion, ahora: float) -> bool:
        estado = self._estado_tracks.setdefault(track_id, {"ultimo": {}, "frames_limpios": 0})
        ultima = estado["ultimo"].get((infraccion.clave, infraccion.tipo))
        if ultima is not None and (ahora - ultima) < self.enfriamiento:
            return False
        estado["ultimo"][(infraccion.clave, infraccion.tipo)] = ahora
        return True

    def _registrar_estabilidad(self, track_id: int, limpio: bool) -> None:
        estado = self._estado_tracks.setdefault(track_id, {"ultimo": {}, "frames_limpios": 0})
        estado["frames_limpios"] = estado["frames_limpios"] + 1 if limpio else 0

    # -------------------------------------------------------------------------
    # CUADRO DE CUMPLIMIENTO (estado por persona para la vista de cámara)
    # -------------------------------------------------------------------------

    @staticmethod
    def _estado_cuadro(infracciones: List[Infraccion]) -> str:
        """Reduce las infracciones de una persona a un único color."""
        if not infracciones:
            return CUADRO_CONFORME
        gravedad = max((i.gravedad for i in infracciones),
                       key=lambda g: {GRAVEDAD_GRAVE: 2, GRAVEDAD_INTERMEDIA: 1}.get(g, 0))
        return CUADRO_CRITICO if gravedad == GRAVEDAD_GRAVE else CUADRO_INTERMEDIO

    def construir_cuadro(self, analisis: FrameAnalisis, rol_id: int = 1) -> Dict[str, Any]:
        """Arma el payload del cuadro en vivo: una fila por persona detectada.

        Es estado de pantalla, no historial: el backend lo guarda unos segundos
        y lo reemite por SSE. Por eso no pasa por el enfriamiento de alertas.
        """
        obligatorios, nombre_rol = self.epp_obligatorio(rol_id)

        filas: List[Dict[str, Any]] = []
        for persona in analisis.personas:
            propias = [i for i in analisis.estado_actual if i.track_id == persona.track_id]
            estado = self._estado_cuadro(propias)

            # Lo más grave primero: el supervisor debe ver el casco en la
            # cima de la lista, no el chaleco.
            orden = {GRAVEDAD_GRAVE: 0, GRAVEDAD_INTERMEDIA: 1, GRAVEDAD_LEVE: 2}
            faltantes = [
                {
                    "clave": i.clave,
                    "nombre": i.nombre_catalogo,
                    "tipo": i.tipo,
                    "gravedad": i.gravedad,
                    "confianza": round(i.confianza, 3),
                    "descripcion": i.descripcion,
                }
                for i in sorted(propias, key=lambda x: orden.get(x.gravedad, 3))
            ]

            usados = sorted({d.clave for d in persona.elementos
                             if d.clave and d.estado == ESTADO_CONFORME})

            fila: Dict[str, Any] = {
                "trackId": persona.track_id,
                "estado": estado,
                "confianza": round(persona.confianza, 3),
                "lado": persona.lado,
                "obligatorios": obligatorios,
                "conformes": usados,
                "faltantes": faltantes,
            }
            if self.cuadro_incluir_cajas:
                c = persona.caja
                fila["caja"] = [round(c.x1), round(c.y1), round(c.x2), round(c.y2)]
            filas.append(fila)

        return {
            "rolId": rol_id,
            "nombreRol": nombre_rol,
            "fotograma": analisis.fotograma,
            "fps": round(analisis.fps, 2),
            "dimensiones": list(self._ultimas_dimensiones) if self._ultimas_dimensiones else None,
            "personas": filas,
        }

    # -------------------------------------------------------------------------
    # ANOTACIÓN DE LA EVIDENCIA
    # -------------------------------------------------------------------------

    COLORES = {
        ESTADO_CONFORME: (34, 197, 94),     # verde
        ESTADO_AUSENTE: (239, 68, 68),       # rojo
        ESTADO_IMPOSTOR: (249, 115, 22),     # naranja
        "ANOMALIA": (168, 85, 247),          # morado
        "OTRO": (148, 163, 184),             # gris
    }

    @staticmethod
    def _texto(cv2, imagen, texto, origen, color, escala=0.45):
        (ancho_texto, alto_texto), _ = cv2.getTextSize(texto, cv2.FONT_HERSHEY_SIMPLEX, escala, 1)
        x = int(max(0, min(origen[0], imagen.shape[1] - ancho_texto - 4)))
        y = int(max(alto_texto + 4, min(origen[1], imagen.shape[0] - 4)))
        cv2.rectangle(imagen, (x, y - alto_texto - 4), (x + ancho_texto + 4, y + 2), (18, 22, 30), -1)
        cv2.putText(imagen, texto, (x + 2, y - 2), cv2.FONT_HERSHEY_SIMPLEX, escala, color, 1, cv2.LINE_AA)

    def _anotar(self, frame: np.ndarray, analisis: FrameAnalisis, linea: Optional[float]) -> np.ndarray:
        import cv2

        imagen = frame.copy()
        alto, ancho = imagen.shape[:2]
        ayu = max(2, int(min(ancho, alto) / 420))

        if linea is not None:
            y = int(linea * alto)
            cv2.line(imagen, (0, y), (ancho, y), (246, 166, 35), max(1, ayu // 2))
            self._texto(cv2, imagen, "LINEA DE CONTROL", (8, y - 8), (246, 166, 35), 0.45)

        for elemento in analisis.elementos:
            color = self.COLORES.get(elemento.estado, self.COLORES["OTRO"])
            x1, y1, x2, y2 = (int(v) for v in elemento.caja.como_lista())
            cv2.rectangle(imagen, (x1, y1), (x2, y2), color, ayu)
            sufijo = " [SIN ASIGNAR]" if elemento.track_id == TRACK_SIN_ASIGNAR else ""
            etiqueta = f"{elemento.clase} {elemento.confianza:.2f}{sufijo}"
            self._texto(cv2, imagen, etiqueta, (x1, y1 - 4), color, 0.42)

        for persona in analisis.personas:
            propias = [i for i in analisis.estado_actual if i.track_id == persona.track_id]
            estado = self._estado_cuadro(propias)
            color = COLOR_CUADRO[estado]
            x1, y1, x2, y2 = (int(v) for v in persona.caja.como_lista())
            cv2.rectangle(imagen, (x1, y1), (x2, y2), color, ayu + 1)
            cv2.circle(imagen, (int(persona.caja.pie[0]), int(persona.caja.pie[1])), ayu + 3, color, -1)
            etiqueta = f"#{persona.track_id} {estado}"
            if propias:
                etiqueta += f" - {len(propias)} falta(s)"
            self._texto(cv2, imagen, etiqueta, (x1, y1 - 4), color, 0.5)

        # Panel de resumen
        nuevas = [i for i in analisis.infracciones if not i.es_anomalia]
        resumen = f"PERSONAS: {len(analisis.personas)}   INFRACCIONES NUEVAS: {len(nuevas)}"
        cv2.rectangle(imagen, (0, 0), (ancho, 26), (18, 22, 30), -1)
        cv2.putText(imagen, resumen, (8, 18), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (235, 238, 245), 1, cv2.LINE_AA)

        return imagen

    # -------------------------------------------------------------------------
    # API PÚBLICA
    # -------------------------------------------------------------------------

    def analizar(self, frame: np.ndarray, rol_id: int = 1, linea: Optional[float] = None) -> FrameAnalisis:
        """Analiza un frame y devuelve el estado de cumplimiento de todas las personas."""
        inicio = time.time()
        self._contador_fotogramas += 1
        ahora = time.time()

        detecciones_persona, elementos = self._inferir(frame)

        personas: List[Persona] = []
        for deteccion in detecciones_persona:
            if deteccion.track_id is None:
                continue
            personas.append(Persona(track_id=deteccion.track_id, caja=deteccion.caja, confianza=deteccion.confianza))

        sueltos = self._asociar(personas, elementos)

        obligatorios, _ = self.epp_obligatorio(rol_id)

        nuevas: List[Infraccion] = []
        vigentes: List[Infraccion] = []
        for persona in personas:
            candidatas = self._evaluar_hallazgos(persona.elementos, persona.track_id, obligatorios)
            # Tras evaluar, la persona conserva sólo su EPP conforme: es lo que
            # determina el color de la caja en la evidencia (rojo si le falta algo).
            persona.elementos = [d for d in persona.elementos if d.estado == ESTADO_CONFORME]
            vigentes.extend(candidatas)
            nuevas.extend(i for i in candidatas if self._debe_emitir(persona.track_id, i, ahora))
            self._registrar_estabilidad(persona.track_id, limpio=len(candidatas) == 0)

        if sueltos:
            candidatas = self._evaluar_hallazgos(sueltos, TRACK_SIN_ASIGNAR, obligatorios)
            vigentes.extend(candidatas)
            nuevas.extend(i for i in candidatas if self._debe_emitir(TRACK_SIN_ASIGNAR, i, ahora))

        # Limpieza de tracks desaparecidos (libera el enfriamiento).
        # TRACK_SIN_ASIGNAR es persistente: su enfriamiento se agota por tiempo.
        vivos = {p.track_id for p in personas}
        for track_id in list(self._estado_tracks):
            if track_id not in vivos and track_id != TRACK_SIN_ASIGNAR:
                del self._estado_tracks[track_id]

        analisis = FrameAnalisis(
            personas=personas,
            elementos=elementos,
            infracciones=nuevas,
            estado_actual=vigentes,
            fotograma=self._contador_fotogramas,
        )
        self._ultimas_dimensiones = (int(frame.shape[1]), int(frame.shape[0]))
        analisis.imagen_anotada = self._anotar(frame, analisis, linea)
        analisis.fps = 1.0 / max(1e-6, time.time() - inicio)
        return analisis

    def cerrar(self) -> None:
        for _, modelo in self._modelos:
            try:
                predictor = getattr(modelo, "predictor", None)
                if predictor is not None:
                    predictor.stream = False
            except Exception:  # pragma: no cover - limpieza best-effort
                pass
