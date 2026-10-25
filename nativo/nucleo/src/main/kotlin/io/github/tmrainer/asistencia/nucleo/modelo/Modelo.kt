// Modelo.kt — forma de los datos. Porte de web/js/modelo.js (constructores).
// Sin Android, sin red.
//
// Las clases son inmutables: se cambian con copy(). Todos los campos tienen
// valor por defecto para que un respaldo con un campo ausente o en null se lea
// igual (coerceInputValues). Los conjuntos cerrados (tipoMenu, tipoAfiliado,
// estado…) se guardan como String, como en la web: un valor raro no rompe la
// lectura; las validaciones lo señalan.
package io.github.tmrainer.asistencia.nucleo.modelo

import kotlinx.serialization.Serializable

const val VERSION_ESQUEMA = 1
const val VERSION_PADRON = 3      // v3: raciones derivadas de la asistencia
const val VERSION_CALENDARIO = 1

// ------------------------------------------------------------ Formato B-1

@Serializable
data class Periodo(
    val tipo: String = "mensual",     // "semanal" | "quincenal" | "mensual"
    val inicio: String = "",          // ISO
    val fin: String = "",             // ISO
    val etiqueta: String = ""         // lo que se escribe en "MES DE RENDICION"
)

@Serializable
data class Egreso(
    val id: String = "",
    val fechaCompra: String = "",
    val descripcion: String = "",
    val insumoId: String? = null,
    val cantidad: Double? = null,
    val cantidadVarios: Boolean = false,
    val unidadMedida: String = "",
    val rucProveedor: String = "",
    val proveedorNombre: String = "",     // solo para sugerencias; NO va al formato
    val boletaSerie: String = "",
    val boletaCorrelativo: String = "",
    val precioUnitarioCent: Long? = null,
    val precioUnitarioVarios: Boolean = false,
    val montoTotalCent: Long? = null,     // SIEMPRE obligatorio
    val origenFondo: String = "subsidio"  // "subsidio" | "ayuda_social" | "aporte_propio"
)

/** Cuándo se exportó un B-1 (pagina-b1.js, registrarExportacion). Va en la copia. */
@Serializable
data class Generacion(
    val en: String = "",
    val formato: String = "",
    val totalCent: Long? = null,
    val como: String = ""
)

@Serializable
data class Rendicion(
    val version: Int = VERSION_ESQUEMA,
    val id: String = "",
    val actualizadoEn: String = "",
    val tipoCentro: String = "",          // "comedor" | "olla_comun"
    val nombreCentro: String = "",
    val codigoPca: String = "",
    val periodo: Periodo = Periodo(),
    val fechaRendicion: String = "",
    val nombrePresidenta: String = "",
    val dniPresidenta: String = "",
    val celular: String = "",
    val montoSubsidioCent: Long? = null,
    val fechaAsignacion: String = "",
    val egresos: List<Egreso> = emptyList(),
    val nombrePresidentaFirma: String = "",
    val dniPresidentaFirma: String = "",
    val nombreTesorera: String = "",
    val dniTesorera: String = "",
    val generaciones: List<Generacion> = emptyList(),
    /** Lo comprado con dinero de las señoras: NO va en el B-1 ni en sus totales. */
    val gastosPropios: List<GastoPropio> = emptyList()
)

/** Una compra con dinero propio, fuera del B-1. Opcional: se anota si quieren. */
@Serializable
data class GastoPropio(
    val id: String = "",
    val fecha: String = "",
    val descripcion: String = "",
    val montoCent: Long? = null,
    val nota: String = ""
)

// ----------------------------------------------------------------- Padrón

@Serializable
data class Afiliado(
    val id: String = "",
    val apellidoPaterno: String = "",
    val apellidoMaterno: String = "",
    val nombres: String = "",
    val numeroDocumento: String = "",      // OBLIGATORIO
    val tipoDocumento: String = "dni",     // "dni" | "ce"
    val fechaNacimiento: String = "",      // opcional: la vía rápida es el grupo etario
    val grupoEtarioManual: String? = null, // OBLIGATORIO si no hay fecha de nacimiento
    val sexo: String = "",
    val tipoAfiliado: String = "",         // OBLIGATORIO: "habitual" | "caso_social"
    val activo: Boolean = true,
    val altaEn: String = "",
    val bajaEn: String = "",
    val motivoBaja: String = "",
    val origenDato: String = "manual",     // "manual" | "escaneo_dni" | "importado"
    val verificadoPor: String = "",
    val nota: String = ""
)

@Serializable
data class Asistencia(
    val afiliadoId: String = "",
    val tipoMenu: String = "normal"        // "normal" | "ayuda_social"
)

/** Cuentas escritas a mano en la v1/v2: se conservan, no se reconstruyen. */
@Serializable
data class Legado(
    val racionesNormales: Int? = null,
    val racionesAyudaSocial: Int? = null
)

/** Quienes comieron sin estar en el padrón, por menú. */
@Serializable
data class SinInscribir(
    val normal: Int? = null,
    val ayudaSocial: Int? = null
)

/**
 * Un día de atención. Las raciones NO se escriben: salen de contar la
 * asistencia. Los precios son una COPIA del precio
 * configurado al crear el día.
 */
@Serializable
data class Atencion(
    val id: String = "",
    val fecha: String = "",
    val asistencias: List<Asistencia> = emptyList(),
    val precioMenuNormalCent: Long? = null,
    val precioMenuAyudaSocialCent: Long? = null,
    val precioTomadoDeConfig: Boolean = false,
    val legado: Legado? = null,
    val nota: String = "",
    val sinInscribir: SinInscribir? = null,
    /** Qué se cocinó ese día. Lo escribe la encargada. */
    val menuDelDia: String = "",
    /** Cuántas raciones se prepararon. Lo escribe la encargada; las servidas se cuentan de la asistencia. */
    val racionesPreparadas: Int? = null
)

@Serializable
data class Padron(
    val version: Int = VERSION_PADRON,
    val afiliados: List<Afiliado> = emptyList(),
    val atenciones: List<Atencion> = emptyList(),
    /** Cada hoja de la Municipalidad que se cargó, y a quién agregó o retiró. */
    val hojasMunicipales: List<HojaMunicipal> = emptyList()
)

/**
 * Una actualización del padrón con la hoja que entrega la Municipalidad:
 * de qué fecha es la hoja, cuándo se cargó,
 * cuántas personas traía, y los ids de quienes entraron y salieron con ella.
 */
@Serializable
data class HojaMunicipal(
    val fecha: String = "",
    val cargadaEn: String = "",
    val personas: Int = 0,
    val agregados: List<String> = emptyList(),
    val retirados: List<String> = emptyList()
)

// ------------------------------------------------------------- Calendario

@Serializable
data class Entrega(
    val id: String = "",
    val fechaLimite: String = "",
    val estado: String = "pendiente",      // "pendiente" | "entregada" | "observada" | "conforme"
    val nota: String = ""
)

@Serializable
data class Calendario(
    val version: Int = VERSION_CALENDARIO,
    val periodicidad: String = "mensual",  // "semanal" | "quincenal" | "mensual"
    val fechaLimiteBase: String = "",      // una fecha de entrega que la usuaria conoce
    val diasDeAviso: List<Int> = listOf(7, 3, 1),
    val entregas: List<Entrega> = emptyList()
)

// ----------------------------------------------------------------- Config

@Serializable
data class Calibracion(val offsetXmm: Double = 0.0, val offsetYmm: Double = 0.0)

/** Encabezado de la última rendición, para proponerlo (sugerencias.js). */
@Serializable
data class UltimoCentro(
    val guardadoEn: String = "",
    val tipoCentro: String = "",
    val nombreCentro: String = "",
    val codigoPca: String = "",
    val nombrePresidenta: String = "",
    val dniPresidenta: String = "",
    val celular: String = "",
    val nombreTesorera: String = "",
    val dniTesorera: String = ""
)

@Serializable
data class Config(
    val version: Int = VERSION_ESQUEMA,
    val calibracion: Calibracion = Calibracion(),
    val ultimoCentro: UltimoCentro? = null,
    // Precio del menú, que el centro ya tiene fijado. Se copia a cada día nuevo.
    val precioMenuNormalCent: Long? = null,
    val precioMenuAyudaSocialCent: Long? = null
) {
    fun precios() = PreciosMenu(precioMenuNormalCent, precioMenuAyudaSocialCent)
}

/** Los dos precios vigentes (preciosActuales() de estado.js). */
data class PreciosMenu(val precioMenuNormalCent: Long?, val precioMenuAyudaSocialCent: Long?)

/** Quien usa el teléfono (perfil.js). No va en los respaldos. */
data class Perfil(
    val nombre: String = "",
    val rol: String = "",        // "presidenta" | "tesorera" | "apoyo"
    val dni: String = "",
    val celular: String = ""
)

// ----------------------------------------------------------- Respaldo

/** El respaldo completo que exporta la web (armarRespaldo() en ui.js). */
@Serializable
data class Respaldo(
    val exportadoEn: String = "",
    val aplicacion: String = "rendicion-b1",
    val rendiciones: List<Rendicion> = emptyList(),
    val padron: Padron? = null,
    val calendario: Calendario? = null,
    val config: Config? = null
)

// ---------------------------------------------------------- constructores

fun nuevoEgreso() = Egreso(id = uuid())

fun nuevaRendicion(ahora: String = ahoraIso()) = Rendicion(
    id = uuid(),
    actualizadoEn = ahora,
    egresos = listOf(nuevoEgreso())
)

fun nuevoAfiliado(hoy: String = hoyIso()) = Afiliado(id = uuid(), altaEn = hoy)

/**
 * Solo construye el día: NO lo agrega al padrón. La fecha es hoy; quien
 * necesita otro día la sobrescribe con copy(). `precios` null deja los
 * precios vacíos y precioTomadoDeConfig en false.
 */
fun nuevaAtencion(precios: PreciosMenu?, hoy: String = hoyIso()) = Atencion(
    id = uuid(),
    fecha = hoy,
    precioMenuNormalCent = precios?.precioMenuNormalCent,
    precioMenuAyudaSocialCent = precios?.precioMenuAyudaSocialCent,
    precioTomadoDeConfig = precios != null
)

fun padronVacio() = Padron()

fun calendarioVacio() = Calendario()

fun nuevaEntrega(fechaLimite: String?) = Entrega(id = uuid(), fechaLimite = fechaLimite ?: "")

fun configVacia() = Config()
