// armar.mjs — junta el código del Excel de la app en un solo archivo para
// Apps Script: reportes.js.
//
//   node apps-script/armar.mjs      (desde nube/; también lo corre npm run pruebas)
//
// Apps Script no entiende módulos (import/export), pero sí todo lo demás que
// usan modelo.js, calculos.js, xlsx.js y excel-reportes.js. Aquí cada módulo
// queda envuelto en una función que devuelve lo que exportaba, y cada import
// pasa a leer de ahí. Así el correo lleva EXACTAMENTE el mismo Excel que
// exporta la app, sin mantener dos copias. Si cambia alguno de esos archivos,
// hay que volver a armar (las pruebas lo hacen solas).
//
// También toma el projectId de web/js/firebase-config.js, para que el script
// sepa a qué proyecto hablarle sin escribirlo dos veces.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const aqui = path.dirname(fileURLToPath(import.meta.url));
const raiz = path.resolve(aqui, '../../web/js');
const SALIDA = path.join(aqui, 'reportes.js');

// En el orden de sus dependencias.
const MODULOS = [
  ['modelo', 'modelo.js'],
  ['calculos', 'calculos.js'],
  ['xlsx', 'xlsx.js'],
  ['excelReportes', 'excel-reportes.js']
];
const alias = new Map(MODULOS.map(([variable, archivo]) => ['./' + archivo, variable]));

// Lo que el navegador trae y Apps Script no: codificar texto a bytes UTF-8
// (xlsx.js lo usa para armar el archivo).
const POLIFILL = `if (typeof TextEncoder === 'undefined') {
  globalThis.TextEncoder = class {
    encode(texto) {
      const u = unescape(encodeURIComponent(String(texto)));
      const bytes = new Uint8Array(u.length);
      for (let i = 0; i < u.length; i += 1) bytes[i] = u.charCodeAt(i);
      return bytes;
    }
  };
}`;

function envolver(variable, archivo) {
  let codigo = fs.readFileSync(path.join(raiz, archivo), 'utf8');
  const nombres = [];
  codigo = codigo.replace(/^import\s*\{([^}]*)\}\s*from\s*'([^']+)';/gm, (_, lista, de) => {
    if (!alias.has(de)) throw new Error(`${archivo} importa ${de}, que armar.mjs no incluye`);
    return `const {${lista}} = ${alias.get(de)};`;
  });
  codigo = codigo.replace(/^export (async function|function|const|let) ([A-Za-z_$][\w$]*)/gm, (_, tipo, nombre) => {
    nombres.push(nombre);
    return `${tipo} ${nombre}`;
  });
  const resto = codigo.match(/^(import|export)\b.*/m);
  if (resto) throw new Error(`${archivo}: armar.mjs no entiende «${resto[0]}»`);
  return `// ---------------------------------------------------------------- ${archivo}\n` +
    `const ${variable} = (() => {\n${codigo}\nreturn { ${nombres.join(', ')} };\n})();`;
}

/** El archivo entero, como texto. */
export function armar() {
  const config = fs.readFileSync(path.join(raiz, 'firebase-config.js'), 'utf8');
  const projectId = (config.match(/projectId:\s*'([^']*)'/) || [, ''])[1];
  return [
    '// reportes.js — GENERADO por armar.mjs desde web/js/ (modelo, calculos, xlsx,',
    '// excel-reportes). No editar aquí: cambiar el original y volver a armar.',
    '// Es el mismo código con el que la app exporta el Excel.',
    '',
    POLIFILL,
    '',
    `const PROYECTO_FIREBASE = ${JSON.stringify(projectId)};   // de web/js/firebase-config.js`,
    '',
    ...MODULOS.map(([variable, archivo]) => envolver(variable, archivo))
  ].join('\n') + '\n';
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const texto = armar();
  fs.writeFileSync(SALIDA, texto);
  const projectId = texto.match(/PROYECTO_FIREBASE = "([^"]*)"/)[1];
  console.log(`${path.relative(process.cwd(), SALIDA)}: ${(texto.length / 1024).toFixed(0)} KB` +
    (projectId ? ` (proyecto ${projectId})` : ' (firebase-config.js sin projectId: el script no sabrá a qué proyecto hablarle)'));
}
