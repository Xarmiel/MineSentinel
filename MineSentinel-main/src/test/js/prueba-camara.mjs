/**
 * Banco de pruebas de la vista de cámara (sin navegador).
 * =============================================================================
 * Ejecutar desde la raíz del proyecto Spring Boot:
 *
 *     node src/test/js/prueba-camara.mjs
 *
 * Por qué existe esto: `/camara` no es JavaScript en un archivo, es un bloque
 * inline dentro de una plantilla Thymeleaf, y el proyecto no tiene bundler ni
 * framework de tests. Sin nada que lo compruebe, el mapeo de coordenadas del
 * overlay se rompe en silencio: la vista sigue pareciendo correcta, sólo que
 * las cajas ya no caen sobre las personas.
 *
 * La trampa concreta que cubre: el motor calcula `caja` sobre el frame ORIGINAL
 * y publica un JPEG reescalado a `ancho_maximo`; además el <img> usa
 * `object-fit: cover`, que recorta. Son dos ajustes de escala independientes y
 * sólo se compensan los dos a la vez.
 *
 * Sin dependencias: el DOM simulado es un dozen de líneas.
 */

import { readFileSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const aqui = dirname(fileURLToPath(import.meta.url));
const plantilla = join(aqui, '..', '..', 'main', 'resources', 'templates', 'camara.html');

// ---------------------------------------------------------------------------
// Extracción del módulo: la plantilla no declara exports (no los necesita),
// así que se añaden aquí para poder invocar las funciones desde fuera.
// ---------------------------------------------------------------------------
const html = readFileSync(plantilla, 'utf8');
const coincidencia = html.match(/<script type="module">([\s\S]*?)<\/script>/);
if (!coincidencia) {
    console.error('No se encontró el <script type="module"> en ' + plantilla);
    process.exit(1);
}
const dir = mkdtempSync(join(tmpdir(), 'camara-'));
const modulo = join(dir, 'camara.mjs');
writeFileSync(modulo,
    coincidencia[1] +
    '\nexport { pintarCuadro, pintarOverlay, limpiarOverlay, escalaCajas,' +
    ' ordenarPorGravedad, estadoDe };\n', 'utf8');

const llamadas = [];

function crearContexto() {
    return {
        setTransform() { },
        clearRect() { },
        measureText(t) { return { width: t.length * 6 }; },
        fillRect(x, y, w, h) { llamadas.push({ op: 'fillRect', x, y, w, h }); },
        strokeRect(x, y, w, h) { llamadas.push({ op: 'strokeRect', x, y, w, h }); },
        fillText() { }
    };
}

function crearElemento(id) {
    return {
        id, width: 0, height: 0,
        naturalWidth: 0, naturalHeight: 0,
        style: {}, className: '', textContent: '', innerHTML: '',
        attributes: {}, listeners: {},
        context: crearContexto(),
        getContext() { return this.context; },
        getBoundingClientRect() { return this.rect || { width: 0, height: 0 }; },
        getAttribute(n) { return n in this.attributes ? this.attributes[n] : null; },
        setAttribute(n, v) { this.attributes[n] = v; },
        removeAttribute(n) { delete this.attributes[n]; },
        addEventListener(ev, fn) { (this.listeners[ev] = this.listeners[ev] || []).push(fn); }
    };
}

const elementos = {};
for (const id of ['marcoMotor', 'overlayCamara', 'placeholderCamara', 'textoCamara',
    'estadoDeteccion', 'cuadroCuerpo', 'cuadroEstado', 'avisoMotor']) {
    elementos[id] = crearElemento(id);
}

globalThis.document = {
    getElementById: id => elementos[id] || null,
    // escapar() depende de que textContent -> innerHTML haga el escapado real.
    createElement: () => {
        let t = '';
        return {
            set textContent(v) { t = v == null ? '' : String(v); },
            get textContent() { return t; },
            get innerHTML() {
                return t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
            }
        };
    }
};
globalThis.window = { devicePixelRatio: 1, addEventListener() { } };
// Frame válido: el módulo sólo marca hayFrame cuando recibe una imagen real.
globalThis.fetch = url => String(url).includes('/vision/frame')
    ? Promise.resolve({
        status: 200, ok: true,
        blob: () => Promise.resolve({ size: 8000, type: 'image/jpeg' }),
        headers: { get: () => 'image/jpeg' }
    })
    : Promise.resolve({ ok: false });
globalThis.EventSource = class { addEventListener() { } };
globalThis.URL = { createObjectURL: () => 'blob:x', revokeObjectURL() { } };
globalThis.setInterval = () => 0;

const mod = await import(pathToFileURL(modulo).href);

// Deja que pedirFrame() resuelva antes de medir nada.
await new Promise(r => setTimeout(r, 0));
await new Promise(r => setTimeout(r, 0));

// ---------------------------------------------------------------------------

const fallos = [];

function comprobar(nombre, condicion, detalle) {
    console.log('  ' + (condicion ? 'OK   ' : 'FALLO') + ' - ' + nombre +
        (detalle ? ' | ' + detalle : ''));
    if (!condicion) fallos.push(nombre);
}

function escena({ natural, rect }) {
    elementos.overlayCamara.rect = rect;
    elementos.marcoMotor.rect = rect;
    elementos.marcoMotor.naturalWidth = natural[0];
    elementos.marcoMotor.naturalHeight = natural[1];
    elementos.marcoMotor.listeners.load.forEach(fn => fn());
    llamadas.length = 0;
}

const rects = () => llamadas.filter(c => c.op === 'strokeRect');
const f = n => Number(n).toFixed(1);

function persona(over) {
    return Object.assign({
        trackId: 1, estado: 'CONFORME', confianza: 0.9,
        conformes: [], obligatorios: [], faltantes: [], caja: [0, 0, 10, 10]
    }, over);
}

// =============================================================================
console.log('CASO 1 - el motor reescala el frame a 960 y cover NO recorta');
// Original 1280x720 -> publicado 960x540. Contenedor 800x450 (16:9) exacto.
escena({ natural: [960, 540], rect: { width: 800, height: 450 } });
mod.pintarCuadro({
    dimensiones: [1280, 720], nombreRol: 'Operador',
    personas: [persona({
        trackId: 7, estado: 'CRITICO', confianza: 0.4,
        conformes: ['Casco de Seguridad'], obligatorios: ['CASCO', 'GAFAS', 'GUANTES', 'CHALECO'],
        faltantes: [{ nombre: 'Gafas de Seguridad', gravedad: 'GRAVE', tipo: 'FALTANTE' }],
        caja: [0, 0, 640, 360]
    })]
});
const r1 = rects()[0];
comprobar('la caja izquierda ocupa la mitad del contenedor',
    f(r1.x) === '0.0' && f(r1.y) === '0.0' && f(r1.w) === '400.0' && f(r1.h) === '225.0',
    `x=${f(r1.x)} y=${f(r1.y)} w=${f(r1.w)} h=${f(r1.h)}`);

// =============================================================================
console.log('\nCASO 2 - frame 4:3 en contenedor 16:9: cover RECORTA arriba y abajo');
// cover escala 800/640 = 1.25 -> 800x600, recorta 75px por arriba y por abajo.
escena({ natural: [640, 480], rect: { width: 800, height: 450 } });
mod.pintarCuadro({
    dimensiones: [640, 480], nombreRol: 'Operador',
    personas: [
        persona({
            trackId: 1, estado: 'CONFORME',
            conformes: ['Casco', 'Gafas', 'Guantes', 'Chaleco'],
            obligatorios: ['CASCO', 'CHALECO'], caja: [0, 240, 320, 480]
        }),
        persona({
            trackId: 2, estado: 'CRITICO',
            obligatorios: ['CASCO'],
            faltantes: [{ nombre: 'Casco', gravedad: 'GRAVE', tipo: 'FALTANTE' }],
            caja: [320, 240, 640, 480]
        })
    ]
});
const r2 = rects();
comprobar('se dibujan las 2 cajas', r2.length === 2, `n=${r2.length}`);
comprobar('la caja de la mitad izquierda ocupa x=0..400',
    f(r2[1].x) === '0.0' && f(r2[1].w) === '400.0', `x=${f(r2[1].x)} w=${f(r2[1].w)}`);
comprobar('el recorte vertical de cover está descontado',
    f(r2[1].y) === '225.0' && f(r2[1].h) === '300.0', `y=${f(r2[1].y)} h=${f(r2[1].h)}`);
comprobar('lo que queda fuera de pantalla cae fuera del contenedor',
    r2[1].y + r2[1].h > 450, `borde inferior=${f(r2[1].y + r2[1].h)} vs alto=450`);

// =============================================================================
console.log('\nCASO 3 - tabla y overlay ordenan de más grave a menos');
const filas = [...elementos.cuadroCuerpo.innerHTML.matchAll(/#(\d+)/g)].map(m => m[1]);
comprobar('la tabla pone el crítico primero', filas.join(',') === '2,1',
    `tracks=${filas.join(',')}`);
comprobar('hay una etiqueta por caja',
    llamadas.filter(c => c.op === 'fillRect').length === 2);

// =============================================================================
console.log('\nCASO 4 - sin `dimensiones`: la caja ya viene en píxeles de la imagen');
escena({ natural: [800, 450], rect: { width: 800, height: 450 } });
mod.pintarCuadro({
    dimensiones: null, nombreRol: 'x',
    personas: [persona({ trackId: 3, caja: [100, 100, 300, 200] })]
});
const r4 = rects()[0];
comprobar('mapeo identidad, sin escala extra',
    r4.x === 100 && r4.y === 100 && r4.w === 200 && r4.h === 100,
    `x=${r4.x} y=${r4.y} w=${r4.w} h=${r4.h}`);

// =============================================================================
console.log('\nCASO 5 - la columna de progreso "3 / 4"');
escena({ natural: [800, 450], rect: { width: 800, height: 450 } });
mod.pintarCuadro({
    dimensiones: [800, 450], nombreRol: 'Operador',
    personas: [persona({
        trackId: 5, estado: 'CRITICO', confianza: 0.4,
        conformes: ['Casco', 'Gafas'],
        obligatorios: ['CASCO', 'GAFAS', 'GUANTES', 'CHALECO'],
        faltantes: [{ nombre: 'Guantes', gravedad: 'INTERMEDIO' },
        { nombre: 'Chaleco', gravedad: 'INTERMEDIO' }]
    })]
});
const htmlFila = elementos.cuadroCuerpo.innerHTML;
// 3 chips: uno de estado + uno por cada faltante.
comprobar('estado y los dos faltantes salen como chips',
    (htmlFila.match(/class="chip /g) || []).length === 3);
comprobar('la fila muestra 2 / 4', /<strong>2<\/strong> \/ 4/.test(htmlFila));
comprobar('el estado se rotula con tilde', /CRÍTICO/.test(htmlFila));
comprobar('los nombres de los faltantes se escapan y se pintan',
    /chip intermedio">Guantes</.test(htmlFila) && /chip intermedio">Chaleco</.test(htmlFila));
comprobar('el EPP conforme se lista', /Casco, Gafas/.test(htmlFila));
comprobar('la confianza se muestra como porcentaje', /<td>40%<\/td>/.test(htmlFila));

// =============================================================================
console.log('\nCASO 6 - en simulación se limpia el overlay y se avisa');
escena({ natural: [800, 450], rect: { width: 800, height: 450 } });
mod.pintarCuadro({ dimensiones: [800, 450], simulacion: true, personas: [] });
comprobar('el canvas queda a 0x0',
    elementos.overlayCamara.width === 0 && elementos.overlayCamara.height === 0,
    `${elementos.overlayCamara.width}x${elementos.overlayCamara.height}`);
comprobar('la tabla avisa de que no hay YOLOv8',
    /no está ejecutándose/.test(elementos.cuadroCuerpo.innerHTML));
comprobar('el aviso de simulación es visible', /visible/.test(elementos.avisoMotor.className));
comprobar('el badge de estado es de simulación',
    /simulacion/.test(elementos.cuadroEstado.className));
// Regresión: la rama usaba una variable `ativo` inexistente, y en un
// <script type="module"> eso aborta el callback SSE entero.
comprobar('el aviso se generó sin ReferenceError', elementos.avisoMotor.innerHTML.length > 50);

// =============================================================================
console.log('\nCASO 7 - sin `caja` no se dibuja una caja inválida');
escena({ natural: [800, 450], rect: { width: 800, height: 450 } });
mod.pintarCuadro({
    dimensiones: [800, 450], nombreRol: 'x',
    personas: [persona({ trackId: 9, caja: null }), persona({ trackId: 10, caja: [1, 2] })]
});
comprobar('se ignoran las cajas mal formadas', rects().length === 0, `n=${rects().length}`);

// =============================================================================
console.log('\n' + (fallos.length === 0
    ? `TODAS LAS COMPROBACIONES PASAN`
    : 'FALLOS: ' + fallos.join(' | ')));
process.exit(fallos.length === 0 ? 0 : 1);