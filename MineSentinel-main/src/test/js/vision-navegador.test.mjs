// node --test src/test/js/   (sin dependencias)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { iou, decodificar, TrackerIoU, ReglasEPP, Episodios, Cruces } from '../../main/resources/static/js/vision-navegador.js';

const caja = (x1, y1, x2, y2) => ({ x1, y1, x2, y2 });
const cfg = {
    modelo: { confianza: 0.2, iou: 0.45 },
    asignacion: { margen_x: 0.15, margen_y: 0.1, confianza_suelto: 0.6 },
    catalogo_epp: {
        CASCO: { nombre: 'Casco', gravedad: 'GRAVE', verificable: true },
        CHALECO: { nombre: 'Chaleco', gravedad: 'INTERMEDIO', verificable: true },
        BOTAS: { nombre: 'Botas', gravedad: 'GRAVE', verificable: false }
    },
    mapeo_clases: { CASCO: { conforme: ['helmet'] }, CHALECO: { conforme: ['vest'] } },
    impostores: { GORRA: { sustituye: 'CASCO', clases: ['cap'], nombre: 'Gorra' } },
    roles: { 1: { nombre: 'Operador', obligatorio: ['CASCO', 'CHALECO', 'BOTAS'] } },
    emision: { frames_abrir: 2, frames_cerrar: 2, segundos_evaluacion: 0, confianza_minima_alerta: 0.3 }
};

test('iou', () => {
    assert.equal(iou(caja(0, 0, 10, 10), caja(0, 0, 10, 10)), 1);
    assert.equal(iou(caja(0, 0, 10, 10), caja(20, 20, 30, 30)), 0);
    assert.ok(Math.abs(iou(caja(0, 0, 10, 10), caja(5, 0, 15, 10)) - 1 / 3) < 1e-9);
});

test('decodificar deshace el letterbox y aplica NMS', () => {
    // 2 clases, 3 anclas; layout [1, 4+nc, N]
    const n = 3, filas = [
        [100, 102, 300], [100, 100, 300], [20, 20, 20], [20, 20, 20],  // cx cy w h
        [0.9, 0.8, 0.1], [0.0, 0.0, 0.7]                               // scores
    ];
    const t = { dims: [1, 6, n], data: Float32Array.from(filas.flat()) };
    const r = decodificar(t, 0.2, 0.45, { k: 2, dx: 0, dy: 10 });
    assert.equal(r.length, 2);   // ancla 1 suprimida por NMS frente a la 0
    assert.deepEqual(r[0].caja, caja(45, 40, 55, 50));
    assert.equal(r[1].clase, 1);
    assert.equal(decodificar(t, 0.2, 0.45, { k: 1, dx: 0, dy: 0 }, 1).length, 1);
});

test('tracker mantiene el id y caduca tracks', () => {
    const tr = new TrackerIoU();
    const a = tr.actualizar([{ caja: caja(0, 0, 10, 20), conf: 0.9 }], 0);
    const b = tr.actualizar([{ caja: caja(1, 0, 11, 20), conf: 0.9 }], 100);
    assert.equal(a[0].trackId, b[0].trackId);
    tr.actualizar([], 5000);
    assert.notEqual(tr.actualizar([{ caja: caja(1, 0, 11, 20), conf: 0.9 }], 5100)[0].trackId, a[0].trackId);
});

test('reglas: falta chaleco, casco presente, impostor, botas no verificable', () => {
    const r = new ReglasEPP(cfg);
    const persona = [{ trackId: 7, caja: caja(0, 0, 100, 200), conf: 0.9 }];
    const el = [
        { ...r.clasificar('helmet'), caja: caja(30, -10, 70, 20), confianza: 0.8 },
        { ...r.clasificar('cap'), caja: caja(30, 0, 70, 20), confianza: 0.7 }
    ];
    const a = r.analizar(persona, el, 1);
    const claves = a.estadoActual.map(i => `${i.tipo}:${i.clave}`).sort();
    assert.deepEqual(claves, ['FALTANTE:CHALECO', 'IMPOSTOR:GORRA']);
    const cuadro = r.construirCuadro(a, 1, 5, [640, 480], 1);
    assert.equal(cuadro.personas[0].estado, 'CRITICO');
    assert.deepEqual(cuadro.personas[0].conformes, ['CASCO']);
    assert.deepEqual(cuadro.personas[0].obligatorios, ['CASCO', 'CHALECO']);
});

test('episodios abren tras frames_abrir y cierran tras frames_cerrar', async () => {
    const r = new ReglasEPP(cfg);
    const llamadas = [];
    const ep = new Episodios(r, { abrir: async () => { llamadas.push('abrir'); return 42; }, cerrar: id => llamadas.push('cerrar ' + id) });
    const persona = [{ trackId: 1, caja: caja(0, 0, 100, 200), conf: 0.9 }];
    const conChaleco = [{ ...r.clasificar('helmet'), caja: caja(30, 0, 70, 20), confianza: 0.8 },
                        { ...r.clasificar('vest'), caja: caja(10, 60, 90, 120), confianza: 0.8 }];
    const sinChaleco = conChaleco.slice(0, 1);
    ep.sincronizar(r.analizar(persona, sinChaleco), 0);
    assert.deepEqual(llamadas, []);
    ep.sincronizar(r.analizar(persona, sinChaleco), 1);
    assert.deepEqual(llamadas, ['abrir']);
    ep.sincronizar(r.analizar(persona, conChaleco), 2);
    ep.sincronizar(r.analizar(persona, conChaleco), 3);
    await new Promise(res => setTimeout(res, 0));
    assert.deepEqual(llamadas, ['abrir', 'cerrar 42']);
});

test('cruces de línea', () => {
    const c = new Cruces(0.5);
    assert.deepEqual(c.detectar([{ trackId: 1, caja: caja(0, 0, 10, 40) }], 100), []);
    assert.deepEqual(c.detectar([{ trackId: 1, caja: caja(0, 0, 10, 60) }], 100), [{ tipo: 'ENTRADA', trackId: 1 }]);
});

