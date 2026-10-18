// pruebas-envio.mjs — prueba el script de Apps Script (apps-script/Codigo.js
// con reportes.js recién armado) contra el emulador de Firestore.
//
//   npm run pruebas      (levanta el emulador, corre esto y lo apaga)
//
// El script corre tal cual, en un contexto aislado donde lo que Apps Script
// da (UrlFetchApp, MailApp, Utilities, ScriptApp, Logger) está simulado:
// las llamadas HTTP van de verdad al emulador (con curl, porque en Apps
// Script son síncronas) y los correos se guardan en una lista. Un teléfono
// («ana», con las reglas de verdad) pide envíos; quien coordina pone el
// correo. El reloj se mueve a mano para probar la noche y el día 15.

import fs from 'node:fs';
import vm from 'node:vm';
import { spawnSync } from 'node:child_process';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import * as Fs from 'firebase/firestore';

Fs.setLogLevel('silent');

// ---- el script, recién armado
const armado = spawnSync(process.execPath, ['apps-script/armar.mjs'], { cwd: new URL('.', import.meta.url), encoding: 'utf8' });
if (armado.status !== 0) { console.error(armado.stderr); process.exit(1); }

const correos = [];
const registro = [];
let fallosDeCorreo = 0;
const entorno = vm.createContext({
  Logger: { log: (t) => registro.push(String(t)) },
  UrlFetchApp: { fetch: pedirConCurl },
  ScriptApp: { getOAuthToken: () => 'owner', getProjectTriggers: () => [], deleteTrigger: () => {}, newTrigger: () => ({ timeBased: () => ({ everyMinutes: () => ({ create: () => {} }) }) }) },
  MailApp: { sendEmail: (m) => { if (fallosDeCorreo > 0) { fallosDeCorreo -= 1; throw new Error('Gmail no responde'); } correos.push(m); } },
  Utilities: { newBlob: (datos, tipo, nombre) => ({ bytes: Uint8Array.from(datos, (b) => b & 255), tipo, nombre }) }
});
for (const archivo of ['reportes.js', 'Codigo.js']) {
  vm.runInContext(fs.readFileSync(new URL('./apps-script/' + archivo, import.meta.url), 'utf8'), entorno, { filename: archivo });
}
const AJUSTES = vm.runInContext('AJUSTES', entorno);
const revisar = vm.runInContext('revisar', entorno);
AJUSTES.proyecto = 'demo-ollaconecta';
AJUSTES.raiz = 'http://127.0.0.1:8080/v1';
let reloj = new Date();
AJUSTES.ahora = () => reloj;

/** UrlFetchApp.fetch es síncrono: curl lo es. */
function pedirConCurl(url, { method = 'get', payload, headers = {} }) {
  const args = ['-s', '-S', '-X', method.toUpperCase(), '-w', '\n%{http_code}', '-H', 'Content-Type: application/json'];
  for (const [k, v] of Object.entries(headers)) args.push('-H', `${k}: ${v}`);
  if (payload !== undefined) args.push('--data-binary', '@-');
  args.push(url);
  const r = spawnSync('curl', args, { input: payload === undefined ? '' : payload, encoding: 'utf8' });
  if (r.status !== 0) throw new Error('curl: ' + r.stderr);
  const corte = r.stdout.lastIndexOf('\n');
  return { getResponseCode: () => Number(r.stdout.slice(corte + 1)), getContentText: () => r.stdout.slice(0, corte) };
}

// ---- la nube
const pruebas = await initializeTestEnvironment({
  projectId: 'demo-ollaconecta',
  firestore: { rules: fs.readFileSync(new URL('./firestore.rules', import.meta.url), 'utf8'), host: '127.0.0.1', port: 8080 }
});
// Recién apagada la prueba anterior, el emulador puede estar cancelando sus
// escuchas y rechazar la limpieza («call already cancelled»): se insiste.
for (let intento = 1; ; intento += 1) {
  try { await pruebas.clearFirestore(); break; } catch (e) {
    if (intento === 5) throw e;
    await new Promise((r) => setTimeout(r, 500));
  }
}
const cuenta = (uid) => pruebas.authenticatedContext(uid, { email: `${uid}@gmail.com`, email_verified: true }).firestore();
const ana = cuenta('ana');
const coordina = cuenta('coordina');
const firma = (uid) => ({ actualizadoPor: uid, actualizadoEn: Fs.serverTimestamp() });
const marca = (tipoMenu) => ({ tipoMenu, por: 'ana', en: Fs.serverTimestamp() });

await pruebas.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await Fs.setDoc(Fs.doc(db, 'coordinadores/coordina'), { desde: Fs.serverTimestamp() });
  await Fs.setDoc(Fs.doc(db, 'ollas/flores'), { nombre: 'Las Flores', distrito: 'VMT', creadaEn: Fs.serverTimestamp(), creadaPor: 'coordina' });
  await Fs.setDoc(Fs.doc(db, 'ollas/pinos'), { nombre: 'Los Pinos', distrito: 'VMT', creadaEn: Fs.serverTimestamp(), creadaPor: 'coordina' });
  await Fs.setDoc(Fs.doc(db, 'ollas/flores/miembros/ana'), { uid: 'ana', nombre: 'Ana Pérez', correo: 'ana@gmail.com', rol: 'presidenta', desde: Fs.serverTimestamp(), agregadoPor: 'coordina' });
  const persona = (id, nombres, apellidoPaterno) => ({ id, nombres, apellidoPaterno, apellidoMaterno: '', tipoDocumento: 'dni', numeroDocumento: '0000000' + id.slice(-1), fechaNacimiento: '1980-05-05', tipoAfiliado: 'habitual', activo: true, altaEn: '2026-01-01', ...firma('ana') });
  await Fs.setDoc(Fs.doc(db, 'ollas/flores/personas/p1'), persona('p1', 'Rosa', 'Quispe'));
  await Fs.setDoc(Fs.doc(db, 'ollas/flores/personas/p2'), persona('p2', 'Luz', 'Mamani'));
  const dia = (fecha, marcas, extra = {}) => ({ id: 'd-' + fecha, fecha, precioMenuNormalCent: 300, precioMenuAyudaSocialCent: 100, nota: '', marcas, ...extra, ...firma('ana') });
  await Fs.setDoc(Fs.doc(db, 'ollas/flores/dias/2026-10-14'), dia('2026-10-14', { p1: marca('normal') }));
  await Fs.setDoc(Fs.doc(db, 'ollas/flores/dias/2026-10-15'), dia('2026-10-15', { p1: marca('normal'), p2: marca('ayuda_social') }));
  await Fs.setDoc(Fs.doc(db, 'ollas/flores/dias/2026-10-17'), dia('2026-10-17', {}, { sinInscribir: { normal: 2, ayudaSocial: 0 } }));
});

// ---- ayudas
let pasan = 0; const fallos = [];
function comprobar(nombre, ok, detalle = '') {
  if (ok) { pasan++; console.log(`  ✓ ${nombre}`); } else { fallos.push(nombre); console.log(`  ✗ ${nombre}${detalle ? `\n    ${detalle}` : ''}`); }
}
/** Hora de Lima (UTC-5) de ese día, como Date. */
const lima = (iso, hh, mm = 0) => new Date(Date.parse(iso + 'T00:00:00Z') + (hh + 5) * 3600000 + mm * 60000);
const pedir = (tipo, fecha, inicio = fecha, fin = fecha) => Fs.addDoc(Fs.collection(ana, 'ollas/flores/envios'),
  { tipo, fecha, inicio, fin, modo: 'manual', estado: 'pendiente', pedidoPor: 'ana', pedidoEn: Fs.serverTimestamp() });
const envios = async (fecha) => (await Fs.getDocs(Fs.query(Fs.collection(ana, 'ollas/flores/envios'), Fs.where('fecha', '==', fecha)))).docs
  .map((d) => d.data()).sort((a, b) => a.tipo.localeCompare(b.tipo));
/** Las entradas del ZIP sin comprimir que arma xlsx.js: nombre -> texto. */
function entradasZip(b) {
  const v = new DataView(b.buffer, b.byteOffset, b.byteLength);
  const m = new Map();
  let p = 0;
  while (p + 30 <= b.length && v.getUint32(p, true) === 0x04034b50) {
    const n = v.getUint16(p + 26, true); const e = v.getUint16(p + 28, true); const tam = v.getUint32(p + 18, true);
    m.set(new TextDecoder().decode(b.subarray(p + 30, p + 30 + n)), new TextDecoder().decode(b.subarray(p + 30 + n + e, p + 30 + n + e + tam)));
    p += 30 + n + e + tam;
  }
  return m;
}
const ultimo = () => correos[correos.length - 1];
const hoja = (correo, n = 1) => entradasZip(correo.attachments[0].bytes).get(`xl/worksheets/sheet${n}.xml`) || '';

// ============================================================ 1. sin correo
console.log('Sin correo de UNACEM no sale nada');
reloj = lima('2026-10-14', 10);
await pedir('asistencia', '2026-10-14');
revisar();
comprobar('no se mandó ningún correo', correos.length === 0);
comprobar('la solicitud sigue pendiente', (await envios('2026-10-14'))[0].estado === 'pendiente');
comprobar('y el registro dice por qué', registro.some((t) => t.includes('Falta el correo')));

// ============================================================ 2. manual
console.log('Quien coordina pone el correo; lo pedido desde la app sale');
await Fs.setDoc(Fs.doc(coordina, 'ajustes/envio'), { correo: 'prueba@gmail.com', ...firma('coordina') });
revisar();
comprobar('salió un correo, al correo configurado', correos.length === 1 && ultimo().to === 'prueba@gmail.com', JSON.stringify(ultimo() && ultimo().to));
comprobar('con el asunto de la asistencia del día', ultimo().subject === 'Asistencia · Las Flores · 14-10-26', ultimo().subject);
comprobar('dice quién lo mandó y cuántas raciones', ultimo().body.includes('por Ana Pérez') && ultimo().body.includes('Raciones: 1 (1 de menú normal, 0 de ayuda social)'), ultimo().body);
comprobar('y lo recaudado', ultimo().body.includes('Recaudado: S/ 3.00'), ultimo().body);
const adjunto = ultimo().attachments[0];
comprobar('el adjunto es un .xlsx con el nombre de la olla', adjunto.nombre === 'Asistencia Las Flores 2026-10-14.xlsx' && adjunto.tipo.includes('spreadsheet') && adjunto.bytes[0] === 0x50 && adjunto.bytes[1] === 0x4b, adjunto.nombre);
comprobar('la hoja «Resumen por día» trae el día con su ración', hoja(ultimo()).includes('14-10-26') && hoja(ultimo()).includes('Las Flores'));
comprobar('la hoja «Por persona» trae a Rosa marcada y a Luz sin marca', /Quispe Rosa/.test(hoja(ultimo(), 2)) && /Mamani Luz/.test(hoja(ultimo(), 2)));
let e = (await envios('2026-10-14'))[0];
comprobar('la solicitud quedó «enviado», con a quién y el archivo', e.estado === 'enviado' && e.para === 'prueba@gmail.com' && e.archivo === adjunto.nombre && e.enviadoEn, JSON.stringify(e));
revisar();
comprobar('volver a revisar no la manda de nuevo', correos.length === 1);

// ============================================================ 3. la noche
console.log('De noche sale solo lo que nadie pidió');
reloj = lima('2026-10-14', 21, 30);
revisar();
comprobar('la asistencia de hoy ya se había pedido: no se repite', correos.length === 1);
comprobar('y el 14 no es día de padrón', (await envios('2026-10-14')).length === 1);

reloj = lima('2026-10-15', 21, 30);
revisar();
comprobar('el 15: salen la asistencia del día y el padrón de la quincena', correos.length === 3, String(correos.length));
const [asis, padr] = correos.slice(1).sort((a, b) => a.subject.localeCompare(b.subject));
comprobar('la asistencia dice que fue automática', asis.subject === 'Asistencia · Las Flores · 15-10-26' && asis.body.includes('Envío automático') && asis.body.includes('21:30'), asis.body);
comprobar('con las dos marcas', asis.body.includes('Raciones: 2 (1 de menú normal, 1 de ayuda social)'), asis.body);
comprobar('el padrón va al 15 con los días del 1 al 15', padr.subject === 'Padrón · Las Flores · al 15-10-26' && padr.body.includes('2 personas activas') && padr.body.includes('del 01-10-26 al 15-10-26'), padr.body);
comprobar('el Excel del padrón trae a las dos con sus días', /Mamani/.test(hoja(padr)) && /Quispe/.test(hoja(padr)) && padr.attachments[0].nombre === 'Padron Las Flores 2026-10-15.xlsx');
const del15 = await envios('2026-10-15');
comprobar('quedaron dos solicitudes automáticas, enviadas', del15.length === 2 && del15.every((x) => x.modo === 'automatico' && x.estado === 'enviado' && x.pedidoPor === 'script'), JSON.stringify(del15));
reloj = lima('2026-10-15', 21, 40);
revisar();
comprobar('diez minutos después no se repite nada', correos.length === 3);
comprobar('la otra olla, sin datos, no manda nada', !correos.some((c) => c.subject.includes('Los Pinos')));

reloj = lima('2026-10-16', 21, 30);
revisar();
comprobar('un día sin marcas no manda asistencia', correos.length === 3 && (await envios('2026-10-16')).length === 0);

reloj = lima('2026-10-17', 21, 30);
revisar();
comprobar('un día con gente sin inscribir sí la manda', correos.length === 4 && ultimo().body.includes('Raciones: 2'), ultimo().body);
comprobar('y la hoja los cuenta en «Sin inscribir»', hoja(ultimo()).includes('Sin inscribir'));

// ============================================================ 4. fallos
console.log('Si el correo no sale, se reintenta y después se anota el fallo');
reloj = lima('2026-10-16', 11);
await pedir('padron', '2026-10-16', '2026-10-01', '2026-10-16');
fallosDeCorreo = 3;
revisar();
e = (await envios('2026-10-16'))[0];
comprobar('al primer fallo sigue pendiente, con el error anotado', e.estado === 'pendiente' && e.intentos === 1 && e.error === 'Gmail no responde', JSON.stringify(e));
revisar(); revisar();
e = (await envios('2026-10-16'))[0];
comprobar('al tercero queda en «fallo»', e.estado === 'fallo' && e.intentos === 3, JSON.stringify(e));
revisar();
comprobar('y no se vuelve a intentar', correos.length === 4 && (await envios('2026-10-16'))[0].intentos === 3);
comprobar('un fallo se anota en el registro', registro.some((t) => t.includes('No salió') && t.includes('Gmail no responde')));

await pruebas.cleanup();
console.log(fallos.length ? `\n✗ ${fallos.length} fallan, ${pasan} pasan` : `\n✓ ${pasan} de ${pasan} pasan`);
process.exit(fallos.length ? 1 : 0);
