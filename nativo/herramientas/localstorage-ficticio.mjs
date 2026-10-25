// Lo que el APK 1.7 (la web dentro de Capacitor) tendría guardado en localStorage con
// el respaldo FICTICIO cargado, con el texto exacto de guardar() de web/js/modelo.js,
// más casos límite de desempacarPadron(). Lo usa MigracionWebTest.kt.
//
//   node nativo/herramientas/localstorage-ficticio.mjs
//
// Escribe localstorage-ficticio.json y localstorage-casos.json en
// nucleo/src/test/resources. Es determinista.

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { CLAVES, empacarPadron, desempacarPadron } from '../../web/js/modelo.js';

const aqui = dirname(fileURLToPath(import.meta.url));
const recursos = join(aqui, '..', 'nucleo', 'src', 'test', 'resources');
const respaldo = JSON.parse(readFileSync(join(recursos, 'respaldo-ficticio.json'), 'utf8'));

// Como guardar(): JSON.stringify sin sangría; el padrón, empacado.
const guardado = {
  [CLAVES.padron]: JSON.stringify(empacarPadron(respaldo.padron)),
  [CLAVES.rendiciones]: JSON.stringify(respaldo.rendiciones),
  [CLAVES.calendario]: JSON.stringify(respaldo.calendario),
  [CLAVES.config]: JSON.stringify(respaldo.config),
  [CLAVES.respaldo]: JSON.stringify({ ultimoEn: '2026-10-05' }),
  [CLAVES.olla]: JSON.stringify({ id: 'olla-ficticia', nombre: 'Olla ficticia', rol: 'miembro', juntar: false })
};
writeFileSync(join(recursos, 'localstorage-ficticio.json'), JSON.stringify(guardado, null, 2) + '\n');

// Casos límite: entrada (como estaría en localStorage, ya parseada) y lo que
// devuelve desempacarPadron() de la web.
const afiliados = [{ id: 'p-1' }, { id: 'p-2' }, { id: 'p-3' }];
const UUID = '0f8fad5b-d9cb-469f-a165-70867728950e';
const corto = '~D4-tW9nLRp-hZXCGdyiVDg';   // UUID en el formato de desarrollo: «~» + 16 bytes en base64url
const entradas = [
  // casillas: p-1 y p-3 vinieron; p-3 con ayuda social; alguien fuera del padrón
  { afiliados, atenciones: [{ id: 'a', fecha: '2026-10-01', marcas: { vino: 'F', social: 'E', fuera: [{ afiliadoId: 'x', tipoMenu: 'normal' }] } }] },
  // una casilla que apunta más allá del padrón se descarta
  { afiliados, atenciones: [{ id: 'b', fecha: '2026-10-02', marcas: { vino: 'J' } }] },
  // formato de desarrollo: ids cortos o largos, social por id
  { afiliados, atenciones: [{ id: 'c', fecha: '2026-10-03', marcas: { ids: [corto, 'p-2'], social: ['p-2'] } }] },
  // ya sin empacar, sin marcas o con marcas desconocidas: pasan igual
  { afiliados, atenciones: [{ id: 'd', asistencias: [{ afiliadoId: 'p-1', tipoMenu: 'normal' }] }, { id: 'e', marcas: null }, { id: 'f', marcas: { otra: 1 } }] },
  // padrón sin atenciones: pasa igual
  { afiliados },
  // el padrón ficticio entero, tal como quedó en localStorage
  JSON.parse(guardado[CLAVES.padron])
];
const casos = entradas.map((entrada) => ({ entrada, esperado: desempacarPadron(JSON.parse(JSON.stringify(entrada))) }));
if (casos[2].esperado.atenciones[0].asistencias[0].afiliadoId !== UUID) throw new Error('El id corto de prueba no da el UUID esperado.');
writeFileSync(join(recursos, 'localstorage-casos.json'), JSON.stringify(casos, null, 2) + '\n');
console.log('Escrito localstorage-ficticio.json y localstorage-casos.json');
