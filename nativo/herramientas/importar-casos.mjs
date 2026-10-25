// Corre web/js/importar.js tal cual (Node 22) sobre entradas difíciles (separadores,
// comillas, BOM, saltos de línea, tildes, fechas de varias formas, duplicados, filas
// vacías, títulos en otro orden) con un «hoy» fijo, y escribe entradas + resultados en
// nucleo/src/test/resources/importar-casos.json. ImportarWebTest.kt compara todo menos
// los id (uuid() no puede coincidir).
//
//   node nativo/herramientas/importar-casos.mjs
//
// Datos inventados. Quedan fuera las entradas donde Kotlin difiere a propósito
// (fecha que no existe, edad sin cifras, «Mujer»/«Hombre»): ver Importar.kt.

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  detectarSeparador, parsearTabla, desarmarCelda, detectarColumnas, pareceCabecera,
  parsearFechaFlexible, parsearGrupoEtario, parsearTipoAfiliado, parsearTipoDocumento,
  prepararImportacion, filasImportables
} from '../../web/js/importar.js';
import { nuevoAfiliado, padronVacio } from '../../web/js/modelo.js';

const HOY = '2026-10-20';

// «Hoy» fijo: hoyIso() y el pivote de los años de dos cifras usan new Date().
const DateReal = Date;
const fijo = new DateReal(2026, 9, 20, 12, 0, 0).getTime();
globalThis.Date = class extends DateReal {
  constructor(...a) { super(...(a.length ? a : [fijo])); }
  static now() { return fijo; }
};

const aqui = dirname(fileURLToPath(import.meta.url));
const salida = join(aqui, '..', 'nucleo', 'src', 'test', 'resources', 'importar-casos.json');

const funciones = {
  detectarSeparador: ['a\tb', 'a;b,c;d', 'a,b,c;d', '"x;y",z', '', 'sin separador',
    'a;b\r\nc,d,e,f', '"a\tb";c\td', '﻿Apellido;Nombres'],
  parsearTabla: [
    { texto: 'a;b\r\n"c;d";"e ""f"""\r\n\r\n;;\r\ng;h' },
    { texto: 'a,b\n"multi\nlínea",x\n\n\ny,z\n' },
    { texto: 'a\tb\rc\td\r\re\tf' },
    { texto: '  a  \t b \n="007"\t\'=1+1' },
    { texto: 'a,b;c\nd,e;f', separador: ';' },
    { texto: '' },
    { texto: '"sin cerrar,a\nb' }
  ],
  desarmarCelda: ["'=SUMA(1)", '="00012"', "O'Brien", "'+51", "'@x", "'-3", '="a""b"', '="', "'", '=1+1', ''],
  detectarColumnas: [
    ['Ap. Paterno', 'APELLIDO MATERNO', 'Nombres', 'DNI', 'Grupo de edad', 'Tipo', 'Correo'],
    ['N°', 'Nro. documento', 'Tipo doc.', 'F. Nacimiento', 'Condición', 'Nombre(s)', 'Observaciones'],
    ['DNI', 'dni', 'Nombres y apellidos', 'Apellidos y nombres', '', 'Años', 'Género'],
    ['Primer apellido', 'Segundo apellido', 'Etapa de vida', 'Categoría', 'Fecha nac', 'Número'],
    ['Tipo de documento', 'Tipo de afiliada', 'Documento de identidad', 'Grupo etario (MIDIS)']
  ],
  parsearFechaFlexible: ['14/09/1960', '1-2-05', '01.12.1999', ' 2000-01-31 ', '14-09-27', '14-09-26',
    '5/5/2026', '2010.03.15', 'ayer', '', '14/09', '14-09-2026x', '29/02/2024', '14/09-1960'],
  parsearGrupoEtario: ['NIÑOS', 'menor de edad', 'Infante', 'adolescentes', 'Adulto/a', 'adulto mayor',
    'ANCIANO', 'Joven', 'tercera edad', 'etapa', '', '  adulto  ', 'Adulta', 'Adulto. Mayor'],
  parsearTipoAfiliado: ['Ayuda Social', 'social', 'Vulnerable', 'Sí', 'x', 'X', 'NO', 'Común', 'normal',
    'regular', 'habitual ', 'quizás', '', 'Caso  social'],
  parsearTipoDocumento: ['DNI', 'C.E.', 'CE', 'Carné de extranjería', 'extranjeria', '', 'pasaporte'],
  pareceCabecera: [['Nombres', 'DNI'], ['Nombres', 'Correo'], ['PADRÓN DE BENEFICIARIOS'], []]
};

const resultadosFunciones = Object.fromEntries(Object.entries(funciones).map(([nombre, entradas]) => [
  nombre,
  entradas.map((entrada) => {
    let esperado;
    if (nombre === 'parsearTabla') {
      esperado = parsearTabla(entrada.texto, entrada.separador)
        .map((f) => ({ celdas: [...f], linea: f.linea }));
    } else {
      const fns = { detectarSeparador, desarmarCelda, detectarColumnas, parsearFechaFlexible,
        parsearGrupoEtario, parsearTipoAfiliado, parsearTipoDocumento, pareceCabecera };
      esperado = fns[nombre](entrada);
    }
    return { entrada, esperado };
  })
]));

function persona(datos) {
  return Object.assign(nuevoAfiliado(), { altaEn: '2026-01-01' }, datos);
}

// Un padrón con un documento activo, uno dado de baja y un nombre repetido.
const padronConGente = Object.assign(padronVacio(), {
  afiliados: [
    persona({ id: 'p-1', apellidoPaterno: 'Quispe', apellidoMaterno: 'Rojas', nombres: 'Rosa', numeroDocumento: '00000031' }),
    persona({ id: 'p-2', apellidoPaterno: 'Mamani', nombres: 'Luz', numeroDocumento: '00000032', activo: false, bajaEn: '2026-05-01' }),
    persona({ id: 'p-3', apellidoPaterno: 'Torres', nombres: 'Eva', numeroDocumento: '00000099' }),
    persona({ id: 'p-4', apellidoPaterno: 'Ñahui', apellidoMaterno: 'Pérez', nombres: 'María  José', numeroDocumento: '' }),
    persona({ id: 'p-5', apellidoPaterno: 'Díaz', nombres: 'Eva', numeroDocumento: '00012345' })
  ]
});

const importaciones = [
  {
    nombre: 'TSV pegado de Excel, con título arriba y columnas con otros nombres',
    padron: padronVacio(),
    texto: [
      'PADRÓN DE BENEFICIARIOS 2026\t\t\t',
      'Comedor Ficticio Los Pinos',
      '',
      'N°\tAp. Paterno\tAp. Materno\tNombre(s)\tTipo doc.\tNro. documento\tF. Nacimiento\tSexo\tCondición\tObservaciones',
      '1\tQuispe\tRojas\tRosa Elena\tDNI\t00000011\t14/09/1960\tF\tHabitual\tninguna',
      '2\tMamani\t\tLuz\tC.E.\t000123\t1-2-05\tFemenino\tAyuda social\t',
      '3\tHuamán\tTorres\tJosé\tDNI\t1234567\t2010.03.15\tM\tSí\t',
      '4\tPaz\tSol\tAna\tDNI\t000 000 12\t2000-01-31\tmasculino\tregular\t',
      '5\t\t\t\t\t\t\t\t\t',
      'TOTAL\t\t\t\t\t\t\t\t\t4'
    ].join('\n')
  },
  {
    nombre: 'CSV con punto y coma, BOM, \\r\\n, comillas y salto dentro de una celda',
    padron: padronVacio(),
    texto: '﻿' + [
      'Apellido paterno;Apellido materno;Nombres;DNI;Grupo de edad;Tipo;Sexo',
      '"Quispe";"Rojas";"Rosa ""Rosita""";00000021;Adulto mayor;Ayuda social;F',
      'Ñahui;Pérez;"María; José";="00000022";niña;habitual;F',
      "'=Mamani;;Luz;00000021;adulto;no;",
      'Torres;;"Ana\r\nBeatriz";00000023;Joven;caso social;',
      ';;;;;;',
      'Vargas;;Eva;00000024;vejete;???;',
      '  Rojas  ; ;  Sol  ;  00000025 ; ADOLESCENTE ; HABITUAL ; m '
    ].join('\r\n') + '\r\n'
  },
  {
    nombre: 'CSV con comas, títulos en mayúsculas y en otro orden, apellidos juntos, edad',
    padron: padronConGente,
    texto: [
      'DNI/CE,NOMBRES,APELLIDOS,EDAD,TIPO,GENERO',
      '00000031,Rosa,Quispe Rojas,45 años,Habitual,F',
      '00000032,Luz,Mamani,8,Habitual,',
      '00000033,Ana,De la Cruz Soto,999,Habitual,',
      '00000034,Eva,Torres,67,"Ayuda social",M',
      '00000035,,,30,Habitual,',
      '1234,Leo,Paz,17,habitual,',
      '12345,Eva,Díaz,70,Habitual,',
      ',María José,Ñahui Pérez,,,'
    ].join('\n')
  },
  {
    nombre: 'El reporte que exporta la app, con carné de extranjería y edad sin grupo',
    padron: padronVacio(),
    texto: [
      'N°,Apellido paterno,Apellido materno,Nombres,Tipo de documento,Número de documento,Grupo de edad,Edad,Tipo,Días asistidos',
      '1,Álvarez,Rojas,Rosa,DNI,00000041,Adulto/a,,Habitual,3',
      '2,Benítez,,Luz,CE,001234567,Adolescente,,Ayuda social,0',
      '3,Cárdenas,Soto,Ana,Carné de extranjería,12345,Niño/a,,Habitual,1',
      '4,Díaz,,Eva,DNI,12345,,70,Habitual,2',
      '5,Espinoza,,Leo,DNI,00000045,Adulto/a mayor,40,Habitual,0'
    ].join('\n')
  },
  {
    nombre: 'Fechas de varias formas, con y sin grupo, y años de dos cifras',
    padron: padronVacio(),
    texto: [
      'Apellido paterno\tNombres\tDNI\tFecha de nacimiento\tGrupo\tTipo',
      'Uno\tA\t00000051\t14-09-27\t\tHabitual',
      'Dos\tB\t00000052\t14-09-26\tAdulto\tHabitual',
      'Tres\tC\t00000053\t5/5/2020\tAdulto mayor\tHabitual',
      'Cuatro\tD\t00000054\tayer\tAdulto\tHabitual',
      'Cinco\tE\t00000055\t29.02.2024\t\tHabitual',
      'Seis\tF\t00000056\t\t\tHabitual',
      'Siete\tG\t00000057\t1985-06-30\t\t'
    ].join('\n')
  },
  {
    nombre: 'Saltos de línea \\r sueltos, columnas repetidas y sin reconocer',
    padron: padronVacio(),
    texto: [
      'Nombres\tDNI\tdni\tCorreo\tTeléfono\tApellido\tEtapa',
      'Rosa\t00000061\t99999999\trosa@ejemplo.test\t900000000\tQuispe\tAdulto',
      'Luz\t 00000062 \t\t\t\tMamani\tniño',
      'Ana\t00000061\t\t\t\tTorres\tadulto'
    ].join('\r')
  },
  {
    nombre: 'Solo títulos, sin nadie',
    padron: padronVacio(),
    texto: 'Apellido paterno,Nombres,DNI\n\n'
  },
  {
    nombre: 'Los títulos después de la décima fila no se buscan',
    padron: padronVacio(),
    texto: [...Array.from({ length: 10 }, (_, i) => `Línea de título ${i + 1}`),
      'Apellido paterno\tNombres\tDNI', 'Quispe\tRosa\t00000071'].join('\n')
  },
  {
    nombre: 'Los títulos en la décima fila sí',
    padron: padronVacio(),
    texto: [...Array.from({ length: 9 }, (_, i) => `Línea de título ${i + 1}`),
      'Apellido paterno\tNombres\tDNI', 'Quispe\tRosa\t00000071'].join('\n')
  },
  { nombre: 'Vacío', padron: padronVacio(), texto: '' },
  { nombre: 'Solo espacios', padron: padronVacio(), texto: '   \n\t\n   ' },
  { nombre: 'Sin títulos', padron: padronVacio(), texto: 'Ana;Perez;12345678\nRosa;Quispe;87654321' }
];

function sinId(a) {
  const { id, ...resto } = a;
  return resto;
}

const resultadosImportaciones = importaciones.map(({ nombre, padron, texto }) => {
  const r = prepararImportacion(texto, padron);
  return {
    nombre, texto, padron,
    esperado: {
      columnas: r.columnas,
      sinReconocer: r.sinReconocer,
      filas: r.filas.map((f) => ({
        linea: f.linea, afiliado: sinId(f.afiliado), nombreCompleto: f.nombreCompleto,
        estado: f.estado, avisos: f.avisos, ceroAgregado: f.ceroAgregado
      })),
      resumen: r.resumen || null,
      error: r.error,
      importables: filasImportables(r.filas).map((f) => f.linea)
    }
  };
});

writeFileSync(salida, JSON.stringify({ hoy: HOY, funciones: resultadosFunciones, importaciones: resultadosImportaciones }, null, 2) + '\n');
console.log(`Escrito ${salida}: ${Object.values(resultadosFunciones).flat().length} casos de funciones, ` +
  `${resultadosImportaciones.length} importaciones.`);
