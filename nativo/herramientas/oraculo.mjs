// Calcula con los módulos de la WEB (tal cual, sin tocarlos) lo
// que el núcleo Kotlin debe dar sobre un respaldo, y lo escribe al lado como
// <respaldo>.esperado.json. OraculoTest.kt compara campo a campo.
//
//   node nativo/herramientas/oraculo.mjs <respaldo.json> [--hoy AAAA-MM-DD] [--periodo inicio fin]
//
// Con un respaldo REAL (datos personales): el archivo y su .esperado.json se
// quedan fuera del repositorio (*.json está en .gitignore).
//
// Nota: la web usa hoyIso() por dentro en validarAfiliado(); por eso el
// --hoy de este oráculo solo vale para alertas y fechas de referencia. Para que
// los hallazgos del padrón coincidan, el .esperado.json guarda la fecha real en
// que se generó y Kotlin la usa como «hoy».

import { readFileSync, writeFileSync } from 'node:fs';
import { hoyIso, revisarRespaldo, migrarPadron } from '../../web/js/modelo.js';
import {
  totales, gastoPorOrigen, costoPorRacionCent, resumenEconomicoRaciones, racionesPeriodo, diasAtendidos,
  promedioRaciones, asistenciaPorAfiliado, afiliadosSinAsistencia, filasPadron, resumenEtario,
  afiliadosActivos, desgloseDelDia, recaudacionDelDiaCent, descuadreFila
} from '../../web/js/calculos.js';
import {
  validarRendicion, validarAfiliados, validarAtenciones, validarCalendario, validarPreciosMenu, contar
} from '../../web/js/validaciones.js';
import { alertaProximaEntrega, agendaEntregas, programaDeAvisos } from '../../web/js/alertas.js';
import { sugerenciasEncabezado, sugerirRucProveedor, descripcionesUsadas } from '../../web/js/sugerencias.js';

const args = process.argv.slice(2);
const ruta = args.find((a) => !a.startsWith('--') && !/^\d{4}-\d{2}-\d{2}$/.test(a));
if (!ruta) {
  console.error('Uso: node oraculo.mjs <respaldo.json> [--hoy AAAA-MM-DD] [--periodo inicio fin]');
  process.exit(2);
}
const iHoy = args.indexOf('--hoy');
const hoy = iHoy >= 0 ? args[iHoy + 1] : hoyIso();
const iPer = args.indexOf('--periodo');
const periodoPedido = iPer >= 0 ? [args[iPer + 1], args[iPer + 2]] : null;

const datos = JSON.parse(readFileSync(ruta, 'utf8'));
if (datos.cifrado) {
  console.error('El respaldo está cifrado: ábrelo primero (o usa el respaldo sin contraseña).');
  process.exit(2);
}

const revision = revisarRespaldo(datos);
const salida = { generadoEn: hoyIso(), hoy, revision };

let padronVivo = null;   // el padrón como lo ve la app: migrado y con sinInscribir
if (revision.partes.includes('padron')) {
  const migrado = migrarPadron(datos.padron);
  // La web pierde sinInscribir al migrar (ver modelo.js). Kotlin lo conserva;
  // lo que la usuaria VE en la app es con sinInscribir, así que se repone.
  const crudos = new Map((datos.padron.atenciones || []).map((at) => [at && at.id, at]));
  for (const at of migrado.atenciones) {
    const c = crudos.get(at.id);
    if (c && c.sinInscribir) at.sinInscribir = c.sinInscribir;
  }
  const padron = migrado;
  padronVivo = padron;

  const fechas = padron.atenciones.map((at) => at.fecha).filter((f) => /^\d{4}-\d{2}-\d{2}$/.test(f)).sort();
  const rango = fechas.length ? [fechas[0], fechas[fechas.length - 1]] : [hoy, hoy];
  const mesActual = [`${hoy.slice(0, 7)}-01`, hoy];
  const periodos = [rango, mesActual];
  if (periodoPedido) periodos.push(periodoPedido);

  const filas = (fechaRef) => filasPadron(padron, fechaRef).map((f) => ({
    n: f.n, afiliadoId: f.afiliadoId, tipoDocumento: f.tipoDocumento, grupoEtario: f.grupoEtario,
    grupoEtarioEtiqueta: f.grupoEtarioEtiqueta, edad: f.edad, tipoAfiliadoEtiqueta: f.tipoAfiliadoEtiqueta
  }));

  salida.padron = {
    personas: padron.afiliados.length,
    dias: padron.atenciones.length,
    periodos: periodos.map(([inicio, fin]) => ({
      inicio, fin,
      resumen: resumenEconomicoRaciones(padron, inicio, fin),
      racionesPeriodo: racionesPeriodo(padron, inicio, fin),
      diasAtendidos: diasAtendidos(padron, inicio, fin),
      promedioRaciones: promedioRaciones(padron, inicio, fin),
      asistenciaPorAfiliado: asistenciaPorAfiliado(padron, inicio, fin),
      sinAsistencia: afiliadosSinAsistencia(padron, inicio, fin).map((a) => a.id),
      filasPadron: filas(fin),
      resumenEtario: resumenEtario(padron, fin),
      activos: afiliadosActivos(padron, fin).length
    })),
    hoy: { filasPadron: filas(hoy), resumenEtario: resumenEtario(padron, hoy), activos: afiliadosActivos(padron, hoy).length },
    porDia: padron.atenciones.map((at) => ({
      id: at.id, fecha: at.fecha, desglose: desgloseDelDia(at), recaudacionCent: recaudacionDelDiaCent(at)
    })),
    hallazgosAfiliados: validarAfiliados(padron),
    hallazgosAtenciones: validarAtenciones(padron),
    conteo: contar([...validarAfiliados(padron), ...validarAtenciones(padron)])
  };
}

if (revision.partes.includes('rendiciones')) {
  const padron = padronVivo;
  salida.rendiciones = datos.rendiciones.map((r) => ({
    id: r.id,
    totales: totales(r),
    gastoPorOrigen: gastoPorOrigen(r),
    costoPorRacionCent: padron ? costoPorRacionCent(r, padron) : null,
    descuadres: (r.egresos || []).map((e) => descuadreFila(e)),
    hallazgos: validarRendicion(r),
    conteo: contar(validarRendicion(r))
  }));
  const nombres = [...new Set(datos.rendiciones.flatMap((r) => (r.egresos || []).map((e) => e.proveedorNombre)).filter(Boolean))];
  salida.sugerenciasRuc = Object.fromEntries(nombres.map((n) => [n, sugerirRucProveedor(datos.rendiciones, n)]));
  salida.descripcionesUsadas = descripcionesUsadas(datos.rendiciones);
}

if (revision.partes.includes('calendario')) {
  const c = datos.calendario;
  salida.calendario = {
    alerta: alertaProximaEntrega(c, hoy),
    agenda: agendaEntregas(c, hoy, 6),
    avisos: programaDeAvisos(c, hoy),
    hallazgos: validarCalendario(c)
  };
}
if (revision.partes.includes('config')) {
  salida.config = {
    hallazgosPrecios: validarPreciosMenu(datos.config),
    sugerencias: sugerenciasEncabezado(datos.config)
  };
}

const destino = ruta.replace(/\.json$/, '') + '.esperado.json';
writeFileSync(destino, JSON.stringify(salida, null, 2));
console.log(`Escrito ${destino}`);
