// camara-dni.js — el visor de cámara para leer el DNI: el PDF417 (DNI azul) con
// BarcodeDetector. La zona de texto (MRZ) la lee solo la app nativa (nativo/).
// Ningún fotograma se guarda ni sale del teléfono.

import { crearElemento, icono, destelloLeido } from '../interfaz/ui.js';
import { abrirCamaraTrasera } from './camara-trasera.js';

const PISTA_REVERSO = 'Pon dentro del recuadro el lado del DNI con tres líneas de «<», con buena luz, y mantén el teléfono quieto.';
const PISTA_ASISTENCIA = 'Muestra el lado del DNI con tres líneas de «<». Puedes pasar uno tras otro.';

/** ¿Hay con qué leer el DNI? { codigo, texto } */
export async function lectoresDni() {
  let codigo = false;
  try {
    codigo = 'BarcodeDetector' in window &&
      (await window.BarcodeDetector.getSupportedFormats()).includes('pdf417');
  } catch (e) { codigo = false; }
  const camara = !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia);
  // La zona de texto la lee solo la app nativa: aquí nunca hay con qué, y las
  // pantallas que dependen de ella (inscribir y pasar lista con el DNI) quedan ocultas.
  return { codigo: camara && codigo, texto: false };
}

/**
 * Abre el visor y busca el PDF417 del DNI.
 *
 * modo «asistencia» (pasar lista) necesitaba la zona de texto, que solo lee la
 * app nativa: aquí no tiene con qué leer y lo dice.
 * @param {{ alLeer: (r: {tipo: 'pdf417', cadena: string}) => void,
 *           alCerrar?: () => void, modo?: 'completo' | 'asistencia',
 *           conCodigo?: boolean }} opciones  conCodigo: false deja fuera el PDF417
 * @returns {Promise<void>}
 */
export async function abrirCamaraDni({ alLeer, alCerrar = () => {}, modo = 'completo', conCodigo = true }) {
  const enAsistencia = modo === 'asistencia';
  const lectores = await lectoresDni();
  const codigo = lectores.codigo && !enAsistencia && conCodigo;
  const video = crearElemento('video', { clase: 'camara-video', playsinline: '', muted: '', autoplay: '' });
  video.muted = true;
  const estado = crearElemento('p', { clase: 'camara-estado', role: 'status', 'aria-live': 'polite' });
  // Mientras la cámara abre (puede tardar un par de segundos en un teléfono
  // modesto), un círculo que gira sobre negro: se ve que algo está pasando.
  const cargando = crearElemento('div', { clase: 'camara-cargando' }, [
    crearElemento('span', { clase: 'girador', 'aria-hidden': 'true' }),
    crearElemento('span', { texto: 'Abriendo la cámara…' })
  ]);
  video.addEventListener('playing', () => { cargando.hidden = true; }, { once: true });
  // Con poca luz (un comedor de noche) casi ninguna foto sale legible: la
  // linterna del teléfono, si deja usarla desde la app. Se apaga al cerrar.
  let linternaPrendida = false;
  const linterna = crearElemento('button', {
    type: 'button', clase: 'boton secundario', texto: 'Prender la linterna', hidden: true, 'aria-pressed': 'false',
    onclick: async () => {
      const pista = flujo && flujo.getVideoTracks()[0];
      if (!pista) return;
      try {
        await pista.applyConstraints({ advanced: [{ torch: !linternaPrendida }] });
        linternaPrendida = !linternaPrendida;
        linterna.textContent = linternaPrendida ? 'Apagar la linterna' : 'Prender la linterna';
        linterna.setAttribute('aria-pressed', String(linternaPrendida));
      } catch (e) { linterna.hidden = true; /* este teléfono no deja */ }
    }
  });
  const visor = crearElemento('div', { clase: 'camara-visor' }, [
    video,
    crearElemento('div', { clase: 'camara-marco', 'aria-hidden': 'true' }),
    cargando
  ]);
  const dialogo = crearElemento('dialog', { clase: 'camara-dni', 'aria-labelledby': 'camara-titulo' }, [
    crearElemento('h2', { id: 'camara-titulo', texto: enAsistencia ? 'Pasar lista con el DNI' : 'Leer el DNI' }),
    crearElemento('p', { clase: 'camara-pista', texto: enAsistencia ? PISTA_ASISTENCIA : PISTA_REVERSO }),
    visor,
    estado,
    crearElemento('div', { clase: 'acciones' }, [linterna,
      crearElemento('button', {
        type: 'button', clase: 'boton secundario', texto: enAsistencia ? 'Terminar' : 'Cancelar', onclick: () => cerrar()
      })])
  ]);
  document.body.append(dialogo);
  dialogo.addEventListener('cancel', (e) => { e.preventDefault(); cerrar(); });
  dialogo.showModal();

  let flujo = null;
  let abierto = true;
  let temporizador = 0;

  function cerrar() {
    if (!abierto) return;
    abierto = false;
    clearTimeout(temporizador);
    document.removeEventListener('visibilitychange', alOcultar);
    if (flujo) flujo.getTracks().forEach((t) => t.stop());
    video.srcObject = null;
    dialogo.close();
    dialogo.remove();
    alCerrar();
  }
  // Si se sale de la app con la cámara abierta, se cierra: Android se la
  // quita igual y el visor quedaba congelado gastando batería. Solo con la
  // cámara ya abierta, para no cerrarlo con el aviso de permiso.
  function alOcultar() { if (document.hidden && flujo) cerrar(); }
  document.addEventListener('visibilitychange', alOcultar);

  // Leído: el visor se pone verde un instante y recién entonces se cierra.
  let entregando = false;
  async function entregar(resultado) {
    if (!abierto || entregando) return;
    entregando = true;
    if (navigator.vibrate) { try { navigator.vibrate(60); } catch (e) { /* sin vibración */ } }
    await destelloLeido(visor);
    if (!abierto) return;                   // se canceló durante el destello
    cerrar();
    alLeer(resultado);
  }

  if (!codigo) {
    estado.textContent = 'Este teléfono no puede leer el DNI con la cámara. Escribe los datos a mano.';
    cargando.hidden = true;
    return;
  }
  try {
    flujo = await abrirCamaraTrasera(1920, 1080);
  } catch (e) {
    estado.textContent = 'No se pudo abrir la cámara. Revisa que la app tenga permiso para usarla.';
    cargando.hidden = true;
    return;
  }
  if (!abierto) { flujo.getTracks().forEach((t) => t.stop()); return; }
  try {
    const pistaVideo = flujo.getVideoTracks()[0];
    linterna.hidden = !(pistaVideo.getCapabilities && pistaVideo.getCapabilities().torch);
  } catch (e) { linterna.hidden = true; }
  video.srcObject = flujo;
  try { await video.play(); } catch (e) { /* autoplay: se reintenta en el bucle */ }
  estado.textContent = 'Buscando…';

  const detector = new window.BarcodeDetector({ formats: ['pdf417'] });

  async function buscar() {
    if (!abierto) return;
    if (video.readyState >= 2) {
      try {
        const hallados = await detector.detect(video);
        const pdf = hallados.find((h) => h.format === 'pdf417' && h.rawValue);
        if (pdf) return entregar({ tipo: 'pdf417', cadena: pdf.rawValue });
      } catch (e) {
        /* un fotograma que no se pudo leer: se prueba con el siguiente */
      }
    }
    temporizador = setTimeout(buscar, 250);
  }
  buscar();
}

/** Ícono para los botones que abren el visor. */
export function iconoCamara() {
  return icono('camara', { tam: 22 });
}
