// cuenta.js — la cuenta de Google de quien usa la app y a qué olla pertenece.
//
// Primera parte del sistema central: Firebase Auth
// dice quién es la persona y Firestore guarda las ollas, sus integrantes y
// las invitaciones. El padrón y la asistencia de la olla van y vienen de la
// nube en sincronizacion.js (§h); el B-1 y las entregas siguen en el teléfono.
//
// Firebase se carga solo cuando hay un proyecto configurado
// (firebase-config.js) y recién cuando una página lo pide: las demás páginas
// no lo tocan. La librería vive en vendor/firebase/ (copiada, sin CDN) y va
// en ARCHIVOS de sw.js, así que también carga sin internet. Solo el primer
// inicio de sesión necesita señal: después la sesión queda guardada en el
// teléfono, y Firestore responde con lo último que vio.
//
// En el APK, Google no deja iniciar sesión dentro del visor web: lo hace el
// complemento nativo (@capacitor-firebase/authentication, en
// window.Capacitor.Plugins) y aquí se le pasa la credencial a la librería,
// para que Firestore sepa quién es.
//
// Quién puede qué lo deciden las reglas de nube/firestore.rules, no este
// archivo: lo que hay aquí es lo que la app intenta.

import { CONFIG_FIREBASE, hayCuentas } from './firebase-config.js';
import { esAppNativa } from './avisos-nativos.js';
import { desvincularOlla } from './modelo.js';

export { hayCuentas };

/** Roles dentro de una olla (los mismos de perfil.js). */
export const ROLES_OLLA = [
  ['presidenta', 'Presidenta'],
  ['tesorera', 'Tesorera'],
  ['apoyo', 'Apoyo']
];

let cargando = null;

/** Carga la librería una sola vez. null si no hay proyecto configurado. */
export async function firebase() {
  if (!hayCuentas()) return null;
  if (!cargando) {
    cargando = (async () => {
      const [App, Auth, Fs] = await Promise.all([
        import('../vendor/firebase/firebase-app.js'),
        import('../vendor/firebase/firebase-auth.js'),
        import('../vendor/firebase/firebase-firestore.js')
      ]);
      const app = App.initializeApp(CONFIG_FIREBASE);
      // Copia local de lo leído: sin señal, se trabaja con lo último visto.
      const db = Fs.initializeFirestore(app, {
        localCache: Fs.persistentLocalCache({ tabManager: Fs.persistentMultipleTabManager() }),
        ignoreUndefinedProperties: true   // un campo sin valor en el padrón no debe frenar la subida
      });
      const auth = Auth.getAuth(app);
      await auth.authStateReady();
      return { auth, db, Auth, Fs };
    })();
  }
  return cargando;
}

function complementoNativo() {
  const C = typeof window !== 'undefined' ? window.Capacitor : null;
  return (C && C.Plugins && C.Plugins.FirebaseAuthentication) || null;
}

/** @returns {{uid: string, correo: string, nombre: string} | null} */
export async function quien() {
  const f = await firebase();
  const u = f && f.auth.currentUser;
  return u ? { uid: u.uid, correo: (u.email || '').toLowerCase(), nombre: u.displayName || '' } : null;
}

/** Abre el inicio de sesión con Google. Necesita internet. */
export async function entrar() {
  const f = await firebase();
  if (!f) throw new Error('Esta copia de la app no tiene cuentas configuradas.');
  const { auth, Auth } = f;
  if (esAppNativa()) {
    const P = complementoNativo();
    if (!P) throw new Error('Este APK no trae el inicio de sesión con Google.');
    const r = await P.signInWithGoogle();
    const cred = Auth.GoogleAuthProvider.credential(r.credential && r.credential.idToken);
    await Auth.signInWithCredential(auth, cred);
  } else {
    const proveedor = new Auth.GoogleAuthProvider();
    proveedor.setCustomParameters({ prompt: 'select_account' });
    await Auth.signInWithPopup(auth, proveedor);
  }
  return quien();
}

export async function salir() {
  const f = await firebase();
  if (!f) return;
  const P = complementoNativo();
  if (P) { try { await P.signOut(); } catch (e) { /* la sesión de la librería es la que importa */ } }
  await f.Auth.signOut(f.auth);
  desvincularOlla();   // sin cuenta no hay con qué subir; los datos del teléfono se quedan
}

/**
 * Dónde está la persona en el sistema: si coordina el proyecto, en qué olla
 * está y con qué rol, y qué invitaciones tiene pendientes.
 * @returns {Promise<{
 *   coordina: boolean,
 *   olla: {id: string, nombre: string, rol: string} | null,
 *   invitaciones: {ollaId: string, ollaNombre: string, rol: string, invitadoPor: string}[]
 * } | null>}  null si no entró
 */
export async function pertenencia() {
  const yo = await quien();
  if (!yo) return null;
  const { db, Fs } = await firebase();
  const [coord, miembros, invitaciones] = await Promise.all([
    Fs.getDoc(Fs.doc(db, 'coordinadores', yo.uid)),
    Fs.getDocs(Fs.query(Fs.collectionGroup(db, 'miembros'), Fs.where('uid', '==', yo.uid))),
    Fs.getDocs(Fs.query(Fs.collectionGroup(db, 'invitaciones'), Fs.where('correo', '==', yo.correo)))
  ]);
  const nombreDe = async (ollaId) => {
    const o = await Fs.getDoc(Fs.doc(db, 'ollas', ollaId));
    return o.exists() ? o.data().nombre : '(olla sin nombre)';
  };
  let olla = null;
  if (!miembros.empty) {
    const m = miembros.docs[0];
    const id = m.ref.parent.parent.id;
    olla = { id, nombre: await nombreDe(id), rol: m.data().rol };
  }
  const pendientes = [];
  for (const d of invitaciones.docs) {
    const ollaId = d.ref.parent.parent.id;
    pendientes.push({ ollaId, ollaNombre: await nombreDe(ollaId), rol: d.data().rol, invitadoPor: d.data().invitadoPor });
  }
  return { coordina: coord.exists(), olla, invitaciones: pendientes };
}

/**
 * Acepta una invitación: la persona pasa a ser integrante de esa olla con el
 * rol que le dieron, y la invitación se borra. Las reglas exigen que exista
 * una invitación para este correo con ese rol. Una sola olla por persona: si
 * ya está en una, no se acepta otra.
 */
export async function aceptarInvitacion(inv) {
  const yo = await quien();
  if (!yo) throw new Error('Primero hay que entrar.');
  const p = await pertenencia();
  if (p.olla) throw new Error(`Ya estás en la olla ${p.olla.nombre}. Para cambiarte, pide que te quiten de ella.`);
  const { db, Fs } = await firebase();
  const lote = Fs.writeBatch(db);
  lote.set(Fs.doc(db, 'ollas', inv.ollaId, 'miembros', yo.uid), {
    uid: yo.uid, nombre: yo.nombre, correo: yo.correo, rol: inv.rol,
    desde: Fs.serverTimestamp(), agregadoPor: inv.invitadoPor
  });
  lote.delete(Fs.doc(db, 'ollas', inv.ollaId, 'invitaciones', yo.correo));
  await lote.commit();
}

/** Texto para la persona cuando algo de la nube falla. */
export function explicarError(e) {
  const codigo = (e && e.code) || '';
  const mensaje = (e && e.message) || String(e);
  if (codigo === 'auth/popup-closed-by-user' || codigo === 'auth/cancelled-popup-request') return 'No se eligió ninguna cuenta.';
  if (codigo === 'auth/network-request-failed' || codigo === 'unavailable') return 'Sin internet. Para entrar la primera vez hace falta señal.';
  if (codigo === 'auth/popup-blocked') return 'El navegador bloqueó la ventana de Google. Permítela y vuelve a intentar.';
  if (codigo === 'permission-denied') return 'Tu cuenta no tiene permiso para esto.';
  // El complemento nativo del APK (la pantalla de Google del teléfono)
  // devuelve sus errores sin código y en inglés. Se traducen los dos que la
  // persona provoca: cerrar la pantalla sin elegir cuenta, y no tener señal.
  if (!codigo && /cancel/i.test(mensaje)) return 'No se eligió ninguna cuenta.';
  if (!codigo && typeof navigator !== 'undefined' && navigator.onLine === false) return 'Sin internet. Para entrar la primera vez hace falta señal.';
  return mensaje;
}
