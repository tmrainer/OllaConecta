// Prueba web/js/sincronizacion.js contra el emulador de Firestore, con las
// reglas de verdad. Se corre con `npm run pruebas`.
//
// Hace de un teléfono (el de «ana», con localStorage simulado) que trabaja
// con estado.js como lo hacen Asistencia y Padrón. «beto», otra integrante
// de la misma olla, escribe directo en la nube: es el otro teléfono. Cada
// caso dice qué pasa en uno y qué debe verse en el otro.

import fs from 'node:fs';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import * as Fs from 'firebase/firestore';

Fs.setLogLevel('silent');

// Lo que el navegador da y Node no.
const almacen = new Map();
globalThis.localStorage = {
  getItem: (k) => (almacen.has(k) ? almacen.get(k) : null),
  setItem: (k, v) => { almacen.set(k, String(v)); },
  removeItem: (k) => { almacen.delete(k); },
  clear: () => almacen.clear(),
  key: (i) => [...almacen.keys()][i] ?? null,
  get length() { return almacen.size; }
};
globalThis.addEventListener = () => {};
globalThis.document = { addEventListener: () => {}, visibilityState: 'visible' };

const { CONFIG_FIREBASE } = await import('../web/js/firebase-config.js');
CONFIG_FIREBASE.projectId = 'demo-ollaconecta';   // con esto estado.js sí arranca la nube
const { CLAVES, leer, guardar, vincularOlla, ollaVinculada, nuevaAtencion, nuevoAfiliado } = await import('../web/js/modelo.js');
const { estado, cargar, guardarYa, vigilarPestanas } = await import('../web/js/estado.js');
const { enlazarParaPruebas } = await import('../web/js/sincronizacion.js');

const entorno = await initializeTestEnvironment({
  projectId: 'demo-ollaconecta',
  firestore: { rules: fs.readFileSync(new URL('./firestore.rules', import.meta.url), 'utf8'), host: '127.0.0.1', port: 8080 }
});
await entorno.clearFirestore();
const cuenta = (uid) => entorno.authenticatedContext(uid, { email: `${uid}@gmail.com`, email_verified: true }).firestore();
const ana = cuenta('ana');
const beto = cuenta('beto');
const firmaBeto = () => ({ actualizadoPor: 'beto', actualizadoEn: Fs.serverTimestamp() });
const marcaBeto = (tipoMenu) => ({ tipoMenu, por: 'beto', en: Fs.serverTimestamp() });
const enNube = (camino) => Fs.doc(beto, 'ollas/flores/' + camino);

await entorno.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await Fs.setDoc(Fs.doc(db, 'ollas/flores'), { nombre: 'Las Flores', distrito: 'VMT', creadaEn: Fs.serverTimestamp(), creadaPor: 'coordina' });
  for (const uid of ['ana', 'beto']) {
    await Fs.setDoc(Fs.doc(db, `ollas/flores/miembros/${uid}`), { uid, nombre: uid, correo: `${uid}@gmail.com`, rol: 'apoyo', desde: Fs.serverTimestamp(), agregadoPor: 'coordina' });
  }
});

let pasan = 0; const fallos = [];
function comprobar(nombre, ok, detalle = '') {
  if (ok) { pasan++; console.log(`  ✓ ${nombre}`); } else { fallos.push(nombre); console.log(`  ✗ ${nombre}${detalle ? `\n    ${detalle}` : ''}`); }
}
/** Espera a que `cond()` sea verdad (la nube avisa cuando quiere). */
async function esperar(nombre, cond, ms = 6000) {
  const fin = Date.now() + ms;
  while (Date.now() < fin) {
    let v = null;
    try { v = await cond(); } catch (e) { /* todavía no */ }
    if (v) { comprobar(nombre, true); return v; }
    await new Promise((r) => setTimeout(r, 60));
  }
  comprobar(nombre, false, 'no pasó en el tiempo esperado');
  return null;
}
const persona = (id, nombres) => ({ ...nuevoAfiliado(), id, nombres, apellidoPaterno: 'Prueba', numeroDocumento: '0000000' + id.slice(-1), tipoAfiliado: 'habitual', altaEn: '2026-01-01' });
const dia = (id, fecha, marcas) => ({ ...nuevaAtencion({ precioMenuNormalCent: 300, precioMenuAyudaSocialCent: 100 }), id, fecha, asistencias: marcas });
const nubeDoc = async (camino) => { const d = await Fs.getDoc(enNube(camino)); return d.exists() ? d.data() : null; };
const diaLocal = (fecha) => estado.padron.atenciones.find((a) => a.fecha === fecha);
const marcasDe = (at) => Object.fromEntries((at ? at.asistencias : []).map((x) => [x.afiliadoId, x.tipoMenu]));
let recargas = 0;

console.log('Unirse a la olla: lo del teléfono se junta con lo de la nube, sin pisar nada');
// La nube ya tiene a C y el día D1 con B marcada por beto. Sin precios.
await Fs.setDoc(enNube('personas/C'), { ...persona('C', 'Carla'), ...firmaBeto() });
const { asistencias: _a, ...d1 } = dia('d1', '2026-10-01', []);
await Fs.setDoc(enNube('dias/2026-10-01'), { ...d1, marcas: { B: marcaBeto('normal') }, ...firmaBeto() });
// El teléfono tiene a A y B, el mismo día D1 con A marcada, y precios.
guardar(CLAVES.padron, { version: 3, afiliados: [persona('A', 'Ana'), persona('B', 'Beatriz')], atenciones: [dia('d1', '2026-10-01', [{ afiliadoId: 'A', tipoMenu: 'ayuda_social' }])] });
guardar(CLAVES.config, { version: 1, precioMenuNormalCent: 300, precioMenuAyudaSocialCent: 100 });
vincularOlla({ id: 'flores', nombre: 'Las Flores', rol: 'apoyo', juntar: true });

enlazarParaPruebas({ db: ana, Fs, uid: 'ana' });
cargar();
vigilarPestanas(() => { recargas++; });

await esperar('la nube recibe a A y B firmadas por ana', async () => {
  const a = await nubeDoc('personas/A'); const b = await nubeDoc('personas/B');
  return a && b && a.actualizadoPor === 'ana' && a.nombres === 'Ana';
});
await esperar('el día D1 junta la marca de ana con la de beto', async () => {
  const d = await nubeDoc('dias/2026-10-01');
  return d && d.marcas.A && d.marcas.A.tipoMenu === 'ayuda_social' && d.marcas.A.por === 'ana' && d.marcas.B && d.marcas.B.por === 'beto';
});
await esperar('los precios del teléfono suben porque la nube no tenía', async () => {
  const p = await nubeDoc('ajustes/precios'); return p && p.precioMenuNormalCent === 300 && p.precioMenuAyudaSocialCent === 100;
});
await esperar('el teléfono ve a A, B y C, y el día D1 con las dos marcas', () =>
  estado.padron.afiliados.map((a) => a.id).join() === 'A,B,C' && marcasDe(diaLocal('2026-10-01')).B === 'normal' && marcasDe(diaLocal('2026-10-01')).A === 'ayuda_social');
comprobar('lo mismo quedó guardado en localStorage', leer(CLAVES.padron, {}).afiliados.length === 3);
comprobar('y la página se repintó', recargas >= 1);
comprobar('ya no queda nada por juntar', ollaVinculada().juntar === false);
comprobar('la persona C trae sus campos tal cual', estado.padron.afiliados[2].nombres === 'Carla' && estado.padron.afiliados[2].tipoAfiliado === 'habitual');

console.log('Lo que se marca aquí llega a la nube');
diaLocal('2026-10-01').asistencias.push({ afiliadoId: 'C', tipoMenu: 'normal' });
guardarYa();
await esperar('la marca de C sube con la firma de ana', async () => {
  const d = await nubeDoc('dias/2026-10-01'); return d && d.marcas.C && d.marcas.C.por === 'ana' && d.actualizadoPor === 'ana';
});
diaLocal('2026-10-01').asistencias = diaLocal('2026-10-01').asistencias.filter((x) => x.afiliadoId !== 'B');
guardarYa();
await esperar('desmarcar a B borra solo su marca', async () => {
  const d = await nubeDoc('dias/2026-10-01'); return d && !d.marcas.B && d.marcas.A && d.marcas.C;
});

console.log('Lo que cambia en el otro teléfono llega aquí');
await Fs.setDoc(enNube('personas/C'), { nombres: 'Carla Q.', ...firmaBeto() }, { merge: true });
await esperar('una corrección de beto en C se ve aquí', () => estado.padron.afiliados.find((a) => a.id === 'C').nombres === 'Carla Q.');
const { asistencias: _s, ...d2 } = dia('d2', '2026-10-02', []);
await Fs.setDoc(enNube('dias/2026-10-02'), { ...d2, marcas: { B: marcaBeto('normal') }, ...firmaBeto() });
await esperar('un día nuevo de beto aparece aquí con su marca', () => marcasDe(diaLocal('2026-10-02')).B === 'normal');

console.log('Dos teléfonos sin señal marcan el mismo día y no se pierde nada');
await Fs.disableNetwork(ana);
diaLocal('2026-10-02').asistencias.push({ afiliadoId: 'A', tipoMenu: 'normal' });
guardarYa();
await Fs.setDoc(enNube('dias/2026-10-02'), { marcas: { C: marcaBeto('ayuda_social') }, ...firmaBeto() }, { merge: true });
comprobar('sin señal, el teléfono ya muestra su marca', marcasDe(diaLocal('2026-10-02')).A === 'normal');
await Fs.enableNetwork(ana);
await esperar('al volver la señal, la nube tiene las tres marcas', async () => {
  const d = await nubeDoc('dias/2026-10-02'); return d && d.marcas.A && d.marcas.B && d.marcas.C && d.marcas.A.por === 'ana' && d.marcas.C.por === 'beto';
});
await esperar('y el teléfono también', () => Object.keys(marcasDe(diaLocal('2026-10-02'))).sort().join() === 'A,B,C');

console.log('Borrar');
estado.padron.afiliados = estado.padron.afiliados.filter((a) => a.id !== 'B');
guardarYa();
await esperar('quitar a B del padrón la borra de la nube', async () => (await nubeDoc('personas/B')) === null);
await Fs.deleteDoc(enNube('dias/2026-10-01'));
await esperar('un día borrado allá desaparece aquí', () => !diaLocal('2026-10-01'));
comprobar('los demás días siguen', !!diaLocal('2026-10-02'));

console.log('El precio del menú');
estado.config.precioMenuNormalCent = 350;
guardarYa();
await esperar('cambiarlo aquí lo sube', async () => { const p = await nubeDoc('ajustes/precios'); return p && p.precioMenuNormalCent === 350; });
await Fs.setDoc(enNube('ajustes/precios'), { precioMenuAyudaSocialCent: 150, ...firmaBeto() }, { merge: true });
await esperar('cambiarlo allá lo baja', () => estado.config.precioMenuAyudaSocialCent === 150 && leer(CLAVES.config, {}).precioMenuAyudaSocialCent === 150);

console.log('Un día sin fecha se queda en el teléfono');
const suelto = { ...nuevaAtencion(null), fecha: '' };
estado.padron.atenciones.push(suelto);
guardarYa();
await Fs.setDoc(enNube('personas/C'), { nota: 'otra vez', ...firmaBeto() }, { merge: true });
await esperar('llega un cambio de allá…', () => estado.padron.afiliados.find((a) => a.id === 'C').nota === 'otra vez');
comprobar('…y el día sin fecha sigue aquí', estado.padron.atenciones.some((a) => a.id === suelto.id));
const todos = await Fs.getDocs(Fs.collection(beto, 'ollas/flores/dias'));
comprobar('y no subió a la nube', todos.docs.every((d) => d.id !== '' && d.id !== suelto.id));

await entorno.cleanup();
console.log(fallos.length ? `\n✗ ${fallos.length} fallan, ${pasan} pasan` : `\n✓ ${pasan} de ${pasan} pasan`);
process.exit(fallos.length ? 1 : 0);
