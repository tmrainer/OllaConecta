// ollas.js — lo que hace quien coordina: crear ollas, invitar y quitar gente.
//
// Lo usa coordinacion.html. Quien coordina el proyecto ve todas las ollas;
// una presidenta, solo la suya (puede invitar y quitar a su gente). Las
// reglas de nube/firestore.rules son las que lo imponen; aquí solo se pide.

import { firebase, quien } from './cuenta.js';

/** @returns {Promise<{id: string, nombre: string, distrito: string}[]>} */
export async function listarOllas() {
  const { db, Fs } = await firebase();
  const r = await Fs.getDocs(Fs.query(Fs.collection(db, 'ollas'), Fs.orderBy('nombre')));
  return r.docs.map((d) => ({ id: d.id, ...d.data() }));
}

export async function crearOlla({ nombre, distrito }) {
  const yo = await quien();
  const { db, Fs } = await firebase();
  const ref = await Fs.addDoc(Fs.collection(db, 'ollas'), {
    nombre: nombre.trim(), distrito: (distrito || '').trim(),
    creadaEn: Fs.serverTimestamp(), creadaPor: yo.uid
  });
  return ref.id;
}

/** @returns {Promise<{uid: string, nombre: string, correo: string, rol: string}[]>} */
export async function miembrosDe(ollaId) {
  const { db, Fs } = await firebase();
  const r = await Fs.getDocs(Fs.collection(db, 'ollas', ollaId, 'miembros'));
  return r.docs.map((d) => d.data()).sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
}

/** @returns {Promise<{correo: string, rol: string}[]>} */
export async function invitacionesDe(ollaId) {
  const { db, Fs } = await firebase();
  const r = await Fs.getDocs(Fs.collection(db, 'ollas', ollaId, 'invitaciones'));
  return r.docs.map((d) => d.data()).sort((a, b) => a.correo.localeCompare(b.correo));
}

/** Invita a un correo de Google a la olla con un rol. El correo es el id. */
export async function invitar(ollaId, correo, rol) {
  const yo = await quien();
  const { db, Fs } = await firebase();
  const limpio = correo.trim().toLowerCase();
  await Fs.setDoc(Fs.doc(db, 'ollas', ollaId, 'invitaciones', limpio), {
    correo: limpio, rol, invitadoPor: yo.uid, invitadoEn: Fs.serverTimestamp()
  });
}

export async function cancelarInvitacion(ollaId, correo) {
  const { db, Fs } = await firebase();
  await Fs.deleteDoc(Fs.doc(db, 'ollas', ollaId, 'invitaciones', correo));
}

export async function quitarMiembro(ollaId, uid) {
  const { db, Fs } = await firebase();
  await Fs.deleteDoc(Fs.doc(db, 'ollas', ollaId, 'miembros', uid));
}
