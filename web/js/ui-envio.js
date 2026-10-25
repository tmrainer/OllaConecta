// El botón «Enviar a UNACEM» y el estado del envío de hoy (en camino, pendiente, enviado,
// fallo). Solo aparece con olla vinculada y cuenta abierta; sin señal, la solicitud espera.

import { crearElemento, icono } from './ui.js';
import { hayCuentas, quien, explicarError } from './cuenta.js';
import { ollaVinculada, hoyIso } from './modelo.js';
import { pedirEnvio, vigilarEnvios, correoUnacem } from './envio.js';

const dos = (n) => String(n).padStart(2, '0');
const hora = (t) => { const d = t && t.toDate ? t.toDate() : null; return d ? `${dos(d.getHours())}:${dos(d.getMinutes())}` : ''; };

/**
 * @param {HTMLElement} caja
 * @param {{ tipo: 'asistencia'|'padron', periodo: () => {inicio: string, fin: string}, pista: string }} op
 *   periodo: qué fechas lleva el Excel (se lee al tocar); pista: qué manda y cuándo sale solo
 */
export async function bloqueEnvio(caja, { tipo, periodo, pista }) {
  caja.textContent = '';
  if (!hayCuentas() || !ollaVinculada()) return;
  if (!(await quien())) return;

  const hoy = hoyIso();
  const boton = crearElemento('button', { type: 'button', clase: 'boton secundario', id: `enviar-${tipo}` }, [icono('nube', { tam: 20 }), 'Enviar a UNACEM']);
  const estado = crearElemento('p', { clase: 'estado-guardado', role: 'status', 'aria-live': 'polite' });
  caja.append(crearElemento('div', { clase: 'acciones' }, [boton]), estado, crearElemento('p', { clase: 'pista', texto: pista }));

  const decir = (texto, esError = false) => { estado.textContent = texto; estado.classList.toggle('es-error', esError); };

  let correo = '';
  try { correo = await correoUnacem(); } catch (e) { /* sin señal y sin copia: se intenta igual */ }
  if (!correo) { boton.disabled = true; decir('Falta el correo de UNACEM: lo pone quien coordina, en Coordinación.'); }

  boton.addEventListener('click', () => {
    boton.disabled = true;
    decir('Pidiendo el envío…');
    pedirEnvio({ tipo, fecha: hoy, ...periodo() }).catch((e) => { decir(explicarError(e), true); boton.disabled = false; });
  });

  try {
    await vigilarEnvios({ tipo, fecha: hoy }, (envios, e) => {
      if (!envios) { decir(explicarError(e), true); return; }
      const u = envios[0];
      if (!u) { if (correo) decir(''); return; }
      boton.disabled = !correo || u.estado === 'pendiente';
      if (u.estado === 'enviado') decir(`Enviado hoy a las ${hora(u.enviadoEn)} a ${u.para}.`);
      else if (u.estado === 'fallo') decir(`No se pudo enviar: ${u.error || 'sin detalle'}. Vuelve a intentar.`, true);
      else if (u.enCamino) decir('Pedido. Sale cuando haya señal.');
      else decir(`Pedido a las ${hora(u.pedidoEn)}. Sale en unos minutos` + (u.error ? `; el primer intento falló (${u.error}) y se vuelve a intentar.` : '.'), !!u.error);
    });
  } catch (e) {
    decir(explicarError(e), true);
  }
}
