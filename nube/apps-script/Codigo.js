// El envío a UNACEM: toma las solicitudes de ollas/{olla}/envios, arma el Excel
// (reportes.js: el mismo código con el que la app exporta) y lo manda por correo
// a ajustes/envio. De noche manda solo lo que ninguna olla pidió.
//
// Corre en Google Apps Script, cada 10 minutos (instalar() lo programa), con
// la cuenta dueña del proyecto Firebase. Lee y escribe Firestore por su API
// REST con el permiso de esa cuenta: no pasa por firestore.rules y no guarda
// ninguna clave.
//
// Un envío de asistencia es un día (inicio = fin = fecha). Uno de padrón, las
// personas activas a `fecha` con los días que vinieron entre inicio y fin.

const AJUSTES = {
  proyecto: PROYECTO_FIREBASE,                   // reportes.js lo toma de firebase-config.js
  raiz: 'https://firestore.googleapis.com/v1',
  horaAutomatica: 21,                            // desde esta hora (de Lima) se manda solo lo que falte
  intentos: 3,                                   // veces que se intenta un envío que falla
  ahora: () => new Date()
};

const MIME_XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

/** Lo que corre cada 10 minutos: lo pedido y, de noche, lo que falte. */
function revisar() {
  const correo = correoDestino();
  if (!correo) { Logger.log('Falta el correo de UNACEM (ajustes/envio): no se envía nada.'); return; }
  for (const envio of pendientes()) procesar(envio, correo);
  const ahora = AJUSTES.ahora();
  if (horaLima(ahora) >= AJUSTES.horaAutomatica) {
    const hoy = fechaLima(ahora);
    for (const olla of listar('ollas')) automaticos(olla, hoy, correo);
  }
}

/** Se corre UNA vez a mano, desde el editor: deja programado revisar(). */
function instalar() {
  for (const t of ScriptApp.getProjectTriggers()) ScriptApp.deleteTrigger(t);
  ScriptApp.newTrigger('revisar').timeBased().everyMinutes(10).create();
  Logger.log('Listo: revisar() corre cada 10 minutos.');
}

function correoDestino() {
  const d = leerDoc('ajustes/envio');
  return d && typeof d.correo === 'string' ? d.correo.trim() : '';
}

function pendientes() {
  return consultar('', 'envios', [['estado', 'EQUAL', 'pendiente']], true);
}

/**
 * De noche, por cada olla: la asistencia de hoy si hubo marcas y nadie la
 * pidió; el padrón el 15 y el último día del mes, si la olla tiene a alguien
 * inscrito. Lo que ya se pidió hoy (en cualquier estado) no se repite: un
 * fallo no se reintenta desde aquí.
 */
function automaticos(olla, hoy, correo) {
  const deHoy = consultar(`ollas/${olla.id}`, 'envios', [['fecha', 'EQUAL', hoy]]);
  const ya = (tipo) => deHoy.some((e) => e.tipo === tipo);
  const pedir = (campos) => crear(`ollas/${olla.id}/envios`, {
    ...campos, fecha: hoy, modo: 'automatico', estado: 'pendiente', pedidoPor: 'script', pedidoEn: AJUSTES.ahora()
  });
  if (!ya('asistencia') && hayAsistencia(leerDoc(`ollas/${olla.id}/dias/${hoy}`))) {
    procesar(pedir({ tipo: 'asistencia', inicio: hoy, fin: hoy }), correo);
  }
  if (!ya('padron') && esDiaDePadron(hoy) && listar(`ollas/${olla.id}/personas`).length > 0) {
    const [inicio, fin] = quincena(hoy);
    procesar(pedir({ tipo: 'padron', inicio, fin }), correo);
  }
}

function hayAsistencia(dia) {
  if (!dia) return false;
  const s = dia.sinInscribir || {};
  return Object.keys(dia.marcas || {}).length > 0 || (s.normal || 0) + (s.ayudaSocial || 0) > 0;
}

/** Arma el Excel, manda el correo y anota el resultado en la solicitud. */
function procesar(envio, correo) {
  try {
    const ollaId = envio.ruta.split('/')[1];
    const olla = leerDoc(`ollas/${ollaId}`) || {};
    const nombreOlla = olla.nombre || ollaId;
    const padron = datosDeOlla(ollaId, envio.inicio, envio.fin);
    const { asunto, cuerpo, archivo, bytes } = armarCorreo(envio, nombreOlla, padron, quienPidio(ollaId, envio));
    MailApp.sendEmail({
      to: correo, subject: asunto, body: cuerpo,
      attachments: [Utilities.newBlob(aBytesDeJava(bytes), MIME_XLSX, archivo)]
    });
    actualizar(envio.ruta, { estado: 'enviado', enviadoEn: AJUSTES.ahora(), para: correo, archivo, error: null });
  } catch (e) {
    const intentos = (envio.intentos || 0) + 1;
    Logger.log(`No salió ${envio.ruta} (intento ${intentos}): ${e && e.message ? e.message : e}`);
    actualizar(envio.ruta, {
      estado: intentos >= AJUSTES.intentos ? 'fallo' : 'pendiente', intentos,
      error: String(e && e.message ? e.message : e).slice(0, 500), intentadoEn: AJUSTES.ahora()
    });
  }
}

function quienPidio(ollaId, envio) {
  if (envio.modo === 'automatico') return '';
  const m = leerDoc(`ollas/${ollaId}/miembros/${envio.pedidoPor}`);
  return m ? (m.nombre || m.correo || '') : '';
}

/**
 * El padrón y los días de la olla, en la forma que entiende excel-reportes.js
 * (la misma de `estado.padron` en la app): cada marca de `marcas` pasa a ser
 * una asistencia { afiliadoId, tipoMenu }.
 */
function datosDeOlla(ollaId, inicio, fin) {
  const sinFirma = ({ ruta, actualizadoPor, actualizadoEn, ...datos }) => datos;
  const afiliados = listar(`ollas/${ollaId}/personas`).map(sinFirma);
  const atenciones = consultar(`ollas/${ollaId}`, 'dias', [['fecha', 'GREATER_THAN_OR_EQUAL', inicio], ['fecha', 'LESS_THAN_OR_EQUAL', fin]])
    .map((d) => {
      const { marcas, ...campos } = sinFirma(d);
      const asistencias = Object.keys(marcas || {}).map((afiliadoId) => ({ afiliadoId, tipoMenu: marcas[afiliadoId].tipoMenu }));
      return { ...campos, id: campos.id || d.fecha, asistencias };
    });
  return { afiliados, atenciones };
}

function armarCorreo(envio, nombreOlla, padron, quien) {
  const { aDdMmAa, cuantos, formatearSoles } = modelo;
  const periodo = envio.inicio === envio.fin ? aDdMmAa(envio.inicio) : `del ${aDdMmAa(envio.inicio)} al ${aDdMmAa(envio.fin)}`;
  const cuando = horaTexto(AJUSTES.ahora());
  const pie = [
    '',
    envio.modo === 'automatico'
      ? `Envío automático de OllaConecta a las ${cuando}, con lo que había llegado a la nube hasta esa hora.`
      : `Enviado desde OllaConecta${quien ? ` por ${quien}` : ''} a las ${cuando}.`,
    'Si falta algo, la olla puede volver a enviar desde la app.'
  ];
  if (envio.tipo === 'asistencia') {
    const r = calculos.resumenEconomicoRaciones(padron, envio.inicio, envio.fin);
    const lineas = [
      `Asistencia de la olla ${nombreOlla}, ${periodo}.`,
      '',
      `Raciones: ${r.racionesTotales} (${r.racionesNormales} de menú normal, ${r.racionesAyudaSocial} de ayuda social).`,
      r.recaudacionCent === null ? 'Recaudado: sin precio del menú, no se calcula.' : `Recaudado: S/ ${formatearSoles(r.recaudacionCent)}.`,
      'Va adjunto el Excel con el resumen por día y la lista por persona.',
      ...pie
    ];
    return {
      asunto: `Asistencia · ${nombreOlla} · ${periodo}`,
      cuerpo: lineas.join('\n'),
      archivo: nombreArchivo(`Asistencia ${nombreOlla} ${envio.inicio}`),
      bytes: excelReportes.excelDeAsistencia(padron, { inicio: envio.inicio, fin: envio.fin, centro: nombreOlla })
    };
  }
  const filas = calculos.filasPadron(padron, envio.fecha);
  const lineas = [
    `Padrón de la olla ${nombreOlla}: ${cuantos(filas.length, 'persona activa', 'personas activas')} al ${aDdMmAa(envio.fecha)}, con los días que vinieron ${periodo}.`,
    'Va adjunto el Excel con el padrón y el resumen por grupo de edad.',
    ...pie
  ];
  return {
    asunto: `Padrón · ${nombreOlla} · al ${aDdMmAa(envio.fecha)}`,
    cuerpo: lineas.join('\n'),
    archivo: nombreArchivo(`Padron ${nombreOlla} ${envio.fecha}`),
    bytes: excelReportes.excelDelPadron(padron, { fechaRef: envio.fecha, inicio: envio.inicio, fin: envio.fin, centro: nombreOlla })
  };
}

// El mismo criterio que ui-reportes.js: letras sin tilde, números, guion y punto.
function nombreArchivo(texto) {
  return texto.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^\w.\- ]+/g, '').replace(/\s+/g, ' ').trim().slice(0, 70) + '.xlsx';
}

/** Utilities.newBlob quiere bytes con signo (-128..127), como en Java. */
function aBytesDeJava(bytes) {
  const salida = new Array(bytes.length);
  for (let i = 0; i < bytes.length; i += 1) salida[i] = bytes[i] > 127 ? bytes[i] - 256 : bytes[i];
  return salida;
}

// Perú no cambia la hora en el año: Lima es siempre UTC-5.

const LIMA_MS = -5 * 3600000;
const enLima = (d) => new Date(d.getTime() + LIMA_MS);
const dos = (n) => String(n).padStart(2, '0');
function fechaLima(d) { return enLima(d).toISOString().slice(0, 10); }
function horaLima(d) { return enLima(d).getUTCHours(); }
function horaTexto(d) { const l = enLima(d); return `${dos(l.getUTCHours())}:${dos(l.getUTCMinutes())}`; }

function esDiaDePadron(iso) {
  const [a, m, d] = iso.split('-').map(Number);
  return d === 15 || d === ultimoDia(a, m);
}
function ultimoDia(a, m) { return new Date(Date.UTC(a, m, 0)).getUTCDate(); }
/** La quincena a la que pertenece el día: del 1 al 15, o del 16 a fin de mes. */
function quincena(iso) {
  const [a, m, d] = iso.split('-').map(Number);
  const mes = iso.slice(0, 8);
  return d <= 15 ? [mes + '01', mes + '15'] : [mes + '16', mes + dos(ultimoDia(a, m))];
}

// Documentos como objetos planos; cada documento trae además `ruta`
// ('ollas/x/envios/y') e `id` (el último tramo), salvo que sus campos lo pisen.

function urlDe(camino) {
  return `${AJUSTES.raiz}/projects/${AJUSTES.proyecto}/databases/(default)/documents${camino ? '/' + camino : ''}`;
}

function pedirHttp(metodo, url, cuerpo) {
  const r = UrlFetchApp.fetch(url, {
    method: metodo, contentType: 'application/json', muteHttpExceptions: true,
    payload: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    headers: { Authorization: 'Bearer ' + ScriptApp.getOAuthToken() }
  });
  const codigo = r.getResponseCode();
  const texto = r.getContentText();
  if (codigo === 404) return null;
  if (codigo >= 300) throw new Error(`Firestore respondió ${codigo}: ${texto.slice(0, 300)}`);
  return texto ? JSON.parse(texto) : null;
}

function leerDoc(ruta) {
  const d = pedirHttp('get', urlDe(ruta));
  return d ? deDocumento(d) : null;
}

function listar(ruta) {
  const docs = [];
  let token = '';
  do {
    const r = pedirHttp('get', urlDe(ruta) + '?pageSize=300' + (token ? '&pageToken=' + encodeURIComponent(token) : ''));
    for (const d of (r && r.documents) || []) docs.push(deDocumento(d));
    token = (r && r.nextPageToken) || '';
  } while (token);
  return docs;
}

/** @param {Array<[string, string, *]>} filtros  [campo, operador de Firestore, valor] */
function consultar(padre, coleccion, filtros, enTodas = false) {
  const condiciones = filtros.map(([campo, op, valor]) => ({ fieldFilter: { field: { fieldPath: campo }, op, value: aValor(valor) } }));
  const consulta = { from: [{ collectionId: coleccion, allDescendants: enTodas }] };
  if (condiciones.length === 1) consulta.where = condiciones[0];
  else if (condiciones.length > 1) consulta.where = { compositeFilter: { op: 'AND', filters: condiciones } };
  const r = pedirHttp('post', urlDe(padre) + ':runQuery', { structuredQuery: consulta }) || [];
  return r.filter((x) => x.document).map((x) => deDocumento(x.document));
}

function crear(coleccion, campos) {
  return deDocumento(pedirHttp('post', urlDe(coleccion), { fields: aCampos(campos) }));
}

/** Cambia solo esos campos; los demás quedan como están. */
function actualizar(ruta, campos) {
  const mascara = Object.keys(campos).map((k) => 'updateMask.fieldPaths=' + encodeURIComponent(k)).join('&');
  pedirHttp('patch', urlDe(ruta) + '?' + mascara, { fields: aCampos(campos) });
}

function deDocumento(d) {
  const ruta = d.name.split('/documents/')[1];
  return { id: ruta.slice(ruta.lastIndexOf('/') + 1), ruta, ...deCampos(d.fields) };
}

function aValor(v) {
  if (v === null || v === undefined) return { nullValue: null };
  if (typeof v === 'boolean') return { booleanValue: v };
  if (typeof v === 'number') return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (typeof v === 'string') return { stringValue: v };
  if (v instanceof Date) return { timestampValue: v.toISOString() };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(aValor) } };
  return { mapValue: { fields: aCampos(v) } };
}
function aCampos(objeto) {
  const campos = {};
  for (const k of Object.keys(objeto)) if (objeto[k] !== undefined) campos[k] = aValor(objeto[k]);
  return campos;
}
function deValor(v) {
  if ('stringValue' in v) return v.stringValue;
  if ('integerValue' in v) return Number(v.integerValue);
  if ('doubleValue' in v) return v.doubleValue;
  if ('booleanValue' in v) return v.booleanValue;
  if ('timestampValue' in v) return v.timestampValue;     // texto ISO; el Excel no lo usa
  if ('arrayValue' in v) return (v.arrayValue.values || []).map(deValor);
  if ('mapValue' in v) return deCampos(v.mapValue.fields);
  return null;
}
function deCampos(campos) {
  const o = {};
  for (const k of Object.keys(campos || {})) o[k] = deValor(campos[k]);
  return o;
}
