// Motor de visión en el navegador: port de python_client/epp_detector.py (DetectorEPP)
// y de PipelineEPP (yolo_sentinel_client.py). Usa la cámara de quien abre la página,
// corre YOLOv8 con onnxruntime-web y publica a la misma API que el cliente Python.
// Requiere los globales `ort` (onnxruntime-web) y `jsyaml` cargados antes.

const BASE = '/js/vision/';
const ORT_DIST = 'https://cdn.jsdelivr.net/npm/onnxruntime-web@1.20.1/dist/';
// Nombres de clase fijos de los .onnx exportados (ver python_client: model.names).
// Si se reentrena el modelo de EPP hay que reexportarlo y actualizar esta lista.
// Los .onnx del navegador se exportan a 416 px (no a epp_config.imgsz=640): ~4x menos cómputo que 640.
const IMGSZ = 416;
const MODELO_PERSONAS = { archivo: 'yolov8n.onnx', clasePersona: 0 };
const MODELO_EPP = { archivo: 'best_sentinel_v2.onnx', nombres: ['gloves', 'goggles', 'helmet', 'vest'] };

const ESTADO_CONFORME = 'CONFORME', ESTADO_AUSENTE = 'AUSENTE', ESTADO_IMPOSTOR = 'IMPOSTOR';
const GRAVE = 'GRAVE', INTERMEDIO = 'INTERMEDIO';
const TRACK_SIN_ASIGNAR = -1;
const COLOR_CUADRO = { CONFORME: '#00c800', INTERMEDIO: '#ffa500', CRITICO: '#eb0000' };
const COLOR_ELEMENTO = { CONFORME: '#22c55e', AUSENTE: '#ef4444', IMPOSTOR: '#f97316' };
const ROL_ID = 1;          // igual que --rol por defecto del cliente Python
const LINEA = 0.5;         // igual que --linea por defecto
const DIBUJO_MS = 30;      // ~33 fps de dibujado local
const STREAM_MS = 50;      // hasta ~20 fps de video publicado
const STREAM_EN_VUELO = 6; // POSTs simultáneos: cada uno tarda ~0.5 s ida y vuelta a Railway; con 1 topaba en ~2-8 fps

const normalizar = c => String(c).trim().toLowerCase().replace(/[\s-]+/g, '_').replace(/^_+|_+$/g, '');

// ---------- Geometría ----------
const ancho = c => Math.max(0, c.x2 - c.x1);
const alto = c => Math.max(0, c.y2 - c.y1);
const centro = c => [(c.x1 + c.x2) / 2, (c.y1 + c.y2) / 2];
const expandida = (c, fx, fy) => {
    const ex = ancho(c) * fx, ey = alto(c) * fy;
    return { x1: c.x1 - ex, y1: c.y1 - ey, x2: c.x2 + ex, y2: c.y2 + ey };
};
const contiene = (c, x, y) => c.x1 <= x && x <= c.x2 && c.y1 <= y && y <= c.y2;

export function iou(a, b) {
    const x1 = Math.max(a.x1, b.x1), y1 = Math.max(a.y1, b.y1);
    const x2 = Math.min(a.x2, b.x2), y2 = Math.min(a.y2, b.y2);
    if (x2 <= x1 || y2 <= y1) return 0;
    const inter = (x2 - x1) * (y2 - y1);
    const union = ancho(a) * alto(a) + ancho(b) * alto(b) - inter;
    return union > 0 ? inter / union : 0;
}

function distanciaFuera(x, y, c) {
    const dx = Math.max(c.x1 - x, 0, x - c.x2);
    const dy = Math.max(c.y1 - y, 0, y - c.y2);
    return Math.hypot(dx, dy);
}

// ---------- YOLOv8 (salida [1, 4+nc, N]) ----------
export function decodificar(tensor, conf, iouUmbral, lb, soloClase = null) {
    const d = tensor.data, nc = tensor.dims[1] - 4, n = tensor.dims[2];
    const cajas = [];
    for (let i = 0; i < n; i++) {
        let clase = -1, score = conf;
        const desde = soloClase ?? 0, hasta = soloClase ?? nc - 1;
        for (let c = desde; c <= hasta; c++) {
            const v = d[(4 + c) * n + i];
            if (v > score) { score = v; clase = c; }
        }
        if (clase < 0) continue;
        const cx = d[i], cy = d[n + i], w = d[2 * n + i], h = d[3 * n + i];
        cajas.push({
            clase, conf: score,
            caja: {
                x1: (cx - w / 2 - lb.dx) / lb.k, y1: (cy - h / 2 - lb.dy) / lb.k,
                x2: (cx + w / 2 - lb.dx) / lb.k, y2: (cy + h / 2 - lb.dy) / lb.k
            }
        });
    }
    cajas.sort((a, b) => b.conf - a.conf);
    const salida = [];
    for (const c of cajas) {
        if (!salida.some(s => s.clase === c.clase && iou(s.caja, c.caja) > iouUmbral)) salida.push(c);
    }
    return salida;
}

// shortcut: tracker IoU voraz en lugar de BoT-SORT+ReID; pierde el id si la persona se cruza con otra o sale y vuelve. Portar ByteTrack si hace falta.
export class TrackerIoU {
    constructor(umbralIou = 0.3, maxEdadMs = 1500, confNuevo = 0.4) {
        Object.assign(this, { umbralIou, maxEdadMs, confNuevo, tracks: [], siguiente: 1 });
    }
    actualizar(detecciones, ahora = performance.now()) {
        const pares = [];
        this.tracks.forEach((t, ti) => detecciones.forEach((d, di) => {
            const v = iou(t.caja, d.caja);
            if (v >= this.umbralIou) pares.push([v, ti, di]);
        }));
        pares.sort((a, b) => b[0] - a[0]);
        const usadosT = new Set(), usadosD = new Set(), activos = [];
        for (const [, ti, di] of pares) {
            if (usadosT.has(ti) || usadosD.has(di)) continue;
            usadosT.add(ti); usadosD.add(di);
            Object.assign(this.tracks[ti], { caja: detecciones[di].caja, visto: ahora });
            activos.push({ trackId: this.tracks[ti].id, ...detecciones[di] });
        }
        detecciones.forEach((d, di) => {
            if (usadosD.has(di) || d.conf < this.confNuevo) return;
            const t = { id: this.siguiente++, caja: d.caja, visto: ahora };
            this.tracks.push(t);
            activos.push({ trackId: t.id, ...d });
        });
        this.tracks = this.tracks.filter(t => ahora - t.visto <= this.maxEdadMs);
        return activos;
    }
}

// ---------- Reglas de EPP (DetectorEPP) ----------
export class ReglasEPP {
    constructor(cfg) {
        this.cfg = cfg;
        const m = cfg.modelo || {}, a = cfg.asignacion || {}, e = cfg.emision || {}, q = cfg.cuadro || {};
        this.imgsz = Number(m.imgsz ?? 640);
        this.confianza = Number(m.confianza ?? 0.35);
        this.iouUmbral = Number(m.iou ?? 0.45);
        this.margenX = Number(a.margen_x ?? 0.15);
        this.margenY = Number(a.margen_y ?? 0.10);
        this.confianzaSuelto = Number(a.confianza_suelto ?? 0.60);
        this.catalogo = cfg.catalogo_epp || {};
        this.roles = cfg.roles || {};
        this.impostores = cfg.impostores || {};
        this.confianzaMinimaAlerta = Number(e.confianza_minima_alerta ?? 0.45);
        this.segundosEvaluacion = Number(e.segundos_evaluacion ?? 5.0);
        this.framesAbrir = Math.max(1, Number(e.frames_abrir ?? 3));
        this.framesCerrar = Math.max(1, Number(e.frames_cerrar ?? 8));
        this.cuadroIntervalo = Math.max(1, Number(q.intervalo_fotogramas ?? 10));
        this.cuadroIncluirCajas = q.incluir_cajas ?? true;
        this.anchoMaximo = Number(q.ancho_maximo ?? 960);

        this.gravedad = {};
        for (const [k, v] of Object.entries(this.catalogo)) this.gravedad[k] = String(v.gravedad || INTERMEDIO).toUpperCase();
        for (const k of Object.keys(this.impostores)) this.gravedad[k] = GRAVE;

        this.indice = {};
        for (const [clave, mapeo] of Object.entries(cfg.mapeo_clases || {})) {
            (mapeo.conforme || []).forEach(c => { this.indice[normalizar(c)] = [clave, ESTADO_CONFORME]; });
            (mapeo.ausencia || []).forEach(c => { this.indice[normalizar(c)] = [clave, ESTADO_AUSENTE]; });
        }
        for (const [clave, def] of Object.entries(this.impostores)) {
            (def.clases || []).forEach(c => { this.indice[normalizar(c)] = [clave, ESTADO_IMPOSTOR]; });
        }
    }

    clasificar(nombreClase) {
        const clase = normalizar(nombreClase);
        const [clave, estado] = this.indice[clase] || [null, 'OTRO'];
        return { clase, clave, estado };
    }

    eppObligatorio(rolId) {
        const regla = this.roles[rolId] || this.roles[String(rolId)] || this.roles['*'] || {};
        const claves = (regla.obligatorio || []).filter(c => (this.catalogo[c] || {}).verificable);
        return [claves, regla.nombre || `Rol ${rolId}`];
    }

    asociar(personas, elementos) {
        const sueltos = [];
        for (const el of elementos) {
            const [cx, cy] = centro(el.caja);
            let mejor = null, mejorScore = 0;
            for (const p of personas) {
                let puntaje = iou(el.caja, p.caja);
                if (puntaje === 0) {
                    const margen = expandida(p.caja, this.margenX, this.margenY);
                    const escala = Math.max(ancho(p.caja), alto(p.caja), 1);
                    puntaje = Math.max(0, 1 - distanciaFuera(cx, cy, margen) / (escala * 0.5));
                    if (!contiene(margen, cx, cy) && puntaje < 0.30) puntaje = 0;
                }
                if (puntaje > mejorScore) { mejor = p; mejorScore = puntaje; }
            }
            if (mejor) {
                el.trackId = mejor.trackId;
                mejor.elementos.push(el);
            } else if (el.confianza >= this.confianzaSuelto) {
                el.trackId = TRACK_SIN_ASIGNAR;
                sueltos.push(el);
            }
        }
        return sueltos;
    }

    evaluarHallazgos(elementos, trackId, obligatorios) {
        const infracciones = [];
        const detectados = new Set(elementos.filter(d => d.clave && d.estado === ESTADO_CONFORME).map(d => d.clave));
        for (const clave of obligatorios) {
            const meta = this.catalogo[clave] || {};
            if (!meta.verificable || detectados.has(clave)) continue;
            const nombre = meta.nombre || clave;
            const ausencias = elementos.filter(d => d.clave === clave && d.estado === ESTADO_AUSENTE);
            if (ausencias.length) {
                const mejor = ausencias.reduce((a, b) => (b.confianza > a.confianza ? b : a));
                infracciones.push({
                    tipo: 'FALTANTE', clave, nombreCatalogo: nombre,
                    descripcion: `Sin ${nombre} (detectado como ${mejor.clase})`,
                    confianza: mejor.confianza, trackId, gravedad: this.gravedad[clave] || INTERMEDIO
                });
            } else if (trackId !== TRACK_SIN_ASIGNAR) {
                infracciones.push({
                    tipo: 'FALTANTE', clave, nombreCatalogo: nombre,
                    descripcion: `Sin ${nombre} detectado en la persona`,
                    confianza: this.confianzaMinimaAlerta, trackId, gravedad: this.gravedad[clave] || INTERMEDIO
                });
            }
        }
        for (const d of elementos) {
            if (d.estado !== ESTADO_IMPOSTOR) continue;
            const def = this.impostores[d.clave] || {};
            infracciones.push({
                tipo: 'IMPOSTOR', clave: d.clave, nombreCatalogo: def.nombre || d.clave,
                descripcion: def.descripcion || `Uso de ${d.clase} no apto para seguridad`,
                confianza: d.confianza, trackId, gravedad: this.gravedad[d.clave] || GRAVE
            });
        }
        return infracciones;
    }

    // personasTrack: [{trackId, caja, conf}], elementos: [{clase, clave, estado, caja, confianza}]
    analizar(personasTrack, elementos, rolId = ROL_ID) {
        const personas = personasTrack.map(p => ({ trackId: p.trackId, caja: p.caja, confianza: p.conf, elementos: [] }));
        const sueltos = this.asociar(personas, elementos);
        const [obligatorios] = this.eppObligatorio(rolId);
        const vigentes = [];
        for (const p of personas) {
            vigentes.push(...this.evaluarHallazgos(p.elementos, p.trackId, obligatorios));
            p.elementos = p.elementos.filter(d => d.estado === ESTADO_CONFORME);
        }
        if (sueltos.length) vigentes.push(...this.evaluarHallazgos(sueltos, TRACK_SIN_ASIGNAR, obligatorios));
        return { personas, elementos, estadoActual: vigentes };
    }

    static estadoCuadro(infracciones) {
        if (!infracciones.length) return 'CONFORME';
        return infracciones.some(i => i.gravedad === GRAVE) ? 'CRITICO' : 'INTERMEDIO';
    }

    construirCuadro(analisis, fotograma, fps, dimensiones, rolId = ROL_ID) {
        const [obligatorios, nombreRol] = this.eppObligatorio(rolId);
        const orden = { GRAVE: 0, INTERMEDIO: 1, LEVE: 2 };
        const personas = analisis.personas.map(p => {
            const propias = analisis.estadoActual.filter(i => i.trackId === p.trackId);
            const fila = {
                trackId: p.trackId,
                estado: ReglasEPP.estadoCuadro(propias),
                confianza: +p.confianza.toFixed(3),
                lado: p.lado ?? null,
                obligatorios,
                conformes: [...new Set(p.elementos.map(d => d.clave))].sort(),
                faltantes: propias.slice().sort((a, b) => (orden[a.gravedad] ?? 3) - (orden[b.gravedad] ?? 3)).map(i => ({
                    clave: i.clave, nombre: i.nombreCatalogo, tipo: i.tipo, gravedad: i.gravedad,
                    confianza: +i.confianza.toFixed(3), descripcion: i.descripcion
                }))
            };
            if (this.cuadroIncluirCajas) fila.caja = [p.caja.x1, p.caja.y1, p.caja.x2, p.caja.y2].map(Math.round);
            return fila;
        });
        return { rolId, nombreRol, fotograma, fps: +fps.toFixed(2), dimensiones, personas };
    }
}

// ---------- Ciclo de vida de episodios (PipelineEPP._sincronizar_episodios) ----------
// api: { abrir(infraccion, confianza) -> Promise<faltaId|null>, cerrar(faltaId, trackId) }
export class Episodios {
    constructor(reglas, api) {
        Object.assign(this, { reglas, api });
        this.abiertas = new Map();      // key -> Promise<faltaId|null>
        this.cuentaAbrir = new Map();
        this.cuentaCerrar = new Map();
        this.primerAvistamiento = new Map();
        this.confianzas = new Map();
    }

    sincronizar(analisis, ahora = Date.now() / 1000) {
        const incumpliendo = new Map();
        for (const i of analisis.estadoActual) {
            if (i.trackId == null || i.trackId === TRACK_SIN_ASIGNAR) continue;
            incumpliendo.set(`${i.trackId}|${i.clave}|${i.tipo}`, i);
        }
        const visibles = new Set(analisis.personas.map(p => p.trackId));

        for (const [key, apertura] of [...this.abiertas]) {
            const trackId = Number(key.split('|')[0]);
            if (incumpliendo.has(key) || !visibles.has(trackId)) {
                this.cuentaCerrar.delete(key);
                continue;
            }
            const cierre = (this.cuentaCerrar.get(key) || 0) + 1;
            if (cierre >= this.reglas.framesCerrar) {
                apertura.then(faltaId => faltaId && this.api.cerrar(faltaId, trackId));
                this.abiertas.delete(key);
                this.cuentaCerrar.delete(key);
            } else {
                this.cuentaCerrar.set(key, cierre);
            }
        }

        for (const tid of visibles) if (!this.primerAvistamiento.has(tid)) this.primerAvistamiento.set(tid, ahora);
        for (const [key, i] of incumpliendo) {
            if (!this.confianzas.has(key)) this.confianzas.set(key, []);
            this.confianzas.get(key).push(i.confianza);
        }

        for (const [key, infraccion] of incumpliendo) {
            if (this.abiertas.has(key)) continue;
            const visible = ahora - (this.primerAvistamiento.get(infraccion.trackId) ?? ahora);
            if (visible < this.reglas.segundosEvaluacion) continue;
            const cuenta = (this.cuentaAbrir.get(key) || 0) + 1;
            if (cuenta < this.reglas.framesAbrir) { this.cuentaAbrir.set(key, cuenta); continue; }
            this.cuentaAbrir.delete(key);
            const lista = this.confianzas.get(key) || [infraccion.confianza];
            const promedio = Math.round(lista.reduce((a, b) => a + b, 0) / lista.length * 1000) / 1000;
            const apertura = Promise.resolve(this.api.abrir(infraccion, promedio)).catch(() => null);
            this.abiertas.set(key, apertura);
            apertura.then(faltaId => { if (!faltaId && this.abiertas.get(key) === apertura) this.abiertas.delete(key); });
        }

        for (const key of [...this.cuentaAbrir.keys()]) {
            if (!incumpliendo.has(key) && !this.abiertas.has(key)) { this.cuentaAbrir.delete(key); this.confianzas.delete(key); }
        }
        for (const key of [...this.cuentaCerrar.keys()]) {
            if (incumpliendo.has(key) || !this.abiertas.has(key)) this.cuentaCerrar.delete(key);
        }
        for (const tid of [...this.primerAvistamiento.keys()]) if (!visibles.has(tid)) this.primerAvistamiento.delete(tid);
    }
}

// ---------- Cruces de la línea de control (aforo) ----------
export class Cruces {
    constructor(linea = LINEA) { this.linea = linea; this.lado = new Map(); }
    detectar(personas, altoFrame) {
        const movimientos = [];
        for (const p of personas) {
            const lado = p.caja.y2 / Math.max(1, altoFrame) >= this.linea ? 'ABAJO' : 'ARRIBA';
            const anterior = this.lado.get(p.trackId);
            this.lado.set(p.trackId, lado);
            p.lado = lado;
            if (anterior && anterior !== lado) {
                movimientos.push({ tipo: anterior === 'ARRIBA' ? 'ENTRADA' : 'SALIDA', trackId: p.trackId });
            }
        }
        const vivos = new Set(personas.map(p => p.trackId));
        for (const tid of [...this.lado.keys()]) if (!vivos.has(tid)) this.lado.delete(tid);
        return movimientos;
    }
}

// ---------- API del backend (misma que MineSentinelClient) ----------
async function postJson(url, cuerpo) {
    const res = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(cuerpo) });
    if (!res.ok) throw new Error(`${url} -> ${res.status}`);
    return res.status === 204 ? null : res.json().catch(() => null);
}

const aJpeg = (lienzo, calidad = 0.75) => new Promise(r => lienzo.toBlob(r, 'image/jpeg', calidad));

// ---------- Motor ----------
export class MotorNavegador {
    constructor({ video, lienzo, onEstado = () => { } }) {
        Object.assign(this, { video, lienzo, onEstado });
        this.activo = false;
    }

    async cargar() {
        this.onEstado('Cargando configuración y modelos (≈25 MB, solo la primera vez)…');
        const cfg = jsyaml.load(await (await fetch(BASE + 'epp_config.yaml')).text());
        this.reglas = new ReglasEPP(cfg);
        ort.env.wasm.wasmPaths = ORT_DIST;
        // navigator.gpu puede existir sin adaptador (p. ej. Opera GX en Linux); sin GPU, WASM va en un
        // Web Worker (proxy) para no congelar la página. El proxy no admite WebGPU.
        const adaptador = navigator.gpu ? await navigator.gpu.requestAdapter().catch(() => null) : null;
        ort.env.wasm.proxy = !adaptador;
        // Hilos WASM (solo con crossOriginIsolated, ver CamaraController); ort por defecto se queda en 4.
        ort.env.wasm.numThreads = Math.min(8, Math.ceil((navigator.hardwareConcurrency || 4) / 2));
        const proveedores = adaptador ? ['webgpu', 'wasm'] : ['wasm'];
        const crear = archivo => ort.InferenceSession.create(BASE + archivo, { executionProviders: proveedores });
        [this.sesionPersonas, this.sesionEpp] = await Promise.all([crear(MODELO_PERSONAS.archivo), crear(MODELO_EPP.archivo)]);

        this.catalogo = {};
        try {
            const items = await (await fetch('/api/v1/catalogos/epp')).json();
            for (const it of items) if (it.nombre) this.catalogo[String(it.nombre).trim().toLowerCase()] = it.eppId;
        } catch (e) {
            console.warn('[vision] catálogo de EPP no disponible', e);
        }
    }

    async iniciar() {
        if (!this.reglas) await this.cargar();
        this.onEstado('Pidiendo permiso para usar la cámara…');
        this.flujo = await navigator.mediaDevices.getUserMedia({
            video: { width: { ideal: 1280 }, height: { ideal: 720 } }, audio: false
        });
        this.video.srcObject = this.flujo;
        await this.video.play();

        this.tracker = new TrackerIoU();
        this.cruces = new Cruces();
        this.episodios = new Episodios(this.reglas, {
            abrir: (i, conf) => this.abrirFalta(i, conf),
            cerrar: (faltaId, trackId) => postJson('/api/v1/seguridad/infracciones/resolucion',
                { faltaId, trackId, motivo: 'EPP repuesto por el trabajador' }).catch(e => console.warn('[vision]', e))
        });
        this.analisis = null;
        this.fotograma = 0;
        this.fps = 0;
        this.framesEnVuelo = 0;
        this.ultimoDibujo = 0;
        this.ultimoEnvio = 0;
        this.personas = [];
        this.elementos = [];
        this.turnoEpp = false;
        this.activo = true;

        this.entrada = document.createElement('canvas');
        this.entrada.width = this.entrada.height = IMGSZ;
        this.salida = document.createElement('canvas');

        this.bucleVideo();
        this.bucleIA();
        this.onEstado('Transmitiendo desde esta cámara · YOLOv8 en el navegador');
    }

    detener() {
        this.activo = false;
        this.flujo?.getTracks().forEach(t => t.stop());
        this.video.srcObject = null;
        this.onEstado('Transmisión detenida');
    }

    // Letterbox gris como Ultralytics; devuelve el tensor y la transformación inversa.
    preprocesar() {
        const s = IMGSZ, w = this.video.videoWidth, h = this.video.videoHeight;
        const k = Math.min(s / w, s / h), nw = Math.round(w * k), nh = Math.round(h * k);
        const dx = Math.floor((s - nw) / 2), dy = Math.floor((s - nh) / 2);
        const ctx = this.entrada.getContext('2d', { willReadFrequently: true });
        ctx.fillStyle = 'rgb(114,114,114)';
        ctx.fillRect(0, 0, s, s);
        ctx.drawImage(this.video, dx, dy, nw, nh);
        const px = ctx.getImageData(0, 0, s, s).data, area = s * s;
        const datos = new Float32Array(3 * area);
        for (let i = 0; i < area; i++) {
            datos[i] = px[i * 4] / 255;
            datos[area + i] = px[i * 4 + 1] / 255;
            datos[2 * area + i] = px[i * 4 + 2] / 255;
        }
        return { tensor: new ort.Tensor('float32', datos, [1, 3, s, s]), lb: { k, dx, dy } };
    }

    // Un modelo por ciclo, alternando personas / EPP: cada análisis cuesta la mitad y combina la
    // salida fresca de uno con la última del otro (unos 45 ms de diferencia, despreciable).
    async inferir() {
        const { tensor, lb } = this.preprocesar();
        const r = this.reglas;
        this.turnoEpp = !this.turnoEpp;
        if (this.turnoEpp) {
            const salida = await this.sesionEpp.run({ [this.sesionEpp.inputNames[0]]: tensor });
            this.elementos = decodificar(salida[this.sesionEpp.outputNames[0]], r.confianza, r.iouUmbral, lb)
                .map(d => ({ ...r.clasificar(MODELO_EPP.nombres[d.clase] ?? d.clase), caja: d.caja, confianza: d.conf }))
                .filter(d => d.estado !== 'OTRO');
        } else {
            const salida = await this.sesionPersonas.run({ [this.sesionPersonas.inputNames[0]]: tensor });
            this.personas = this.tracker.actualizar(
                decodificar(salida[this.sesionPersonas.outputNames[0]], 0.30, r.iouUmbral, lb, MODELO_PERSONAS.clasePersona));
        }
        return { personas: this.personas, elementos: this.elementos };
    }

    async bucleIA() {
        while (this.activo) {
            try {
                const inicio = performance.now();
                const { personas, elementos } = await this.inferir();
                if (!this.activo) break;
                const analisis = this.reglas.analizar(personas, elementos);
                this.fotograma++;
                this.fps = 1000 / Math.max(1, performance.now() - inicio);
                this.analisis = analisis;

                for (const m of this.cruces.detectar(analisis.personas, this.video.videoHeight)) {
                    postJson('/api/v1/aforo/movimiento', { tipoMovimiento: m.tipo, trackId: m.trackId, rolId: ROL_ID })
                        .catch(e => console.warn('[vision]', e));
                }
                this.episodios.sincronizar(analisis);
                if (this.fotograma % this.reglas.cuadroIntervalo === 0) {
                    const cuadro = this.reglas.construirCuadro(analisis, this.fotograma, this.fps,
                        [this.video.videoWidth, this.video.videoHeight]);
                    postJson('/api/v1/vision/cuadro', cuadro).catch(e => console.warn('[vision]', e));
                }
            } catch (e) {
                console.error('[vision] fallo en inferencia', e);
                await new Promise(r => setTimeout(r, 1000));
            }
        }
    }

    bucleVideo() {
        if (!this.activo) return;
        const w = this.video.videoWidth, h = this.video.videoHeight, ahora = performance.now();
        // La cámara da ~30 fps: en pantallas de 120-144 Hz redibujar en cada rAF solo le quita CPU a la IA.
        if (w && h && ahora - this.ultimoDibujo >= DIBUJO_MS) {
            this.ultimoDibujo = ahora;
            if (this.lienzo.width !== w || this.lienzo.height !== h) { this.lienzo.width = w; this.lienzo.height = h; }
            const ctx = this.lienzo.getContext('2d');
            ctx.drawImage(this.video, 0, 0, w, h);
            this.anotar(ctx, w, h);
        }
        if (w && h) this.publicarFrame(w, h); // ritmo propio (STREAM_MS), no atado al dibujado
        requestAnimationFrame(() => this.bucleVideo());
    }

    anotar(ctx, w, h) {
        const ayu = Math.max(2, Math.floor(Math.min(w, h) / 420));
        const texto = (t, x, y, color, px) => {
            ctx.font = `${px}px sans-serif`;
            const tw = ctx.measureText(t).width;
            x = Math.max(0, Math.min(x, w - tw - 4));
            y = Math.max(px + 4, Math.min(y, h - 4));
            ctx.fillStyle = '#121620';
            ctx.fillRect(x, y - px - 4, tw + 4, px + 6);
            ctx.fillStyle = color;
            ctx.fillText(t, x + 2, y - 2);
        };
        const yLinea = LINEA * h;
        ctx.strokeStyle = '#23a6f6';
        ctx.lineWidth = Math.max(1, ayu / 2);
        ctx.beginPath(); ctx.moveTo(0, yLinea); ctx.lineTo(w, yLinea); ctx.stroke();
        texto('LINEA DE CONTROL', 8, yLinea - 8, '#23a6f6', 14);

        const a = this.analisis;
        if (a) {
            for (const el of a.elementos) {
                const c = COLOR_ELEMENTO[el.estado] || '#94a3b8';
                ctx.strokeStyle = c; ctx.lineWidth = ayu;
                ctx.strokeRect(el.caja.x1, el.caja.y1, ancho(el.caja), alto(el.caja));
                const sufijo = el.trackId === TRACK_SIN_ASIGNAR ? ' [SIN ASIGNAR]' : '';
                texto(`${el.clase} ${el.confianza.toFixed(2)}${sufijo}`, el.caja.x1, el.caja.y1 - 4, c, 13);
            }
            for (const p of a.personas) {
                const propias = a.estadoActual.filter(i => i.trackId === p.trackId);
                const estado = ReglasEPP.estadoCuadro(propias);
                const c = COLOR_CUADRO[estado];
                ctx.strokeStyle = c; ctx.lineWidth = ayu + 1;
                ctx.strokeRect(p.caja.x1, p.caja.y1, ancho(p.caja), alto(p.caja));
                ctx.fillStyle = c;
                ctx.beginPath(); ctx.arc((p.caja.x1 + p.caja.x2) / 2, p.caja.y2, ayu + 3, 0, 2 * Math.PI); ctx.fill();
                texto(`#${p.trackId} ${estado}${propias.length ? ` - ${propias.length} falta(s)` : ''}`, p.caja.x1, p.caja.y1 - 4, c, 15);
            }
        }
        ctx.fillStyle = '#121620';
        ctx.fillRect(0, 0, w, 26);
        ctx.fillStyle = '#ebeef5';
        ctx.font = '16px sans-serif';
        ctx.fillText(`PERSONAS: ${a ? a.personas.length : 0}   IA: ${this.fps.toFixed(1)} fps`, 8, 19);
    }

    // Un POST por frame a /vision/stream (el servidor lee hasta EOF), como el cliente Python.
    async publicarFrame(w, h) {
        const ahora = performance.now();
        // shortcut: con varios POSTs en vuelo un frame viejo puede llegar después de uno nuevo (salto mínimo).
        if (this.framesEnVuelo >= STREAM_EN_VUELO || ahora - this.ultimoEnvio < STREAM_MS) return;
        this.framesEnVuelo++;
        this.ultimoEnvio = ahora;
        try {
            const k = Math.min(1, this.reglas.anchoMaximo / w);
            this.salida.width = Math.round(w * k);
            this.salida.height = Math.round(h * k);
            this.salida.getContext('2d').drawImage(this.lienzo, 0, 0, this.salida.width, this.salida.height);
            const jpeg = new Uint8Array(await (await aJpeg(this.salida)).arrayBuffer());
            const cuerpo = new Uint8Array(4 + jpeg.length);
            new DataView(cuerpo.buffer).setUint32(0, jpeg.length);
            cuerpo.set(jpeg, 4);
            await fetch('/api/v1/vision/stream', { method: 'POST', headers: { 'Content-Type': 'application/octet-stream' }, body: cuerpo });
        } catch (e) {
            console.warn('[vision] no se pudo publicar el frame', e);
        } finally {
            this.framesEnVuelo--;
        }
    }

    async abrirFalta(infraccion, confianza) {
        const eppId = this.catalogo[String(infraccion.nombreCatalogo).trim().toLowerCase()];
        if (eppId == null) {
            console.warn('[vision] infracción descartada: no está en el catálogo', infraccion.nombreCatalogo);
            return null;
        }
        let snapshotUrl = '';
        try {
            const datos = new FormData();
            datos.append('file', await aJpeg(this.lienzo, 0.85), `epp_abrir_${this.fotograma}.jpg`);
            const res = await fetch('/api/v1/evidencias/upload', { method: 'POST', body: datos });
            if (res.status === 201) snapshotUrl = (await res.json()).snapshotUrl || '';
        } catch (e) {
            console.warn('[vision] no se pudo subir la evidencia', e);
        }
        const falta = await postJson('/api/v1/seguridad/infracciones', {
            eppId, rolId: ROL_ID, nivelConfianza: Math.round(confianza * 100) / 100, snapshotUrl,
            tipoInfraccion: infraccion.tipo, descripcion: infraccion.descripcion.slice(0, 250), trackId: infraccion.trackId
        });
        return falta?.faltaId ?? null;
    }
}
