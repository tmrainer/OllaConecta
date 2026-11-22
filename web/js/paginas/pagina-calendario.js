// pagina-calendario.js — entregas del formato y cuenta regresiva.

import {
  CLAVES, leer, guardar, calendarioVacio, nuevaEntrega, aDdMmAa, hoyIso, debounce, esIso, cuantos, fechaLarga
} from '../nucleo/modelo.js';
import { alertaProximaEntrega, agendaEntregas, PERIODICIDADES } from '../nucleo/alertas.js';
import { validarCalendario, validarRendicion } from '../nucleo/validaciones.js';
import {
  buscar, crearElemento, montarCabecera, pintarPanel,
  marcarCampo, pintarAvisosAlmacenamiento, avisarAlSalirDelCampo, mostrarGuardado, estadoVacio
} from '../interfaz/ui.js';

let calendario = leer(CLAVES.calendario, calendarioVacio());
if (!Array.isArray(calendario.entregas)) calendario.entregas = [];
if (!Array.isArray(calendario.diasDeAviso)) calendario.diasDeAviso = [7, 3, 1];

// Hay un cambio que el guardado diferido todavía no escribió.
let pendiente = false;

function escribir() {
  pendiente = false;
  const ok = guardar(CLAVES.calendario, calendario);
  mostrarGuardado(ok);
  return ok;
}

const guardarDiferido = debounce(() => { if (pendiente) escribir(); }, 400);

// Como en las otras páginas: salir antes de los 400 ms no pierde lo último.
addEventListener('pagehide', () => { if (pendiente) escribir(); });
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'hidden' && pendiente) escribir();
});

function cambio() {
  pendiente = true;
  guardarDiferido();
  pintarBanner();
  pintarCuentaRegresiva();
  pintarAgenda();
  refrescarValidacion();
}

function pintarBanner() {
  // Aquí no hay franja: lo dice, más grande, la cuenta regresiva de arriba.
  buscar('#banner').textContent = '';
}

const ETIQUETA_ESTADO = {
  pendiente: 'Pendiente',
  entregada: 'Entregada',
  observada: 'Observada',
  conforme: 'Conforme'
};

function registroDe(fechaLimite) {
  let reg = calendario.entregas.find((e) => e.fechaLimite === fechaLimite);
  if (!reg) {
    reg = nuevaEntrega(fechaLimite);
    calendario.entregas.push(reg);
  }
  return reg;
}

/**
 * Cómo va el B-1 que hay que entregar: lo que falta, o que ya está listo. Lo
 * que se entrega es el B-1, así que la cuenta regresiva sola no basta.
 */
function pintarEstadoB1() {
  const caja = buscar('#estado-b1');
  caja.textContent = '';
  const r = leer(CLAVES.rendiciones, [])[0];
  if (!r) return;
  const hallazgos = validarRendicion(r);
  const faltan = hallazgos.filter((x) => x.severidad === 'error' && x.falta).length;
  const mal = hallazgos.filter((x) => x.severidad === 'error' && !x.falta).length;
  const listo = faltan === 0 && mal === 0;
  const p = r.periodo || {};
  const nombre = (p.etiqueta || '').trim() ? ` de ${p.etiqueta.trim()}` : '';
  const partes = [];
  if (faltan) partes.push(faltan === 1 ? 'falta 1 dato' : `faltan ${faltan} datos`);
  if (mal) partes.push(`${mal} por corregir`);
  caja.append(crearElemento('div', { clase: `estado-b1 ${listo ? 'listo' : 'pendiente'}` }, [
    crearElemento('span', {
      texto: listo ? `El Formato B-1${nombre} está listo para entregar.` : `Al Formato B-1${nombre} le ${partes.join(' y ')}.`
    }),
    crearElemento('a', { clase: 'boton diminuto secundario', href: './formato-b1.html', texto: listo ? 'Ver el B-1' : 'Completar el B-1' })
  ]));
}

/** Lo primero que se ve: cuántos días faltan, grande, como en un reloj. */
function pintarCuentaRegresiva() {
  const caja = buscar('#cuenta-regresiva');
  caja.textContent = '';
  const a = alertaProximaEntrega(calendario, hoyIso());
  if (!a || !a.configurado) return;
  const dias = a.diasRestantes;
  const largo = { semanal: 7, quincenal: 15, mensual: 30 }[calendario.periodicidad] || 30;
  const lleno = dias < 0 ? 100 : Math.round(Math.max(0, Math.min(1, 1 - dias / largo)) * 100);
  const numero = dias < 0 ? String(-dias) : String(dias);
  const unidad = dias < 0 ? (dias === -1 ? 'día de atraso' : 'días de atraso')
    : dias === 0 ? 'hoy' : dias === 1 ? 'día' : 'días';
  caja.append(crearElemento('div', { clase: `reloj-entrega ${a.nivel}`, 'data-leer': '' }, [
    crearElemento('div', { clase: 'reloj-circulo', style: `--lleno:${lleno}%`, 'aria-hidden': 'true' }, [
      crearElemento('div', {}, [
        crearElemento('b', { texto: dias === 0 ? '¡Hoy!' : numero }),
        dias === 0 ? null : crearElemento('span', { texto: ' ' + unidad })
      ])
    ]),
    crearElemento('p', { clase: 'reloj-texto', texto: dias < 0
      ? `La entrega del ${aDdMmAa(a.fechaLimite)} venció hace ${cuantos(-dias, 'día')}.`
      : dias === 0 ? `Hoy vence la entrega del B-1 (${aDdMmAa(a.fechaLimite)}).`
        : `Faltan ${cuantos(dias, 'día')} para entregar el B-1: ${fechaLarga(a.fechaLimite).toLowerCase()}.` })
  ]));
}

function pintarAgenda() {
  const caja = buscar('#agenda');
  caja.textContent = '';

  if (!esIso(calendario.fechaLimiteBase)) {
    caja.append(estadoVacio({
      icono: 'calendario', titulo: 'Todavía no hay fechas',
      texto: 'Pon abajo, en «Cada cuánto entregas», una fecha de entrega que conozcas. Aquí verás las próximas.'
    }));
    return;
  }

  const lista = crearElemento('ul', { clase: 'agenda' });
  for (const item of agendaEntregas(calendario, hoyIso(), 6)) {
    const reg = calendario.entregas.find((e) => e.fechaLimite === item.fechaLimite);
    const estado = reg ? reg.estado : 'pendiente';

    const texto = item.diasRestantes < 0
      ? `hace ${cuantos(Math.abs(item.diasRestantes), 'día')}`
      : item.diasRestantes === 0 ? 'es hoy'
        : item.diasRestantes === 1 ? 'falta 1 día' : `faltan ${item.diasRestantes} días`;

    const selEstado = crearElemento('select', {}, Object.entries(ETIQUETA_ESTADO)
      .map(([v, t]) => crearElemento('option', { value: v, texto: t })));
    selEstado.value = estado;
    selEstado.addEventListener('change', () => {
      registroDe(item.fechaLimite).estado = selEstado.value;
      cambio();
    });

    lista.append(crearElemento('li', { clase: `agenda-item ${item.nivel}` }, [
      crearElemento('span', { clase: 'agenda-fecha', texto: aDdMmAa(item.fechaLimite) }),
      crearElemento('span', { clase: 'agenda-dias', texto }),
      crearElemento('span', { clase: 'agenda-estado' }, [selEstado])
    ]));
  }
  caja.append(lista);
}

function refrescarValidacion() {
  const hallazgos = validarCalendario(calendario);
  pintarPanel(buscar('#panel'), hallazgos, { alIrA: irACampo });
  for (const caja of document.querySelectorAll('[data-campo]')) {
    marcarCampo(caja, hallazgos.filter((x) => x.campo === caja.dataset.campo));
  }
}

function irACampo(campo) {
  const caja = document.querySelector(`[data-campo="${CSS.escape(campo)}"]`);
  if (!caja) return;
  caja.scrollIntoView({ block: 'center' });
  const control = caja.querySelector('input, select');
  if (control) control.focus({ preventScroll: true });
}

montarCabecera('calendario', 'Fechas de entrega del B-1');
pintarAvisosAlmacenamiento(buscar('#avisos-sistema'));

const selPer = buscar('#periodicidad');
selPer.value = calendario.periodicidad || 'mensual';
selPer.addEventListener('change', () => { calendario.periodicidad = selPer.value; cambio(); });

const fBase = buscar('#fechaBase');
fBase.value = calendario.fechaLimiteBase || '';
fBase.addEventListener('input', () => { calendario.fechaLimiteBase = fBase.value; cambio(); });

// Casillas, no números separados por comas: escribir «7, 3, 1» era fácil de
// equivocar. Un valor guardado que no está entre estos (de antes) se conserva.
const OPCIONES_AVISO = [[1, '1 día antes'], [3, '3 días antes'], [7, '7 días antes'], [15, '15 días antes']];
const cajaAviso = buscar('#dias-aviso');
const msgAviso = buscar('#dias-aviso-msg');
for (const [dias, texto] of OPCIONES_AVISO) {
  const c = crearElemento('input', { type: 'checkbox', id: `aviso-${dias}`, value: String(dias) });
  c.checked = calendario.diasDeAviso.includes(dias);
  c.addEventListener('change', () => {
    const otros = calendario.diasDeAviso.filter((n) => n !== dias);
    if (!c.checked && otros.length === 0) {
      c.checked = true;                    // sin ninguno no habría aviso previo
      msgAviso.textContent = 'Deja marcado al menos uno.';
      return;
    }
    msgAviso.textContent = '';
    calendario.diasDeAviso = (c.checked ? [...otros, dias] : otros).sort((a, b) => b - a);
    pintarAvisoElegido();
    cambio();
  });
  cajaAviso.append(crearElemento('label', { clase: 'opcion', for: c.id }, [c, texto]));
}

/** La barra «Avisarme», cerrada: «1 día antes», «1, 3 y 7 días antes». Se marcan varios, así que no se cierra sola. */
function pintarAvisoElegido() {
  const dias = [...calendario.diasDeAviso].sort((a, b) => a - b);
  const lista = dias.length > 1 ? `${dias.slice(0, -1).join(', ')} y ${dias[dias.length - 1]}` : String(dias[0]);
  buscar('#aviso-elegido').textContent = dias.length === 0 ? 'Sin aviso'
    : dias.length === 1 && dias[0] === 1 ? '1 día antes' : `${lista} días antes`;
}
pintarAvisoElegido();

// PERIODICIDADES se usa para que el texto de la UI y el cálculo no se separen.
for (const [clave, , detalle] of PERIODICIDADES) {
  const opt = [...selPer.options].find((o) => o.value === clave);
  if (opt) opt.title = detalle;
}

pintarBanner();
pintarCuentaRegresiva();
pintarEstadoB1();
pintarAgenda();
refrescarValidacion();

avisarAlSalirDelCampo(refrescarValidacion);
