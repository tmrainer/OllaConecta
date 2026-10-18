// firebase-config.js — datos públicos del proyecto Firebase de la app.
//
// Se copian de la consola de Firebase (Configuración del proyecto → Tus apps →
// app web). No son secretos: identifican el proyecto, igual que una dirección.
// Lo que protege los datos son las reglas de nube/firestore.rules, que se
// cumplen en los servidores de Google.
//
// Mientras projectId esté vacío, la app no carga Firebase ni se conecta a
// nada: funciona como siempre, solo en este teléfono (ver cuenta.js).

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
