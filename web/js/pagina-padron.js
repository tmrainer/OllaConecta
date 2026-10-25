// pagina-padron.js — controlador de padron.html.
// Solo cablea: la interfaz vive en ui-personas.js, ui-importar.js y ui-reportes.js.
// La inscripción por código de barras del DNI no se muestra hasta que funcione
// (escaneo-dni.js es todavía un esqueleto).

import { hoyIso, cuantos } from './modelo.js';
import { validarAfiliados } from './validaciones.js';
import {
  estado, cargar, alGuardar, guardarPronto, guardarYa, periodoPorDefecto,
  vigilarPestanas
} from './estado.js';
import {
  montarAltaPersona, pintarListaPersonas, pintarResumenEtario, montarFiltrosPersonas, elegirFiltro
} from './ui-personas.js';
import { montarImportacion } from './ui-importar.js';
import { montarExportarPadron } from './ui-reportes.js';
import { bloqueEnvio } from './ui-envio.js';
import {
  buscar, montarCabecera,
  pintarAvisosAlmacenamiento, aplicarHallazgos, irACampo, avisarAlSalirDelCampo,
  repintarConservandoVista, mostrarGuardado, crearElemento, icono
} from './ui.js';

cargar();
alGuardar(mostrarGuardado);

const periodo = periodoPorDefecto();

const ctx = {
  fechaRef: hoyIso(),
  busqueda: '',
  filtro: 'activos',
  conteo: null,
  inicio: periodo.inicio,
  fin: periodo.fin,
  estadoTexto: null,
  onCambio: () => { guardarPronto(); refrescar(); },
  onRepintar: () => { guardarPronto(); repintar(); }
};

function validar() {
  const hallazgos = validarAfiliados(estado.padron);
  aplicarHallazgos(buscar('#panel'), hallazgos, (c) => irACampo(c, {
    antesDeBuscar: () => {        // puede estar oculta por el filtro o la búsqueda
      ctx.busqueda = '';
      buscar('#buscarAfiliado').value = '';
      elegirFiltro(buscar('#lista-afiliados'), ctx, 'todas');
    }
  }));
}

function refrescar() {
  pintarResumenEtario(buscar('#resumen-etario'), ctx);
  validar();
}

function repintar() {
  pintarListaPersonas(buscar('#lista-afiliados'), ctx);
  refrescar();
}

montarCabecera('padron', cuantos(estado.padron.afiliados.length, 'persona'));
pintarAvisosAlmacenamiento(buscar('#avisos-sistema'));

ctx.conteo = buscar('#conteo-afiliados');
ctx.estadoTexto = buscar('#estado-guardado');

const fRef = buscar('#fechaRef');
fRef.value = ctx.fechaRef;
fRef.addEventListener('input', () => { ctx.fechaRef = fRef.value || hoyIso(); repintar(); });

montarFiltrosPersonas(buscar('#lista-afiliados'), ctx);
montarExportarPadron(ctx);
// A UNACEM va el padrón activo a hoy, con los días asistidos de las fechas de arriba.
bloqueEnvio(buscar('#envio-padron'), {
  tipo: 'padron', periodo: () => ({ inicio: ctx.inicio, fin: ctx.fin }),
  pista: 'Manda el padrón en Excel al correo de UNACEM, desde la nube de la olla, con los días asistidos entre esas fechas. Sale solo el 15 y el último día de cada mes.'
});

montarAltaPersona(ctx);

// Inscribir es de vez en cuando; consultar la lista, de todos los días. El alta
// es una ventana flotante: se abre con el botón verde de abajo (o desde el
// Inicio, que llega con #inscribir) y se queda abierta para inscribir a varias
// seguidas hasta «Cerrar». Escape también cierra (lo da <dialog>).
const alta = buscar('#bloque-alta');
function abrirInscripcion() {
  if (!alta.open) alta.showModal();
  const primero = buscar('#altaPaterno');
  if (primero) primero.focus();
}
buscar('#cerrar-alta').addEventListener('click', () => alta.close());
alta.addEventListener('close', () => {
  if (location.hash === '#inscribir') history.replaceState(null, '', location.pathname);
  buscar('.fab').focus();
});
document.body.classList.add('con-fab');
document.body.append(crearElemento('button', {
  type: 'button', clase: 'fab', 'aria-label': 'Inscribir a una persona', onclick: abrirInscripcion
}, [icono('mas', { tam: 26 }), crearElemento('span', { texto: 'Inscribir' })]));
if (location.hash === '#inscribir') abrirInscripcion();
addEventListener('hashchange', () => { if (location.hash === '#inscribir') abrirInscripcion(); });
montarImportacion(ctx);
repintar();
// Consolida la migración del padrón si la hubo. Cuando el teléfono queda libre:
// no hace falta para ver la pantalla, y escribir el padrón entero cuesta.
(window.requestIdleCallback || setTimeout)(() => guardarYa(), { timeout: 2000 });
vigilarPestanas(() => repintarConservandoVista(repintar));

avisarAlSalirDelCampo(validar);
