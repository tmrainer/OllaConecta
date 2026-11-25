// Que la app abra sin internet. Solo guarda los archivos de la app en Cache Storage;
// los DATOS no pasan por aquí (siguen en localStorage). No habla con otro dominio.
//
// Siempre sirve una copia completa y coherente: pedir archivo por archivo con mala
// señal podía mezclar un HTML nuevo con un JS viejo y dejar la página en blanco.
// Las novedades se revalidan en segundo plano (con ETag) y solo si llegaron TODAS y
// algo cambió se reemplaza la copia entera de una vez.

const PREFIJO = 'pi3-app-';
const MINUTOS_ENTRE_REVISIONES = 30;

// Al agregar un archivo a la app, va aquí también (humo.mjs lo comprueba).
const ARCHIVOS = [
  // Páginas y recursos principales
  './',
  './index.html',
  './asistencia.html',
  './padron.html',
  './formato-b1.html',
  './calendario.html',
  './estilos.css',
  './manifest.webmanifest',
  './ajustes.html',
  './datos.html',
  './probar-dni.html',
  './vista-previa.html',
  './coordinacion.html',

  // Iconos, fuentes e imágenes
  './icono.svg',
  './icono-maskable.svg',
  './icono-180.png',
  './icono-192.png',
  './icono-512.png',
  './icono-maskable-512.png',
  './fuentes/atkinson-hyperlegible-next-400.woff2',
  './fuentes/atkinson-hyperlegible-next-600.woff2',
  './fuentes/atkinson-hyperlegible-next-700.woff2',
  './img/ollaconecta-icono.webp',

  // Núcleo: datos y reglas de la aplicación
  './js/nucleo/alertas.js',
  './js/nucleo/calculos.js',
  './js/nucleo/escaneo-dni.js',
  './js/nucleo/estado.js',
  './js/nucleo/importar.js',
  './js/nucleo/modelo.js',
  './js/nucleo/perfil.js',
  './js/nucleo/lista-qr.js',
  './js/nucleo/compartir-qr.js',
  './js/nucleo/sugerencias.js',
  './js/nucleo/validaciones.js',

  // Documentos: PDF, Excel y formato B-1
  './js/documentos/plantilla-b1.js',
  './js/documentos/hoja-b1.js',
  './js/documentos/pdf-b1.js',
  './js/documentos/pdf.js',
  './js/documentos/pdf-reportes.js',
  './js/documentos/excel-reportes.js',
  './js/documentos/xlsx.js',

  // Cámara
  './js/camara/camara-dni.js',
  './js/camara/camara-qr.js',
  './js/camara/camara-trasera.js',

  // Interfaz compartida
  './js/interfaz/errores.js',
  './js/interfaz/ui-compartir.js',
  './js/interfaz/preferencias.js',
  './js/interfaz/compartir-archivo.js',
  './js/interfaz/ui-asistencia.js',
  './js/interfaz/ui-importar.js',
  './js/interfaz/ui.js',
  './js/interfaz/ui-personas.js',
  './js/interfaz/ui-reportes.js',
  './js/interfaz/ui-cuenta.js',
  './js/interfaz/ui-envio.js',

  // Controladores de las páginas
  './js/paginas/pagina-asistencia.js',
  './js/paginas/pagina-inicio.js',
  './js/paginas/pagina-vista-previa.js',
  './js/paginas/pagina-ajustes.js',
  './js/paginas/pagina-datos.js',
  './js/paginas/pagina-probar-dni.js',
  './js/paginas/pagina-b1.js',
  './js/paginas/pagina-calendario.js',
  './js/paginas/pagina-padron.js',
  './js/paginas/pagina-coordinacion.js',

  // Integración con la nube
  './js/nube/firebase-config.js',
  './js/nube/cuenta.js',
  './js/nube/ollas.js',
  './js/nube/sincronizacion.js',
  './js/nube/envio.js',

  // Librerías externas
  './vendor/qrcode-generator.js',
  './vendor/firebase/firebase-app.js',
  './vendor/firebase/firebase-auth.js',
  './vendor/firebase/firebase-firestore.js'
];

let ultimaRevision = 0;

/** Baja todos los archivos. Si falta uno, falla entero: nunca una copia a medias. */
async function bajarTodo(modo) {
  return Promise.all(ARCHIVOS.map(async (url) => {
    const resp = await fetch(url, { cache: modo });
    if (!resp.ok) throw new Error(`${url}: ${resp.status}`);
    return [url, resp];
  }));
}

async function guardarCopia(pares) {
  const nombre = PREFIJO + Date.now();
  const cache = await caches.open(nombre);
  await Promise.all(pares.map(([url, resp]) => cache.put(url, resp)));
  // Recién con la copia nueva completa se borran las viejas.
  const claves = await caches.keys();
  await Promise.all(claves.filter((k) => k.startsWith(PREFIJO) && k !== nombre).map((k) => caches.delete(k)));
  copiaAbierta = Promise.resolve(cache);
}

// La copia abierta se recuerda: cada página pide unos 15 archivos y buscar
// la copia (caches.keys + caches.open) en cada uno se notaba en un teléfono
// lento. guardarCopia() la reemplaza al armar una copia nueva.
let copiaAbierta = null;

async function copiaActual() {
  if (!copiaAbierta) {
    copiaAbierta = (async () => {
      const claves = (await caches.keys()).filter((k) => k.startsWith(PREFIJO)).sort();
      return claves.length ? caches.open(claves[claves.length - 1]) : null;
    })();
  }
  const cache = await copiaAbierta;
  if (!cache) copiaAbierta = null;      // sin copia todavía: se vuelve a preguntar la próxima vez
  return cache;
}

self.addEventListener('install', (e) => {
  e.waitUntil(bajarTodo('reload').then(guardarCopia).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== self.location.origin) return;
  e.respondWith((async () => {
    const cache = await copiaActual();
    const guardada = cache && await cache.match(req, { ignoreSearch: true });
    return guardada || fetch(req);    // lo que no es de la app (p. ej. pruebas.html) va a la red
  })());
});

/** ¿Hay versión nueva? Si la hay y llegó completa, reemplaza la copia y avisa. */
async function revisar(forzar) {
  if (!forzar && Date.now() - ultimaRevision < MINUTOS_ENTRE_REVISIONES * 60000) return;
  ultimaRevision = Date.now();
  const cache = await copiaActual();
  let nuevos;
  try {
    nuevos = await bajarTodo('no-cache');
  } catch (e) {
    return;                               // sin señal o a medias: se queda la copia actual
  }
  let cambio = !cache;
  if (cache) {
    for (const [url, resp] of nuevos) {
      const vieja = await cache.match(url);
      if (!vieja || await vieja.text() !== await resp.clone().text()) { cambio = true; break; }
    }
  }
  if (!cambio) return;
  await guardarCopia(nuevos);
  for (const c of await self.clients.matchAll({ type: 'window' })) c.postMessage('version-nueva');
}

self.addEventListener('message', (e) => {
  if (e.data === 'buscar-actualizacion') e.waitUntil(revisar(false));
  if (e.data === 'buscar-actualizacion-ya') e.waitUntil(revisar(true));   // humo.mjs
});
