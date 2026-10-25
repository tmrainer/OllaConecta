// Corre web/js/escaneo-dni.js (tal cual, sin tocarlo) sobre entradas SINTÉTICAS y
// guarda entradas y salidas en nucleo/src/test/resources/escaneo-dni.esperado.json.
// EscaneoDniWebTest.kt las vuelve a calcular con EscaneoDni.kt y compara una por una.
//
//   node nativo/herramientas/escaneo-dni-casos.mjs        (Node 22 o más nuevo)
//
// Todo es inventado: DNI 0000xxxx, nunca un DNI real. Las MRZ llevan sus dígitos de
// control (calculados aquí, aparte del módulo) y después se «ensucian» como las lee
// el reconocimiento de texto. Es determinista: la misma semilla da el mismo archivo.

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  parsearCadenaDni, digitoControlIcao, leerMrzTd1, buscarMrzTd1, unirFilasOcr, combinarMrzYAnverso,
  normalizarLetras, leerPdf417DniAzul, plantillaAnonima
} from '../../web/js/escaneo-dni.js';

const aqui = dirname(fileURLToPath(import.meta.url));
const destino = join(aqui, '..', 'nucleo', 'src', 'test', 'resources', 'escaneo-dni.esperado.json');

// Azar reproducible (LCG).
let semilla = 20261024;
const azar = () => (semilla = (semilla * 1664525 + 1013904223) % 4294967296) / 4294967296;
const entre = (a, b) => a + Math.floor(azar() * (b - a + 1));
const elegir = (lista) => lista[entre(0, lista.length - 1)];

/** Dígito de control ICAO 9303-3 §4.9, escrito aparte del módulo que se prueba. */
function dc(texto) {
  let suma = 0;
  for (let i = 0; i < texto.length; i += 1) {
    const c = texto[i];
    const v = c === '<' ? 0 : /[0-9]/.test(c) ? Number(c) : c.charCodeAt(0) - 55;
    suma += v * [7, 3, 1][i % 3];
  }
  return String(suma % 10);
}
const rellenar = (s, n) => (s + '<'.repeat(n)).slice(0, n);
const sinTildes = (s) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toUpperCase();

const APELLIDOS = ['QUISPE', 'MAMANI', 'HUAMÁN', 'FLORES', 'ROJAS', 'VILLANUEVA', 'NÚÑEZ', 'PEÑA', 'MUÑOZ',
  'CÁCERES', 'ÁLVAREZ', 'CHÁVEZ', 'TICONA', 'CONDORI', 'GUTIÉRREZ', 'SÁNCHEZ', 'ESPINOZA', 'QUIÑONES', 'DE LA CRUZ'];
const NOMBRES = ['JOSÉ', 'MARÍA', 'ROSA', 'JUAN', 'ANA', 'LUIS', 'CARMEN', 'JESÚS', 'NOEMÍ', 'ÁNGEL', 'SOFÍA',
  'RAÚL', 'INÉS', 'PEDRO', 'ELENA', 'BENITO', 'GLADYS', 'ÓSCAR', 'TERESA', 'SAÚL', 'ZOILA', 'IBETH'];

/** Una persona inventada, con su MRZ TD1 bien formada. */
function persona() {
  const paterno = elegir(APELLIDOS);
  let materno = elegir(APELLIDOS);
  if (materno === paterno) materno = 'TORRES';
  const nombres = entre(1, 2) === 1 ? [elegir(NOMBRES)] : [elegir(NOMBRES), elegir(NOMBRES)];
  const dni = '0000' + String(entre(0, 9999)).padStart(4, '0');
  const nac = `${String(entre(30, 99)).padStart(2, '0')}${String(entre(1, 12)).padStart(2, '0')}${String(entre(1, 28)).padStart(2, '0')}`;
  const vence = `${String(entre(26, 35)).padStart(2, '0')}${String(entre(1, 12)).padStart(2, '0')}${String(entre(1, 28)).padStart(2, '0')}`;
  const sexo = elegir(['M', 'F']);
  return { paterno, materno, nombres, mrz: armarMrz({ pais: 'PER', numero: dni, nac, sexo, vence, apellidos: [paterno], nombres }) };
}

function armarMrz({ pais, numero, nac, sexo, vence, apellidos, nombres }) {
  const num9 = rellenar(numero, 9);
  const l1 = rellenar('I<' + pais + num9 + dc(num9), 30);
  const l2sin = nac + dc(nac) + sexo + vence + dc(vence) + pais + '<'.repeat(11);
  const general = l1.slice(5, 30) + l2sin.slice(0, 7) + l2sin.slice(8, 15) + l2sin.slice(18, 29);
  const l2 = l2sin + dc(general);
  const nombre = (lista) => lista.map((x) => sinTildes(x).replace(/[^A-Z]+/g, '<')).join('<');
  const l3 = rellenar(nombre(apellidos) + '<<' + nombre(nombres), 30);
  return [l1, l2, l3];
}

const A_LETRA_OCR = { 0: ['O', 'Q', 'D'], 1: ['I', 'L'], 2: ['Z'], 5: ['S'], 6: ['G'], 8: ['B'] };
const A_CIFRA_OCR = { O: '0', I: '1', Z: '2', S: '5', G: '6', B: '8' };

function ensuciar(lineas, nivel) {
  return lineas.map((l, n) => {
    let s = [...l].map((c) => {
      if (azar() > nivel) return c;
      if (A_LETRA_OCR[c]) return elegir(A_LETRA_OCR[c]);   // una cifra leída como letra
      if (A_CIFRA_OCR[c]) return A_CIFRA_OCR[c];           // una letra leída como cifra
      return c;
    }).join('');
    if (azar() < nivel) s = s.replace(/<{3,}/, (m) => m.slice(entre(1, m.length - 1)));   // se come «<»
    if (azar() < nivel) s = s.replace('<<', '«');
    if (azar() < nivel / 2) { const i = entre(1, s.length - 1); s = s.slice(0, i) + ' ' + s.slice(i); }
    if (azar() < nivel / 3) { const i = entre(1, s.length - 1); s = s.slice(0, i) + elegir(['.', ',', '‘', '·']) + s.slice(i); }
    if (azar() < nivel / 3) s = s.toLowerCase();
    if (n === 1 && azar() < nivel / 3) s += '<<';
    return s;
  });
}

/** Cambia un dígito de control (posición de la línea 1 o 2) por otro. */
function controlMalo(lineas, linea, pos) {
  const l = lineas[linea];
  const otro = String((Number(l[pos]) + entre(1, 9)) % 10);
  const r = lineas.slice();
  r[linea] = l.slice(0, pos) + otro + l.slice(pos + 1);
  return r;
}

/** Las filas como fragmentos de ML Kit: partidas, inclinadas y en desorden. */
function aFragmentos(filas, { angulo = 0, partir = true, conCaja = true } = {}) {
  const pendiente = Math.tan((angulo * Math.PI) / 180);
  const fragmentos = [];
  filas.forEach((fila, n) => {
    const y0 = 60 + n * 110;
    const piezas = partir && fila.length > 12 && azar() < 0.6
      ? (() => { const i = entre(4, fila.length - 4); return [[0, fila.slice(0, i)], [i, fila.slice(i)]]; })()
      : [[0, fila]];
    for (const [desde, texto] of piezas) {
      const izquierda = 40 + desde * 30 + (desde ? entre(5, 60) : 0);
      const derecha = izquierda + texto.length * 30;
      const dy = Math.round(pendiente * (izquierda + derecha) / 2) + entre(-3, 3);
      const f = { texto, arriba: y0 + dy, abajo: y0 + 50 + dy, izquierda };
      if (conCaja) { f.derecha = derecha; f.angulo = angulo; }
      fragmentos.push(f);
    }
  });
  for (let i = fragmentos.length - 1; i > 0; i -= 1) {
    const j = entre(0, i);
    [fragmentos[i], fragmentos[j]] = [fragmentos[j], fragmentos[i]];
  }
  return fragmentos;
}

const CONFUNDIR = { B: 'S', S: 'B', O: '0', N: 'M', M: 'N', E: 'F', C: 'G', V: 'Y', I: 'L', R: 'P' };

function anverso(p, variante) {
  const conTilde = (s) => (variante === 'sin-tildes' ? sinTildes(s) : s);
  const caso = (s) => (variante === 'minusculas' ? s.toLowerCase() : s);
  const filas = ['REPÚBLICA DEL PERÚ', 'DOCUMENTO NACIONAL DE IDENTIDAD', 'DNI ' + p.mrz[0].slice(5, 13)];
  const pat = conTilde(p.paterno);
  const mat = conTilde(p.materno);
  const nom = conTilde(p.nombres.join(' '));
  if (variante === 'misma-fila') {
    filas.push(`Primer Apellido ${pat}`, `Segundo Apellido ${mat}`, `Pre Nombres ${nom}`);
  } else if (variante === 'fecha-en-medio') {
    filas.push('Primer Apellido', pat, 'Segundo Apellido', 'Fecha de Emisión', mat + ' 12/03/2015', 'Prenombres', nom);
  } else {
    filas.push('Primer Apellido', pat, 'Segundo Apellido', mat, 'Pre Nombres', nom);
  }
  filas.push('Sexo ' + p.mrz[1][7], 'Fecha de Nacimiento', 'Estado Civil S', 'UBIGEO');
  return filas.map(caso);
}

/** Una letra confundible cambiada en el texto (para los casos «dudoso»). */
function confundirUna(texto) {
  const posiciones = [...texto].map((c, i) => (CONFUNDIR[c] ? i : -1)).filter((i) => i >= 0);
  if (!posiciones.length) return texto;
  const i = elegir(posiciones);
  return texto.slice(0, i) + CONFUNDIR[texto[i]] + texto.slice(i + 1);
}

const casos = [];
const caso = (funcion, entrada, salida) => casos.push({ funcion, entrada, salida: salida === undefined ? null : salida });
const FECHAS = ['2026-10-24', '2026-01-01', '2031-06-15', '1999-12-31'];

// Dígito de control.
for (const t of ['', '<', 'L898902C3', '740812', '120415', 'D23145890', '00001234', 'ABCXYZ<<<', 'ñ', 'a', 'Ü', '12 3']) {
  caso('digitoControlIcao', { texto: t }, digitoControlIcao(t));
}

// MRZ bien formadas, con todos los controles, y cada control roto por separado.
const personas = Array.from({ length: 30 }, persona);
const ICAO = ['I<UTOD231458907<<<<<<<<<<<<<<<', '7408122F1204159UTO<<<<<<<<<<<6', 'ERIKSSON<<ANNA<MARIA<<<<<<<<<<'];
for (const fechaRef of FECHAS) caso('leerMrzTd1', { lineas: ICAO, fechaRef }, leerMrzTd1(ICAO, fechaRef));
for (const p of personas) {
  const fechaRef = elegir(FECHAS);
  caso('leerMrzTd1', { lineas: p.mrz, fechaRef }, leerMrzTd1(p.mrz, fechaRef));
  for (const [linea, pos] of [[0, 14], [1, 6], [1, 14], [1, 29]]) {
    const malo = controlMalo(p.mrz, linea, pos);
    caso('leerMrzTd1', { lineas: malo, fechaRef }, leerMrzTd1(malo, fechaRef));
  }
}
// Fechas imposibles con su control bien puesto, siglos, sexo raro, nombres raros.
const especiales = [
  { pais: 'PER', numero: '00000001', nac: '991332', sexo: 'M', vence: '300101', apellidos: ['ROJAS'], nombres: ['ANA'] },
  { pais: 'PER', numero: '00000002', nac: '000229', sexo: 'F', vence: '010229', apellidos: ['ROJAS'], nombres: ['ANA'] },
  { pais: 'PER', numero: '00000003', nac: '250101', sexo: '<', vence: '750101', apellidos: ['PEÑA'], nombres: ['NOEMÍ'] },
  { pais: 'PER', numero: '00000004', nac: '260101', sexo: 'X', vence: '760101', apellidos: ['DE LA CRUZ'], nombres: [] },
  { pais: 'PER', numero: '00000005', nac: '000000', sexo: 'M', vence: '991231', apellidos: [], nombres: ['LUIS'] },
  { pais: 'UT<', numero: 'AB12345', nac: '800515', sexo: 'F', vence: '280515', apellidos: ['QUISPEMAMANIHUAMANFLORES'], nombres: ['ROSAMARIA', 'CARMEN'] }
];
for (const e of especiales) {
  const m = armarMrz(e);
  for (const fechaRef of FECHAS) caso('leerMrzTd1', { lineas: m, fechaRef }, leerMrzTd1(m, fechaRef));
}
// DNI azul: un «<» de menos entre apellido y nombres; «<» leído como K.
for (const p of personas.slice(0, 6)) {
  const l3 = rellenar(p.mrz[2].replace('<<', '<').replace(/<+$/, ''), 30);
  caso('leerMrzTd1', { lineas: [p.mrz[0], p.mrz[1], l3], fechaRef: '2026-10-24' }, leerMrzTd1([p.mrz[0], p.mrz[1], l3], '2026-10-24'));
}
// Lo que no es una MRZ.
for (const lineas of [null, [], ['x', 'y', 'z'], ICAO.slice(0, 2), [...ICAO, ICAO[0]], [ICAO[0], null, ICAO[2]],
  ICAO.map((l) => l.toLowerCase()), ['P' + ICAO[0].slice(1), ICAO[1], ICAO[2]], [ICAO[0] + '<', ICAO[1], ICAO[2]]]) {
  caso('leerMrzTd1', { lineas, fechaRef: '2026-10-24' }, leerMrzTd1(lineas, '2026-10-24'));
}
// Fecha de referencia rara (en Kotlin, una fecha de referencia sin año no da fecha).
caso('leerMrzTd1', { lineas: ICAO, fechaRef: 'xx' }, leerMrzTd1(ICAO, 'xx'));

// Buscar la MRZ entre lo que leyó la cámara, con ruido OCR de distinto nivel.
for (const p of personas) {
  for (const nivel of [0, 0.05, 0.15, 0.35]) {
    const sucia = ensuciar(p.mrz, nivel);
    const lineas = entre(0, 1) ? ['REPUBLICA DEL PERU', ...sucia] : [...sucia, 'Firma'];
    const fechaRef = elegir(FECHAS);
    caso('buscarMrzTd1', { lineas, fechaRef }, buscarMrzTd1(lineas, fechaRef));
  }
  // Un control roto: con ruido o sin él, nunca hay MRZ.
  const malo = controlMalo(p.mrz, 1, elegir([6, 14, 29]));
  caso('buscarMrzTd1', { lineas: ensuciar(malo, 0.1), fechaRef: '2026-10-24' }, buscarMrzTd1(ensuciar(malo, 0.1), '2026-10-24'));
}
for (const lineas of [null, [], ['I'], ['I<PER', 'x', 'y'], ['I<UTOD23145890 7<<<<<<<<<<<<<<<', '74O8122F12O4159UTO<<<<<<<<<<<6',
  'ERIKSSON<<ANNA<MARIA<<<<<<<<<<'], ['I<UT0D231458907<<<<<<<<<<<<<<<', '7408122F1204159UT0<<<<<<<<<<<6', 'ER1KSS0N«ANNA<MAR1A'],
['i<utod231458907', '7408122f1204159uto<6', 'eriksson<<anna<maria']]) {
  caso('buscarMrzTd1', { lineas, fechaRef: '2026-10-11' }, buscarMrzTd1(lineas, '2026-10-11'));
}

// Unir en filas: pedazos partidos, inclinados, desordenados.
for (const p of personas.slice(0, 20)) {
  const angulo = elegir([0, 0, 1.5, -2.25, 4, -6.5]);
  const fragmentos = aFragmentos(ensuciar(p.mrz, 0.05), { angulo, conCaja: azar() < 0.8 });
  caso('unirFilasOcr', { fragmentos }, unirFilasOcr(fragmentos));
}
caso('unirFilasOcr', { fragmentos: null }, unirFilasOcr(null));
caso('unirFilasOcr', { fragmentos: [] }, unirFilasOcr([]));
caso('unirFilasOcr', { fragmentos: [{ texto: 'ABC', arriba: 10, abajo: 10, izquierda: 5 }, { texto: 'DEF', arriba: 10, abajo: 10, izquierda: 1 }] },
  unirFilasOcr([{ texto: 'ABC', arriba: 10, abajo: 10, izquierda: 5 }, { texto: 'DEF', arriba: 10, abajo: 10, izquierda: 1 }]));

// El anverso cruzado con la MRZ: tildes, ñ, rótulos, letras confundidas, otro DNI.
const VARIANTES = ['normal', 'misma-fila', 'fecha-en-medio', 'sin-tildes', 'minusculas'];
for (const [k, p] of personas.entries()) {
  const mrz = leerMrzTd1(p.mrz, '2026-10-24').campos;
  const campos = { apellidos: mrz.apellidos, nombres: mrz.nombres };
  const filas = anverso(p, VARIANTES[k % VARIANTES.length]);
  caso('combinarMrzYAnverso', { camposMrz: campos, filas }, combinarMrzYAnverso(campos, filas));
  // Una letra confundida en la MRZ, o en el anverso.
  const camposDudosos = { apellidos: campos.apellidos, nombres: confundirUna(campos.nombres) };
  caso('combinarMrzYAnverso', { camposMrz: camposDudosos, filas }, combinarMrzYAnverso(camposDudosos, filas));
  const filasDudosas = filas.map((f) => (f === p.paterno ? confundirUna(f) : f));
  caso('combinarMrzYAnverso', { camposMrz: campos, filas: filasDudosas }, combinarMrzYAnverso(campos, filasDudosas));
  // DNI azul: «<» perdido entre nombres (letras juntas) o leído como K.
  const juntos = { apellidos: campos.apellidos, nombres: campos.nombres.replace(/ /g, '') };
  caso('combinarMrzYAnverso', { camposMrz: juntos, filas }, combinarMrzYAnverso(juntos, filas));
  const conK = { apellidos: [campos.apellidos[0] + 'K'], nombres: campos.nombres.replace(/ /g, 'K') };
  caso('combinarMrzYAnverso', { camposMrz: conK, filas }, combinarMrzYAnverso(conK, filas));
  // El anverso de otra persona.
  const otra = anverso(personas[(k + 7) % personas.length], 'normal');
  caso('combinarMrzYAnverso', { camposMrz: campos, filas: otra }, combinarMrzYAnverso(campos, otra));
}
caso('combinarMrzYAnverso', { camposMrz: null, filas: ['MUÑOZ'] }, combinarMrzYAnverso(null, ['MUÑOZ']));
caso('combinarMrzYAnverso', { camposMrz: { apellidos: ['MUNOZ'], nombres: 'ANA' }, filas: null }, combinarMrzYAnverso({ apellidos: ['MUNOZ'], nombres: 'ANA' }, null));
caso('combinarMrzYAnverso', { camposMrz: { apellidos: [], nombres: '' }, filas: ['Primer Apellido', 'ROJAS'] },
  combinarMrzYAnverso({ apellidos: [], nombres: '' }, ['Primer Apellido', 'ROJAS']));

// De punta a punta: fragmentos del reverso y del anverso, como en la cámara.
for (const p of personas.slice(0, 12)) {
  const fechaRef = '2026-10-24';
  const reverso = aFragmentos(ensuciar(p.mrz, 0.08), { angulo: elegir([0, 2, -3]) });
  const frente = aFragmentos(anverso(p, elegir(VARIANTES)), { angulo: elegir([0, 1, -1.5]), partir: false });
  const filasReverso = unirFilasOcr(reverso);
  const mrz = buscarMrzTd1(filasReverso, fechaRef);
  const filasFrente = unirFilasOcr(frente);
  caso('deCamara', { reverso, frente, fechaRef }, {
    filasReverso, mrz, filasFrente, anverso: mrz ? combinarMrzYAnverso(mrz.campos, filasFrente) : null
  });
}

// Normalizar letras y la plantilla anónima.
for (const t of ['Peña Ñahui-José', '  ', '', null, 'Straße', 'ÆON', 'Ǆemal', 'josé  maría\tdel\nCARMEN', 'ÑAÑA123', 'Ⅻ', 'ı']) {
  caso('normalizarLetras', { texto: t }, normalizarLetras(t));
}
for (const t of ['Huamán 12<3', 'A\u0001ÿ', '\u0007', null, '', 'I<PER00001234<8', 'Ωmega Жук 😀 ½ ﬁ', '\u0000\t\u001D~{', 'ǅ']) {
  caso('plantillaAnonima', { texto: t }, plantillaAnonima(t));
}

// PDF417 del DNI azul (SIN VERIFICAR): cadenas sintéticas con la distribución del tercero.
function pdf417({ numero, paterno, materno, nombres, sexo, vence, relleno = '\u001D' }) {
  const campo = (s, n) => (s + ' '.repeat(n)).slice(0, n);
  let s = '01' + campo(numero, 8) + campo(paterno, 40) + campo(materno, 40) + campo(nombres, 35) + sexo;
  s += relleno.repeat(448 - s.length) + campo(vence, 8) + '\u0003ÿ\u0010';
  return s;
}
for (const p of personas.slice(0, 8)) {
  const c = pdf417({
    numero: p.mrz[0].slice(5, 13), paterno: p.paterno, materno: p.materno, nombres: p.nombres.join(' '),
    sexo: elegir(['1', '2', '3']), vence: '20' + p.mrz[1].slice(8, 14), relleno: elegir(['\u001D', ' ', ' ', '﻿'])
  });
  caso('leerPdf417DniAzul', { cadena: c }, leerPdf417DniAzul(c));
}
for (const c of ['', null, 'x'.repeat(600), '01\u001D1234567\u001D', '01 0000123 ', pdf417({ numero: '0000ABCD', paterno: '\u001DROJAS\u001D',
  materno: ' PEÑA　', nombres: '\u0085ANA\u0085', sexo: '1', vence: '2030013' })]) {
  caso('leerPdf417DniAzul', { cadena: c }, leerPdf417DniAzul(c));
}

// El stub: ante todo, todo vacío.
for (const c of ['', null, 'CUALQUIER COSA 12345678', pdf417({ numero: '00000001', paterno: 'ROJAS', materno: 'PEÑA', nombres: 'ANA', sexo: '2', vence: '20300101' })]) {
  caso('parsearCadenaDni', { cadena: c }, parsearCadenaDni(c));
}

// Un caso por línea: el diff dice qué caso cambió.
writeFileSync(destino, '{"generadoCon": "web/js/escaneo-dni.js", "casos": [\n' +
  casos.map((x) => JSON.stringify(x)).join(',\n') + '\n]}\n');
const porFuncion = casos.reduce((m, x) => ({ ...m, [x.funcion]: (m[x.funcion] || 0) + 1 }), {});
const conMrz = casos.filter((x) => x.funcion === 'buscarMrzTd1' && x.salida).length;
console.log(`${casos.length} casos en ${destino}`);
console.log(porFuncion, `buscarMrzTd1 encontró ${conMrz}`);
