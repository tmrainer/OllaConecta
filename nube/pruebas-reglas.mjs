// pruebas-reglas.mjs — prueba firestore.rules contra el emulador de Firestore.
//
//   npm run pruebas      (levanta el emulador, corre esto y lo apaga)
//
// Cada caso dice lo que una cuenta intenta y si las reglas lo dejan. Las
// cuentas son ficticias: «coordina» coordina el proyecto; «ana» es la
// presidenta de la olla Las Flores; «beto», su apoyo; «carla» no está en
// ninguna olla y «dora» tiene una invitación pendiente. La olla Los Pinos
// existe para comprobar que nadie ve la de al lado.

import fs from 'node:fs';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import {
  doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc, addDoc, collection, collectionGroup, query, where,
  writeBatch, serverTimestamp, deleteField, setLogLevel
} from 'firebase/firestore';

// Cada intento frenado lo registra la librería como error: aquí es lo esperado.
setLogLevel('silent');

const entorno = await initializeTestEnvironment({
  projectId: 'demo-ollaconecta',
  firestore: { rules: fs.readFileSync(new URL('./firestore.rules', import.meta.url), 'utf8'), host: '127.0.0.1', port: 8080 }
});

const cuenta = (uid, correo) => entorno.authenticatedContext(uid, { email: correo, email_verified: true }).firestore();
const coordina = cuenta('coordina', 'coordina@gmail.com');
const ana = cuenta('ana', 'ana@gmail.com');
const beto = cuenta('beto', 'beto@gmail.com');
const carla = cuenta('carla', 'carla@gmail.com');
const dora = cuenta('dora', 'dora@gmail.com');
const nadie = entorno.unauthenticatedContext().firestore();

await entorno.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await setDoc(doc(db, 'coordinadores/coordina'), { desde: serverTimestamp() });
  await setDoc(doc(db, 'ollas/flores'), { nombre: 'Las Flores', distrito: 'VMT', creadaEn: serverTimestamp(), creadaPor: 'coordina' });
  await setDoc(doc(db, 'ollas/pinos'), { nombre: 'Los Pinos', distrito: 'VMT', creadaEn: serverTimestamp(), creadaPor: 'coordina' });
  const miembro = (uid, correo, rol) => ({ uid, nombre: uid, correo, rol, desde: serverTimestamp(), agregadoPor: 'coordina' });
  await setDoc(doc(db, 'ollas/flores/miembros/ana'), miembro('ana', 'ana@gmail.com', 'presidenta'));
  await setDoc(doc(db, 'ollas/flores/miembros/beto'), miembro('beto', 'beto@gmail.com', 'apoyo'));
  await setDoc(doc(db, 'ollas/pinos/miembros/elsa'), miembro('elsa', 'elsa@gmail.com', 'presidenta'));
  await setDoc(doc(db, 'ollas/flores/invitaciones/dora@gmail.com'), { correo: 'dora@gmail.com', rol: 'tesorera', invitadoPor: 'ana', invitadoEn: serverTimestamp() });
  await setDoc(doc(db, 'ollas/flores/personas/p1'), { id: 'p1', nombres: 'Rosa', actualizadoPor: 'ana', actualizadoEn: serverTimestamp() });
  await setDoc(doc(db, 'ollas/flores/dias/2026-10-01'), { id: 'd1', fecha: '2026-10-01', nota: '', marcas: { p1: { tipoMenu: 'normal', por: 'ana', en: serverTimestamp() } }, actualizadoPor: 'ana', actualizadoEn: serverTimestamp() });
  await setDoc(doc(db, 'ollas/pinos/personas/p9'), { id: 'p9', nombres: 'Elsa', actualizadoPor: 'elsa', actualizadoEn: serverTimestamp() });
});

let pasan = 0; const fallos = [];
async function caso(nombre, promesa) {
  try { await promesa; pasan++; console.log(`  ✓ ${nombre}`); } catch (e) { fallos.push(nombre); console.log(`  ✗ ${nombre}\n    ${e.message.split('\n')[0]}`); }
}
const deja = (nombre, p) => caso(nombre, assertSucceeds(p));
const frena = (nombre, p) => caso(nombre, assertFails(p));

const nuevaInvitacion = (db, uid, correo, rol = 'apoyo') => ({ correo, rol, invitadoPor: uid, invitadoEn: serverTimestamp() });
const unirse = (db, uid, correo, olla, rol, invitadoPor) => {
  const lote = writeBatch(db);
  lote.set(doc(db, `ollas/${olla}/miembros/${uid}`), { uid, nombre: uid, correo, rol, desde: serverTimestamp(), agregadoPor: invitadoPor });
  lote.delete(doc(db, `ollas/${olla}/invitaciones/${correo}`));
  return lote.commit();
};

console.log('Quién ve qué');
await frena('sin entrar no se lee nada', getDoc(doc(nadie, 'ollas/flores')));
await deja('una integrante lee su olla', getDoc(doc(ana, 'ollas/flores')));
await deja('una integrante lee a su gente', getDocs(collection(beto, 'ollas/flores/miembros')));
await frena('una integrante NO lee la olla de al lado', getDoc(doc(ana, 'ollas/pinos')));
await frena('una integrante NO lee a la gente de al lado', getDocs(collection(ana, 'ollas/pinos/miembros')));
await frena('quien no está en ninguna olla no lee ninguna', getDoc(doc(carla, 'ollas/flores')));
await deja('quien coordina lee todas las ollas', getDocs(collection(coordina, 'ollas')));
await frena('una integrante NO lista todas las ollas', getDocs(collection(ana, 'ollas')));
await deja('cada cuenta puede preguntar si coordina', getDoc(doc(carla, 'coordinadores/carla')));
await frena('pero no por otra cuenta', getDoc(doc(carla, 'coordinadores/coordina')));
await deja('cada cuenta encuentra su olla (grupo miembros, solo lo propio)',
  getDocs(query(collectionGroup(beto, 'miembros'), where('uid', '==', 'beto'))));
await frena('y no la de otra persona', getDocs(query(collectionGroup(beto, 'miembros'), where('uid', '==', 'ana'))));
await deja('cada cuenta encuentra sus invitaciones (grupo invitaciones)',
  getDocs(query(collectionGroup(dora, 'invitaciones'), where('correo', '==', 'dora@gmail.com'))));
await frena('y no las de otro correo', getDocs(query(collectionGroup(carla, 'invitaciones'), where('correo', '==', 'dora@gmail.com'))));

console.log('Ollas');
await deja('quien coordina crea una olla', addDoc(collection(coordina, 'ollas'), { nombre: 'Nueva', distrito: '', creadaEn: serverTimestamp(), creadaPor: 'coordina' }));
await frena('una presidenta NO crea ollas', addDoc(collection(ana, 'ollas'), { nombre: 'Mía', distrito: '', creadaEn: serverTimestamp(), creadaPor: 'ana' }));
await frena('ni quien coordina la firma con otro nombre', addDoc(collection(coordina, 'ollas'), { nombre: 'X', distrito: '', creadaEn: serverTimestamp(), creadaPor: 'ana' }));
await frena('ni sin nombre', addDoc(collection(coordina, 'ollas'), { nombre: '', distrito: '', creadaEn: serverTimestamp(), creadaPor: 'coordina' }));
await deja('quien coordina renombra una olla', updateDoc(doc(coordina, 'ollas/flores'), { nombre: 'Las Flores de VMT' }));
await frena('nadie borra una olla', deleteDoc(doc(coordina, 'ollas/pinos')));

console.log('Invitar');
await deja('quien coordina invita a cualquier olla', setDoc(doc(coordina, 'ollas/pinos/invitaciones/fede@gmail.com'), nuevaInvitacion(coordina, 'coordina', 'fede@gmail.com')));
await deja('la presidenta invita a su olla', setDoc(doc(ana, 'ollas/flores/invitaciones/gina@gmail.com'), nuevaInvitacion(ana, 'ana', 'gina@gmail.com')));
await frena('la presidenta NO invita a la olla de al lado', setDoc(doc(ana, 'ollas/pinos/invitaciones/gina@gmail.com'), nuevaInvitacion(ana, 'ana', 'gina@gmail.com')));
await frena('el apoyo NO invita', setDoc(doc(beto, 'ollas/flores/invitaciones/hugo@gmail.com'), nuevaInvitacion(beto, 'beto', 'hugo@gmail.com')));
await frena('una invitación no se firma con otro nombre', setDoc(doc(ana, 'ollas/flores/invitaciones/hugo@gmail.com'), nuevaInvitacion(ana, 'coordina', 'hugo@gmail.com')));
await frena('ni con un rol inventado', setDoc(doc(ana, 'ollas/flores/invitaciones/hugo@gmail.com'), nuevaInvitacion(ana, 'ana', 'hugo@gmail.com', 'jefa')));
await frena('ni con un correo mal escrito', setDoc(doc(ana, 'ollas/flores/invitaciones/hugo'), nuevaInvitacion(ana, 'ana', 'hugo')));
await frena('ni con mayúsculas en el correo', setDoc(doc(ana, 'ollas/flores/invitaciones/Hugo@gmail.com'), nuevaInvitacion(ana, 'ana', 'Hugo@gmail.com')));
await deja('la presidenta cancela una invitación suya', deleteDoc(doc(ana, 'ollas/flores/invitaciones/gina@gmail.com')));
await deja('la persona invitada ve su invitación', getDoc(doc(dora, 'ollas/flores/invitaciones/dora@gmail.com')));
await frena('otra persona no la ve', getDoc(doc(carla, 'ollas/flores/invitaciones/dora@gmail.com')));

console.log('Unirse');
await frena('sin invitación nadie se agrega', unirse(carla, 'carla', 'carla@gmail.com', 'flores', 'apoyo', 'ana'));
await frena('con invitación, pero con otro rol, tampoco', unirse(dora, 'dora', 'dora@gmail.com', 'flores', 'presidenta', 'ana'));
await frena('ni diciendo que la invitó otra persona', unirse(dora, 'dora', 'dora@gmail.com', 'flores', 'tesorera', 'coordina'));
await frena('ni agregando a otra cuenta', setDoc(doc(dora, 'ollas/flores/miembros/carla'), { uid: 'carla', nombre: 'Carla', correo: 'carla@gmail.com', rol: 'apoyo', desde: serverTimestamp(), agregadoPor: 'ana' }));
await deja('con invitación y el rol que le dieron, se une y la invitación se borra', unirse(dora, 'dora', 'dora@gmail.com', 'flores', 'tesorera', 'ana'));
await deja('y ya lee su olla', getDoc(doc(dora, 'ollas/flores')));

console.log('Roles y quitar');
await frena('nadie se sube el rol a sí misma', updateDoc(doc(beto, 'ollas/flores/miembros/beto'), { rol: 'presidenta' }));
await frena('la presidenta NO cambia roles', updateDoc(doc(ana, 'ollas/flores/miembros/beto'), { rol: 'tesorera' }));
await deja('quien coordina sí', updateDoc(doc(coordina, 'ollas/flores/miembros/beto'), { rol: 'tesorera' }));
await frena('pero no a un rol inventado', updateDoc(doc(coordina, 'ollas/flores/miembros/beto'), { rol: 'dueño' }));
await deja('cada persona corrige su propio nombre', updateDoc(doc(beto, 'ollas/flores/miembros/beto'), { nombre: 'Beto Q.' }));
await frena('pero no su correo', updateDoc(doc(beto, 'ollas/flores/miembros/beto'), { correo: 'otro@gmail.com' }));
await frena('el apoyo NO quita a nadie', deleteDoc(doc(beto, 'ollas/flores/miembros/dora')));
await frena('la presidenta NO se quita a sí misma', deleteDoc(doc(ana, 'ollas/flores/miembros/ana')));
await deja('la presidenta quita a su gente', deleteDoc(doc(ana, 'ollas/flores/miembros/dora')));
await frena('pero no a la de al lado', deleteDoc(doc(ana, 'ollas/pinos/miembros/elsa')));
await deja('quien coordina quita a cualquiera', deleteDoc(doc(coordina, 'ollas/pinos/miembros/elsa')));
await frena('nadie se hace coordinador desde la app', setDoc(doc(ana, 'coordinadores/ana'), { desde: serverTimestamp() }));

console.log('Los datos de la olla (padrón, asistencia, precios)');
const firma = (uid) => ({ actualizadoPor: uid, actualizadoEn: serverTimestamp() });
await deja('una integrante lee el padrón de su olla', getDocs(collection(beto, 'ollas/flores/personas')));
await deja('y los días', getDocs(collection(beto, 'ollas/flores/dias')));
await frena('pero no el padrón de al lado', getDocs(collection(beto, 'ollas/pinos/personas')));
await frena('quien no está en ninguna olla no lee nada', getDoc(doc(carla, 'ollas/flores/personas/p1')));
await deja('quien coordina lee cualquier olla', getDoc(doc(coordina, 'ollas/pinos/personas/p9')));
await deja('el apoyo inscribe a una persona', setDoc(doc(beto, 'ollas/flores/personas/p2'), { id: 'p2', nombres: 'Luz', ...firma('beto') }));
await frena('pero no sin firmar', setDoc(doc(beto, 'ollas/flores/personas/p3'), { id: 'p3', nombres: 'Luz' }));
await frena('ni firmando por otra persona', setDoc(doc(beto, 'ollas/flores/personas/p3'), { id: 'p3', nombres: 'Luz', ...firma('ana') }));
await frena('ni con una hora inventada', setDoc(doc(beto, 'ollas/flores/personas/p3'), { id: 'p3', nombres: 'Luz', actualizadoPor: 'beto', actualizadoEn: new Date() }));
await frena('ni con un id que no es el del documento', setDoc(doc(beto, 'ollas/flores/personas/p3'), { id: 'p4', nombres: 'Luz', ...firma('beto') }));
await frena('ni en la olla de al lado', setDoc(doc(beto, 'ollas/pinos/personas/p3'), { id: 'p3', nombres: 'Luz', ...firma('beto') }));
await frena('quien no está en la olla no inscribe', setDoc(doc(carla, 'ollas/flores/personas/p3'), { id: 'p3', nombres: 'Luz', ...firma('carla') }));
await deja('una integrante corrige a una persona', setDoc(doc(ana, 'ollas/flores/personas/p1'), { id: 'p1', nombres: 'Rosa M.', ...firma('ana') }));
await deja('y da de baja (borra) a una', deleteDoc(doc(ana, 'ollas/flores/personas/p2')));
await deja('una integrante marca a alguien en un día que ya existe (solo su marca)',
  setDoc(doc(beto, 'ollas/flores/dias/2026-10-01'), { marcas: { p2: { tipoMenu: 'normal', por: 'beto', en: serverTimestamp() } }, ...firma('beto') }, { merge: true }));
await deja('y desmarca', setDoc(doc(beto, 'ollas/flores/dias/2026-10-01'), { marcas: { p2: deleteField() }, ...firma('beto') }, { merge: true }));
await deja('abre un día nuevo', setDoc(doc(beto, 'ollas/flores/dias/2026-10-02'), { id: 'd2', fecha: '2026-10-02', marcas: {}, ...firma('beto') }));
await frena('pero no con una fecha que no cuadra con el documento', setDoc(doc(beto, 'ollas/flores/dias/2026-10-03'), { id: 'd3', fecha: '2026-10-04', ...firma('beto') }));
await frena('ni con un documento que no es una fecha', setDoc(doc(beto, 'ollas/flores/dias/hoy'), { id: 'd3', fecha: 'hoy', ...firma('beto') }));
await frena('ni sin firmar', setDoc(doc(beto, 'ollas/flores/dias/2026-10-01'), { nota: 'x' }, { merge: true }));
await deja('borra un día', deleteDoc(doc(beto, 'ollas/flores/dias/2026-10-02')));
await deja('fija el precio del menú', setDoc(doc(ana, 'ollas/flores/ajustes/precios'), { precioMenuNormalCent: 300, precioMenuAyudaSocialCent: 100, ...firma('ana') }, { merge: true }));
await frena('pero no sin firmar', setDoc(doc(ana, 'ollas/flores/ajustes/precios'), { precioMenuNormalCent: 350 }, { merge: true }));
await frena('ni lo borra', deleteDoc(doc(ana, 'ollas/flores/ajustes/precios')));
await deja('quien coordina escribe en cualquier olla', setDoc(doc(coordina, 'ollas/pinos/dias/2026-10-01'), { id: 'd9', fecha: '2026-10-01', marcas: {}, ...firma('coordina') }));

console.log('El envío a UNACEM (solicitudes y correo de destino)');
const solicitud = (uid, extra = {}) => ({ tipo: 'asistencia', fecha: '2026-10-01', inicio: '2026-10-01', fin: '2026-10-01', modo: 'manual', estado: 'pendiente', pedidoPor: uid, pedidoEn: serverTimestamp(), ...extra });
await deja('una integrante pide enviar la asistencia de un día', addDoc(collection(beto, 'ollas/flores/envios'), solicitud('beto')));
await deja('o el padrón de una quincena', addDoc(collection(ana, 'ollas/flores/envios'), solicitud('ana', { tipo: 'padron', inicio: '2026-10-01', fin: '2026-10-15' })));
await frena('pero no en la olla de al lado', addDoc(collection(beto, 'ollas/pinos/envios'), solicitud('beto')));
await frena('ni quien no está en la olla', addDoc(collection(carla, 'ollas/flores/envios'), solicitud('carla')));
await frena('ni firmando por otra persona', addDoc(collection(beto, 'ollas/flores/envios'), solicitud('ana')));
await frena('ni con una hora inventada', addDoc(collection(beto, 'ollas/flores/envios'), solicitud('beto', { pedidoEn: new Date() })));
await frena('ni ya «enviado»', addDoc(collection(beto, 'ollas/flores/envios'), solicitud('beto', { estado: 'enviado' })));
await frena('ni haciéndose pasar por el envío automático', addDoc(collection(beto, 'ollas/flores/envios'), solicitud('beto', { modo: 'automatico' })));
await frena('ni de un tipo inventado', addDoc(collection(beto, 'ollas/flores/envios'), solicitud('beto', { tipo: 'b1' })));
await frena('ni con fechas que no son fechas', addDoc(collection(beto, 'ollas/flores/envios'), solicitud('beto', { inicio: 'ayer' })));
await frena('ni con el periodo al revés', addDoc(collection(beto, 'ollas/flores/envios'), solicitud('beto', { inicio: '2026-10-05', fin: '2026-10-01' })));
await frena('ni con campos de más (lo demás lo anota el script)', addDoc(collection(beto, 'ollas/flores/envios'), solicitud('beto', { para: 'otro@gmail.com' })));
const envioDeBeto = (await getDocs(query(collection(beto, 'ollas/flores/envios'), where('pedidoPor', '==', 'beto')))).docs[0].ref;
await deja('una integrante ve los envíos de su olla', getDocs(collection(ana, 'ollas/flores/envios')));
await frena('pero no los de al lado', getDocs(collection(beto, 'ollas/pinos/envios')));
await frena('nadie marca un envío como enviado desde la app', updateDoc(envioDeBeto, { estado: 'enviado' }));
await frena('ni lo borra', deleteDoc(envioDeBeto));
await deja('quien coordina pone el correo de UNACEM', setDoc(doc(coordina, 'ajustes/envio'), { correo: 'prueba@gmail.com', ...firma('coordina') }));
await frena('pero no sin firmar', setDoc(doc(coordina, 'ajustes/envio'), { correo: 'prueba@gmail.com' }));
await frena('ni con otros campos', setDoc(doc(coordina, 'ajustes/envio'), { correo: 'prueba@gmail.com', hora: 21, ...firma('coordina') }));
await frena('una presidenta no lo cambia', setDoc(doc(ana, 'ajustes/envio'), { correo: 'ana@gmail.com', ...firma('ana') }));
await deja('pero sí lo lee (para avisar si falta)', getDoc(doc(ana, 'ajustes/envio')));
await frena('sin entrar, no', getDoc(doc(nadie, 'ajustes/envio')));
await frena('nadie lo borra', deleteDoc(doc(coordina, 'ajustes/envio')));

await entorno.cleanup();
console.log(fallos.length ? `\n✗ ${fallos.length} fallan, ${pasan} pasan` : `\n✓ ${pasan} de ${pasan} pasan`);
process.exit(fallos.length ? 1 : 0);
