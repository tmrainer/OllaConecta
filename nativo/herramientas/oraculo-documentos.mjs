// oraculo-documentos.mjs — arma con los módulos de la WEB (tal cual, sin
// tocarlos) los Excel y los PDF del respaldo ficticio, con una fecha fija, y
// los guarda en nucleo/src/test/resources/documentos/. DocumentosOraculoTest.kt
// arma lo mismo en Kotlin y compara byte a byte.
//
//   node nativo/herramientas/oraculo-documentos.mjs
//
// También lee con leerXlsx() de la web (en un Chromium sin ventana: hace falta
// DOMParser) esos Excel, el padrón de ejemplo de herramientas/ y un .xlsx
// comprimido con textos compartidos, y escribe lo leído en <archivo>.tsv. Sin
// Chromium se avisa y los .tsv quedan como estaban. Chromium: el de Playwright
// (~/.cache/ms-playwright), uno del sistema o CHROME=/ruta/a/chrome.
//
// Solo datos inventados (respaldo-ficticio.json). Es determinista.

import { readFileSync, writeFileSync, mkdirSync, mkdtempSync, existsSync, readdirSync, copyFileSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn, execFileSync } from 'node:child_process';
import http from 'node:http';
import os from 'node:os';
import zlib from 'node:zlib';
import { migrarPadron, nuevaRendicion, nuevoEgreso, nuevoAfiliado, padronVacio } from '../../web/js/modelo.js';
import { hojasDelB1 } from '../../web/js/hoja-b1.js';
import { pdfDelB1 } from '../../web/js/pdf-b1.js';
import { pdfDelPadron, pdfDeAsistencia } from '../../web/js/pdf-reportes.js';
import { excelDelPadron, excelDeAsistencia, excelDelB1 } from '../../web/js/excel-reportes.js';

const aqui = dirname(fileURLToPath(import.meta.url));
const RAIZ = join(aqui, '..', '..');
const RECURSOS = join(aqui, '..', 'nucleo', 'src', 'test', 'resources');
const DESTINO = join(RECURSOS, 'documentos');
mkdirSync(DESTINO, { recursive: true });

// Lo mismo que usa DocumentosOraculoTest.kt.
const GENERADO = '20-10-26 18:40';
const HOY = '2026-10-20';

const datos = JSON.parse(readFileSync(join(RECURSOS, 'respaldo-ficticio.json'), 'utf8'));
// El padrón como lo ve la app (igual que oraculo.mjs): migrado y con sinInscribir.
const padron = migrarPadron(datos.padron);
const crudos = new Map((datos.padron.atenciones || []).map((at) => [at && at.id, at]));
for (const at of padron.atenciones) {
  const c = crudos.get(at.id);
  if (c && c.sinInscribir) at.sinInscribir = c.sinInscribir;
}
const centro = datos.config.ultimoCentro.nombreCentro;

const archivos = {
  'padron.xlsx': excelDelPadron(padron, { fechaRef: HOY, inicio: '2026-10-01', fin: HOY, centro }),
  'padron.pdf': pdfDelPadron(padron, { fechaRef: HOY, inicio: '2026-10-01', fin: HOY, centro, generado: GENERADO }),
  'padron-vacio.xlsx': excelDelPadron(padronVacio(), { fechaRef: HOY, inicio: '2026-10-01', fin: HOY }),
  'padron-vacio.pdf': pdfDelPadron(padronVacio(), { fechaRef: HOY, inicio: '2026-10-01', fin: HOY, generado: GENERADO }),
  // Hasta 31 días: con la planilla por persona en hojas apaisadas.
  'asistencia-setiembre.xlsx': excelDeAsistencia(padron, { inicio: '2026-09-01', fin: '2026-09-30', centro }),
  'asistencia-setiembre.pdf': pdfDeAsistencia(padron, { inicio: '2026-09-01', fin: '2026-09-30', centro, generado: GENERADO }),
  // Más de 31 días: sin planilla en el PDF; en el Excel sí.
  'asistencia-todo.xlsx': excelDeAsistencia(padron, { inicio: '2026-08-01', fin: HOY }),
  'asistencia-todo.pdf': pdfDeAsistencia(padron, { inicio: '2026-08-01', fin: HOY, generado: GENERADO }),
  'asistencia-vacia.pdf': pdfDeAsistencia(padron, { inicio: '2025-01-01', fin: '2025-01-31', generado: GENERADO }),
  'b1-vacio.xlsx': excelDelB1(nuevaRendicion()),
  'b1-vacio.pdf': pdfDelB1(hojasDelB1(null), { generado: GENERADO })
};
datos.rendiciones.forEach((r, i) => {
  archivos[`b1-${i + 1}.xlsx`] = excelDelB1(r);
  archivos[`b1-${i + 1}.pdf`] = pdfDelB1(hojasDelB1(r), { generado: GENERADO, titulo: `Formato B-1 ${r.periodo.etiqueta}` });
});
// Textos que no entran en su caja (más chico, en dos renglones, cortado con
// «…»), letras fuera de WinAnsi, \ y paréntesis, cantidades con decimales.
// El respaldo ficticio no llega a esos caminos. Se guarda como respaldo para
// que Kotlin lea exactamente lo mismo.
const largo = {
  exportadoEn: '2026-10-20T18:40:00.000Z',
  aplicacion: 'rendicion-b1',
  rendiciones: [{
    ...nuevaRendicion(), id: 'r-largo', actualizadoEn: '2026-10-20T18:40:00.000Z', tipoCentro: 'comedor',
    nombreCentro: 'Comedor Popular Autogestionario «Virgen de la Candelaria» del A. H. Nueva Esperanza (zona alta)',
    codigoPca: 'PCA\\0001 (b)', periodo: { tipo: 'quincenal', inicio: '2026-10-01', fin: '2026-10-15', etiqueta: 'Primera quincena de octubre — 2026' },
    fechaRendicion: '2026-10-16', nombrePresidenta: 'María Ñahui Quispe de la Cruz Huamán Yupanqui Ccopa Chávez Inca Vásquez Óre',
    dniPresidenta: '00000009', celular: '900 000 000 (solo WhatsApp) 🍲', montoSubsidioCent: 123456, fechaAsignacion: '2026-10-02',
    nombrePresidentaFirma: 'María Ñahui Quispe de la Cruz', dniPresidentaFirma: '00000009',
    nombreTesorera: 'Úrsula Ávila Ochoa', dniTesorera: '00000010',
    egresos: [
      ['Aceite vegetal', 2.5, 'L', 1250, 3125],
      ['Arroz extra graneado en sacos', 0.30000000000000004, 'Kg', 330, 99],
      ['Verduras surtidas para la semana: zanahoria, cebolla, ajo y culantro', 1, 'varios', 2000, 2000],
      ['Pescado bonito fresco del terminal pesquero de Villa María del Triunfo (dos cajas grandes)', 3, 'caja', 4500, 13500],
      ['Ω símbolo € y \\ barra (paréntesis)', 1e-7, 'unidad de medida muy larga', 1, 1]
    ].map(([descripcion, cantidad, unidadMedida, precioUnitarioCent, montoTotalCent], i) => ({
      ...nuevoEgreso(), id: `e-largo-${i}`, fechaCompra: `2026-10-0${i + 3}`, descripcion, cantidad, unidadMedida,
      rucProveedor: `1000000000${i}`, boletaSerie: i === 2 ? '' : 'B001-SERIE-LARGA', boletaCorrelativo: `00000${i}`,
      precioUnitarioCent, montoTotalCent
    }))
  }],
  padron: {
    version: 3,
    afiliados: [
      ['Huamán', 'Yupanqui', 'Ana María de los Ángeles Guadalupe Concepción', 'dni', '01234567', '1950-02-28', null, 'caso_social'],
      ['de la Cruz', '', 'José', 'ce', '000123456789', '', 'nino', 'habitual'],
      ['Ñahui', 'Quispe', 'Rosa (Rosita)', 'dni', '00000003', '2010-10-21', null, 'habitual'],
      ['Ochoa', 'Ávila', 'Luz', 'dni', '', '', null, '']
    ].map(([apellidoPaterno, apellidoMaterno, nombres, tipoDocumento, numeroDocumento, fechaNacimiento, grupoEtarioManual, tipoAfiliado], i) => ({
      ...nuevoAfiliado(), id: `a-largo-${i}`, apellidoPaterno, apellidoMaterno, nombres, tipoDocumento, numeroDocumento,
      fechaNacimiento, grupoEtarioManual, tipoAfiliado, altaEn: '2026-01-01'
    })),
    atenciones: [
      { id: 'at-largo-1', fecha: '2026-10-05', precioMenuNormalCent: 250, precioMenuAyudaSocialCent: 0, precioTomadoDeConfig: true,
        legado: null, nota: '', sinInscribir: { normal: 2, ayudaSocial: 1 },
        asistencias: [{ afiliadoId: 'a-largo-0', tipoMenu: 'ayuda_social' }, { afiliadoId: 'a-largo-2', tipoMenu: 'normal' }] },
      { id: 'at-largo-2', fecha: '2026-10-06', precioMenuNormalCent: null, precioMenuAyudaSocialCent: 0, precioTomadoDeConfig: false,
        legado: null, nota: '', asistencias: [{ afiliadoId: 'a-largo-1', tipoMenu: 'normal' }] }
    ]
  }
};
writeFileSync(join(RECURSOS, 'respaldo-documentos.json'), JSON.stringify(largo, null, 2));
const padronLargo = migrarPadron(largo.padron);
for (const at of padronLargo.atenciones) {
  const c = largo.padron.atenciones.find((x) => x.id === at.id);
  if (c.sinInscribir) at.sinInscribir = c.sinInscribir;
}
const rLargo = largo.rendiciones[0];
Object.assign(archivos, {
  'largo-b1.pdf': pdfDelB1(hojasDelB1(rLargo), { generado: GENERADO, titulo: 'B-1 (largo) \\ —' }),
  'largo-b1.xlsx': excelDelB1(rLargo),
  'largo-padron.pdf': pdfDelPadron(padronLargo, { fechaRef: HOY, inicio: '2026-10-01', fin: HOY, centro: rLargo.nombreCentro, generado: GENERADO }),
  'largo-padron.xlsx': excelDelPadron(padronLargo, { fechaRef: HOY, inicio: '2026-10-01', fin: HOY, centro: rLargo.nombreCentro }),
  'largo-asistencia.pdf': pdfDeAsistencia(padronLargo, { inicio: '2026-10-01', fin: HOY, centro: rLargo.nombreCentro, generado: GENERADO }),
  'largo-asistencia.xlsx': excelDeAsistencia(padronLargo, { inicio: '2026-10-01', fin: HOY, centro: rLargo.nombreCentro })
});

for (const [nombre, bytes] of Object.entries(archivos)) writeFileSync(join(DESTINO, nombre), bytes);
console.log(`Escritos ${Object.keys(archivos).length} documentos en ${DESTINO}`);

// ------------------------------------------------------ .xlsx para leer
//
// Como lo guarda Excel: comprimido, con textos compartidos (uno con varias
// partes), una fecha con estilo, otra guardada como fecha (t="d"), libro desde
// 1904, columnas salteadas, filas vacías, comillas y tabuladores.

function zipComprimido(entradas) {
  const partes = [];
  const central = [];
  let pos = 0;
  for (const [nombre, texto] of entradas) {
    const n = Buffer.from(nombre);
    const crudo = Buffer.from(texto);
    const comp = zlib.deflateRawSync(crudo);
    const crc = zlib.crc32(crudo);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0); local.writeUInt16LE(20, 4); local.writeUInt16LE(0x0800, 6);
    local.writeUInt16LE(8, 8); local.writeUInt16LE(0, 10); local.writeUInt16LE(33, 12);
    local.writeUInt32LE(crc, 14); local.writeUInt32LE(comp.length, 18); local.writeUInt32LE(crudo.length, 22);
    local.writeUInt16LE(n.length, 26); local.writeUInt16LE(4, 28);
    const extra = Buffer.from([0xfe, 0xca, 0, 0]); // un «extra» en la cabecera local, como deja Java
    const cab = Buffer.alloc(46);
    cab.writeUInt32LE(0x02014b50, 0); cab.writeUInt16LE(20, 4); cab.writeUInt16LE(20, 6); cab.writeUInt16LE(0x0800, 8);
    cab.writeUInt16LE(8, 10); cab.writeUInt16LE(0, 12); cab.writeUInt16LE(33, 14); cab.writeUInt32LE(crc, 16);
    cab.writeUInt32LE(comp.length, 20); cab.writeUInt32LE(crudo.length, 24); cab.writeUInt16LE(n.length, 28);
    cab.writeUInt32LE(pos, 42);
    partes.push(local, n, extra, comp);
    central.push(cab, n);
    pos += 30 + n.length + extra.length + comp.length;
  }
  const largoCentral = central.reduce((s, b) => s + b.length, 0);
  const fin = Buffer.alloc(22);
  fin.writeUInt32LE(0x06054b50, 0); fin.writeUInt16LE(entradas.length, 8); fin.writeUInt16LE(entradas.length, 10);
  fin.writeUInt32LE(largoCentral, 12); fin.writeUInt32LE(pos, 16);
  return Buffer.concat([...partes, ...central, fin]);
}

const NS = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main';
const NS_R = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships';
writeFileSync(join(DESTINO, 'leer-variado.xlsx'), zipComprimido([
  ['[Content_Types].xml', '<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"/>'],
  ['xl/workbook.xml', `﻿<?xml version="1.0" encoding="UTF-8"?><x:workbook xmlns:x="${NS}" xmlns:r="${NS_R}">` +
    '<x:workbookPr date1904="1"/><x:sheets><x:sheet name="Lista" sheetId="7" r:id="rId9"/><x:sheet name="Otra" sheetId="1" r:id="rId1"/></x:sheets></x:workbook>'],
  ['xl/_rels/workbook.xml.rels', '<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
    '<Relationship Id="rId1" Type="x" Target="worksheets/sheet1.xml"/><Relationship Id="rId9" Type="x" Target="/xl/worksheets/lista.xml"/></Relationships>'],
  ['xl/sharedStrings.xml', `<?xml version="1.0"?><sst xmlns="${NS}"><si><t>Apellido</t></si><si><r><t>Ñahui </t></r><r><rPr><b/></rPr><t>Quispe</t></r></si>` +
    '<si><t xml:space="preserve">  dice "hola"\tcon tab  </t></si><si><t>Línea uno\nLínea dos</t></si><si><t>&lt;&amp;&gt;</t></si></sst>'],
  ['xl/styles.xml', `<?xml version="1.0"?><styleSheet xmlns="${NS}"><numFmts count="2"><numFmt numFmtId="164" formatCode="&quot;día&quot; 0"/>` +
    '<numFmt numFmtId="165" formatCode="[$-280A]d/mm/yyyy"/></numFmts><cellXfs count="4"><xf numFmtId="0"/><xf numFmtId="14"/><xf numFmtId="164"/><xf numFmtId="165"/></cellXfs></styleSheet>'],
  ['xl/worksheets/lista.xml', `<?xml version="1.0"?><worksheet xmlns="${NS}"><sheetData>` +
    '<row r="2"><c r="A2" t="s"><v>0</v></c><c r="C2" t="s"><v>1</v></c><c r="E2"><v>12.50</v></c></row>' +
    '<row r="3"><c r="B3" t="s"><v>2</v></c><c r="C3" t="s"><v>3</v></c><c r="D3" t="s"><v>4</v></c><c r="F3" t="s"><v>99</v></c></row>' +
    '<row r="6"><c r="A6" s="1"><v>45000</v></c><c r="B6" s="2"><v>45000</v></c><c r="C6" s="3"><v>45000.75</v></c>' +
    '<c r="D6" t="d"><v>2026-10-20T00:00:00</v></c><c r="E6" t="str"><v>00012</v></c><c r="F6" t="inlineStr"><is><t>en </t><t>línea</t></is></c></row>' +
    '<row r="7"><c t="b"><v>1</v></c><c><v>-3</v></c><c r="E7" t="e"><v>#N/A</v></c></row>' +
    '<row r="9"></row></sheetData></worksheet>'],
  ['xl/worksheets/sheet1.xml', `<?xml version="1.0"?><worksheet xmlns="${NS}"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>no es esta</t></is></c></row></sheetData></worksheet>`]
]));
copyFileSync(join(RAIZ, 'herramientas', 'padron-de-ejemplo.xlsx'), join(DESTINO, 'padron-de-ejemplo.xlsx'));

// ------------------------------------------- leerXlsx() en un Chromium

function buscarChromium() {
  if (process.env.CHROME) return process.env.CHROME;
  const cache = join(os.homedir(), '.cache', 'ms-playwright');
  if (existsSync(cache)) {
    for (const dir of readdirSync(cache).sort().reverse()) {
      for (const rel of ['chrome-headless-shell-linux64/chrome-headless-shell', 'chrome-linux/chrome']) {
        const p = join(cache, dir, rel);
        if (existsSync(p)) return p;
      }
    }
  }
  for (const nombre of ['chromium', 'chromium-browser', 'google-chrome', 'google-chrome-stable']) {
    try { return execFileSync('which', [nombre], { encoding: 'utf8' }).trim(); } catch { /* sigue */ }
  }
  return null;
}

const paraLeer = readdirSync(DESTINO).filter((n) => n.endsWith('.xlsx')).sort();
const chromium = buscarChromium();
if (!chromium) {
  console.warn('Sin Chromium: no se actualizaron los .tsv (CHROME=/ruta/a/chrome para indicarlo).');
  process.exit(0);
}

const ARNES = `<!doctype html><meta charset="utf-8"><script type="module">
import { leerXlsx } from '/js/xlsx.js';
const salida = {};
for (const n of ${JSON.stringify(paraLeer)}) {
  const b = await (await fetch('/documentos/' + n)).arrayBuffer();
  try { salida[n] = await leerXlsx(b); } catch (e) { salida[n] = 'ERROR: ' + e.message; }
}
await fetch('/resultado', { method: 'POST', body: JSON.stringify(salida) });
</script>`;

const leido = await new Promise((resolver, fallar) => {
  let proc = null;
  const servidor = http.createServer((req, res) => {
    if (req.method === 'POST' && req.url === '/resultado') {
      let cuerpo = '';
      req.on('data', (d) => { cuerpo += d; });
      req.on('end', () => { res.end('ok'); servidor.close(); proc.kill(); resolver(JSON.parse(cuerpo)); });
      return;
    }
    if (req.url === '/arnes.html') { res.setHeader('Content-Type', 'text/html'); res.end(ARNES); return; }
    const ruta = req.url.startsWith('/documentos/') ? join(DESTINO, req.url.slice(12)) : join(RAIZ, 'web', req.url);
    if (!existsSync(ruta)) { res.statusCode = 404; res.end(); return; }
    res.setHeader('Content-Type', ruta.endsWith('.js') ? 'text/javascript' : 'application/octet-stream');
    res.end(readFileSync(ruta));
  });
  servidor.listen(0, '127.0.0.1', () => {
    const perfil = mkdtempSync(join(os.tmpdir(), 'oraculo-documentos-'));
    proc = spawn(chromium, ['--headless', '--no-sandbox', '--disable-gpu', '--no-first-run', `--user-data-dir=${perfil}`,
      `http://127.0.0.1:${servidor.address().port}/arnes.html`], { stdio: 'ignore' });
    proc.on('exit', () => rmSync(perfil, { recursive: true, force: true }));
    setTimeout(() => { proc.kill(); servidor.close(); fallar(new Error('Chromium no respondió en 30 s')); }, 30000).unref();
  });
});
for (const [nombre, texto] of Object.entries(leido)) writeFileSync(join(DESTINO, nombre.replace(/\.xlsx$/, '.tsv')), texto);
console.log(`Leídos con la web ${Object.keys(leido).length} .xlsx (${chromium})`);
