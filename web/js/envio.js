// envio.js — pedir el envío a UNACEM y saber si salió. La app no manda el correo:
// deja una solicitud en ollas/{olla}/envios y el script de nube/apps-script/ la
// procesa y anota el resultado.

import { firebase, quien } from './cuenta.js';
import { ollaVinculada } from './modelo.js';

/**
 * Deja la solicitud. No espera la respuesta de la nube: sin señal no
 * llegaría nunca, y Firestore igual la guarda y la manda después. Si la nube
 * la rechaza (sin permiso), la promesa falla.
 * @param {{tipo: 'asistencia'|'padron', fecha: string, inicio: string, fin: string}} pedido
 */
export async function pedirEnvio({ tipo, fecha, inicio, fin }) {
  const yo = await quien();
  const olla = ollaVinculada();
  if (!yo || !olla) throw new Error('Hace falta entrar con la cuenta y estar en una olla.');
  const { db, Fs } = await firebase();
  return Fs.addDoc(Fs.collection(db, 'ollas', olla.id, 'envios'), {
    tipo, fecha, inicio, fin, modo: 'manual', estado: 'pendiente', pedidoPor: yo.uid, pedidoEn: Fs.serverTimestamp()
  });
}

/**
 * Los envíos de un tipo y una fecha, del más nuevo al más viejo, al instante
 * y cada vez que cambian (también cuando el script los marca). `enCamino`:
 * el teléfono todavía no logró entregarlo a la nube.
 * @param {{tipo: string, fecha: string}} de
 * @param {(envios: ?Object[], error?: Error) => void} alCambiar
 * @returns {Promise<() => void>}  deja de escuchar
 */
export async function vigilarEnvios({ tipo, fecha }, alCambiar) {
  const olla = ollaVinculada();
  const { db, Fs } = await firebase();
  const consulta = Fs.query(Fs.collection(db, 'ollas', olla.id, 'envios'), Fs.where('tipo', '==', tipo), Fs.where('fecha', '==', fecha));
  return Fs.onSnapshot(consulta, { includeMetadataChanges: true }, (r) => {
    const cuando = (d) => (d.pedidoEn && d.pedidoEn.toMillis ? d.pedidoEn.toMillis() : 0);
    const envios = r.docs.map((d) => ({ id: d.id, enCamino: d.metadata.hasPendingWrites, ...d.data({ serverTimestamps: 'estimate' }) }))
      .sort((a, b) => cuando(b) - cuando(a));
    alCambiar(envios);
  }, (e) => alCambiar(null, e));
}

/** A qué correo manda el script. '' si quien coordina todavía no lo puso. */
export async function correoUnacem() {
  const { db, Fs } = await firebase();
  const d = await Fs.getDoc(Fs.doc(db, 'ajustes', 'envio'));
  return d.exists() && typeof d.data().correo === 'string' ? d.data().correo : '';
}

/** Solo quien coordina (lo imponen las reglas). */
export async function guardarCorreoUnacem(correo) {
  const yo = await quien();
  const { db, Fs } = await firebase();
  await Fs.setDoc(Fs.doc(db, 'ajustes', 'envio'), {
    correo: String(correo || '').trim().toLowerCase(), actualizadoPor: yo.uid, actualizadoEn: Fs.serverTimestamp()
  });
}
