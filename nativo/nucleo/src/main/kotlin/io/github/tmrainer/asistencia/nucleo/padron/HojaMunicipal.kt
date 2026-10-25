// HojaMunicipal.kt — el padrón de la olla y la hoja de la Municipalidad.
// La olla puede inscribir a quien llega: queda «pendiente»
// hasta que la lleven a la Municipalidad y aparezca en la hoja. Cada hoja que
// entrega la Municipalidad es una actualización: se compara por DNI y se ve
// quién entra, quién vuelve, quién sigue, a quién confirma la hoja (las
// pendientes que ya figuran) y quién sale. Una pendiente que todavía no
// figura no sale: sigue pendiente.
//
// Nada se aplica solo: compararConHoja() arma la comparación; aplicarHoja()
// hace lo que la persona eligió. Quien sale queda de baja con el motivo «no
// figura en la hoja municipal del …» (las bajas no borran). Cada carga
// queda anotada en `padron.hojasMunicipales`.
package io.github.tmrainer.asistencia.nucleo.padron

import io.github.tmrainer.asistencia.nucleo.importar.FilaImportada
import io.github.tmrainer.asistencia.nucleo.importar.Importacion
import io.github.tmrainer.asistencia.nucleo.modelo.Afiliado
import io.github.tmrainer.asistencia.nucleo.modelo.HojaMunicipal
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.nucleo.modelo.ahoraIso
import io.github.tmrainer.asistencia.nucleo.modelo.nombreCompleto
import io.github.tmrainer.asistencia.nucleo.modelo.normalizarNombre

/** El motivo de baja de quien no figura en una hoja: se reconoce para el historial. */
fun motivoNoFigura(fechaHoja: String) = "No figura en la hoja municipal del ${aDdMmAa(fechaHoja)}"

/** Alguien de la hoja que ya está en el padrón con el mismo DNI, pero con otro nombre. */
data class NombreDistinto(val mia: Afiliado, val deLaHoja: FilaImportada)

/**
 * Lo que cambiaría la hoja. Se compara por DNI; una fila sin documento, por
 * nombre (si hay uno solo igual).
 */
data class ComparacionHoja(
    /** En la hoja y no aquí: entran. */
    val entran: List<FilaImportada>,
    /** En la hoja y aquí de baja: vuelven a estar activas. */
    val vuelven: List<Afiliado>,
    /** En la hoja y aquí activas: siguen igual. */
    val siguen: List<Afiliado>,
    /** De las que siguen, las que en la hoja tienen otro nombre: la persona elige. */
    val nombreDistinto: List<NombreDistinto>,
    /** De las que siguen, las que la olla inscribió y la hoja ya trae: quedan registradas. */
    val confirman: List<Afiliado>,
    /** Activas aquí y no en la hoja: salen (se dan de baja, no se borran). */
    val salen: List<Afiliado>,
    /** Inscritas por la olla que la hoja todavía no trae: no salen, siguen pendientes. */
    val pendientes: List<Afiliado>,
    /** Filas repetidas dentro de la misma hoja: no se usan. */
    val repetidas: List<FilaImportada>,
    /** Cuántas personas trae la hoja (sin filas vacías ni repetidas). */
    val enLaHoja: Int
) {
    val hayCambios: Boolean get() = entran.isNotEmpty() || vuelven.isNotEmpty() || salen.isNotEmpty() || nombreDistinto.isNotEmpty() || confirman.isNotEmpty()
}

private fun clave(a: Afiliado) = a.numeroDocumento.trim()

/**
 * Compara el padrón con la hoja. [hoja] es `prepararImportacion(texto, Padron())`:
 * contra un padrón vacío, para que la importación solo lea las filas (los
 * repetidos dentro de la hoja sí salen marcados como «duplicada»).
 */
fun compararConHoja(padron: Padron, hoja: Importacion): ComparacionHoja {
    val filas = hoja.filas.filter { it.estado != "vacia" }
    val repetidas = filas.filter { it.estado == "duplicada" }
    val validas = filas.filter { it.estado != "duplicada" }
    val porDoc = padron.afiliados.filter { clave(it).isNotEmpty() }.groupBy { clave(it) }
    val porNombre = padron.afiliados.groupBy { normalizarNombre(nombreCompleto(it)) }

    val entran = ArrayList<FilaImportada>()
    val vuelven = ArrayList<Afiliado>()
    val siguen = ArrayList<Afiliado>()
    val nombreDistinto = ArrayList<NombreDistinto>()
    val enLaHoja = HashSet<String>()           // ids del padrón que figuran en la hoja
    for (f in validas) {
        val doc = clave(f.afiliado)
        // Por DNI; sin DNI, por nombre si hay una sola persona con ese nombre.
        val candidatas = if (doc.isNotEmpty()) porDoc[doc].orEmpty()
        else porNombre[normalizarNombre(f.nombreCompleto)].orEmpty().takeIf { it.size == 1 }.orEmpty()
        // Si el mismo DNI está dos veces aquí, cuenta la activa.
        val mia = candidatas.firstOrNull { it.activo } ?: candidatas.firstOrNull()
        when {
            mia == null -> entran.add(f)
            mia.id in enLaHoja -> {}            // dos filas de la hoja para la misma persona de aquí
            !mia.activo -> { vuelven.add(mia); enLaHoja.add(mia.id) }
            else -> {
                siguen.add(mia); enLaHoja.add(mia.id)
                if (doc.isNotEmpty() && normalizarNombre(nombreCompleto(mia)) != normalizarNombre(f.nombreCompleto)) {
                    nombreDistinto.add(NombreDistinto(mia, f))
                }
            }
        }
    }
    val fuera = padron.afiliados.filter { it.activo && it.id !in enLaHoja }
    val confirman = siguen.filter { it.registroMunicipal == "pendiente" }
    return ComparacionHoja(
        entran, vuelven, siguen, nombreDistinto, confirman,
        salen = fuera.filter { it.registroMunicipal != "pendiente" },
        pendientes = fuera.filter { it.registroMunicipal == "pendiente" },
        repetidas = repetidas, enLaHoja = validas.size
    )
}

/** Lo que eligió la persona: quiénes entran, quiénes salen y a quién se le pone el nombre de la hoja. */
data class EleccionHoja(
    val entran: List<FilaImportada>,
    val vuelven: List<Afiliado>,
    val salen: List<Afiliado>,
    val usarNombreDeLaHoja: List<NombreDistinto> = emptyList(),
    /** Los que siguen (figuran en la hoja): quedan registradas con su fecha. */
    val siguen: List<Afiliado> = emptyList()
)

/**
 * Aplica lo elegido y anota la hoja. Quien entra queda con `altaEn` = fecha de
 * la hoja; quien sale, de baja desde esa fecha con su motivo; quien vuelve,
 * activo otra vez. Quienes figuran en la hoja quedan registradas con esa
 * fecha. Lo demás no se toca.
 */
fun aplicarHoja(padron: Padron, fechaHoja: String, eleccion: EleccionHoja, enLaHoja: Int, cargadaEn: String = ahoraIso()): Padron {
    val figuran = eleccion.siguen.map { it.id }.toSet()
    val salen = eleccion.salen.map { it.id }.toSet()
    val vuelven = eleccion.vuelven.map { it.id }.toSet()
    val nombres = eleccion.usarNombreDeLaHoja.associate { it.mia.id to it.deLaHoja.afiliado }
    val motivo = motivoNoFigura(fechaHoja)
    val afiliados = padron.afiliados.map { a ->
        var x = a
        if (a.id in salen) x = x.copy(activo = false, bajaEn = fechaHoja, motivoBaja = motivo)
        if (a.id in vuelven) x = x.copy(activo = true, bajaEn = "", motivoBaja = "")
        nombres[a.id]?.let { h -> x = x.copy(apellidoPaterno = h.apellidoPaterno, apellidoMaterno = h.apellidoMaterno, nombres = h.nombres) }
        if (a.id in figuran || a.id in vuelven) x = x.copy(registroMunicipal = "registrada", registroMunicipalEn = fechaHoja)
        x
    }
    val nuevos = eleccion.entran.map { it.afiliado.copy(altaEn = fechaHoja, origenDato = "importado", registroMunicipal = "registrada", registroMunicipalEn = fechaHoja) }
    val anotada = HojaMunicipal(
        fecha = fechaHoja, cargadaEn = cargadaEn, personas = enLaHoja,
        agregados = nuevos.map { it.id } + vuelven.toList(), retirados = salen.toList()
    )
    return padron.copy(afiliados = afiliados + nuevos, hojasMunicipales = padron.hojasMunicipales + anotada)
}

/**
 * Copiar a mano una persona de la hoja en papel: se agrega con la fecha de la
 * hoja y se anota en esa misma hoja (si ya había una de esa fecha, se suma).
 */
fun agregarDeLaHoja(padron: Padron, a: Afiliado, fechaHoja: String, cargadaEn: String = ahoraIso()): Padron {
    val nuevo = a.copy(altaEn = fechaHoja, registroMunicipal = "registrada", registroMunicipalEn = fechaHoja)
    return padron.copy(afiliados = padron.afiliados + nuevo, hojasMunicipales = anotarEn(padron.hojasMunicipales, fechaHoja, cargadaEn) {
        it.copy(agregados = it.agregados + nuevo.id)
    })
}

/** Retirar a mano a quien ya no figura en la hoja en papel: de baja desde la fecha de la hoja, con su motivo. */
fun retirarPorLaHoja(padron: Padron, afiliadoId: String, fechaHoja: String, cargadaEn: String = ahoraIso()): Padron =
    padron.copy(
        afiliados = padron.afiliados.map { if (it.id == afiliadoId) it.copy(activo = false, bajaEn = fechaHoja, motivoBaja = motivoNoFigura(fechaHoja)) else it },
        hojasMunicipales = anotarEn(padron.hojasMunicipales, fechaHoja, cargadaEn) { it.copy(retirados = it.retirados + afiliadoId) }
    )

private fun anotarEn(hojas: List<HojaMunicipal>, fecha: String, cargadaEn: String, cambio: (HojaMunicipal) -> HojaMunicipal): List<HojaMunicipal> {
    val i = hojas.indexOfLast { it.fecha == fecha }
    return if (i >= 0) hojas.toMutableList().also { it[i] = cambio(it[i]) }
    else hojas + cambio(HojaMunicipal(fecha = fecha, cargadaEn = cargadaEn))
}

/** La última hoja cargada (la de fecha más nueva), o null si todavía no se cargó ninguna. */
fun ultimaHoja(padron: Padron): HojaMunicipal? = padron.hojasMunicipales.maxByOrNull { it.fecha }

/** Inscribir en la olla a quien llegó: queda pendiente de registrar en la Municipalidad. */
fun inscribirEnLaOlla(padron: Padron, a: Afiliado): Padron =
    padron.copy(afiliados = padron.afiliados + a.copy(registroMunicipal = "pendiente", registroMunicipalEn = ""))

/** Marcar a mano que ya está registrada en la Municipalidad (por ejemplo, con la constancia en la mano). */
fun marcarRegistrada(padron: Padron, afiliadoId: String, fecha: String): Padron =
    padron.copy(afiliados = padron.afiliados.map { if (it.id == afiliadoId) it.copy(registroMunicipal = "registrada", registroMunicipalEn = fecha) else it })

/** Quienes la olla inscribió y falta llevar a la Municipalidad (activas). */
fun pendientesDeRegistro(padron: Padron): List<Afiliado> = padron.afiliados.filter { it.activo && it.registroMunicipal == "pendiente" }
