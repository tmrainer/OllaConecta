// firebase-config.js — datos públicos del proyecto Firebase. No son secretos: los datos
// los protegen las reglas de nube/firestore.rules. Con projectId vacío, la app no
// carga Firebase y funciona solo en este teléfono (ver cuenta.js).

export const CONFIG_FIREBASE = {
  apiKey: '',
  authDomain: '',
  projectId: '',
  appId: ''
};

/** ¿Esta copia de la app tiene un proyecto Firebase configurado? */
export function hayCuentas() {
  return !!CONFIG_FIREBASE.projectId;
}
