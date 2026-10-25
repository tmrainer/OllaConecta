// pagina-coordinacion.js — controlador de coordinacion.html: ollas y personas.
// Cada cambio se vuelve a pedir a Firestore, para mostrar lo que quedó de verdad.

import { buscar, crearElemento, montarCabecera, pintarAvisosAlmacenamiento } from './ui.js';
import { hayCuentas, quien, pertenencia, explicarError, ROLES_OLLA } from './cuenta.js';
import { listarOllas, crearOlla, miembrosDe, invitacionesDe, invitar, cancelarInvitacion, quitarMiembro } from './ollas.js';
import { correoUnacem, guardarCorreoUnacem } from './envio.js';

montarCabecera('coordinacion', 'Ollas y personas');
pintarAvisosAlmacenamiento(buscar('#avisos-sistema'));

const raiz = buscar('#coordinacion');
const nombreRol = (rol) => (ROLES_OLLA.find(([r]) => r === rol) || [rol, rol])[1];

const parrafo = (texto, clase = '') => crearElemento('p', { clase, texto });
const estadoDe = () => crearElemento('p', { clase: 'estado-guardado', role: 'status', 'aria-live': 'polite' });

function conError(estado, e) {
  estado.textContent = explicarError(e);
  estado.classList.add('es-error');
}

/** Integrantes e invitaciones de una olla, con invitar y quitar. */
async function fichaOlla(olla, yo) {
  const bloque = crearElemento('section', { clase: 'bloque', 'aria-labelledby': `titulo-olla-${olla.id}` }, [
    crearElemento('h2', { id: `titulo-olla-${olla.id}`, texto: olla.nombre })
  ]);
  if (olla.distrito) bloque.append(parrafo(olla.distrito, 'pista'));
  const estado = estadoDe();
  const repintar = () => fichaOlla(olla, yo).then((nueva) => bloque.replaceWith(nueva));
  const accion = async (hacer, boton) => {
    boton.disabled = true;
    try { await hacer(); await repintar(); } catch (e) { conError(estado, e); boton.disabled = false; }
  };

  let miembros = []; let invitaciones = [];
  try { [miembros, invitaciones] = await Promise.all([miembrosDe(olla.id), invitacionesDe(olla.id)]); } catch (e) { conError(estado, e); }

  const lista = crearElemento('ul', { clase: 'lista-datos personas' });
  for (const m of miembros) {
    const quitar = crearElemento('button', { type: 'button', clase: 'boton diminuto peligro', texto: 'Quitar', hidden: m.uid === yo.uid });
    quitar.addEventListener('click', () => {
      if (confirm(`¿Quitar a ${m.nombre || m.correo} de la olla ${olla.nombre}?`)) accion(() => quitarMiembro(olla.id, m.uid), quitar);
    });
    lista.append(crearElemento('li', { clase: 'persona-fila' }, [
      crearElemento('div', {}, [
        crearElemento('b', { texto: m.nombre || m.correo }), ` · ${nombreRol(m.rol)}`,
        m.nombre ? crearElemento('p', { clase: 'pista', texto: m.correo }) : null
      ]),
      quitar
    ]));
  }
  for (const inv of invitaciones) {
    const cancelar = crearElemento('button', { type: 'button', clase: 'boton diminuto secundario', texto: 'Cancelar' });
    cancelar.addEventListener('click', () => accion(() => cancelarInvitacion(olla.id, inv.correo), cancelar));
    lista.append(crearElemento('li', { clase: 'persona-fila' }, [
      crearElemento('div', {}, [crearElemento('b', { texto: inv.correo }), ` · ${nombreRol(inv.rol)}`, parrafo('Invitación pendiente: todavía no entró', 'pista')]),
      cancelar
    ]));
  }
  bloque.append(crearElemento('h3', { texto: 'Personas' }));
  bloque.append(miembros.length || invitaciones.length ? lista : parrafo('Todavía no hay nadie en esta olla.', 'pista'));

  // Invitar: el correo de Google de la persona y su rol.
  const correo = crearElemento('input', { type: 'email', id: `correo-${olla.id}`, autocomplete: 'off', placeholder: 'nombre@gmail.com', required: true });
  const roles = crearElemento('div', { clase: 'opciones' });
  for (const [valor, texto] of ROLES_OLLA) {
    const radio = crearElemento('input', { type: 'radio', name: `rol-${olla.id}`, value: valor, id: `rol-${olla.id}-${valor}` });
    if (valor === 'apoyo') radio.checked = true;
    roles.append(crearElemento('label', { clase: 'opcion', for: radio.id }, [radio, ` ${texto}`]));
  }
  const invitarBoton = crearElemento('button', { type: 'button', clase: 'boton', texto: 'Invitar' });
  invitarBoton.addEventListener('click', () => {
    const c = correo.value.trim().toLowerCase();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(c)) { conError(estado, new Error('Escribe un correo completo, como nombre@gmail.com.')); correo.focus(); return; }
    const rol = roles.querySelector('input:checked').value;
    accion(() => invitar(olla.id, c, rol), invitarBoton);
  });
  bloque.append(
    crearElemento('h3', { texto: 'Invitar a alguien' }),
    parrafo('Escribe el correo con el que entra a Google en su teléfono. Cuando entre a la app, verá la invitación y se unirá.', 'pista'),
    crearElemento('div', { clase: 'campo' }, [crearElemento('label', { for: correo.id, texto: 'Correo de Google' }), correo]),
    crearElemento('fieldset', { style: 'margin-top:10px' }, [crearElemento('legend', { texto: 'Su rol en la olla' }), roles]),
    crearElemento('div', { clase: 'acciones', style: 'margin-top:12px' }, [invitarBoton]),
    estado
  );
  return bloque;
}

async function vistaCoordinacion(yo) {
  const bloque = crearElemento('section', { clase: 'bloque', 'aria-labelledby': 'titulo-ollas' }, [
    crearElemento('h2', { id: 'titulo-ollas', texto: 'Ollas' })
  ]);
  const estado = estadoDe();
  let ollas = [];
  try { ollas = await listarOllas(); } catch (e) { conError(estado, e); }

  const fichas = crearElemento('div');
  const lista = crearElemento('div', { clase: 'acciones' });
  for (const o of ollas) {
    const boton = crearElemento('button', { type: 'button', clase: 'boton secundario', texto: o.nombre, 'aria-pressed': 'false' });
    boton.addEventListener('click', async () => {
      for (const b of lista.querySelectorAll('button')) { b.classList.remove('activo'); b.setAttribute('aria-pressed', 'false'); }
      boton.classList.add('activo'); boton.setAttribute('aria-pressed', 'true');
      fichas.textContent = '';
      fichas.append(await fichaOlla(o, yo));
    });
    lista.append(boton);
  }
  bloque.append(ollas.length ? parrafo('Toca una olla para ver a su gente.', 'pista') : parrafo('Todavía no hay ollas. Crea la primera abajo.', 'pista'), lista);

  // Crear una olla.
  const nombre = crearElemento('input', { type: 'text', id: 'olla-nombre', autocomplete: 'off', placeholder: 'Olla común Las Flores', required: true });
  const distrito = crearElemento('input', { type: 'text', id: 'olla-distrito', autocomplete: 'off', placeholder: 'Villa María del Triunfo' });
  const crear = crearElemento('button', { type: 'button', clase: 'boton', texto: 'Crear la olla' });
  crear.addEventListener('click', async () => {
    if (!nombre.value.trim()) { conError(estado, new Error('Ponle un nombre a la olla.')); nombre.focus(); return; }
    crear.disabled = true;
    try {
      await crearOlla({ nombre: nombre.value, distrito: distrito.value });
      raiz.textContent = '';
      await pintar();
    } catch (e) { conError(estado, e); crear.disabled = false; }
  });
  bloque.append(
    crearElemento('h3', { texto: 'Nueva olla' }),
    crearElemento('div', { clase: 'campo' }, [crearElemento('label', { for: nombre.id, texto: 'Nombre' }), nombre]),
    crearElemento('div', { clase: 'campo', style: 'margin-top:10px' }, [crearElemento('label', { for: distrito.id, texto: 'Distrito (opcional)' }), distrito]),
    crearElemento('div', { clase: 'acciones', style: 'margin-top:12px' }, [crear]),
    estado
  );
  raiz.append(bloque, fichas, await bloqueEnvioUnacem());
}

/** A qué correo manda el script (ajustes/envio). Para probar, el propio. */
async function bloqueEnvioUnacem() {
  const bloque = crearElemento('section', { clase: 'bloque', 'aria-labelledby': 'titulo-envio' }, [
    crearElemento('h2', { id: 'titulo-envio', texto: 'Envío a UNACEM' }),
    parrafo('A este correo llegan, de todas las ollas, la asistencia de cada día (la que pidan desde la app o, si nadie la pide, sola a las 9 de la noche) ' +
      'y el padrón el 15 y a fin de mes. Para probar, pon el tuyo. Lleva nombres y DNI: solo la dirección acordada con UNACEM.', 'pista')
  ]);
  const estado = estadoDe();
  const correo = crearElemento('input', { type: 'email', id: 'correo-unacem', autocomplete: 'off', placeholder: 'nombre@unacem.com.pe' });
  try { correo.value = await correoUnacem(); } catch (e) { conError(estado, e); }
  const guardar = crearElemento('button', { type: 'button', clase: 'boton', texto: 'Guardar el correo' });
  guardar.addEventListener('click', async () => {
    const c = correo.value.trim().toLowerCase();
    if (c && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(c)) { conError(estado, new Error('Escribe un correo completo, como nombre@unacem.com.pe.')); correo.focus(); return; }
    guardar.disabled = true;
    try {
      await guardarCorreoUnacem(c);
      estado.classList.remove('es-error');
      estado.textContent = c ? `Listo: los envíos van a ${c}.` : 'Sin correo: no se envía nada hasta que pongas uno.';
    } catch (e) { conError(estado, e); }
    guardar.disabled = false;
  });
  bloque.append(
    crearElemento('div', { clase: 'campo' }, [crearElemento('label', { for: correo.id, texto: 'Correo de destino' }), correo]),
    crearElemento('div', { clase: 'acciones', style: 'margin-top:12px' }, [guardar]),
    estado
  );
  return bloque;
}

async function pintar() {
  const aviso = (titulo, texto) => raiz.append(crearElemento('section', { clase: 'bloque' }, [
    crearElemento('h2', { texto: titulo }), parrafo(texto, 'pista'),
    crearElemento('div', { clase: 'acciones' }, [crearElemento('a', { clase: 'boton secundario', href: './datos.html#cuenta', texto: 'Ir a Tus datos' })])
  ]));
  if (!hayCuentas()) return aviso('Sin cuentas', 'Esta versión todavía no tiene cuentas: funciona solo en este teléfono.');
  const yo = await quien();
  if (!yo) return aviso('Primero entra con tu cuenta', 'En Tus datos, «Entrar con Google». Después vuelve aquí.');
  const p = await pertenencia();
  if (p.coordina) return vistaCoordinacion(yo);
  if (p.olla && p.olla.rol === 'presidenta') return raiz.append(await fichaOlla(p.olla, yo));
  return aviso('Esta pantalla es para quien coordina', p.olla
    ? `Estás en la olla ${p.olla.nombre} como ${nombreRol(p.olla.rol).toLowerCase()}. Invita y quita gente la presidenta o quien coordina el proyecto.`
    : 'Tu cuenta todavía no está en ninguna olla.');
}

pintar().catch((e) => raiz.append(crearElemento('p', { clase: 'estado-guardado es-error', texto: explicarError(e) })));
