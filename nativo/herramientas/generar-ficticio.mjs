// Arma un respaldo FICTICIO con la forma exacta que exporta la web, usando los propios
// módulos de web/js. Todo es inventado: nombres genéricos, DNI 0000xxxx, RUC 1000000000x.
//
//   node nativo/herramientas/generar-ficticio.mjs
//
// Escribe en nucleo/src/test/resources respaldo-ficticio.json y
// respaldo-ficticio-protegido.json (contraseña «prueba-1234»). Es determinista.

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  nuevaRendicion, nuevoEgreso, nuevoAfiliado, nuevaAtencion, padronVacio, calendarioVacio,
  configVacia, cifrarRespaldo
} from '../../web/js/modelo.js';
import { menuPorDefecto } from '../../web/js/calculos.js';
import { instantaneaCentro } from '../../web/js/sugerencias.js';

const aqui = dirname(fileURLToPath(import.meta.url));
const destino = join(aqui, '..', 'nucleo', 'src', 'test', 'resources');

// Azar reproducible (LCG), para que el archivo no cambie entre corridas.
let semilla = 20261020;
const azar = () => (semilla = (semilla * 1664525 + 1013904223) % 4294967296) / 4294967296;
const entre = (a, b) => a + Math.floor(azar() * (b - a + 1));
const elegir = (lista) => lista[entre(0, lista.length - 1)];
let contadorId = 0;
const id = (prefijo) => `${prefijo}-${String(++contadorId).padStart(4, '0')}`;

const APELLIDOS = ['Álvarez', 'Ñahui', 'Núñez', 'Quispe', 'Zapata', 'Pérez', 'de la Cruz', 'Mamani',
  'Ávila', 'Yupanqui', 'Huamán', 'Ccopa', 'Ochoa', 'Llanos', 'Vásquez', 'Óre', 'Chávez', 'Inca'];
const NOMBRES = ['Ana María', 'José', 'Ñusta', 'Rosa', 'Luz', 'Ángel', 'Óscar', 'María Élena', 'Juan',
  'Carmen', 'Rubén', 'Inés', 'Úrsula', 'Zoila', 'Elías'];

const HOY = '2026-10-20';

const afiliados = [];
const nacimientoConEdad = (edad) => `${2026 - edad}-${String(entre(1, 12)).padStart(2, '0')}-${String(entre(1, 28)).padStart(2, '0')}`;

for (let i = 0; i < 40; i += 1) {
  const a = nuevoAfiliado();
  a.id = id('p');
  a.apellidoPaterno = elegir(APELLIDOS);
  a.apellidoMaterno = azar() < 0.9 ? elegir(APELLIDOS) : '';
  a.nombres = elegir(NOMBRES);
  a.numeroDocumento = `0000${String(i + 1).padStart(4, '0')}`;
  a.tipoAfiliado = azar() < 0.8 ? 'habitual' : 'caso_social';
  a.sexo = elegir(['F', 'F', 'M', '']);
  a.altaEn = '2026-07-01';
  if (i % 5 === 0) {
    a.grupoEtarioManual = elegir(['nino', 'adolescente', 'adulto', 'adulto_mayor']);   // sin fecha
  } else {
    a.fechaNacimiento = nacimientoConEdad(elegir([3, 11, 12, 17, 18, 35, 59, 60, 78]));
  }
  afiliados.push(a);
}
// Casos especiales, para que las validaciones tengan de qué hablar.
afiliados[1].tipoDocumento = 'ce'; afiliados[1].numeroDocumento = 'CE00000002';
afiliados[2].tipoDocumento = 'ce'; afiliados[2].numeroDocumento = 'X1234';            // CE mal formado (error)
afiliados[3].grupoEtarioManual = null; afiliados[3].fechaNacimiento = '';            // sin grupo (error)
afiliados[4].numeroDocumento = afiliados[5].numeroDocumento;                         // documento repetido (error)
afiliados[6].apellidoPaterno = afiliados[7].apellidoPaterno;                         // mismo nombre completo (aviso)
afiliados[6].apellidoMaterno = afiliados[7].apellidoMaterno;
afiliados[6].nombres = afiliados[7].nombres;
afiliados[8].origenDato = 'escaneo_dni'; afiliados[8].verificadoPor = '';            // escaneo sin confirmar (aviso)
afiliados[9].activo = false; afiliados[9].bajaEn = '2026-09-15'; afiliados[9].motivoBaja = 'Se mudó';
afiliados[10].activo = false; afiliados[10].bajaEn = '';                             // inactiva sin fecha: fuera siempre
afiliados[11].altaEn = '2026-09-10';                                                 // entra tarde: marcada antes → aviso
afiliados[12].tipoAfiliado = '';                                                     // sin tipo (error)
afiliados[13].fechaNacimiento = '2026-12-31';                                        // en el futuro (error)
afiliados[14].nota = 'Trae a sus dos hijos';
afiliados[15].fechaNacimiento = '1966-10-20';                                        // cumple 60 justo HOY

const PRECIOS = { precioMenuNormalCent: 300, precioMenuAyudaSocialCent: 100 };
const atenciones = [];
const fechas = [];
for (let d = new Date(2026, 7, 1); d <= new Date(2026, 9, 19); d.setDate(d.getDate() + 1)) {
  if (d.getDay() === 0) continue;   // domingo no hay olla
  fechas.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`);
}
fechas.forEach((fecha, i) => {
  const at = nuevaAtencion(i % 17 === 5 ? null : PRECIOS);   // algunos días sin precio
  at.id = id('d');
  at.fecha = fecha;
  if (i % 23 === 3) {
    // Día heredado de la v2: cuentas a mano, sin asistencia.
    at.legado = { racionesNormales: i === 3 ? 90 : entre(20, 30), racionesAyudaSocial: entre(3, 8) };
    at.precioTomadoDeConfig = false;
  } else {
    const activos = afiliados.filter((a) => a.activo && a.altaEn <= fecha || (a.bajaEn && a.bajaEn > fecha));
    for (const a of activos) {
      if (azar() < 0.7) at.asistencias.push({ afiliadoId: a.id, tipoMenu: azar() < 0.95 ? menuPorDefecto(a) : 'ayuda_social' });
    }
  }
  if (i % 11 === 7) at.sinInscribir = { normal: entre(1, 3), ayudaSocial: entre(0, 1) };
  if (i % 29 === 10) at.nota = 'Vino la nutricionista';
  if (i % 31 === 12) at.precioMenuAyudaSocialCent = 400;   // ayuda social más cara que el normal (aviso)
  atenciones.push(at);
});
// Marcada dos veces (error), alguien que ya no está (aviso), fecha repetida (aviso).
atenciones[20].asistencias.push({ ...atenciones[20].asistencias[0] });
atenciones[21].asistencias.push({ afiliadoId: 'p-ya-no-esta', tipoMenu: 'normal' });
atenciones.push(Object.assign(nuevaAtencion(PRECIOS), { id: id('d'), fecha: atenciones[22].fecha, asistencias: [] }));
// Quien entró el 10-09 marcada el 1-09 (aviso: no estaba activa).
atenciones[25].asistencias.push({ afiliadoId: afiliados[11].id, tipoMenu: 'normal' });

const padron = Object.assign(padronVacio(), { afiliados, atenciones });

function egreso(i, extra) {
  return Object.assign(nuevoEgreso(), {
    id: id('e'),
    fechaCompra: `2026-09-${String(2 + (i % 26)).padStart(2, '0')}`,
    descripcion: elegir(['Saco de papa', 'Arroz', 'Pollo', 'Verduras', 'Aceite', 'Carne', 'Gas']),
    cantidad: entre(1, 10), unidadMedida: elegir(['Kg', 'unid', 'saco', 'L']),
    rucProveedor: `1000000000${i % 9}`, proveedorNombre: elegir(['Mercado Central', 'Bodega Luz', 'Avícola San Pedro']),
    boletaSerie: elegir(['E001', 'B001', '0001']), boletaCorrelativo: String(100 + i),
    precioUnitarioCent: entre(100, 2000),
    origenFondo: elegir(['subsidio', 'subsidio', 'subsidio', 'ayuda_social', 'aporte_propio'])
  }, extra);
}

const r1 = nuevaRendicion();
Object.assign(r1, {
  id: id('r'), actualizadoEn: '2026-10-01T12:00:00.000Z',
  tipoCentro: 'olla_comun', nombreCentro: 'Olla ficticia Los Ñandúes', codigoPca: '0000000001',
  periodo: { tipo: 'mensual', inicio: '2026-09-01', fin: '2026-09-30', etiqueta: 'Setiembre' },
  fechaRendicion: '2026-10-01', nombrePresidenta: 'Rosa Prueba Ficticia', dniPresidenta: '00000001',
  celular: '900000001', montoSubsidioCent: 150000, fechaAsignacion: '2026-09-01',
  nombrePresidentaFirma: 'Rosa Prueba Ficticia', dniPresidentaFirma: '00000001',
  nombreTesorera: 'Luz Prueba Ficticia', dniTesorera: '00000002',
  generaciones: [{ en: '2026-10-02T10:00:00.000Z', formato: 'pdf', totalCent: null, como: 'compartido' }]
});
r1.egresos = Array.from({ length: 19 }, (_, i) => egreso(i));      // 19 > 17 filas: segunda hoja (aviso)
r1.egresos.forEach((e) => { e.montoTotalCent = e.cantidad * e.precioUnitarioCent; });
r1.egresos[2].montoTotalCent += 50;                                  // descuadre (aviso)
Object.assign(r1.egresos[3], { cantidad: null, cantidadVarios: true, precioUnitarioCent: null, precioUnitarioVarios: true, unidadMedida: 'varios', montoTotalCent: 14185 });
r1.egresos[4].boletaSerie = r1.egresos[0].boletaSerie; r1.egresos[4].boletaCorrelativo = r1.egresos[0].boletaCorrelativo;   // boleta repetida
r1.egresos[5].rucProveedor = '30000000005';                          // RUC que no empieza en 10/15/17/20 (aviso)
r1.egresos[6].fechaCompra = '2026-08-30';                            // antes de la asignación (error) y fuera del periodo
r1.egresos[7].rucProveedor = '';                                     // falta RUC (falta)
r1.egresos[8].precioUnitarioCent = null; r1.egresos[8].cantidad = 2.5;   // cantidad decimal, sin precio unitario
r1.generaciones[0].totalCent = r1.egresos.reduce((s, e) => s + e.montoTotalCent, 0);

const r2 = nuevaRendicion();
Object.assign(r2, {
  id: id('r'), actualizadoEn: '2026-09-01T12:00:00.000Z',
  tipoCentro: 'comedor', nombreCentro: 'Olla ficticia Los Ñandúes', codigoPca: '0000000001',
  periodo: { tipo: 'quincenal', inicio: '2026-08-16', fin: '2026-08-31', etiqueta: 'Quincenal' },
  fechaRendicion: '2026-09-01', nombrePresidenta: 'Rosa Prueba Ficticia', dniPresidenta: '00000001',
  celular: '900000001', montoSubsidioCent: 80000, fechaAsignacion: '2026-08-14'
});
r2.egresos = [egreso(30, { fechaCompra: '2026-08-20', cantidad: 2, precioUnitarioCent: 2500, montoTotalCent: 5000 }),
  egreso(31, { fechaCompra: '2026-08-25', cantidad: 10, precioUnitarioCent: 1500, montoTotalCent: 15000 }),
  nuevoEgreso()];                                                    // total 200.00 < subsidio 800.00: saldo (aviso) + fila vacía

const rendiciones = [r1, r2];   // la primera es la que está en curso

const calendario = Object.assign(calendarioVacio(), {
  periodicidad: 'mensual', fechaLimiteBase: '2026-09-15', diasDeAviso: [7, 3, 1],
  entregas: [
    { id: id('c'), fechaLimite: '2026-08-15', estado: 'conforme', nota: '' },
    { id: id('c'), fechaLimite: '2026-09-15', estado: 'entregada', nota: 'Con una observación menor' },
    { id: id('c'), fechaLimite: '2026-10-15', estado: 'pendiente', nota: '' }
  ]
});

const config = Object.assign(configVacia(), {
  precioMenuNormalCent: 300, precioMenuAyudaSocialCent: 100,
  ultimoCentro: instantaneaCentro(r1)
});

const respaldo = {
  exportadoEn: `${HOY}T15:00:00.000Z`,
  aplicacion: 'rendicion-b1',
  rendiciones, padron, calendario, config
};
writeFileSync(join(destino, 'respaldo-ficticio.json'), JSON.stringify(respaldo, null, 2));
const sobre = await cifrarRespaldo(respaldo, 'prueba-1234');
writeFileSync(join(destino, 'respaldo-ficticio-protegido.json'), JSON.stringify(sobre, null, 2));
console.log(`Escrito: ${afiliados.length} personas, ${atenciones.length} días, ${rendiciones.length} B-1 → ${destino}`);
