// Una copia de respaldo FICTICIA y completa para probar la app: socias con su familia
// (pareja, hijos, madres o padres), los datos del libro de socias y del formato municipal,
// la directiva de la olla, asistencia de setiembre a noviembre, el B-1 (un mes y una
// quincena) y las entregas. Todos los DNI empiezan con 7000: no son de nadie. Sale siempre
// igual (el azar tiene semilla fija).
//
//   node herramientas/padron-de-prueba.mjs [archivo.json]
//
// Sin archivo, escribe nativo/app/src/main/assets/datos-de-prueba.json: la app la trae
// adentro y la carga con «Tus datos → Cargar datos de prueba». También se carga con
// «Recuperar una copia». Reemplaza todo lo de ese teléfono: no en la app de una olla de verdad.
//
// A propósito, para ver los avisos del padrón: un familiar sin parentesco, una socia
// de baja con una hija activa y una persona sin condición, pendiente de la Municipalidad.
import { readFileSync, writeFileSync } from 'node:fs';

const archivo = process.argv[2] ?? new URL('../nativo/app/src/main/assets/datos-de-prueba.json', import.meta.url).pathname;
const HOY = new Date(Date.UTC(2026, 10, 22));

// Azar con semilla (mulberry32): los mismos datos cada vez.
let semilla = 22112026;
function azar() {
  semilla = (semilla + 0x6d2b79f5) | 0;
  let t = Math.imul(semilla ^ (semilla >>> 15), 1 | semilla);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}
const entre = (a, b) => a + Math.floor(azar() * (b - a + 1));
const uno = (xs) => xs[Math.floor(azar() * xs.length)];
const iso = (d) => d.toISOString().slice(0, 10);

const PAT = ['Quispe', 'Mamani', 'Huamán', 'Flores', 'Condori', 'Rojas', 'Chávez', 'Ramos', 'Torres', 'Vargas', 'Cárdenas', 'Paucar', 'Ccahuana', 'Yupanqui', 'Salazar', 'Rivas'];
const NOM_F = ['Rosa', 'María', 'Luz', 'Carmen', 'Juana', 'Elena', 'Norma', 'Gladys', 'Yolanda', 'Marisol', 'Sonia', 'Flor', 'Nelly', 'Beatriz', 'Silvia', 'Lidia'];
const NOM_M = ['Juan', 'Pedro', 'Luis', 'Carlos', 'Jorge', 'Víctor', 'Raúl', 'Mario', 'Hugo', 'Edwin', 'Wilber', 'Néstor'];
const NOM_N_F = ['Valentina', 'Camila', 'Luciana', 'Ximena', 'Nayeli', 'Danna', 'Kiara', 'Mía'];
const NOM_N_M = ['Thiago', 'Mateo', 'Santiago', 'Gael', 'Dylan', 'Liam', 'Adrián', 'Jhair'];
const CALLES = ['Mz A Lt 4', 'Mz B Lt 12', 'Mz C Lt 7', 'Mz D Lt 3', 'Mz E Lt 15', 'Mz F Lt 9', 'Mz G Lt 1', 'Mz H Lt 20'];

let siguienteDni = 70000001;
let siguienteId = 0;
// Hace `desde` a `hasta` años, en un día cualquiera.
const nacimiento = (desde, hasta) => iso(new Date(Date.UTC(HOY.getUTCFullYear() - entre(desde, hasta), entre(0, 11), entre(1, 28))));

const afiliados = [];
const usados = new Set();
function persona(pat, mat, nombres, sexo, fechaNacimiento, extra = {}) {
  // Sin nombres completos repetidos: la app avisa si hay dos iguales.
  const lista = NOM_N_F.includes(nombres) ? NOM_N_F : NOM_N_M.includes(nombres) ? NOM_N_M : [nombres];
  while (usados.has(`${pat}|${mat}|${nombres}`) && lista.length > 1) nombres = uno(lista);
  usados.add(`${pat}|${mat}|${nombres}`);
  const a = {
    id: `t-${String(++siguienteId).padStart(4, '0')}`, apellidoPaterno: pat, apellidoMaterno: mat, nombres,
    numeroDocumento: String(siguienteDni++), tipoDocumento: 'dni', fechaNacimiento, grupoEtarioManual: null, sexo,
    tipoAfiliado: 'habitual', activo: true, altaEn: '2026-03-01', bajaEn: '', motivoBaja: '', origenDato: 'manual',
    verificadoPor: '', nota: 'Dato ficticio de prueba', registroMunicipal: 'registrada', registroMunicipalEn: '2026-10-14',
    condicion: '', titularId: '', parentesco: '', ocupacion: '', direccion: '', telefono: '',
    distrito: 'Villa María del Triunfo', provincia: 'Lima', departamento: 'Lima', profesion: '', actividad: '',
    gradoInstruccion: '', estadoCivil: '', carnet: '', fotoDni: '', fotoPersona: '', ...extra,
  };
  afiliados.push(a);
  return a;
}

// [estado civil de la socia, hijos, madres o padres en la casa]
const PLAN = [
  ['casada', 3, 0], ['conviviente', 2, 0], ['soltera', 1, 0], ['casada', 2, 1], ['viuda', 0, 1], ['conviviente', 4, 0],
  ['soltera', 0, 0], ['casada', 1, 0], ['conviviente', 2, 1], ['separada', 3, 0], ['casada', 2, 0], ['soltera', 2, 0],
];
const ESTADO = { casada: 'Casada', conviviente: 'Conviviente', soltera: 'Soltera', viuda: 'Viuda', separada: 'Separada' };
PLAN.forEach(([estado, hijos, mayores], i) => {
  const pat = PAT[i], mat = PAT[(i + 5) % PAT.length];
  const direccion = `${CALLES[i % CALLES.length]} As. Los Jardines`;
  const conPareja = estado === 'casada' || estado === 'conviviente';
  const s = persona(pat, mat, NOM_F[i], 'F', nacimiento(28, 58), {
    condicion: 'socia', carnet: String(i + 1).padStart(3, '0'), direccion, telefono: `9${entre(10000000, 99999999)}`,
    ocupacion: 'Ama de casa', actividad: uno(['Ama de casa', 'Comerciante', 'Costurera', 'Vendedora']),
    gradoInstruccion: uno(['Primaria completa', 'Secundaria incompleta', 'Secundaria completa', 'Técnica']),
    estadoCivil: ESTADO[estado], tipoAfiliado: i === 4 ? 'caso_social' : 'habitual',
  });
  const patPareja = PAT[(i + 3) % PAT.length];
  if (conPareja) {
    persona(patPareja, PAT[(i + 7) % PAT.length], NOM_M[i % NOM_M.length], 'M', nacimiento(30, 60), {
      condicion: 'familiar', titularId: s.id, parentesco: estado === 'casada' ? 'esposo/a' : 'conviviente',
      ocupacion: uno(['Albañil', 'Mototaxista', 'Obrero', 'Comerciante']), direccion,
    });
  }
  for (let h = 0; h < hijos; h++) {
    const nina = azar() < 0.5;
    persona(conPareja ? patPareja : pat, pat, uno(nina ? NOM_N_F : NOM_N_M), nina ? 'F' : 'M', nacimiento(1, 17), {
      condicion: 'familiar', titularId: s.id, parentesco: 'hijo/a', ocupacion: 'Estudiante', direccion,
    });
  }
  for (let m = 0; m < mayores; m++) {
    persona(mat, PAT[(i + 9) % PAT.length], uno(['Teodora', 'Florencia', 'Eusebia']), 'F', nacimiento(68, 84), {
      condicion: 'familiar', titularId: s.id, parentesco: 'madre/padre', tipoAfiliado: 'caso_social', direccion,
    });
  }
});
// Los casos que deben dar aviso.
persona('Rojas', 'Paucar', 'Kevin', 'M', nacimiento(18, 22), { condicion: 'familiar', titularId: afiliados[0].id, ocupacion: 'Estudiante' });
const deBaja = persona('Torres', 'Ramos', 'Hilda', 'F', nacimiento(40, 50), { condicion: 'socia', activo: false, bajaEn: '2026-10-31', motivoBaja: 'Se mudó', carnet: '099' });
persona('Ramos', 'Torres', 'Brenda', 'F', nacimiento(8, 12), { condicion: 'familiar', titularId: deBaja.id, parentesco: 'hijo/a' });
persona('Salazar', 'Rivas', 'Néstor', 'M', nacimiento(35, 45), { registroMunicipal: 'pendiente', registroMunicipalEn: '' });

// Asistencia de setiembre a noviembre: días de semana hasta ayer; cada activo viene 3 de cada 4 días.
const activos = afiliados.filter((a) => a.activo);
const atenciones = [];
for (let d = new Date(Date.UTC(2026, 8, 1)); d < HOY; d.setUTCDate(d.getUTCDate() + 1)) {
  const dia = d.getUTCDay();
  if (dia === 0 || dia === 6) continue;
  const vinieron = activos.filter(() => azar() < 0.75);
  atenciones.push({
    id: `td-${String(atenciones.length + 1).padStart(3, '0')}`, fecha: iso(d),
    menuDelDia: uno(['Arroz con pollo', 'Lentejas con arroz', 'Tallarines rojos', 'Ají de gallina', 'Estofado de pollo']),
    racionesPreparadas: vinieron.length + entre(0, 5), precioMenuNormalCent: 300, precioMenuAyudaSocialCent: 100,
    asistencias: vinieron.map((a) => ({ afiliadoId: a.id, tipoMenu: a.tipoAfiliado === 'caso_social' ? 'ayuda_social' : 'normal' })),
  });
}

const presidenta = afiliados[0];
const olla = {
  nombre: 'Olla Común Los Jardines (prueba)', codigoPca: '0000000098', direccion: 'As. Los Jardines Mz A Lt 1', distrito: 'Villa María del Triunfo',
  presidenta: { nombre: `${presidenta.nombres} ${presidenta.apellidoPaterno} ${presidenta.apellidoMaterno}`, dni: presidenta.numeroDocumento, celular: presidenta.telefono },
  tesorera: { nombre: 'María Mamani Chávez', dni: '70009001', celular: '900000002' },
  secretaria: { nombre: 'Luz Flores Ramos', dni: '70009002', celular: '900000003' },
  fiscal: { nombre: 'Carmen Condori Torres', dni: '70009003', celular: '900000004' },
  montoSubsidioCent: 150000,
};
// El B-1 (Setiembre y una quincena de agosto, con sus compras) y las entregas: los del
// respaldo ficticio de las pruebas, con los datos de esta olla.
const ficticio = JSON.parse(readFileSync(new URL('../nativo/nucleo/src/test/resources/respaldo-ficticio.json', import.meta.url), 'utf8'));
const rendiciones = ficticio.rendiciones.map((r) => ({ ...r, nombreCentro: olla.nombre, codigoPca: olla.codigoPca }));
const respaldo = {
  exportadoEn: '2026-11-22T17:00:00.000Z', aplicacion: 'rendicion-b1',
  rendiciones,
  padron: { version: 1, afiliados, atenciones },
  calendario: ficticio.calendario,
  config: { version: 1, calibracion: { offsetXmm: 0, offsetYmm: 0 }, olla, precioMenuNormalCent: 300, precioMenuAyudaSocialCent: 100 },
};
writeFileSync(archivo, JSON.stringify(respaldo, null, 1));

const familiares = afiliados.filter((a) => a.condicion === 'familiar');
const cuenta = (p) => familiares.filter((a) => p.includes(a.parentesco)).length;
console.log(`${archivo}: ${afiliados.length} personas, ${afiliados.filter((a) => a.condicion === 'socia').length} socias y ${familiares.length} familiares ` +
  `(${cuenta(['hijo/a'])} hijos, ${cuenta(['esposo/a', 'conviviente'])} parejas, ${cuenta(['madre/padre'])} madres o padres); ${atenciones.length} días de asistencia; ` +
  `${rendiciones.length} B-1 y ${ficticio.calendario.entregas.length} entregas.`);
