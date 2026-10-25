// Los correos a UNACEM que arma la web (armarCorreo() de
// nube/apps-script/Codigo.js, con su reportes.js tal como está en el
// repositorio) sobre el respaldo ficticio, para que EnvioTest.kt compare el
// asunto, el texto y el nombre del adjunto. El Excel ya lo compara
// DocumentosOraculoTest byte a byte.
//
//   node nativo/herramientas/correo-casos.mjs
import fs from 'node:fs';
import vm from 'node:vm';

const raiz = new URL('../../', import.meta.url);
const entorno = vm.createContext({ Logger: { log: () => {} } });
for (const archivo of ['reportes.js', 'Codigo.js']) {
  vm.runInContext(fs.readFileSync(new URL('nube/apps-script/' + archivo, raiz), 'utf8'), entorno, { filename: archivo });
}
const AJUSTES = vm.runInContext('AJUSTES', entorno);
const armarCorreo = vm.runInContext('armarCorreo', entorno);
const quincena = vm.runInContext('quincena', entorno);
const esDiaDePadron = vm.runInContext('esDiaDePadron', entorno);
// 20:15 en Lima (UTC-5).
AJUSTES.ahora = () => new Date(Date.UTC(2026, 9, 24, 1, 15));

const respaldo = JSON.parse(fs.readFileSync(new URL('nativo/nucleo/src/test/resources/respaldo-ficticio.json', raiz), 'utf8'));
const padron = { afiliados: respaldo.padron.afiliados, atenciones: respaldo.padron.atenciones };
const olla = 'Olla ficticia Los Ñandúes';
const quien = 'Rosa Quispe';

const fechas = [...new Set(padron.atenciones.map((a) => a.fecha))].sort();
const elegidas = [fechas[0], fechas[Math.floor(fechas.length / 2)], fechas[fechas.length - 1], '2026-09-15', '2026-09-30', '2026-10-31', '2024-02-29', '2026-01-01'];

const casos = [];
for (const fecha of elegidas) {
  const a = armarCorreo({ tipo: 'asistencia', fecha, inicio: fecha, fin: fecha, modo: 'manual' }, olla, padron, quien);
  casos.push({ tipo: 'asistencia', fecha, asunto: a.asunto, cuerpo: a.cuerpo, archivo: a.archivo });
  const [inicio, fin] = quincena(fecha);
  const p = armarCorreo({ tipo: 'padron', fecha, inicio, fin, modo: 'manual' }, olla, padron, quien);
  casos.push({ tipo: 'padron', fecha, inicio, fin, esDiaDePadron: esDiaDePadron(fecha), asunto: p.asunto, cuerpo: p.cuerpo, archivo: p.archivo });
}
const salida = new URL('nativo/nucleo/src/test/resources/correo-casos.json', raiz);
fs.writeFileSync(salida, JSON.stringify({ olla, quien, hora: '20:15', casos }, null, 1) + '\n');
console.log(`${casos.length} correos → ${salida.pathname}`);
