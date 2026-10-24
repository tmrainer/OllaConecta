// qr-cruzado.mjs — cruza el QR del núcleo Kotlin con el de la WEB (módulos
// tal cual, sin tocarlos; Node 22 o más nuevo). Dos usos:
//
//   node nativo/herramientas/qr-cruzado.mjs
//     Escribe nucleo/src/test/resources/qr-cruzado.json con lo que arma la web:
//     a) matrices de qrcode-generator para textos como los de la app (partes
//        P3…, partes de solo cifras 81…, textos justo en los límites de cada
//        versión); QrCruzadoTest exige la misma matriz, módulo por módulo.
//     b) el padrón, la asistencia (un día y todos) y las entregas del respaldo
//        FICTICIO: el JSON de cada paquete y los textos de sus QR. Kotlin debe
//        armar el mismo JSON y leer esos QR.
//
//   node nativo/herramientas/qr-cruzado.mjs --verificar <partes-kotlin.json>
//     Lee con la web los QR que armó Kotlin (QrCruzadoTest los escribe en
//     nucleo/build/qr-cruzado/) y comprueba que dan el mismo paquete que la
//     web arma del respaldo. Sale con código 1 si algo no coincide.
//
// Solo datos inventados: el respaldo ficticio y textos generados.

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import qrcode from '../../web/vendor/qrcode-generator.js';
import { migrarPadron } from '../../web/js/modelo.js';
import { codificarListaQr } from '../../web/js/lista-qr.js';
import {
  codificarParaQr, leerParteQr, juntarPartesQr, paqueteDelPadron, paqueteDeAsistencia, paqueteDeEntregas
} from '../../web/js/compartir-qr.js';

const aqui = dirname(fileURLToPath(import.meta.url));
const recursos = join(aqui, '../nucleo/src/test/resources');

// El padrón como lo ve la app (igual que oraculo.mjs): migrado y con sinInscribir.
function respaldoFicticio() {
  const datos = JSON.parse(readFileSync(join(recursos, 'respaldo-ficticio.json'), 'utf8'));
  const padron = migrarPadron(datos.padron);
  const crudos = new Map((datos.padron.atenciones || []).map((at) => [at && at.id, at]));
  for (const at of padron.atenciones) {
    const c = crudos.get(at.id);
    if (c && c.sinInscribir) at.sinInscribir = c.sinInscribir;
  }
  return { padron, calendario: datos.calendario };
}

/** Los paquetes que se cruzan. Mismos nombres en QrCruzadoTest. */
function paquetes() {
  const { padron, calendario } = respaldoFicticio();
  const fechas = padron.atenciones.map((a) => a.fecha);
  return {
    padron: { tipo: 'padron', paquete: paqueteDelPadron(padron) },
    'asistencia-un-dia': { tipo: 'asistencia', paquete: paqueteDeAsistencia(padron, [fechas[fechas.length - 1]]).paquete },
    'asistencia-todo': { tipo: 'asistencia', paquete: paqueteDeAsistencia(padron, fechas).paquete },
    entregas: { tipo: 'entregas', paquete: paqueteDeEntregas(calendario) }
  };
}

/** Lo que juntarPartesQr devuelve, sin el «tipo» que agrega: comparable con el paquete. */
const sinTipo = ({ tipo, ...resto }) => resto;

/** El texto base45 de un envío, sin cabeceras: para ver si el deflate salió igual. */
const contenido = (partes) => partes.map((t) => t.slice(11, -1)).join('');

if (process.argv[2] === '--verificar') {
  const kotlin = JSON.parse(readFileSync(process.argv[3], 'utf8'));
  const web = paquetes();
  let fallas = 0;
  for (const [nombre, { tipo, paquete }] of Object.entries(web)) {
    const esperado = JSON.stringify(paquete);
    for (const clave of ['partes', 'partesChicas']) {
      const partes = kotlin[nombre] && kotlin[nombre][clave];
      if (!partes) { console.error(`${nombre}/${clave}: falta en ${process.argv[3]}`); fallas += 1; continue; }
      const vuelta = await juntarPartesQr(partes.map(leerParteQr).reverse());
      const obtenido = vuelta && vuelta.tipo === tipo ? JSON.stringify(sinTipo(vuelta)) : null;
      if (obtenido !== esperado) { console.error(`${nombre}/${clave}: la web no lee lo mismo`); fallas += 1; continue; }
      const propio = await codificarParaQr(tipo, paquete, clave === 'partesChicas' ? { porParte: 300 } : {});
      const igual = contenido(propio) === contenido(partes);
      console.log(`${nombre}/${clave}: la web lee ${partes.length} QR de Kotlin; deflate ${igual ? 'idéntico' : 'distinto (mismo contenido)'}`);
    }
  }
  if (fallas) { console.error(`${fallas} fallas`); process.exit(1); }
  console.log('OK');
  process.exit(0);
}

// ---------------------------------------------------------- a) matrices

const ALFANUM = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:';
let semilla = 12345;
const azar = () => (semilla = (semilla * 1103515245 + 12345) % 2147483648) / 2147483648;
const alfanumerico = (n) => Array.from({ length: n }, () => ALFANUM[Math.floor(azar() * 45)]).join('');
const cifras = (n) => Array.from({ length: n }, () => String(Math.floor(azar() * 10))).join('');

function matriz(texto) {
  const qr = qrcode(0, 'L');                                  // como dibujarQr() de camara-qr.js
  qr.addData(texto, /^\d+$/.test(texto) ? 'Numeric' : 'Alphanumeric');
  qr.make();
  const n = qr.getModuleCount();
  const filas = [];
  for (let f = 0; f < n; f += 1) {
    let fila = '';
    for (let c = 0; c < n; c += 1) fila += qr.isDark(f, c) ? '1' : '0';
    filas.push(fila);
  }
  return { texto, version: (n - 17) / 4, filas };
}

/** El texto más largo que entra en esa versión y uno con una letra más (ya no entra). */
function justoEnElLimite(version, generar) {
  const cabe = (n) => matriz(generar(n)).version <= version;
  let [bajo, alto] = [1, 8000];                               // cabe(bajo), no cabe(alto)
  while (alto - bajo > 1) { const medio = (bajo + alto) >> 1; if (cabe(medio)) bajo = medio; else alto = medio; }
  return [generar(bajo), generar(bajo + 1)];
}

const envios = paquetes();
const partesDe = async (nombre, opciones) => codificarParaQr(envios[nombre].tipo, envios[nombre].paquete, opciones);
const marcas = (n) => Array.from({ length: n }, (_, i) => ({ dni: String(10000000 + i * 37), menu: i % 3 ? 'normal' : 'ayuda_social' }));

const textos = [
  (await partesDe('entregas'))[0],                            // corto, alfanumérico
  (await partesDe('asistencia-un-dia'))[0],
  (await partesDe('padron'))[0],                              // ~1 400: el QR solo más grande
  ...(await partesDe('asistencia-todo')).slice(0, 2),         // cuadros animados (≤ 900)
  codificarListaQr({ fecha: '2026-10-12', marcas: marcas(1) })[0],   // solo cifras, chico
  codificarListaQr({ fecha: '2026-10-12', marcas: marcas(80) })[0],  // solo cifras, parte llena
  ...justoEnElLimite(1, alfanumerico),                        // fin de la versión 1 y comienzo de la 2
  ...justoEnElLimite(6, alfanumerico),                        // de la 6 a la 7 (lleva información de versión)
  ...justoEnElLimite(9, alfanumerico),                        // de la 9 a la 10 (cambia el largo del contador)
  ...justoEnElLimite(26, alfanumerico),                       // de la 26 a la 27 (otra vez)
  ...justoEnElLimite(1, cifras),                              // solo cifras: de la 1 a la 2
  ...justoEnElLimite(9, cifras),
  alfanumerico(1700),                                         // la versión 24 que todavía lee un moto g23
  alfanumerico(4296),                                         // lo máximo alfanumérico (versión 40)
  cifras(7089)                                                // lo máximo en cifras (versión 40)
];
const matrices = textos.map(matriz);

// --------------------------------------------------------- b) envíos

const salidaEnvios = {};
for (const [nombre, { tipo, paquete }] of Object.entries(envios)) {
  salidaEnvios[nombre] = {
    tipo,
    json: JSON.stringify(paquete),
    partes: await partesDe(nombre),
    partesChicas: await partesDe(nombre, { porParte: 300 })
  };
  const vuelta = await juntarPartesQr(salidaEnvios[nombre].partes.map(leerParteQr));
  if (JSON.stringify(sinTipo(vuelta)) !== salidaEnvios[nombre].json) throw new Error(`${nombre}: la web no lee lo suyo`);
}

const destino = join(recursos, 'qr-cruzado.json');
writeFileSync(destino, JSON.stringify({ matrices, envios: salidaEnvios }) + '\n');
console.log(`Escrito ${destino}: ${matrices.length} matrices (versiones ${matrices.map((m) => m.version).join(', ')}), ` +
  Object.entries(salidaEnvios).map(([n, e]) => `${n} ${e.partes.length}+${e.partesChicas.length} QR`).join(', '));
