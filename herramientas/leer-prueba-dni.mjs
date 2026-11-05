// Lee lo que comparte la versión de prueba de la app nativa (Ajustes → Probar el
// escaneo del DNI): la línea «OLLADNI1:…» es el detalle en JSON, comprimido con
// gzip y en base64. Sin dependencias.
//
//   node herramientas/leer-prueba-dni.mjs mensaje.txt
//   pbpaste | node herramientas/leer-prueba-dni.mjs        (o wl-paste, xclip -o)
//
// Trae datos de un DNI real: no lo subas al repositorio.

import fs from 'node:fs';
import { gunzipSync } from 'node:zlib';

const texto = fs.readFileSync(process.argv[2] || 0, 'utf8');
const bloques = [...texto.matchAll(/OLLADNI1:([A-Za-z0-9_=-]+)/g)].map((m) => m[1]);
if (bloques.length === 0) {
  console.error('No encontré ninguna línea «OLLADNI1:…».');
  process.exit(1);
}
for (const b of bloques) {
  const json = gunzipSync(Buffer.from(b.replace(/-/g, '+').replace(/_/g, '/'), 'base64')).toString('utf8');
  console.log(JSON.stringify(JSON.parse(json), null, 2));
}
