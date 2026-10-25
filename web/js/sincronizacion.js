// El padrón, la asistencia y el precio del menú, también en Firestore. El teléfono
// sigue con localStorage: lo que cambia se empuja y lo que avisa la nube se escribe
// aquí. Sin señal, Firestore guarda los empujes y los manda cuando vuelve.
//
//   ollas/{olla}/personas/{id}        el afiliado tal cual, más actualizadoPor/En
//   ollas/{olla}/dias/{fecha}         los campos del día, más marcas: { [afiliadoId]: { tipoMenu, por, en } }
//   ollas/{olla}/ajustes/precios      el precio del menú (config), más actualizadoPor/En
// Cada marca es un campo aparte: así dos teléfonos sin señal pueden marcar el mismo
// día sin pisarse. `base` es lo último que teléfono y nube tuvieron igual.
//
// Al unirse a la olla (o tras recuperar una copia o recibir por QR), lo del teléfono
// se JUNTA con la nube: se sube solo lo que falta, nunca se borra ni se pisa; por eso
// hace falta una foto del servidor, no de la caché.
// Días sin fecha o con fecha repetida no suben; el B-1 y las entregas tampoco.

import { firebase, quien } from './cuenta.js';
import { estado, cargar, guardarPendiente, preciosActuales } from './estado.js';
import { CLAVES, guardar, esIso, ollaVinculada, vincularOlla, desvincularOlla } from './modelo.js';

const MENUS = new Set(['normal', 'ayuda_social']);
const TANDA = 400;   // Firestore admite 500 escrituras por lote

let ctx = null;        // { db, Fs, uid, ollaId, ref }
let base = null;       // foto() de lo último que el teléfono y la nube tuvieron igual
let detenido = false;
let enviados = 0;      // escrituras mandadas: para saber si un empuje escribió algo
const bajas = [];      // para dejar de escuchar

// Lo que hay en la nube, al día con cada aviso: mismas formas que foto().
const nube = {
  personas: new Map(), dias: new Map(), precios: null,
  listos: new Set(),      // qué escuchas ya avisaron una vez
  delServidor: false      // ya se vio una foto del servidor (no solo de la caché)
};

function ordenar(v) {
  if (Array.isArray(v)) return v.map(ordenar);
  if (v && typeof v === 'object') {
    const o = {};
    for (const k of Object.keys(v).sort()) o[k] = ordenar(v[k]);
    return o;
  }
  return v;
}
/** El mismo objeto, siempre el mismo texto: para comparar sin importar el orden de las claves. */
const canon = (v) => JSON.stringify(ordenar(v));

/**
 * El padrón y los precios en la forma en que se comparan y se suben.
 * @returns {{ personas: Map<string, {datos: Object, json: string}>,
 *   dias: Map<string, {campos: Object, json: string, marcas: Map<string, string>}>,
 *   precios: Object, preciosJson: string }}
 */
function foto(padron, precios) {
  const personas = new Map();
  for (const a of padron.afiliados) {
    if (a && typeof a.id === 'string' && a.id && !personas.has(a.id)) personas.set(a.id, { datos: a, json: canon(a) });
  }
  const dias = new Map();
  for (const at of padron.atenciones) {
    if (!at || !esIso(at.fecha) || dias.has(at.fecha)) continue;
    const { asistencias, ...campos } = at;
    const marcas = new Map();
    for (const x of Array.isArray(asistencias) ? asistencias : []) {
      if (x && typeof x.afiliadoId === 'string' && MENUS.has(x.tipoMenu)) marcas.set(x.afiliadoId, x.tipoMenu);
    }
    dias.set(at.fecha, { campos, json: canon(campos), marcas });
  }
  const p = soloPrecios(precios);
  return { personas, dias, precios: p, preciosJson: canon(p) };
}

/** De la config, solo el precio del menú: es lo único de ella que sube. */
function soloPrecios(c) {
  const n = (v) => (Number.isFinite(v) ? v : null);
  return { precioMenuNormalCent: n(c.precioMenuNormalCent), precioMenuAyudaSocialCent: n(c.precioMenuAyudaSocialCent) };
}

function mismasMarcas(a, b) {
  if (a.size !== b.size) return false;
  for (const [id, tipo] of a) if (b.get(id) !== tipo) return false;
  return true;
}

function iguales(a, b) {
  if (a.personas.size !== b.personas.size || a.dias.size !== b.dias.size || a.preciosJson !== b.preciosJson) return false;
  for (const [id, p] of a.personas) { const q = b.personas.get(id); if (!q || q.json !== p.json) return false; }
  for (const [f, d] of a.dias) { const e = b.dias.get(f); if (!e || e.json !== d.json || !mismasMarcas(d.marcas, e.marcas)) return false; }
  return true;
}

function personaDe(doc) {
  const { actualizadoPor, actualizadoEn, ...datos } = doc.data();
  return { datos, json: canon(datos) };
}

function diaDe(doc) {
  const { actualizadoPor, actualizadoEn, marcas, ...campos } = doc.data();
  const m = new Map();
  for (const [id, x] of Object.entries(marcas || {})) if (x && MENUS.has(x.tipoMenu)) m.set(id, x.tipoMenu);
  return { campos, json: canon(campos), marcas: m };
}

/** Los precios que valen: los de la nube, o los de aquí mientras la nube no tenga. */
function preciosEfectivos() {
  return nube.precios || preciosActuales();
}

function fotoNube() {
  const p = soloPrecios(preciosEfectivos());
  return { personas: nube.personas, dias: nube.dias, precios: p, preciosJson: canon(p) };
}

/**
 * El padrón como queda con lo de la nube. Conserva el orden que ya tenía
 * este teléfono; lo nuevo va al final. Los días sin fecha válida o con fecha
 * repetida (que no suben) se quedan como estaban.
 */
function armar() {
  const posP = new Map(estado.padron.afiliados.map((a, i) => [a.id, i]));
  const lugar = (m, k) => (m.has(k) ? m.get(k) : Number.MAX_SAFE_INTEGER);
  const cmp = (x, y) => (x < y ? -1 : x > y ? 1 : 0);
  const afiliados = [...nube.personas.values()].map((p) => p.datos)
    .sort((a, b) => lugar(posP, a.id) - lugar(posP, b.id) || cmp(a.altaEn || '', b.altaEn || '') || cmp(a.id, b.id));
  const pos = new Map(afiliados.map((a, i) => [a.id, i]));

  const posD = new Map();
  const sueltos = [];
  const vistas = new Set();
  estado.padron.atenciones.forEach((at, i) => {
    if (esIso(at.fecha) && !vistas.has(at.fecha)) { vistas.add(at.fecha); posD.set(at.fecha, i); } else sueltos.push(at);
  });
  const atenciones = [...nube.dias.entries()].map(([fecha, d]) => ({
    ...d.campos,
    id: d.campos.id || fecha,
    fecha,
    asistencias: [...d.marcas].map(([afiliadoId, tipoMenu]) => ({ afiliadoId, tipoMenu }))
      .sort((x, y) => lugar(pos, x.afiliadoId) - lugar(pos, y.afiliadoId) || cmp(x.afiliadoId, y.afiliadoId))
  })).sort((a, b) => lugar(posD, a.fecha) - lugar(posD, b.fecha) || cmp(a.fecha, b.fecha));
  return { ...estado.padron, afiliados, atenciones: atenciones.concat(sueltos) };
}

/** Escribe en el teléfono lo que hay en la nube y repinta. */
function aplicar(alRecargar) {
  const padron = armar();
  const precios = preciosEfectivos();
  if (!guardar(CLAVES.padron, padron)) return;   // sin espacio: se avisa por leer()/guardar(), y no se toca base
  if (nube.precios) {
    Object.assign(estado.config, nube.precios);
    guardar(CLAVES.config, estado.config);
  }
  base = foto(padron, precios);
  cargar();
  alRecargar();
}

function firma() {
  return { actualizadoPor: ctx.uid, actualizadoEn: ctx.Fs.serverTimestamp() };
}
function marca(tipoMenu) {
  return { tipoMenu, por: ctx.uid, en: ctx.Fs.serverTimestamp() };
}

function fallo(e) {
  if (e && e.code === 'permission-denied') {
    // Ya no está en la olla (o nunca estuvo): se deja de sincronizar. Lo del
    // teléfono se queda. Tus datos lo explica al volver a entrar.
    detener();
    desvincularOlla();
  }
  console.warn('La nube de la olla no respondió:', e);
}

/** Junta escrituras y las manda por tandas. Devuelve cuántas mandó. */
class Lote {
  constructor() { this.ops = []; }
  set(ref, datos, opciones) { this.ops.push((b) => (opciones ? b.set(ref, datos, opciones) : b.set(ref, datos))); }
  delete(ref) { this.ops.push((b) => b.delete(ref)); }
  enviar() {
    for (let i = 0; i < this.ops.length; i += TANDA) {
      const b = ctx.Fs.writeBatch(ctx.db);
      for (const op of this.ops.slice(i, i + TANDA)) op(b);
      b.commit().catch(fallo);
    }
    enviados += this.ops.length;
    return this.ops.length;
  }
}

/** Sube lo que cambió en este teléfono desde `base`. Devuelve cuántas escrituras mandó. */
function empujar() {
  if (!ctx || detenido || !base) return 0;
  const ahora = foto(estado.padron, preciosActuales());
  const lote = new Lote();
  const { Fs } = ctx;

  for (const [id, p] of ahora.personas) {
    const q = base.personas.get(id);
    if (!q || q.json !== p.json) lote.set(ctx.ref('personas', id), { ...p.datos, ...firma() });
  }
  for (const id of base.personas.keys()) if (!ahora.personas.has(id)) lote.delete(ctx.ref('personas', id));

  for (const [fecha, d] of ahora.dias) {
    const e = base.dias.get(fecha);
    const cambios = {};
    if (!e) {
      Object.assign(cambios, d.campos);
    } else if (e.json !== d.json) {
      for (const k of new Set([...Object.keys(d.campos), ...Object.keys(e.campos)])) {
        if (canon(d.campos[k]) !== canon(e.campos[k])) cambios[k] = d.campos[k] === undefined ? Fs.deleteField() : d.campos[k];
      }
    }
    const marcas = {};
    for (const [id, tipo] of d.marcas) if (!e || e.marcas.get(id) !== tipo) marcas[id] = marca(tipo);
    if (e) for (const id of e.marcas.keys()) if (!d.marcas.has(id)) marcas[id] = Fs.deleteField();
    if (Object.keys(marcas).length) cambios.marcas = marcas;
    if (Object.keys(cambios).length) lote.set(ctx.ref('dias', fecha), { ...cambios, fecha, ...firma() }, { merge: true });
  }
  for (const fecha of base.dias.keys()) if (!ahora.dias.has(fecha)) lote.delete(ctx.ref('dias', fecha));

  if (ahora.preciosJson !== base.preciosJson) lote.set(ctx.ref('ajustes', 'precios'), { ...ahora.precios, ...firma() }, { merge: true });

  base = ahora;
  return lote.enviar();
}

/**
 * Primera vez: sube lo que este teléfono tiene y la nube no. No borra ni pisa
 * nada de la nube. Devuelve cuántas escrituras mandó.
 */
function juntar() {
  const ahora = foto(estado.padron, preciosActuales());
  const lote = new Lote();
  for (const [id, p] of ahora.personas) {
    if (!nube.personas.has(id)) lote.set(ctx.ref('personas', id), { ...p.datos, ...firma() });
  }
  for (const [fecha, d] of ahora.dias) {
    const e = nube.dias.get(fecha);
    const marcas = {};
    for (const [id, tipo] of d.marcas) if (!e || !e.marcas.has(id)) marcas[id] = marca(tipo);
    if (!e) lote.set(ctx.ref('dias', fecha), { ...d.campos, fecha, marcas, ...firma() });
    else if (Object.keys(marcas).length) lote.set(ctx.ref('dias', fecha), { marcas, ...firma() }, { merge: true });
  }
  const p = ahora.precios;
  if (!nube.precios && (p.precioMenuNormalCent !== null || p.precioMenuAyudaSocialCent !== null)) {
    lote.set(ctx.ref('ajustes', 'precios'), { ...p, ...firma() }, { merge: true });
  }
  return lote.enviar();
}

let enlacePruebas = null;
/** Solo para nube/pruebas-sincronizacion.mjs: esta conexión en vez de la cuenta de Google. */
export function enlazarParaPruebas(enlace) { enlacePruebas = enlace; }

async function enlaceReal() {
  const yo = await quien();
  const f = await firebase();
  return yo && f ? { db: f.db, Fs: f.Fs, uid: yo.uid } : null;
}

function detener() {
  detenido = true;
  for (const baja of bajas.splice(0)) baja();
}

/** Cada aviso de la nube: primero sube lo de aquí, después baja lo de allá. */
function alLlegar(alRecargar) {
  if (detenido || nube.listos.size < 3) return;
  const o = ollaVinculada();
  if (!o || o.id !== ctx.ollaId) { detener(); return; }
  if (o.juntar) {
    if (!nube.delServidor) return;           // hay que ver lo que la nube tiene de verdad
    const n = juntar();
    vincularOlla({ ...o, juntar: false });
    if (n) return;                           // la nube avisará de nuevo, ya con esto
  }
  const antes = enviados;
  guardarPendiente();                        // escribe (y empuja) lo que esté a medio guardar
  empujar();
  if (enviados !== antes) return;            // la nube avisará de nuevo, ya con esto
  if (!iguales(fotoNube(), base)) aplicar(alRecargar);
}

/**
 * Empieza a sincronizar con la olla a la que está unido este teléfono.
 * @param {string} fotoInicial  JSON de { padron, precios } tal como estaban al
 *   abrir la página: lo último que la nube vio de este teléfono
 * @param {() => void} alRecargar  repinta la página con los datos recargados
 * @returns {Promise<{ empujar: () => number, rebasar: () => void } | null>}
 *   null si no hay cuenta, olla o proyecto
 */
export async function vigilarNube(fotoInicial, alRecargar) {
  const olla = ollaVinculada();
  const enlace = olla && (enlacePruebas || await enlaceReal());
  if (!enlace) return null;
  const { db, Fs, uid } = enlace;
  ctx = { db, Fs, uid, ollaId: olla.id, ref: (...camino) => Fs.doc(db, 'ollas', olla.id, ...camino) };
  const inicial = JSON.parse(fotoInicial);
  base = foto(inicial.padron, inicial.precios);

  const opciones = { includeMetadataChanges: true };   // para enterarse de cuándo llega la foto del servidor
  const escuchar = (nombre, ref, alCambiar) => {
    bajas.push(Fs.onSnapshot(ref, opciones, (snap) => {
      alCambiar(snap);
      nube.listos.add(nombre);
      if (!snap.metadata.fromCache) nube.delServidor = true;
      alLlegar(alRecargar);
    }, fallo));
  };
  escuchar('personas', Fs.collection(db, 'ollas', olla.id, 'personas'), (snap) => {
    for (const c of snap.docChanges()) {
      if (c.type === 'removed') nube.personas.delete(c.doc.id);
      else nube.personas.set(c.doc.id, personaDe(c.doc));
    }
  });
  escuchar('dias', Fs.collection(db, 'ollas', olla.id, 'dias'), (snap) => {
    for (const c of snap.docChanges()) {
      if (c.type === 'removed') nube.dias.delete(c.doc.id);
      else nube.dias.set(c.doc.id, diaDe(c.doc));
    }
  });
  escuchar('precios', ctx.ref('ajustes', 'precios'), (snap) => {
    nube.precios = snap.exists() ? soloPrecios(snap.data()) : null;
  });

  return {
    empujar,
    /** Lo que hay ahora en el teléfono ya está en la nube (lo subió otra pestaña). */
    rebasar: () => { base = foto(estado.padron, preciosActuales()); }
  };
}
