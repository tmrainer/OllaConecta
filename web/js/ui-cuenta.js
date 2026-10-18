// ui-cuenta.js — el bloque «Tu cuenta» de Tus datos: entrar con Google, ver
// en qué olla se está y aceptar una invitación.
//
// Al entrar, si el perfil del teléfono (perfil.js) está en blanco, se le
// proponen el nombre de la cuenta y el rol de la olla: solo llena lo vacío,
// nunca pisa lo que la persona escribió.

import { crearElemento } from './ui.js';
import {
  hayCuentas, quien, entrar, salir, pertenencia, aceptarInvitacion, explicarError, ROLES_OLLA
} from './cuenta.js';
import { leerPerfil, guardarPerfil } from './perfil.js';

const nombreRol = (rol) => (ROLES_OLLA.find(([r]) => r === rol) || [rol, rol])[1];

function proponerPerfil(yo, olla) {
  const p = leerPerfil();
  let cambio = false;
  if (!p.nombre && yo.nombre) { p.nombre = yo.nombre; cambio = true; }
  if (!p.rol && olla && ROLES_OLLA.some(([r]) => r === olla.rol)) { p.rol = olla.rol; cambio = true; }
  if (cambio) guardarPerfil(p);
}

/** Arma el bloque dentro de `contenedor` y lo vuelve a pintar tras cada acción. */
export function bloqueCuenta(contenedor) {
  const estado = crearElemento('p', { clase: 'estado-guardado', role: 'status', 'aria-live': 'polite' });

  const decir = (texto, esError = false) => {
    estado.textContent = texto;
    estado.classList.toggle('es-error', esError);
  };

  const intentar = async (accion, boton) => {
    boton.disabled = true;
    decir('Un momento…');
    try { await accion(); decir(''); await pintar(); } catch (e) { decir(explicarError(e), true); boton.disabled = false; }
  };

  async function pintar() {
    contenedor.textContent = '';
    if (!hayCuentas()) {
      contenedor.append(crearElemento('p', { clase: 'pista', texto: 'Esta versión todavía no tiene cuentas: funciona solo en este teléfono.' }));
      return;
    }
    const yo = await quien();
    if (!yo) {
      const boton = crearElemento('button', { type: 'button', clase: 'boton', texto: 'Entrar con Google' });
      boton.addEventListener('click', () => intentar(entrar, boton));
      contenedor.append(crearElemento('div', { clase: 'acciones' }, [boton]), estado);
      return;
    }
    contenedor.append(crearElemento('p', { 'data-leer': true }, [
      'Entraste como ', crearElemento('b', { texto: yo.nombre || yo.correo }), yo.nombre ? ` (${yo.correo}).` : '.'
    ]));
    let p = null;
    try { p = await pertenencia(); } catch (e) { contenedor.append(crearElemento('p', { clase: 'estado-guardado es-error', texto: explicarError(e) })); }
    if (p) {
      proponerPerfil(yo, p.olla);
      if (p.coordina) contenedor.append(crearElemento('p', { clase: 'dato-fuerte', texto: 'Coordinas el proyecto: ves todas las ollas.' }));
      if (p.olla) {
        contenedor.append(crearElemento('p', {}, [
          'Estás en la olla ', crearElemento('b', { texto: p.olla.nombre }), ` como ${nombreRol(p.olla.rol).toLowerCase()}.`
        ]));
      } else if (!p.coordina && !p.invitaciones.length) {
        contenedor.append(crearElemento('p', { clase: 'pista', texto:
          `Tu cuenta (${yo.correo}) todavía no está en ninguna olla. Pide a quien coordina que te invite con este correo.` }));
      }
      for (const inv of p.invitaciones) {
        const unirme = crearElemento('button', { type: 'button', clase: 'boton diminuto', texto: 'Unirme' });
        unirme.addEventListener('click', () => intentar(() => aceptarInvitacion(inv), unirme));
        contenedor.append(crearElemento('div', { clase: 'aviso-sistema' }, [
          crearElemento('p', {}, ['Te invitaron a la olla ', crearElemento('b', { texto: inv.ollaNombre }), ` como ${nombreRol(inv.rol).toLowerCase()}.`]),
          unirme
        ]));
      }
      if (p.coordina || (p.olla && p.olla.rol === 'presidenta')) {
        contenedor.append(crearElemento('div', { clase: 'acciones' }, [
          crearElemento('a', { clase: 'boton secundario', href: './coordinacion.html', texto: p.coordina ? 'Coordinación: ollas y personas' : 'Las personas de tu olla' })
        ]));
      }
    }
    const botonSalir = crearElemento('button', { type: 'button', clase: 'boton diminuto secundario', texto: 'Salir de la cuenta' });
    botonSalir.addEventListener('click', () => intentar(salir, botonSalir));
    contenedor.append(crearElemento('div', { clase: 'acciones' }, [botonSalir]), estado);
  }

  pintar().catch((e) => { contenedor.textContent = ''; contenedor.append(crearElemento('p', { clase: 'estado-guardado es-error', texto: explicarError(e) })); });
}
