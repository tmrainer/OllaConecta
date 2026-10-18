// reportes.js — GENERADO por armar.mjs desde web/js/ (modelo, calculos, xlsx,
// excel-reportes). No editar aquí: cambiar el original y volver a armar.
// Es el mismo código con el que la app exporta el Excel.

if (typeof TextEncoder === 'undefined') {
  globalThis.TextEncoder = class {
    encode(texto) {
      const u = unescape(encodeURIComponent(String(texto)));
      const bytes = new Uint8Array(u.length);
      for (let i = 0; i < u.length; i += 1) bytes[i] = u.charCodeAt(i);
      return bytes;
    }
  };
}

const PROYECTO_FIREBASE = "";   // de web/js/firebase-config.js

// ---------------------------------------------------------------- modelo.js
const modelo = (() => {
// modelo.js — forma de los datos, dinero, fechas y persistencia.
// Sin DOM, sin red.

const CLAVES = {
  rendiciones: 'b1.rendiciones.v1',
  padron: 'b1.padron.v1',
  insumos: 'b1.insumos.v1',       // reservada: catálogo de insumos (fase 6); hoy nada la usa
  calendario: 'b1.calendario.v1',
  config: 'b1.config.v1',
  respaldo: 'b1.respaldo.v1',    // { ultimoEn: 'AAAA-MM-DD' }: cuándo se exportó el último respaldo
  olla: 'b1.olla.v1'             // { id, nombre, rol, juntar }: la olla de la nube a la que está unido este teléfono
};

const VERSION_ESQUEMA = 1;
const VERSION_PADRON = 3;      // v3: raciones derivadas de la asistencia
const VERSION_CALENDARIO = 1;

// ---------------------------------------------------------------- utilidades

function uuid() {
  if (globalThis.crypto && typeof globalThis.crypto.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  // Respaldo sin dependencias para navegadores sin randomUUID.
  return 'id-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10);
}

function debounce(fn, ms) {
  let t = null;
  return function (...args) {
    clearTimeout(t);
    t = setTimeout(() => fn.apply(this, args), ms);
  };
}

/**
 * «1 persona», «3 personas»: el plural de verdad, no «persona(s)», que se lee
 * mal en voz alta y cuesta a quien lee despacio.
 * @param {number} n
 * @param {string} uno  la palabra en singular
 * @param {string} [varios]  el plural, si no es singular + «s»
 */
function cuantos(n, uno, varios = uno + 's') {
  return `${n} ${n === 1 ? uno : varios}`;
}

function pad2(n) {
  return String(n).padStart(2, '0');
}

// --------------------------------------------------------------------- dinero
// Todo monto vive como ENTERO de céntimos. Nunca se usa coma flotante.

/**
 * Convierte texto escrito por la usuaria a céntimos enteros.
 * Devuelve null si está vacío o no es un monto válido. Nunca adivina.
 */
function aCentimos(texto) {
  if (texto === null || texto === undefined) return null;
  const limpio = String(texto).trim().replace(/\s/g, '').replace(',', '.');
  if (limpio === '') return null;
  if (!/^\d+(\.\d{0,2})?$/.test(limpio)) return null;
  const [entera, decimal = ''] = limpio.split('.');
  const dec = (decimal + '00').slice(0, 2);
  return Number(entera) * 100 + Number(dec);
}

/** Céntimos a texto "209.85". Devuelve '' si es null: lo vacío se muestra vacío. */
function formatearSoles(centimos) {
  if (centimos === null || centimos === undefined || !Number.isFinite(centimos)) return '';
  const signo = centimos < 0 ? '-' : '';
  const abs = Math.abs(Math.round(centimos));
  return signo + Math.floor(abs / 100) + '.' + pad2(abs % 100);
}

/** Cantidad numérica libre (puede llevar decimales). null si no es válida. */
function aNumero(texto) {
  if (texto === null || texto === undefined) return null;
  const limpio = String(texto).trim().replace(',', '.');
  if (limpio === '') return null;
  if (!/^\d+(\.\d+)?$/.test(limpio)) return null;
  return Number(limpio);
}

// --------------------------------------------------------------------- fechas

/**
 * Fecha de hoy en hora LOCAL, como 'AAAA-MM-DD'. No usa toISOString(), que
 * daría la fecha UTC (en Lima, ya "mañana" después de las 19:00).
 * Es la fecha por defecto de casi todo: el día de atención nuevo, el alta, y
 * la referencia de edad cuando no se pasa otra. No lee ni escribe datos guardados.
 * @returns {string}
 */
function hoyIso() {
  const d = new Date();
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

/** ISO 'AAAA-MM-DD' a Date local (evita el corrimiento UTC de new Date(iso)). */
function aFecha(iso) {
  if (!esIso(iso)) return null;
  const [a, m, d] = iso.split('-').map(Number);
  const f = new Date(a, m - 1, d);
  return Number.isNaN(f.getTime()) ? null : f;
}

function aIso(fecha) {
  if (!(fecha instanceof Date) || Number.isNaN(fecha.getTime())) return '';
  return `${fecha.getFullYear()}-${pad2(fecha.getMonth() + 1)}-${pad2(fecha.getDate())}`;
}

/**
 * ¿Tiene `valor` la FORMA 'AAAA-MM-DD'? Solo revisa el formato: '2024-02-31'
 * pasa. Para saber si la fecha existe, usar aFecha(), que devuelve null si no.
 * Se usa como guarda antes de comparar fechas como texto. No lee ni escribe datos.
 * @param {*} valor
 * @returns {boolean}
 */
function esIso(valor) {
  return typeof valor === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(valor);
}

/** 'AAAA-MM-DD' a 'DD-MM-AA', como se escribe en el papel. */
const MESES_LARGOS = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto',
  'septiembre', 'octubre', 'noviembre', 'diciembre'];
const DIAS_SEMANA = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];

/** «Sábado 11 de octubre»: la fecha como se dice. Solo para mostrar. */
function fechaLarga(iso) {
  if (!esIso(iso)) return '';
  const [a, m, d] = iso.split('-').map(Number);
  return `${DIAS_SEMANA[new Date(a, m - 1, d).getDay()]} ${d} de ${MESES_LARGOS[m - 1]}`;
}

function aDdMmAa(iso) {
  if (!esIso(iso)) return '';
  const [a, m, d] = iso.split('-');
  return `${d}-${m}-${a.slice(2)}`;
}

function diasEntre(isoA, isoB) {
  const a = aFecha(isoA);
  const b = aFecha(isoB);
  if (!a || !b) return null;
  return Math.round((b - a) / 86400000);
}

const MESES = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Setiembre', 'Octubre', 'Noviembre', 'Diciembre'
];

// ------------------------------------------------------------ constructores

function nuevoEgreso() {
  return {
    id: uuid(),
    fechaCompra: '',
    descripcion: '',
    insumoId: null,
    cantidad: null,
    cantidadVarios: false,
    unidadMedida: '',
    rucProveedor: '',
    proveedorNombre: '',
    boletaSerie: '',
    boletaCorrelativo: '',
    precioUnitarioCent: null,
    precioUnitarioVarios: false,
    montoTotalCent: null,
    origenFondo: 'subsidio'
  };
}

function nuevaRendicion() {
  return {
    version: VERSION_ESQUEMA,
    id: uuid(),
    actualizadoEn: new Date().toISOString(),
    tipoCentro: '',
    nombreCentro: '',
    codigoPca: '',
    periodo: { tipo: 'mensual', inicio: '', fin: '', etiqueta: '' },
    fechaRendicion: '',
    nombrePresidenta: '',
    dniPresidenta: '',
    celular: '',
    montoSubsidioCent: null,
    fechaAsignacion: '',
    egresos: [nuevoEgreso()],
    nombrePresidentaFirma: '',
    dniPresidentaFirma: '',
    nombreTesorera: '',
    dniTesorera: ''
  };
}

function nuevoAfiliado() {
  return {
    id: uuid(),
    apellidoPaterno: '',
    apellidoMaterno: '',
    nombres: '',
    numeroDocumento: '',       // OBLIGATORIO
    tipoDocumento: 'dni',      // 'dni' | 'ce'
    fechaNacimiento: '',       // opcional: la vía rápida es el grupo etario
    grupoEtarioManual: null,   // OBLIGATORIO si no hay fecha de nacimiento
    sexo: '',
    tipoAfiliado: '',          // OBLIGATORIO: 'habitual' | 'caso_social'
    activo: true,
    altaEn: hoyIso(),
    bajaEn: '',
    motivoBaja: '',
    origenDato: 'manual',
    verificadoPor: '',
    nota: ''
  };
}

/**
 * Un día de atención. Las raciones NO se escriben: salen de contar la asistencia.
 * La comida se prepara el mismo día según quién viene.
 *
 * Los precios son una COPIA del precio configurado al crear el día, para que el
 * histórico no cambie si mañana sube el menú. Editables por si ese día fue otro.
 *
 * Solo construye el objeto: NO lo agrega a `padron.atenciones` ni lo guarda.
 * Quien llama decide cuándo (ver asegurarHoyGuardado en ui-asistencia.js).
 * La fecha sale de hoyIso(); quien necesita otro día la sobrescribe.
 *
 * @param {{precioMenuNormalCent: ?number, precioMenuAyudaSocialCent: ?number}|null} precios
 *   normalmente preciosActuales() de estado.js; null deja los precios vacíos.
 * @returns {{id: string, fecha: string, asistencias: Array<{afiliadoId: string, tipoMenu: string}>,
 *   precioMenuNormalCent: ?number, precioMenuAyudaSocialCent: ?number,
 *   precioTomadoDeConfig: boolean, legado: ?Object, nota: string}}
 */
function nuevaAtencion(precios) {
  return {
    id: uuid(),
    fecha: hoyIso(),
    asistencias: [],            // [{ afiliadoId, tipoMenu: 'normal'|'ayuda_social' }]
    precioMenuNormalCent: precios ? precios.precioMenuNormalCent : null,
    precioMenuAyudaSocialCent: precios ? precios.precioMenuAyudaSocialCent : null,
    precioTomadoDeConfig: !!precios,
    legado: null,               // solo en días migrados desde la v2
    nota: ''
  };
}

function padronVacio() {
  return { version: VERSION_PADRON, afiliados: [], atenciones: [] };
}

function calendarioVacio() {
  return {
    version: VERSION_CALENDARIO,
    periodicidad: 'mensual',      // 'semanal' | 'quincenal' | 'mensual'
    fechaLimiteBase: '',          // una fecha de entrega que la usuaria conoce
    diasDeAviso: [7, 3, 1],
    entregas: []                  // [{ id, fechaLimite, estado, nota }]
  };
}

function nuevaEntrega(fechaLimite) {
  return { id: uuid(), fechaLimite: fechaLimite || '', estado: 'pendiente', nota: '' };
}

function configVacia() {
  return {
    version: VERSION_ESQUEMA,
    calibracion: { offsetXmm: 0, offsetYmm: 0 },
    ultimoCentro: null,
    // Precio del menú, que el centro ya tiene fijado. Se copia a cada día nuevo.
    precioMenuNormalCent: null,
    precioMenuAyudaSocialCent: null
  };
}

/**
 * Migración del padrón a la v3. No inventa datos.
 *
 * - v1 -> v2: racionesServidas/racionesCasoSocial se separaban en dos cuentas.
 * - v2 -> v3: las raciones ya no se escriben, se cuentan de la asistencia. Los
 *   días que traían cuentas escritas a mano y no tienen asistencia marcada
 *   conservan esas cifras en `legado`, porque no hay forma de reconstruir quién
 *   vino aquel día sin inventárselo. Siguen sumando, y la interfaz dice de dónde
 *   salen.
 */
function migrarPadron(datos) {
  if (!datos || typeof datos !== 'object') return padronVacio();
  const salida = {
    version: VERSION_PADRON,
    afiliados: Array.isArray(datos.afiliados) ? datos.afiliados : [],
    atenciones: []
  };

  for (const a of salida.afiliados) {
    if (a.tipoAfiliado === undefined || a.tipoAfiliado === null) a.tipoAfiliado = '';
    if (a.tipoDocumento === 'sin_documento') a.tipoDocumento = 'dni';
  }

  for (const at of Array.isArray(datos.atenciones) ? datos.atenciones : []) {
    if (!at || typeof at !== 'object') continue;
    const base = nuevaAtencion(null);
    base.id = at.id || uuid();
    base.fecha = at.fecha || '';
    base.nota = at.nota || '';
    base.asistencias = Array.isArray(at.asistencias) ? at.asistencias : [];
    base.precioMenuNormalCent = Number.isFinite(at.precioMenuNormalCent) ? at.precioMenuNormalCent : null;
    base.precioMenuAyudaSocialCent = Number.isFinite(at.precioMenuAyudaSocialCent) ? at.precioMenuAyudaSocialCent : null;
    base.precioTomadoDeConfig = false;

    if (at.legado) {
      base.legado = at.legado;
    } else if (base.asistencias.length === 0) {
      // Cuentas escritas a mano en v1 o v2.
      const total = Number.isFinite(at.racionesServidas) ? at.racionesServidas : null;
      const social = Number.isFinite(at.racionesCasoSocial) ? at.racionesCasoSocial : null;
      const normalesV2 = Number.isFinite(at.racionesNormales) ? at.racionesNormales : null;
      const socialV2 = Number.isFinite(at.racionesAyudaSocial) ? at.racionesAyudaSocial : null;

      const normales = normalesV2 !== null ? normalesV2
        : (total === null ? null : (social === null ? total : total - social));
      const ayuda = socialV2 !== null ? socialV2 : social;

      if (normales !== null || ayuda !== null) {
        base.legado = { racionesNormales: normales || 0, racionesAyudaSocial: ayuda || 0 };
      }
    }
    salida.atenciones.push(base);
  }
  return salida;
}

/** Nombre completo para mostrar y ordenar. No inventa nada: solo une lo que hay. */
function nombreCompleto(afiliado) {
  return [afiliado.apellidoPaterno, afiliado.apellidoMaterno, afiliado.nombres]
    .map((p) => (p || '').trim())
    .filter(Boolean)
    .join(' ');
}

/**
 * Orden alfabético en castellano (la ñ va después de la n), para listas de
 * nombres: `lista.sort((a, b) => ORDEN_NOMBRES.compare(x, y))`. Armado una
 * vez, es varias veces más rápido que localeCompare en cada comparación.
 */
const ORDEN_NOMBRES = new Intl.Collator('es');

/** Normaliza para comparar duplicados: sin tildes, sin dobles espacios, minúsculas. */
function normalizarNombre(texto) {
  return String(texto || '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
}

// --------------------------------------------------------------- persistencia
// Toda lectura tolera ausencia y corrupción. Ante duda se arranca vacío y se
// avisa; nunca se borra en silencio lo que había.

const avisos = [];

function avisosAlmacenamiento() {
  return avisos.slice();
}

// ------------------------------------- asistencia compacta en localStorage

// Guardada como lista de marcas, la asistencia de 150 personas llenaba el
// límite de Safari (~5 MB) en unos dos años, y con 300 en uno. Por eso cada día
// se guarda como CASILLAS: una por persona, en el orden de padron.afiliados que
// va en la MISMA clave guardada, 6 personas por carácter:
//   marcas: { vino: 'texto', social: 'texto', fuera: [...] }
// vino marca a quién vino; social, a quién comió menú de ayuda social; fuera
// (rara vez) guarda tal cual las marcas de alguien que ya no está en el
// padrón. 300 personas ocupan 50 caracteres por día, no ~4.800.
//
// Es SOLO el formato en localStorage: en memoria, en los cálculos y en los
// respaldos exportados la forma sigue siendo asistencias: [{ afiliadoId,
// tipoMenu }], en el orden del padrón. leer() y guardar() traducen solos;
// leer() entiende también los dos formatos anteriores.

const MENUS = new Set(['normal', 'ayuda_social']);
const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';

/** Posiciones [0, 3, 7…] -> texto de casillas. */
function aCasillas(posiciones) {
  const grupos = [];
  for (const i of posiciones) {
    const g = Math.floor(i / 6);
    while (grupos.length <= g) grupos.push(0);
    grupos[g] |= 1 << (i % 6);
  }
  return grupos.map((n) => B64[n]).join('');
}

/** Texto de casillas -> posiciones marcadas, de menor a mayor. */
function deCasillas(texto) {
  const posiciones = [];
  for (let g = 0; g < texto.length; g += 1) {
    const n = B64.indexOf(texto[g]);
    for (let b = 0; b < 6; b += 1) if (n & (1 << b)) posiciones.push(g * 6 + b);
  }
  return posiciones;
}

// Formato anterior (solo hubo copias de desarrollo): un UUID iba como «~» y
// sus 16 bytes en base64url. Se sigue leyendo; ya no se escribe.
function idLargo(id) {
  if (typeof id !== 'string' || id[0] !== '~' || id.length !== 23) return id;
  let hex = '';
  let acumulado = 0;
  let bits = 0;
  for (const c of id.slice(1)) {
    acumulado = (acumulado << 6) | B64.indexOf(c);
    bits += 6;
    while (bits >= 4 && hex.length < 32) {
      bits -= 4;
      hex += ((acumulado >> bits) & 15).toString(16);
    }
    acumulado &= (1 << bits) - 1;
  }
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** Padrón tal como se guarda: la asistencia de cada día, en casillas. */
function empacarPadron(padron) {
  if (!padron || !Array.isArray(padron.atenciones) || !Array.isArray(padron.afiliados)) return padron;
  const posicion = new Map();
  padron.afiliados.forEach((a, i) => { if (a && !posicion.has(a.id)) posicion.set(a.id, i); });
  return {
    ...padron,
    atenciones: padron.atenciones.map((at) => {
      const lista = at && at.asistencias;
      // Lo que no tiene la forma de siempre se guarda tal cual: nunca se pierde un dato.
      if (!Array.isArray(lista) || !lista.every((x) => x && typeof x.afiliadoId === 'string' &&
        MENUS.has(x.tipoMenu) && Object.keys(x).length === 2)) return at;
      if (new Set(lista.map((x) => x.afiliadoId)).size !== lista.length) return at;
      const vino = [];
      const social = [];
      const fuera = [];
      for (const x of lista) {
        const i = posicion.get(x.afiliadoId);
        if (i === undefined) { fuera.push(x); continue; }
        vino.push(i);
        if (x.tipoMenu === 'ayuda_social') social.push(i);
      }
      const { asistencias, ...resto } = at;
      const marcas = { vino: aCasillas(vino) };
      if (social.length) marcas.social = aCasillas(social);
      if (fuera.length) marcas.fuera = fuera;
      return { ...resto, marcas };
    })
  };
}

/** El camino de vuelta de empacarPadron(). Lo que ya viene sin empacar, pasa igual. */
function desempacarPadron(padron) {
  if (!padron || !Array.isArray(padron.atenciones)) return padron;
  const afiliados = Array.isArray(padron.afiliados) ? padron.afiliados : [];
  return {
    ...padron,
    atenciones: padron.atenciones.map((at) => {
      const m = at && at.marcas;
      if (!m) return at;
      const { marcas, ...resto } = at;
      if (typeof m.vino === 'string') {
        const social = new Set(deCasillas(m.social || ''));
        const asistencias = deCasillas(m.vino)
          .filter((i) => afiliados[i])
          .map((i) => ({ afiliadoId: afiliados[i].id, tipoMenu: social.has(i) ? 'ayuda_social' : 'normal' }));
        return { ...resto, asistencias: asistencias.concat(Array.isArray(m.fuera) ? m.fuera : []) };
      }
      if (Array.isArray(m.ids)) {
        const social = new Set(m.social || []);
        return {
          ...resto,
          asistencias: m.ids.map((id) => ({
            afiliadoId: idLargo(id), tipoMenu: social.has(id) ? 'ayuda_social' : 'normal'
          }))
        };
      }
      return at;
    })
  };
}

// Safari da ~5 MB por sitio y cuenta 2 bytes por carácter: ~2,5 millones de
// caracteres. Chrome da algo más. Se mide contra lo más chico.
const LIMITE_CARACTERES = 2500000;

/**
 * Cuánto ocupa la app en localStorage: sus claves (b1.*) y sus valores.
 * @returns {{ caracteres: number, fraccion: number }}  fraccion de 0 a 1 (o más)
 */
function usoAlmacenamiento() {
  let caracteres = 0;
  try {
    for (let i = 0; i < localStorage.length; i += 1) {
      const k = localStorage.key(i);
      if (k && k.startsWith('b1.')) caracteres += k.length + (localStorage.getItem(k) || '').length;
    }
  } catch (e) { /* bloqueado: ya lo avisa leer() */ }
  return { caracteres, fraccion: caracteres / LIMITE_CARACTERES };
}

function leer(clave, porDefecto) {
  let crudo = null;
  try {
    crudo = localStorage.getItem(clave);
  } catch (e) {
    avisos.push(`No se pudo leer el almacenamiento del navegador (${clave}). ` +
      'Si estás en modo incógnito o con los datos de sitio bloqueados, nada se guardará.');
    return porDefecto;
  }
  if (crudo === null) return porDefecto;
  try {
    const valor = JSON.parse(crudo);
    if (valor === null || typeof valor !== 'object') throw new Error('forma inesperada');
    return clave === CLAVES.padron ? desempacarPadron(valor) : valor;
  } catch (e) {
    // Se conserva lo ilegible por si se puede recuperar a mano.
    try {
      localStorage.setItem(clave + '.respaldo', crudo);
    } catch (e2) { /* sin espacio: se sigue igual */ }
    avisos.push(`Los datos guardados en "${clave}" no se pudieron leer y se ` +
      `conservaron aparte en "${clave}.respaldo". Se empezó con los datos vacíos.`);
    return porDefecto;
  }
}

function guardar(clave, valor) {
  try {
    const texto = JSON.stringify(clave === CLAVES.padron ? empacarPadron(valor) : valor);
    // Si no cambió nada, no se escribe: es más rápido y no despierta a las
    // otras pestañas (evento storage) por gusto.
    if (localStorage.getItem(clave) !== texto) localStorage.setItem(clave, texto);
    return true;
  } catch (e) {
    avisos.push(`No se pudo guardar en "${clave}". Puede que el almacenamiento ` +
      'esté lleno o bloqueado. Exporta un respaldo antes de cerrar.');
    return false;
  }
}

// ------------------------------------------------------ la olla de la nube

/**
 * A qué olla de la nube está unido este teléfono (ver sincronizacion.js).
 * `juntar` dice que lo que hay en este teléfono todavía no se juntó con lo de
 * la nube: se pone al unirse y cada vez que algo se escribe aquí sin pasar por
 * la nube (recuperar una copia, recibir por QR).
 * @returns {{id: string, nombre: string, rol: string, juntar: boolean} | null}
 */
function ollaVinculada() {
  const o = leer(CLAVES.olla, null);
  return o && typeof o.id === 'string' ? o : null;
}

function vincularOlla(olla) {
  return guardar(CLAVES.olla, olla);
}

function desvincularOlla() {
  try { localStorage.removeItem(CLAVES.olla); } catch (e) { /* bloqueado: ya lo avisa leer() */ }
}

/** Lo que hay en este teléfono se juntará con la nube la próxima vez que haya señal. */
function pedirJuntar() {
  const o = ollaVinculada();
  if (o && !o.juntar) vincularOlla({ ...o, juntar: true });
}

function borrarTodo() {
  let ok = true;
  for (const clave of Object.values(CLAVES)) {
    try {
      localStorage.removeItem(clave);
      localStorage.removeItem(clave + '.respaldo');
    } catch (e) { ok = false; }
  }
  return ok;
}

// ------------------------------------------------------ restaurar un respaldo

/**
 * Revisa un respaldo antes de restaurarlo: qué trae y si tiene la forma que
 * espera la aplicación. No escribe nada.
 *
 * Solo cuentan las partes que el archivo trae de verdad: si no trae el
 * calendario, el de este dispositivo se queda como está.
 * @param {*} datos  el objeto leído del .json (ya descifrado)
 * @returns {{ error: ?string, partes: string[], resumen: string }}
 */
function revisarRespaldo(datos) {
  const sinNada = { partes: [], resumen: '' };
  if (!datos || typeof datos !== 'object' || Array.isArray(datos)) {
    return { error: 'El archivo no es un respaldo de esta aplicación.', ...sinNada };
  }
  const partes = [];
  const textos = [];
  const errores = [];
  if (datos.padron !== undefined && datos.padron !== null) {
    if (typeof datos.padron !== 'object' || !Array.isArray(datos.padron.afiliados)) {
      errores.push('el padrón no tiene la lista de personas');
    } else {
      partes.push('padron');
      const n = datos.padron.afiliados.length;
      const dias = Array.isArray(datos.padron.atenciones) ? datos.padron.atenciones.length : 0;
      textos.push(`${cuantos(n, 'persona')} en el padrón y ${cuantos(dias, 'día')} de asistencia`);
    }
  }
  if (datos.rendiciones !== undefined && datos.rendiciones !== null) {
    if (!Array.isArray(datos.rendiciones)) errores.push('las rendiciones no son una lista');
    else if (datos.rendiciones.length) {
      partes.push('rendiciones');
      textos.push(cuantos(datos.rendiciones.length, 'Formato B-1', 'Formatos B-1'));
    }
  }
  if (datos.calendario !== undefined && datos.calendario !== null) {
    if (typeof datos.calendario !== 'object' || Array.isArray(datos.calendario)) errores.push('el calendario está dañado');
    else { partes.push('calendario'); textos.push('el calendario de entregas'); }
  }
  if (datos.config !== undefined && datos.config !== null) {
    if (typeof datos.config !== 'object' || Array.isArray(datos.config)) errores.push('la configuración está dañada');
    else { partes.push('config'); textos.push('los precios del menú y datos del centro'); }
  }
  if (errores.length) {
    return { error: `El respaldo está dañado: ${errores.join('; ')}. No se cambió nada.`, ...sinNada };
  }
  if (!partes.length) {
    return { error: 'El archivo no trae datos para restaurar. No se cambió nada.', ...sinNada };
  }
  return { error: null, partes, resumen: textos.join(', ') };
}

/**
 * Escribe en este dispositivo TODAS las partes que trae el respaldo (padrón,
 * rendiciones, calendario y config), sea cual sea la página desde donde se
 * importe. Antes, cada página restauraba solo lo suyo y el resto se perdía sin
 * aviso. Si una escritura falla, lo dice: devuelve false.
 * @param {Object} datos  respaldo ya revisado con revisarRespaldo()
 * @returns {boolean}
 */
function restaurarRespaldo(datos) {
  const { error, partes } = revisarRespaldo(datos);
  if (error) return false;
  let ok = true;
  if (partes.includes('padron')) {
    ok = guardar(CLAVES.padron, migrarPadron(datos.padron)) && ok;
    pedirJuntar();
  }
  if (partes.includes('rendiciones')) ok = guardar(CLAVES.rendiciones, datos.rendiciones) && ok;
  if (partes.includes('calendario')) ok = guardar(CLAVES.calendario, datos.calendario) && ok;
  if (partes.includes('config')) ok = guardar(CLAVES.config, datos.config) && ok;
  return ok;
}

// --------------------------------------------------- respaldo con contraseña

// El respaldo lleva nombres, documentos y asistencia de personas reales. Con
// contraseña se cifra con AES-GCM (Web Crypto del navegador: sin librerías) y
// una clave derivada con PBKDF2. Sin la contraseña no hay forma de abrirlo:
// tampoco para quien la olvidó.

const ITERACIONES_PBKDF2 = 310000;   // recomendación OWASP para PBKDF2-SHA256

// Por partes: pasar un respaldo de 1 MB de una vez a fromCharCode(...) supera
// el máximo de argumentos y revienta.
function aBase64(bytes) {
  const b = new Uint8Array(bytes);
  let texto = '';
  for (let i = 0; i < b.length; i += 0x8000) texto += String.fromCharCode(...b.subarray(i, i + 0x8000));
  return btoa(texto);
}
const deBase64 = (texto) => Uint8Array.from(atob(texto), (c) => c.charCodeAt(0));

async function claveDesdeContrasena(contrasena, sal, iteraciones) {
  const base = await crypto.subtle.importKey(
    'raw', new TextEncoder().encode(contrasena), 'PBKDF2', false, ['deriveKey']);
  return crypto.subtle.deriveKey(
    { name: 'PBKDF2', hash: 'SHA-256', salt: sal, iterations: iteraciones },
    base, { name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
}

/**
 * Cifra un respaldo. Devuelve el «sobre» que se guarda como .json: dice cómo
 * se cifró (para poder abrirlo) pero no deja ver nada de adentro.
 * @param {Object} datos  el respaldo completo
 * @param {string} contrasena
 * @returns {Promise<Object>}
 */
async function cifrarRespaldo(datos, contrasena) {
  const sal = crypto.getRandomValues(new Uint8Array(16));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const clave = await claveDesdeContrasena(contrasena, sal, ITERACIONES_PBKDF2);
  const cifrado = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv }, clave, new TextEncoder().encode(JSON.stringify(datos)));
  return {
    aplicacion: 'rendicion-b1',
    cifrado: {
      algoritmo: 'AES-GCM', derivacion: 'PBKDF2-SHA256',
      iteraciones: ITERACIONES_PBKDF2, sal: aBase64(sal), iv: aBase64(iv)
    },
    datos: aBase64(cifrado)
  };
}

/** ¿Es un respaldo cifrado por cifrarRespaldo()? */
function esRespaldoCifrado(objeto) {
  return !!(objeto && objeto.cifrado && typeof objeto.datos === 'string');
}

/**
 * Abre un respaldo cifrado. Con la contraseña equivocada (o el archivo
 * alterado) AES-GCM no descifra: lanza un error, nunca devuelve basura.
 * @param {Object} sobre
 * @param {string} contrasena
 * @returns {Promise<Object>}
 */
async function descifrarRespaldo(sobre, contrasena) {
  const c = sobre.cifrado;
  const clave = await claveDesdeContrasena(contrasena, deBase64(c.sal), c.iteraciones);
  const plano = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: deBase64(c.iv) }, clave, deBase64(sobre.datos));
  return JSON.parse(new TextDecoder().decode(plano));
}


return { CLAVES, VERSION_ESQUEMA, VERSION_PADRON, VERSION_CALENDARIO, uuid, debounce, cuantos, aCentimos, formatearSoles, aNumero, hoyIso, aFecha, aIso, esIso, fechaLarga, aDdMmAa, diasEntre, MESES, nuevoEgreso, nuevaRendicion, nuevoAfiliado, nuevaAtencion, padronVacio, calendarioVacio, nuevaEntrega, configVacia, migrarPadron, nombreCompleto, ORDEN_NOMBRES, normalizarNombre, avisosAlmacenamiento, empacarPadron, desempacarPadron, usoAlmacenamiento, leer, guardar, ollaVinculada, vincularOlla, desvincularOlla, pedirJuntar, borrarTodo, revisarRespaldo, restaurarRespaldo, cifrarRespaldo, esRespaldoCifrado, descifrarRespaldo };
})();
// ---------------------------------------------------------------- calculos.js
const calculos = (() => {
// calculos.js — funciones puras. Sin DOM, sin estado, sin red.
// Todo monto en céntimos enteros.

const { aFecha, aIso, esIso, hoyIso, MESES } = modelo;

// ------------------------------------------------- Totales del Formato B-1

function totalRendicionGastos(rendicion) {
  const filas = (rendicion && rendicion.egresos) || [];
  let total = 0;
  for (const fila of filas) {
    if (Number.isFinite(fila.montoTotalCent)) total += fila.montoTotalCent;
  }
  return total;
}

/**
 * DECISIÓN ABIERTA: sin verificar.
 * min(subsidio, total) se dedujo de UN solo ejemplar llenado a mano, en el que
 * el total (S/ 209.85) superaba al subsidio (S/ 200.00). El tramo en que el
 * total es MENOR que el subsidio no tiene ninguna evidencia detrás.
 * No apoyarse en esta fórmula como si estuviera confirmada.
 */
function gastosConSubsidio(rendicion) {
  const subsidio = rendicion ? rendicion.montoSubsidioCent : null;
  if (!Number.isFinite(subsidio)) return null;
  return Math.min(subsidio, totalRendicionGastos(rendicion));
}

function aporteCentroAtencion(rendicion) {
  const subsidio = rendicion ? rendicion.montoSubsidioCent : null;
  if (!Number.isFinite(subsidio)) return null;
  return Math.max(0, totalRendicionGastos(rendicion) - subsidio);
}

/**
 * Los tres renglones del pie. Si falta el subsidio, los tres quedan en null:
 * un cero donde falta un dato es un dato inventado.
 */
function totales(rendicion) {
  const subsidio = rendicion ? rendicion.montoSubsidioCent : null;
  if (!Number.isFinite(subsidio)) {
    return { total: null, conSubsidio: null, aporte: null };
  }
  const total = totalRendicionGastos(rendicion);
  return {
    total,
    conSubsidio: Math.min(subsidio, total),
    aporte: Math.max(0, total - subsidio)
  };
}

const ORIGENES_FONDO = ['subsidio', 'ayuda_social', 'aporte_propio'];

function gastoPorOrigen(rendicion) {
  const acum = { subsidio: 0, ayuda_social: 0, aporte_propio: 0 };
  for (const fila of (rendicion && rendicion.egresos) || []) {
    if (!Number.isFinite(fila.montoTotalCent)) continue;
    const o = ORIGENES_FONDO.includes(fila.origenFondo) ? fila.origenFondo : 'subsidio';
    acum[o] += fila.montoTotalCent;
  }
  return acum;
}

/** Comprobación de fila: cantidad x precio unitario frente al monto total. */
function descuadreFila(fila) {
  if (!fila) return null;
  if (fila.cantidadVarios || fila.precioUnitarioVarios) return null;
  if (!Number.isFinite(fila.cantidad) || !Number.isFinite(fila.precioUnitarioCent)) return null;
  if (!Number.isFinite(fila.montoTotalCent)) return null;
  const esperado = Math.round(fila.cantidad * fila.precioUnitarioCent);
  const diferencia = fila.montoTotalCent - esperado;
  if (Math.abs(diferencia) <= 1) return null;
  return { esperado, declarado: fila.montoTotalCent, diferencia };
}

// --------------------------------------------------------- Padrón

/**
 * ¿Estaba activa esta persona en la fecha `ref` ('AAAA-MM-DD' válida)?
 * Una baja (activo = false con bajaEn) no toca el historial: antes de esa
 * fecha la persona sigue contando, en los reportes y en los días pasados.
 * Solo activo = false sin fecha de baja la deja fuera de todas las fechas.
 */
function activoEn(a, ref) {
  if (!a.activo && !esIso(a.bajaEn)) return false;
  if (esIso(a.altaEn) && a.altaEn > ref) return false;
  if (esIso(a.bajaEn) && a.bajaEn <= ref) return false;
  return true;
}

function afiliadosActivos(padron, fechaIso) {
  const ref = esIso(fechaIso) ? fechaIso : hoyIso();
  return ((padron && padron.afiliados) || []).filter((a) => activoEn(a, ref));
}

function edadEnFecha(fechaNacimientoIso, fechaRefIso) {
  const nac = aFecha(fechaNacimientoIso);
  const ref = aFecha(fechaRefIso);
  if (!nac || !ref) return null;
  let edad = ref.getFullYear() - nac.getFullYear();
  const cumplioEsteAno =
    ref.getMonth() > nac.getMonth() ||
    (ref.getMonth() === nac.getMonth() && ref.getDate() >= nac.getDate());
  if (!cumplioEsteAno) edad -= 1;
  return edad;
}

// Tramos de las «etapas de vida» del MINSA, Documento Técnico del Modelo de
// Cuidado Integral de Salud por Curso de Vida (RM 030-2020-MINSA):
//   Niña/niño        0 a 11 años, 11 meses y 29 días
//   Adolescente     12 a 17 años, 11 meses y 29 días
//   Adulto joven    18 a 29 años, 11 meses y 29 días
//   Adulto          30 a 59 años, 11 meses y 29 días
//   Adulto mayor    60 a más
// Aquí se usan cuatro grupos, con las mismas fronteras: «adulto» reúne al adulto
// joven y al adulto del MINSA. Si un formato municipal llegara a pedir «joven»
// aparte, se parte en 29/30 sin mover ninguna otra frontera.
const TRAMOS_ETARIOS = [
  { clave: 'nino', etiqueta: 'Niño/a', detalle: '0 a 11 años', min: 0, max: 11 },
  { clave: 'adolescente', etiqueta: 'Adolescente', detalle: '12 a 17 años', min: 12, max: 17 },
  { clave: 'adulto', etiqueta: 'Adulto/a', detalle: '18 a 59 años', min: 18, max: 59 },
  { clave: 'adulto_mayor', etiqueta: 'Adulto/a mayor', detalle: '60 a más', min: 60, max: Infinity }
];

/** Clave del tramo etario para una edad en años. null si la edad no es válida. */
function grupoPorEdad(edad) {
  if (!Number.isFinite(edad) || edad < 0) return null;
  const tramo = TRAMOS_ETARIOS.find((t) => edad >= t.min && edad <= t.max);
  return tramo ? tramo.clave : null;
}

/**
 * Se calcula SIEMPRE respecto a una fecha de referencia, nunca se congela al
 * alta: quien cumple 60 cambia de grupo sin que nadie tenga que acordarse.
 * Sin fecha de nacimiento ni grupo manual devuelve null — no se asume "adulto".
 */
function grupoEtario(afiliado, fechaRefIso) {
  if (!afiliado) return null;
  if (esIso(afiliado.fechaNacimiento)) {
    return grupoPorEdad(edadEnFecha(afiliado.fechaNacimiento, fechaRefIso || hoyIso()));
  }
  return afiliado.grupoEtarioManual || null;
}

function etiquetaGrupoEtario(clave) {
  const tramo = TRAMOS_ETARIOS.find((t) => t.clave === clave);
  return tramo ? tramo.etiqueta : 'Sin dato';
}

const TIPOS_AFILIADO = [
  { clave: 'habitual', etiqueta: 'Habitual', menu: 'normal' },
  { clave: 'caso_social', etiqueta: 'Ayuda social', menu: 'ayuda_social' }
];

/** Menú que le corresponde por defecto a una persona, según cómo se inscribió. */
function menuPorDefecto(afiliado) {
  const t = TIPOS_AFILIADO.find((x) => x.clave === (afiliado && afiliado.tipoAfiliado));
  return t ? t.menu : 'normal';
}

/** Conteo por grupo etario de los afiliados activos a una fecha. */
function resumenEtario(padron, fechaRefIso) {
  const ref = esIso(fechaRefIso) ? fechaRefIso : hoyIso();
  const conteo = { nino: 0, adolescente: 0, adulto: 0, adulto_mayor: 0, sin_dato: 0 };
  for (const a of afiliadosActivos(padron, ref)) {
    const g = grupoEtario(a, ref);
    conteo[g || 'sin_dato'] += 1;
  }
  return conteo;
}

// ------------------------------------------- Asistencia, raciones y precios
//
// Las raciones NO se escriben: se cuentan de la asistencia marcada, porque la
// comida se prepara el mismo día según quién viene. El precio sí está fijado de
// antemano y se copia a cada día nuevo.

function atencionesDelPeriodo(padron, inicioIso, finIso) {
  if (!esIso(inicioIso) || !esIso(finIso)) return [];
  return ((padron && padron.atenciones) || [])
    .filter((at) => esIso(at.fecha) && at.fecha >= inicioIso && at.fecha <= finIso);
}

/**
 * Las asistencias marcadas de un día. Es la ÚNICA puerta de lectura de
 * `atencion.asistencias`: asistio, menuDe, desgloseDelDia, asistenciaPorAfiliado
 * y validarAtencion pasan por aquí. Quienes escriben ese arreglo están en
 * ui-asistencia.js (filaToque y marcarTodos).
 *
 * Devuelve el MISMO arreglo guardado, no una copia: no mutarlo desde aquí.
 * @param {?Object} at  una atención (un día) de `padron.atenciones`
 * @returns {Array<{afiliadoId: string, tipoMenu: 'normal'|'ayuda_social'}>}
 *   vacío si `at` falta o no tiene asistencias.
 */
function asistenciasDelDia(at) {
  return Array.isArray(at && at.asistencias) ? at.asistencias : [];
}

/**
 * ¿Está marcada esta persona en este día? Lee `at.asistencias` vía
 * asistenciasDelDia. No escribe nada.
 * @param {?Object} at  una atención (un día)
 * @param {string} afiliadoId
 * @returns {boolean}
 */
function asistio(at, afiliadoId) {
  return asistenciasDelDia(at).some((x) => x.afiliadoId === afiliadoId);
}

/**
 * Las asistencias de un día con TODAS las personas de `activos` marcadas, para
 * el botón «Marcar a todos». Quien ya estaba marcado conserva el menú que se le
 * eligió ese día; los demás reciben el de su inscripción. No modifica `at`:
 * devuelve un arreglo nuevo para asignarlo a `at.asistencias`.
 * @param {?Object} at  una atención (un día)
 * @param {Object[]} activos  afiliados a marcar
 * @returns {Array<{afiliadoId: string, tipoMenu: 'normal'|'ayuda_social'}>}
 */
function asistenciasConTodos(at, activos) {
  return activos.map((p) => {
    const previo = asistenciasDelDia(at).find((x) => x.afiliadoId === p.id);
    return { afiliadoId: p.id, tipoMenu: previo ? previo.tipoMenu : menuPorDefecto(p) };
  });
}

function menuDe(at, afiliadoId) {
  const reg = asistenciasDelDia(at).find((x) => x.afiliadoId === afiliadoId);
  return reg ? reg.tipoMenu : null;
}

/**
 * Quienes comieron ese día sin estar en el padrón (de paso, un familiar): solo
 * se cuentan, por tipo de menú. Sin esto sumaban cero raciones y cero soles.
 * `at.sinInscribir` puede faltar (días anteriores a este campo): vale 0 y 0.
 * @returns {{ normal: number, ayudaSocial: number, total: number }}
 */
function sinInscribirDelDia(at) {
  const s = (at && at.sinInscribir) || {};
  const n = (v) => (Number.isInteger(v) && v > 0 ? v : 0);
  const normal = n(s.normal);
  const ayudaSocial = n(s.ayudaSocial);
  return { normal, ayudaSocial, total: normal + ayudaSocial };
}

/**
 * Raciones de un día, por tipo de menú: las de la asistencia (o las anotadas a
 * mano en la versión anterior) más las de quienes comieron sin estar inscritos.
 * `origen` dice de dónde salen las primeras; `sinInscribir`, cuántas son de
 * gente fuera del padrón.
 */
function desgloseDelDia(at) {
  const asistencias = asistenciasDelDia(at);
  const fuera = sinInscribirDelDia(at);
  if (asistencias.length === 0 && at && at.legado) {
    const n = (Number.isFinite(at.legado.racionesNormales) ? at.legado.racionesNormales : 0) + fuera.normal;
    const a = (Number.isFinite(at.legado.racionesAyudaSocial) ? at.legado.racionesAyudaSocial : 0) + fuera.ayudaSocial;
    return { normal: n, ayudaSocial: a, total: n + a, origen: 'legado', sinInscribir: fuera.total };
  }
  const normal = asistencias.filter((x) => x.tipoMenu !== 'ayuda_social').length + fuera.normal;
  const ayudaSocial = asistencias.filter((x) => x.tipoMenu === 'ayuda_social').length + fuera.ayudaSocial;
  return { normal, ayudaSocial, total: normal + ayudaSocial, origen: 'asistencia', sinInscribir: fuera.total };
}

function racionesDelDia(at) {
  return desgloseDelDia(at).total;
}

/**
 * Lo recaudado en un día: raciones x precio de su menú.
 * Devuelve null si falta el precio de un tipo de menú que SÍ tuvo raciones:
 * un total a medias es peor que ningún total.
 */
function recaudacionDelDiaCent(at) {
  if (!at) return null;
  const d = desgloseDelDia(at);
  let total = 0;
  const pares = [
    [d.normal, at.precioMenuNormalCent],
    [d.ayudaSocial, at.precioMenuAyudaSocialCent]
  ];
  for (const [raciones, precio] of pares) {
    if (raciones === 0) continue;
    if (!Number.isFinite(precio)) return null;   // falta el precio: no se estima
    total += raciones * precio;
  }
  return total;
}

function racionesPeriodo(padron, inicioIso, finIso) {
  return atencionesDelPeriodo(padron, inicioIso, finIso)
    .reduce((acc, at) => acc + racionesDelDia(at), 0);
}

function diasAtendidos(padron, inicioIso, finIso) {
  return atencionesDelPeriodo(padron, inicioIso, finIso)
    .filter((at) => racionesDelDia(at) > 0).length;
}

function promedioRaciones(padron, inicioIso, finIso) {
  const dias = diasAtendidos(padron, inicioIso, finIso);
  if (dias === 0) return null;
  return racionesPeriodo(padron, inicioIso, finIso) / dias;
}

/**
 * Resumen económico de las raciones de un periodo.
 * - precioPromedioRacionCent: promedio PONDERADO por ración, no promedio de precios.
 * - diasSinPrecio: días excluidos por falta de precio. Se muestra siempre, para
 *   que nadie lea el promedio como si cubriera todo el periodo.
 */
function resumenEconomicoRaciones(padron, inicioIso, finIso) {
  const atenciones = atencionesDelPeriodo(padron, inicioIso, finIso);
  let recaudacion = 0;
  let racionesConPrecio = 0;
  let diasSinPrecio = 0;
  let racionesNormales = 0;
  let racionesAyuda = 0;
  let diasDeLegado = 0;

  for (const at of atenciones) {
    const d = desgloseDelDia(at);
    racionesNormales += d.normal;
    racionesAyuda += d.ayudaSocial;
    if (d.origen === 'legado') diasDeLegado += 1;

    const dia = recaudacionDelDiaCent(at);
    if (dia === null) { diasSinPrecio += 1; continue; }
    recaudacion += dia;
    racionesConPrecio += d.total;
  }

  return {
    racionesTotales: racionesNormales + racionesAyuda,
    racionesNormales,
    racionesAyudaSocial: racionesAyuda,
    recaudacionCent: racionesConPrecio > 0 ? recaudacion : null,
    racionesConPrecio,
    diasSinPrecio,
    diasDeLegado,
    precioPromedioRacionCent: racionesConPrecio > 0
      ? Math.round(recaudacion / racionesConPrecio)
      : null
  };
}

function costoPorRacionCent(rendicion, padron) {
  if (!rendicion || !rendicion.periodo) return null;
  const raciones = racionesPeriodo(padron, rendicion.periodo.inicio, rendicion.periodo.fin);
  if (!raciones) return null;
  return Math.round(totalRendicionGastos(rendicion) / raciones);
}

function conteoAsistenciaDelDia(at) {
  const d = desgloseDelDia(at);
  return { total: d.total, normal: d.normal, ayuda_social: d.ayudaSocial, origen: d.origen };
}

/**
 * Días que asistió cada persona dentro de un periodo.
 * Devuelve [{ afiliadoId, dias, normal, ayudaSocial }] ordenado de más a menos.
 *
 * Lee `padron.atenciones` del periodo (atencionesDelPeriodo) y, de cada día,
 * `at.asistencias` (asistenciasDelDia). No escribe nada. Es el punto donde el
 * padrón exportado (PDF y Excel) lee lo que se marcó en la pantalla de asistencia
 * (excel-reportes.js y pdf-reportes.js, al exportar el padrón).
 *
 * Solo aparece quien asistió al menos un día: los demás no tienen fila.
 * Los días migrados con `legado` no suman, porque no dicen quién vino.
 *
 * @param {{atenciones: Object[]}} padron  normalmente estado.padron
 * @param {string} inicioIso  'AAAA-MM-DD', incluido
 * @param {string} finIso     'AAAA-MM-DD', incluido
 * @returns {Array<{afiliadoId: string, dias: number, normal: number, ayudaSocial: number}>}
 *   vacío si alguna de las dos fechas no es válida.
 */
function asistenciaPorAfiliado(padron, inicioIso, finIso) {
  const mapa = new Map();
  for (const at of atencionesDelPeriodo(padron, inicioIso, finIso)) {
    for (const a of asistenciasDelDia(at)) {
      if (!a || !a.afiliadoId) continue;
      const fila = mapa.get(a.afiliadoId) ||
        { afiliadoId: a.afiliadoId, dias: 0, normal: 0, ayudaSocial: 0 };
      fila.dias += 1;
      if (a.tipoMenu === 'ayuda_social') fila.ayudaSocial += 1; else fila.normal += 1;
      mapa.set(a.afiliadoId, fila);
    }
  }
  return [...mapa.values()].sort((a, b) => b.dias - a.dias);
}

/**
 * Personas activas del padrón que NO asistieron ni un día en el periodo.
 * `activasAIso` dice a qué fecha se mira quién está activo; por defecto, al
 * cierre del periodo. El reporte del padrón pasa su fechaRef, para que la lista
 * coincida con las filas de su tabla que tienen 0 días.
 */
function afiliadosSinAsistencia(padron, inicioIso, finIso, activasAIso = finIso) {
  const conAsistencia = new Set(
    asistenciaPorAfiliado(padron, inicioIso, finIso).map((x) => x.afiliadoId));
  return afiliadosActivos(padron, activasAIso).filter((a) => !conAsistencia.has(a.id));
}

// ------------------------------------------------- Reporte para el padrón
//
// Filas listas para copiar al formato de padrón que pide la Municipalidad.
// Solo ordena y numera datos ya ingresados: no completa ni deduce ninguno.

/**
 * Filas del reporte del padrón: personas activas a `fechaRefIso`, ordenadas por
 * apellidos y nombres, y numeradas desde 1.
 *
 * Lee `padron.afiliados` (vía afiliadosActivos). NO lee asistencias: los días
 * asistidos los agrega quien llama, con asistenciaPorAfiliado. No escribe nada.
 *
 * Edad y grupo etario se calculan a `fechaRefIso`, no se leen de lo guardado
 * (salvo `grupoEtarioManual` cuando no hay fecha de nacimiento).
 *
 * @param {{afiliados: Object[]}} padron  normalmente estado.padron
 * @param {string} [fechaRefIso]  'AAAA-MM-DD'; si no es válida, se usa hoy.
 * @returns {Array<{n: number, afiliadoId: string, apellidoPaterno: string,
 *   apellidoMaterno: string, nombres: string, tipoDocumento: string,
 *   numeroDocumento: string, grupoEtario: ?string, grupoEtarioEtiqueta: string,
 *   edad: ?number, tipoAfiliado: string, tipoAfiliadoEtiqueta: string, sexo: string}>}
 */
function filasPadron(padron, fechaRefIso) {
  const ref = esIso(fechaRefIso) ? fechaRefIso : hoyIso();
  return afiliadosActivos(padron, ref)
    .slice()
    .sort((a, b) => {
      const an = `${a.apellidoPaterno} ${a.apellidoMaterno} ${a.nombres}`.trim();
      const bn = `${b.apellidoPaterno} ${b.apellidoMaterno} ${b.nombres}`.trim();
      return an.localeCompare(bn, 'es');
    })
    .map((a, i) => {
      const g = grupoEtario(a, ref);
      const edad = esIso(a.fechaNacimiento) ? edadEnFecha(a.fechaNacimiento, ref) : null;
      return {
        n: i + 1,
        afiliadoId: a.id,
        apellidoPaterno: a.apellidoPaterno || '',
        apellidoMaterno: a.apellidoMaterno || '',
        nombres: a.nombres || '',
        tipoDocumento: (a.tipoDocumento || '').toUpperCase(),
        numeroDocumento: a.numeroDocumento || '',
        grupoEtario: g,
        grupoEtarioEtiqueta: g ? etiquetaGrupoEtario(g) : '',
        edad,
        tipoAfiliado: a.tipoAfiliado || '',
        tipoAfiliadoEtiqueta: a.tipoAfiliado === 'caso_social' ? 'Ayuda social'
          : a.tipoAfiliado === 'habitual' ? 'Habitual' : '',
        sexo: a.sexo || ''
      };
    });
}

// ----------------------------------------------------- Periodo propuesto
// La app PROPONE; la usuaria acepta o cambia. Nunca se aplica sola.

function proponerPeriodo(tipo, fechaRefIso) {
  const ref = aFecha(esIso(fechaRefIso) ? fechaRefIso : hoyIso());
  if (!ref) return { tipo, inicio: '', fin: '', etiqueta: '', alternativas: [] };

  if (tipo === 'semanal') {
    const dia = ref.getDay();                 // 0 domingo … 6 sábado
    const desplazamiento = dia === 0 ? 6 : dia - 1;  // semana de lunes a domingo
    const inicio = new Date(ref.getFullYear(), ref.getMonth(), ref.getDate() - desplazamiento);
    const fin = new Date(inicio.getFullYear(), inicio.getMonth(), inicio.getDate() + 6);
    return {
      tipo, inicio: aIso(inicio), fin: aIso(fin),
      etiqueta: 'Entrega semanal', alternativas: ['Entrega semanal']
    };
  }

  if (tipo === 'quincenal') {
    const primeraMitad = ref.getDate() <= 15;
    const inicio = new Date(ref.getFullYear(), ref.getMonth(), primeraMitad ? 1 : 16);
    const fin = primeraMitad
      ? new Date(ref.getFullYear(), ref.getMonth(), 15)
      : new Date(ref.getFullYear(), ref.getMonth() + 1, 0);
    return {
      tipo, inicio: aIso(inicio), fin: aIso(fin),
      etiqueta: 'Quincenal', alternativas: ['Quincenal']
    };
  }

  const inicio = new Date(ref.getFullYear(), ref.getMonth(), 1);
  const fin = new Date(ref.getFullYear(), ref.getMonth() + 1, 0);
  const nombreMes = MESES[ref.getMonth()];
  return {
    tipo: 'mensual', inicio: aIso(inicio), fin: aIso(fin),
    // El ejemplar llenado a mano dice "Setiembre": esa es la única evidencia
    // directa de cómo se rotula. Se ofrecen las dos y elige la usuaria.
    etiqueta: nombreMes, alternativas: [nombreMes, 'Mensual']
  };
}

function duracionPeriodo(periodo) {
  if (!periodo || !esIso(periodo.inicio) || !esIso(periodo.fin)) return null;
  const a = aFecha(periodo.inicio);
  const b = aFecha(periodo.fin);
  return Math.round((b - a) / 86400000) + 1;
}

return { totalRendicionGastos, gastosConSubsidio, aporteCentroAtencion, totales, ORIGENES_FONDO, gastoPorOrigen, descuadreFila, activoEn, afiliadosActivos, edadEnFecha, TRAMOS_ETARIOS, grupoPorEdad, grupoEtario, etiquetaGrupoEtario, TIPOS_AFILIADO, menuPorDefecto, resumenEtario, atencionesDelPeriodo, asistenciasDelDia, asistio, asistenciasConTodos, menuDe, sinInscribirDelDia, desgloseDelDia, racionesDelDia, recaudacionDelDiaCent, racionesPeriodo, diasAtendidos, promedioRaciones, resumenEconomicoRaciones, costoPorRacionCent, conteoAsistenciaDelDia, asistenciaPorAfiliado, afiliadosSinAsistencia, filasPadron, proponerPeriodo, duracionPeriodo };
})();
// ---------------------------------------------------------------- xlsx.js
const xlsx = (() => {
// xlsx.js — leer y escribir archivos de Excel (.xlsx) sin librerías.
//
// Leer: la primera hoja, para importar un padrón (leerXlsx).
// Escribir: los reportes que se exportan (armarXlsx; los arma excel-reportes.js).
//
// Antes había que abrir el Excel y «Guardar como» CSV: justo el paso donde se
// traba quien tiene poca costumbre con la computadora. Un .xlsx es un ZIP con
// XML adentro; el navegador ya trae lo necesario para abrirlo
// (DecompressionStream y DOMParser), así que no se agrega ninguna dependencia
// ni se manda nada a ningún lado.
//
// Devuelve el texto de la hoja separado por tabuladores, igual que lo que se
// pega desde Excel: de ahí en adelante, la importación es la misma de siempre.
// No lee el Excel antiguo (.xls), que es otro formato.

const firma = (v, i) => v.getUint32(i, true);

/** Las entradas del ZIP: nombre -> { metodo, inicio, largo }. */
function indiceZip(bytes) {
  const v = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  let fin = -1;
  for (let i = bytes.length - 22; i >= Math.max(0, bytes.length - 65557); i -= 1) {
    if (firma(v, i) === 0x06054b50) { fin = i; break; }
  }
  if (fin < 0) throw new Error('no es un archivo .xlsx');
  const total = v.getUint16(fin + 10, true);
  let p = v.getUint32(fin + 16, true);
  const entradas = new Map();
  const decodificar = new TextDecoder();
  for (let n = 0; n < total; n += 1) {
    if (firma(v, p) !== 0x02014b50) throw new Error('el archivo está dañado');
    const metodo = v.getUint16(p + 10, true);
    const largo = v.getUint32(p + 20, true);
    const largoNombre = v.getUint16(p + 28, true);
    const largoExtra = v.getUint16(p + 30, true);
    const largoComentario = v.getUint16(p + 32, true);
    const local = v.getUint32(p + 42, true);
    const nombre = decodificar.decode(bytes.subarray(p + 46, p + 46 + largoNombre));
    // Los datos empiezan después de la cabecera local, que tiene su propio largo de extra.
    const inicio = local + 30 + v.getUint16(local + 26, true) + v.getUint16(local + 28, true);
    entradas.set(nombre, { metodo, inicio, largo });
    p += 46 + largoNombre + largoExtra + largoComentario;
  }
  return entradas;
}

async function leerEntrada(bytes, entradas, nombre) {
  const e = entradas.get(nombre);
  if (!e) return null;
  const crudo = bytes.subarray(e.inicio, e.inicio + e.largo);
  if (e.metodo === 0) return new TextDecoder().decode(crudo);
  if (e.metodo !== 8) throw new Error('compresión no soportada');
  const flujo = new Blob([crudo]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
  return new Response(flujo).text();
}

const xml = (texto) => new DOMParser().parseFromString(texto, 'application/xml');
// Los XML de Excel usan espacios de nombres: se busca por nombre local.
const hijos = (nodo, nombre) => [...nodo.getElementsByTagNameNS('*', nombre)];

/** Fechas: los formatos de número de Excel que muestran días, meses o años. */
function estilosDeFecha(texto) {
  const fechas = new Set();
  if (!texto) return fechas;
  const doc = xml(texto);
  const propios = new Map(hijos(doc, 'numFmt').map((f) => [f.getAttribute('numFmtId'), f.getAttribute('formatCode') || '']));
  const xfs = hijos(doc, 'cellXfs')[0];
  if (!xfs) return fechas;
  hijos(xfs, 'xf').forEach((xf, i) => {
    const id = Number(xf.getAttribute('numFmtId'));
    const codigo = propios.get(String(id));
    const esFecha = (id >= 14 && id <= 22) || (id >= 45 && id <= 47) ||
      (codigo !== undefined && /[dmy]/i.test(codigo.replace(/"[^"]*"|\[[^\]]*\]/g, '')));
    if (esFecha) fechas.add(i);
  });
  return fechas;
}

/** Número de serie de Excel -> 'AAAA-MM-DD'. */
function serieAFecha(serie, desde1904) {
  const dias = Math.floor(serie) + (desde1904 ? 1462 : 0);
  const d = new Date(Date.UTC(1899, 11, 30) + dias * 86400000);
  return d.toISOString().slice(0, 10);
}

/** «B12» -> 1 (columnas desde 0). */
function columna(ref) {
  let n = 0;
  for (const c of String(ref).replace(/\d+$/, '')) n = n * 26 + (c.charCodeAt(0) - 64);
  return n - 1;
}

function celdaTsv(valor) {
  const t = String(valor).replace(/[\t\r\n]+/g, ' ').trim();
  return t.includes('"') ? `"${t.replace(/"/g, '""')}"` : t;
}

/**
 * La primera hoja de un .xlsx como texto separado por tabuladores.
 * @param {ArrayBuffer} buffer  el archivo
 * @returns {Promise<string>}
 */
async function leerXlsx(buffer) {
  const bytes = new Uint8Array(buffer);
  const entradas = indiceZip(bytes);

  const libro = xml(await leerEntrada(bytes, entradas, 'xl/workbook.xml') || '<x/>');
  const primera = hijos(libro, 'sheet')[0];
  if (!primera) throw new Error('el libro no tiene hojas');
  const desde1904 = (hijos(libro, 'workbookPr')[0] || { getAttribute: () => null }).getAttribute('date1904');
  const rid = primera.getAttributeNS('http://schemas.openxmlformats.org/officeDocument/2006/relationships', 'id') ||
    primera.getAttribute('r:id');
  let ruta = 'xl/worksheets/sheet1.xml';
  const rels = await leerEntrada(bytes, entradas, 'xl/_rels/workbook.xml.rels');
  if (rels && rid) {
    const rel = hijos(xml(rels), 'Relationship').find((r) => r.getAttribute('Id') === rid);
    if (rel) {
      const destino = rel.getAttribute('Target');
      ruta = destino.startsWith('/') ? destino.slice(1) : 'xl/' + destino.replace(/^\.\//, '');
    }
  }

  const compartidas = [];
  const textos = await leerEntrada(bytes, entradas, 'xl/sharedStrings.xml');
  if (textos) {
    for (const si of hijos(xml(textos), 'si')) {
      compartidas.push(hijos(si, 't').map((t) => t.textContent).join(''));
    }
  }
  const fechas = estilosDeFecha(await leerEntrada(bytes, entradas, 'xl/styles.xml'));

  const hoja = await leerEntrada(bytes, entradas, ruta);
  if (!hoja) throw new Error('no se encontró la primera hoja');
  // Las filas vacías se dejan como líneas vacías: así el número de fila que
  // muestra la revisión es el mismo que se ve en Excel.
  const filas = [];
  for (const fila of hijos(xml(hoja), 'row')) {
    const r = Number(fila.getAttribute('r'));
    while (Number.isInteger(r) && filas.length < r - 1) filas.push('');
    const celdas = [];
    for (const c of hijos(fila, 'c')) {
      const tipo = c.getAttribute('t');
      const v = hijos(c, 'v')[0];
      let valor = '';
      if (tipo === 's') valor = compartidas[Number(v && v.textContent)] || '';
      else if (tipo === 'inlineStr') valor = hijos(c, 't').map((t) => t.textContent).join('');
      else if (v) {
        valor = v.textContent;
        const estilo = Number(c.getAttribute('s') || 0);
        if (tipo === 'd') valor = valor.slice(0, 10);          // fecha ISO guardada como tal
        else if ((!tipo || tipo === 'n') && fechas.has(estilo) && Number.isFinite(Number(valor))) {
          valor = serieAFecha(Number(valor), desde1904 === '1' || desde1904 === 'true');
        }
      }
      const i = c.getAttribute('r') ? columna(c.getAttribute('r')) : celdas.length;
      while (celdas.length < i) celdas.push('');
      celdas[i] = celdaTsv(valor);
    }
    filas.push(celdas.join('\t'));
  }
  return filas.join('\n').replace(/\n+$/, '');
}

/** ¿Empieza como un ZIP (y por lo tanto, quizás un .xlsx)? */
function pareceXlsx(buffer) {
  const b = new Uint8Array(buffer, 0, Math.min(4, buffer.byteLength));
  return b[0] === 0x50 && b[1] === 0x4b && b[2] === 3 && b[3] === 4;
}

// ------------------------------------------------------------------ escribir
//
// Lo mínimo que Excel, Google Sheets y WPS (el del celular) abren sin quejarse:
// texto en la propia celda (sin tabla de textos compartidos), negrita y
// soles con dos decimales. El ZIP va sin comprimir: un reporte pesa pocos KB y
// así no hace falta nada asíncrono.

const TABLA_CRC = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k += 1) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
function crc32(bytes) {
  let c = 0xffffffff;
  for (const b of bytes) c = TABLA_CRC[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

/** ZIP sin compresión: [{nombre, bytes}] -> bytes. */
function armarZip(archivos) {
  const cod = new TextEncoder();
  const partes = [];
  const central = [];
  let desplazamiento = 0;
  for (const { nombre, bytes } of archivos) {
    const n = cod.encode(nombre);
    const crc = crc32(bytes);
    const local = new DataView(new ArrayBuffer(30));
    // Firma, versión 2.0, nombres en UTF-8, sin compresión, fecha 01-01-1980.
    [[0, 0x04034b50, 4], [4, 20, 2], [6, 0x0800, 2], [8, 0, 2], [10, 0, 2], [12, 33, 2],
      [14, crc, 4], [18, bytes.length, 4], [22, bytes.length, 4], [26, n.length, 2], [28, 0, 2]]
      .forEach(([i, v, t]) => (t === 4 ? local.setUint32(i, v, true) : local.setUint16(i, v, true)));
    const cabecera = new DataView(new ArrayBuffer(46));
    [[0, 0x02014b50, 4], [4, 20, 2], [6, 20, 2], [8, 0x0800, 2], [10, 0, 2], [12, 0, 2], [14, 33, 2],
      [16, crc, 4], [20, bytes.length, 4], [24, bytes.length, 4], [28, n.length, 2], [30, 0, 2], [32, 0, 2],
      [34, 0, 2], [36, 0, 2], [38, 0, 4], [42, desplazamiento, 4]]
      .forEach(([i, v, t]) => (t === 4 ? cabecera.setUint32(i, v, true) : cabecera.setUint16(i, v, true)));
    partes.push(new Uint8Array(local.buffer), n, bytes);
    central.push(new Uint8Array(cabecera.buffer), n);
    desplazamiento += 30 + n.length + bytes.length;
  }
  const largoCentral = central.reduce((s, p) => s + p.length, 0);
  const fin = new DataView(new ArrayBuffer(22));
  [[0, 0x06054b50, 4], [4, 0, 2], [6, 0, 2], [8, archivos.length, 2], [10, archivos.length, 2],
    [12, largoCentral, 4], [16, desplazamiento, 4], [20, 0, 2]]
    .forEach(([i, v, t]) => (t === 4 ? fin.setUint32(i, v, true) : fin.setUint16(i, v, true)));
  const todo = [...partes, ...central, new Uint8Array(fin.buffer)];
  const salida = new Uint8Array(todo.reduce((s, p) => s + p.length, 0));
  let i = 0;
  for (const p of todo) { salida.set(p, i); i += p.length; }
  return salida;
}

// Lo que no puede ir en un XML (caracteres de control) se quita.
const escaparXml = (t) => String(t).replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/g, '')
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

/** 0 -> A, 26 -> AA. */
function letraColumna(i) {
  let s = '';
  for (let n = i + 1; n > 0; n = Math.floor((n - 1) / 26)) s = String.fromCharCode(65 + ((n - 1) % 26)) + s;
  return s;
}

// Estilos (cellXfs): 0 normal, 1 negrita, 2 soles (0.00), 3 soles en negrita.
const ESTILOS = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n' +
  '<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">' +
  '<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>' +
  '<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>' +
  '<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>' +
  '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>' +
  '<cellXfs count="4"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>' +
  '<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>' +
  '<xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>' +
  '<xf numFmtId="2" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyNumberFormat="1"/></cellXfs>' +
  '<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>';

function celdaXml(c, ref) {
  const o = c !== null && typeof c === 'object' ? c : { v: c };
  if (o.v === null || o.v === undefined || o.v === '') return '';
  const s = (o.soles ? 2 : 0) + (o.negrita ? 1 : 0);
  const estilo = s ? ` s="${s}"` : '';
  if (typeof o.v === 'number' && Number.isFinite(o.v)) return `<c r="${ref}"${estilo}><v>${o.v}</v></c>`;
  // Todo lo demás, texto: un DNI con su 0 adelante queda como se escribió.
  return `<c r="${ref}" t="inlineStr"${estilo}><is><t xml:space="preserve">${escaparXml(o.v)}</t></is></c>`;
}

function hojaXml({ filas, anchos = [] }) {
  const cols = anchos.length
    ? `<cols>${anchos.map((a, i) => `<col min="${i + 1}" max="${i + 1}" width="${a}" customWidth="1"/>`).join('')}</cols>` : '';
  const cuerpo = filas.map((fila, f) => {
    const celdas = (fila || []).map((c, i) => celdaXml(c, letraColumna(i) + (f + 1))).join('');
    return celdas ? `<row r="${f + 1}">${celdas}</row>` : '';
  }).join('');
  return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n' +
    `<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">${cols}<sheetData>${cuerpo}</sheetData></worksheet>`;
}

/**
 * Arma un .xlsx. Cada celda es un texto, un número, vacío (null o ''), o
 * { v, negrita?, soles? } (soles: número con dos decimales).
 * @param {{nombre: string, filas: Array<Array<*>>, anchos?: number[]}[]} hojas
 *   anchos: de cada columna, en caracteres
 * @returns {Uint8Array}
 */
function armarXlsx(hojas) {
  const cod = new TextEncoder();
  // Excel no acepta nombres de hoja de más de 31 letras ni con []:*?/\
  const nombres = hojas.map((h, i) => String(h.nombre || `Hoja ${i + 1}`).replace(/[[\]:*?/\\]/g, ' ').slice(0, 31));
  const NS_R = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships';
  const archivos = [
    ['[Content_Types].xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n' +
      '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">' +
      '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>' +
      '<Default Extension="xml" ContentType="application/xml"/>' +
      '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>' +
      '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>' +
      hojas.map((_, i) => `<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>`).join('') +
      '</Types>'],
    ['_rels/.rels', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n' +
      '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
      `<Relationship Id="rId1" Type="${NS_R}/officeDocument" Target="xl/workbook.xml"/></Relationships>`],
    ['xl/workbook.xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n' +
      `<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="${NS_R}"><sheets>` +
      nombres.map((n, i) => `<sheet name="${escaparXml(n)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>`).join('') +
      '</sheets></workbook>'],
    ['xl/_rels/workbook.xml.rels', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n' +
      '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
      hojas.map((_, i) => `<Relationship Id="rId${i + 1}" Type="${NS_R}/worksheet" Target="worksheets/sheet${i + 1}.xml"/>`).join('') +
      `<Relationship Id="rId${hojas.length + 1}" Type="${NS_R}/styles" Target="styles.xml"/></Relationships>`],
    ['xl/styles.xml', ESTILOS],
    ...hojas.map((h, i) => [`xl/worksheets/sheet${i + 1}.xml`, hojaXml(h)])
  ];
  return armarZip(archivos.map(([nombre, texto]) => ({ nombre, bytes: cod.encode(texto) })));
}

return { leerXlsx, pareceXlsx, armarXlsx };
})();
// ---------------------------------------------------------------- excel-reportes.js
const excelReportes = (() => {
// excel-reportes.js — el padrón, la asistencia y el B-1 como Excel (.xlsx).
//
// Las mismas cifras que los PDF (pdf-reportes.js, pdf-b1.js), pero en celdas:
// los números son números (se pueden sumar en la hoja) y los DNI, texto (no
// pierden el 0 de adelante). Solo ordenan y suman lo que ya está guardado: no
// completan ni deducen nada. Puro: recibe los datos y devuelve
// los bytes (xlsx.js).

const { aDdMmAa, nombreCompleto, cuantos } = modelo;
const {
  filasPadron, asistenciaPorAfiliado, atencionesDelPeriodo, asistenciasDelDia, desgloseDelDia,
  recaudacionDelDiaCent, afiliadosActivos, totales, TRAMOS_ETARIOS
} = calculos;
const { armarXlsx } = xlsx;

const DIAS = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
const diaDeSemana = (iso) => { const [a, m, d] = iso.split('-').map(Number); return new Date(a, m - 1, d).getDay(); };
const negrita = (v) => ({ v, negrita: true });
const soles = (cent, conNegrita = false) => (Number.isFinite(cent) ? { v: cent / 100, soles: true, negrita: conNegrita } : '');
const periodoTexto = (inicio, fin) => `del ${aDdMmAa(inicio)} al ${aDdMmAa(fin)}`;

// ------------------------------------------------------------------ padrón

// Los mismos títulos que entiende la importación (importar.js): el archivo se
// puede volver a cargar en otro teléfono.
const CABECERA_PADRON = ['N°', 'Apellido paterno', 'Apellido materno', 'Nombres', 'Tipo de documento',
  'Número de documento', 'Grupo de edad', 'Edad', 'Tipo', 'Días asistidos'];

/**
 * Hoja «Padrón»: las personas activas a `fechaRef`, numeradas y por apellido,
 * con los días que vinieron en el periodo. Hoja «Resumen»: cuántas por grupo
 * de edad (aparte, para que la primera hoja se pueda volver a importar).
 * @param {object} padron
 * @param {{fechaRef: string, inicio: string, fin: string, centro?: string}} op
 * @returns {Uint8Array}
 */
function excelDelPadron(padron, { fechaRef, inicio, fin, centro = '' }) {
  const filas = filasPadron(padron, fechaRef);
  const dias = new Map(asistenciaPorAfiliado(padron, inicio, fin).map((x) => [x.afiliadoId, x.dias]));
  const porGrupo = Object.fromEntries(TRAMOS_ETARIOS.map((t) => [t.clave, 0]));
  let social = 0;
  let sinVenir = 0;
  for (const f of filas) {
    if (f.grupoEtario) porGrupo[f.grupoEtario] += 1;
    if (f.tipoAfiliado === 'caso_social') social += 1;
    if (!dias.get(f.afiliadoId)) sinVenir += 1;
  }
  const subtitulo = `${centro ? centro + ' · ' : ''}Activas al ${aDdMmAa(fechaRef)} · días que vinieron ${periodoTexto(inicio, fin)}`;
  return armarXlsx([
    {
      nombre: 'Padrón',
      anchos: [5, 18, 18, 22, 10, 14, 15, 6, 13, 9],
      filas: [
        [negrita('Padrón de personas')], [subtitulo], [],
        CABECERA_PADRON.map(negrita),
        ...filas.map((f) => [f.n, f.apellidoPaterno, f.apellidoMaterno, f.nombres, f.tipoDocumento, f.numeroDocumento,
          f.grupoEtarioEtiqueta, f.edad, f.tipoAfiliadoEtiqueta, dias.get(f.afiliadoId) || 0])
      ]
    },
    {
      nombre: 'Resumen',
      anchos: [34, 10],
      filas: [
        [negrita('Padrón de personas')], [subtitulo], [],
        [negrita('Total de personas activas'), negrita(filas.length)],
        ...TRAMOS_ETARIOS.map((t) => [`${t.etiqueta} (${t.detalle})`, porGrupo[t.clave]]),
        ['Ayuda social', social],
        ['No vinieron ningún día en esas fechas', sinVenir]
      ]
    }
  ]);
}

// --------------------------------------------------------------- asistencia

/**
 * Hoja «Resumen por día» (raciones por menú, sin inscribir, recaudado) y hoja
 * «Por persona» (una fila por persona y una columna por día; X: menú normal,
 * A: ayuda social). A diferencia del PDF, la planilla sale con cualquier
 * cantidad de días: la hoja no tiene ancho de papel.
 * @param {object} padron
 * @param {{inicio: string, fin: string, centro?: string}} op
 * @returns {Uint8Array}
 */
function excelDeAsistencia(padron, { inicio, fin, centro = '' }) {
  const dias = atencionesDelPeriodo(padron, inicio, fin).slice().sort((a, b) => a.fecha.localeCompare(b.fecha));
  const subtitulo = `${centro ? centro + ' · ' : ''}${periodoTexto(inicio, fin)}`;

  const suma = { normal: 0, ayuda: 0, fuera: 0, total: 0, soles: 0 };
  const filasDia = dias.map((at) => {
    const d = desgloseDelDia(at);
    const rec = recaudacionDelDiaCent(at);
    suma.normal += d.normal; suma.ayuda += d.ayudaSocial; suma.fuera += d.sinInscribir; suma.total += d.total; suma.soles += rec || 0;
    return [aDdMmAa(at.fecha), DIAS[diaDeSemana(at.fecha)], d.normal, d.ayudaSocial, d.sinInscribir, d.total,
      rec === null ? 'falta el precio' : soles(rec)];
  });
  const resumen = [
    [negrita('Asistencia')], [subtitulo], [],
    ['Fecha', 'Día', 'Menú normal', 'Ayuda social', 'Sin inscribir*', 'Raciones', 'Recaudado (S/)'].map(negrita),
    ...filasDia
  ];
  if (dias.length) {
    resumen.push([negrita('Total'), negrita(cuantos(dias.length, 'día')), negrita(suma.normal), negrita(suma.ayuda),
      negrita(suma.fuera), negrita(suma.total), soles(suma.soles, true)]);
    resumen.push([], ['* Quienes comieron sin estar en el padrón. Ya están contados en «Menú normal» y «Ayuda social».']);
  } else {
    resumen.push(['No hay días con asistencia en estas fechas.']);
  }

  const marcas = dias.map((at) => new Map(asistenciasDelDia(at).map((x) => [x.afiliadoId, x.tipoMenu])));
  const vinieron = new Set(marcas.flatMap((m) => [...m.keys()]));
  const personas = [...new Map([...afiliadosActivos(padron, fin), ...(padron.afiliados || []).filter((a) => vinieron.has(a.id))]
    .map((a) => [a.id, a])).values()]
    .sort((a, b) => nombreCompleto(a).localeCompare(nombreCompleto(b), 'es'));
  const porPersona = [
    [negrita('Asistencia por persona')], [`${subtitulo} · X: vino (menú normal) · A: vino (ayuda social)`], [],
    ['N°', 'Apellidos y nombres', 'Documento', ...dias.map((at) => `${aDdMmAa(at.fecha).slice(0, 5)} ${DIAS[diaDeSemana(at.fecha)].slice(0, 3)}`), 'Días']
      .map(negrita),
    ...personas.map((a, i) => {
      const fila = marcas.map((m) => (m.has(a.id) ? (m.get(a.id) === 'ayuda_social' ? 'A' : 'X') : ''));
      return [i + 1, nombreCompleto(a), a.numeroDocumento || '', ...fila, fila.filter(Boolean).length];
    }),
    [null, negrita('Total del día'), null, ...marcas.map((m) => negrita(m.size)), negrita(marcas.reduce((s, m) => s + m.size, 0))]
  ];

  return armarXlsx([
    { nombre: 'Resumen por día', anchos: [10, 11, 12, 12, 13, 10, 15], filas: resumen },
    { nombre: 'Por persona', anchos: [5, 34, 12, ...dias.map(() => 9), 6], filas: porPersona }
  ]);
}

// --------------------------------------------------------------------- B-1

const CANTIDAD = (e) => (e.cantidadVarios ? 'varios' : Number.isFinite(e.cantidad) ? e.cantidad : '');
const PRECIO = (e) => (e.precioUnitarioVarios ? 'varios' : soles(e.precioUnitarioCent));

/**
 * El B-1 en una hoja, en el orden del formato: encabezado, ingresos, compras
 * con sus totales y firmas. Solo lo escrito: lo que falta queda vacío.
 * @param {object} r  una rendición (modelo.js: nuevaRendicion)
 * @returns {Uint8Array}
 */
function excelDelB1(r) {
  const t = totales(r);
  const egresos = (r.egresos || []).filter((e) => e && (e.fechaCompra || String(e.descripcion || '').trim() ||
    Number.isFinite(e.montoTotalCent)));
  const centro = r.tipoCentro === 'comedor' ? 'Comedor' : r.tipoCentro === 'olla_comun' ? 'Olla común' : '';
  const p = r.periodo || {};
  return armarXlsx([{
    nombre: 'Formato B-1',
    anchos: [26, 34, 9, 11, 14, 16, 13, 14],
    filas: [
      [negrita('FORMATO B-1 (Balance de centros de atención subsidiados)')],
      ['Programa de Complementación Alimentaria - PCA · Municipalidad Distrital de Villa María del Triunfo'],
      [],
      ['Centro de atención', centro],
      ['Nombre del centro de atención', r.nombreCentro || ''],
      ['Código de PCA N°', r.codigoPca || ''],
      ['Mes de rendición', p.etiqueta || ''],
      ['Periodo', p.inicio || p.fin ? `${aDdMmAa(p.inicio)} al ${aDdMmAa(p.fin)}` : ''],
      ['Fecha de rendición', aDdMmAa(r.fechaRendicion)],
      ['Presidente(a)', r.nombrePresidenta || ''],
      ['DNI', r.dniPresidenta || ''],
      ['Celular N°', r.celular || ''],
      [],
      [negrita('INGRESOS')],
      ['Monto del subsidio (S/)', soles(r.montoSubsidioCent)],
      ['Fecha de la asignación', aDdMmAa(r.fechaAsignacion)],
      [],
      [negrita('EGRESOS')],
      ['Fecha de la compra', 'Descripción de la compra', 'Cant.', 'Unidad', 'RUC del proveedor', 'N° de boleta',
        'Precio unitario', 'Monto total (S/)'].map(negrita),
      ...egresos.map((e) => [aDdMmAa(e.fechaCompra), e.descripcion || '', CANTIDAD(e), e.unidadMedida || '',
        e.rucProveedor || '', [e.boletaSerie, e.boletaCorrelativo].filter((x) => String(x || '').trim()).join('-'),
        PRECIO(e), soles(e.montoTotalCent)]),
      [],
      ['Gastos con el subsidio económico', null, null, null, null, null, null, soles(t.conSubsidio, true)],
      // «Registrar de corresponder»: si es cero, va vacío, como en la hoja.
      ['Aporte del centro de atención', null, null, null, null, null, null, t.aporte ? soles(t.aporte, true) : ''],
      [negrita('Total de rendición de gastos'), null, null, null, null, null, null, soles(t.total, true)],
      [],
      [negrita('Presidente(a)'), r.nombrePresidentaFirma || '', null, null, negrita('DNI'), r.dniPresidentaFirma || ''],
      [negrita('Tesorero(a)'), r.nombreTesorera || '', null, null, negrita('DNI'), r.dniTesorera || ''],
      [],
      ['Copia de referencia generada con OllaConecta. No reemplaza al formato oficial firmado.']
    ]
  }]);
}

return { CABECERA_PADRON, excelDelPadron, excelDeAsistencia, excelDelB1 };
})();
