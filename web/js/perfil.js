// Quién usa este teléfono: nombre, rol y, si quiere, DNI y celular. No es un
// inicio de sesión: sin servidor, una contraseña no protegería nada.
// Es de este teléfono: vive en «pi3.perfil» y no va en las copias.

const CLAVE = 'pi3.perfil';
const CLAVE_OMITIDO = 'pi3.perfil.omitido';

export const ROLES = [
  ['presidenta', 'Presidenta'],
  ['tesorera', 'Tesorera'],
  ['apoyo', 'Apoyo']
];

/** @returns {{nombre: string, rol: string, dni: string, celular: string}} */
export function leerPerfil() {
  let p = null;
  try { p = JSON.parse(localStorage.getItem(CLAVE) || 'null'); } catch (e) { p = null; }
  const base = { nombre: '', rol: '', dni: '', celular: '' };
  return p && typeof p === 'object' ? { ...base, ...p } : base;
}

export function guardarPerfil(perfil) {
  try { localStorage.setItem(CLAVE, JSON.stringify(perfil)); } catch (e) { /* sin espacio: queda en pantalla */ }
}

/** ¿Ya dijo quién es? Con nombre y rol alcanza. */
export function perfilCompleto(p = leerPerfil()) {
  return !!(String(p.nombre || '').trim() && ROLES.some(([r]) => r === p.rol));
}

/** «Ahora no» en el Inicio: no volver a preguntar. */
export function perfilOmitido() {
  try { return localStorage.getItem(CLAVE_OMITIDO) === '1'; } catch (e) { return false; }
}
export function omitirPerfil() {
  try { localStorage.setItem(CLAVE_OMITIDO, '1'); } catch (e) { /* solo esta vez */ }
}
