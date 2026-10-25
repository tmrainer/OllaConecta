// Qué texto va en cada lugar de la hoja oficial del B-1, ya escrito como en el
// papel (fechas DD-MM-AA, soles con dos decimales, «varios»). Las posiciones
// están en PlantillaB1.kt. No inventa nada: lo que falta queda vacío.
//
// Si hay más compras que renglones, van en una segunda hoja (o más), con el
// encabezado repetido y los tres totales solo en la última.
package io.github.tmrainer.asistencia.nucleo.documentos

import io.github.tmrainer.asistencia.nucleo.calculos.totales
import io.github.tmrainer.asistencia.nucleo.modelo.Egreso
import io.github.tmrainer.asistencia.nucleo.modelo.Rendicion
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.nucleo.modelo.formatearSoles

private fun soles(c: Long?) = formatearSoles(c)
private fun texto(v: String?) = (v ?: "").trimJs()

/** Una compra como renglón de la tabla. Las claves son las de PLANTILLA_B1.egresos.columnas. */
data class FilaEgreso(
    val fechaCompra: String,
    val descripcion: String,
    val cantidad: String,
    val unidadMedida: String,
    val rucProveedor: String,
    val boleta: String,
    val precioUnitario: String,
    val montoTotal: String
) {
    /** El texto de una columna por su clave. */
    operator fun get(columna: String): String = when (columna) {
        "fechaCompra" -> fechaCompra
        "descripcion" -> descripcion
        "cantidad" -> cantidad
        "unidadMedida" -> unidadMedida
        "rucProveedor" -> rucProveedor
        "boleta" -> boleta
        "precioUnitario" -> precioUnitario
        "montoTotal" -> montoTotal
        else -> ""
    }

    companion object {
        /** Las claves, en el orden de la hoja. */
        val CLAVES = listOf("fechaCompra", "descripcion", "cantidad", "unidadMedida", "rucProveedor", "boleta",
            "precioUnitario", "montoTotal")
    }
}

/** Una compra como renglón de la tabla. */
fun filaDeEgreso(e: Egreso): FilaEgreso {
    val serie = texto(e.boletaSerie)
    val numero = texto(e.boletaCorrelativo)
    val cantidad = e.cantidad
    return FilaEgreso(
        fechaCompra = aDdMmAa(e.fechaCompra),
        descripcion = texto(e.descripcion),
        cantidad = if (e.cantidadVarios) "varios" else if (cantidad != null && cantidad.isFinite()) numeroJs(cantidad) else "",
        unidadMedida = texto(e.unidadMedida),
        rucProveedor = texto(e.rucProveedor),
        boleta = if (serie.isNotEmpty() && numero.isNotEmpty()) "$serie-$numero" else serie.ifEmpty { numero },
        precioUnitario = if (e.precioUnitarioVarios) "varios" else soles(e.precioUnitarioCent),
        montoTotal = soles(e.montoTotalCent)
    )
}

private fun filaVacia(f: FilaEgreso) = FilaEgreso.CLAVES.all { f[it] == "" }

/**
 * Una hoja del B-1: los textos del encabezado, ingresos, firmas y (solo en la
 * última) los totales, por clave de PLANTILLA_B1.campos; y sus renglones de compras.
 */
data class HojaB1(val campos: Map<String, String>, val filas: List<FilaEgreso>)

/** Una entrada por hoja. Sin rendición, una hoja vacía. */
fun hojasDelB1(r: Rendicion?): List<HojaB1> {
    val rendicion = r ?: Rendicion()
    val filas = rendicion.egresos.map(::filaDeEgreso).filter { !filaVacia(it) }
    val encabezado = linkedMapOf(
        "marcaComedor" to if (rendicion.tipoCentro == "comedor") "X" else "",
        "marcaOllaComun" to if (rendicion.tipoCentro == "olla_comun") "X" else "",
        "nombreCentro" to texto(rendicion.nombreCentro),
        "codigoPca" to texto(rendicion.codigoPca),
        "periodo" to texto(rendicion.periodo.etiqueta),
        "fechaRendicion" to aDdMmAa(rendicion.fechaRendicion),
        "nombrePresidenta" to texto(rendicion.nombrePresidenta),
        "dniPresidenta" to texto(rendicion.dniPresidenta),
        "celular" to texto(rendicion.celular),
        "montoSubsidio" to soles(rendicion.montoSubsidioCent),
        "fechaAsignacion" to aDdMmAa(rendicion.fechaAsignacion),
        "nombrePresidentaFirma" to texto(rendicion.nombrePresidentaFirma),
        "dniPresidentaFirma" to texto(rendicion.dniPresidentaFirma),
        "nombreTesorera" to texto(rendicion.nombreTesorera),
        "dniTesorera" to texto(rendicion.dniTesorera)
    )
    val t = totales(rendicion)
    val pie = linkedMapOf(
        "totalConSubsidio" to soles(t.conSubsidio),
        // «Registrar de corresponder»: si es cero, el renglón va vacío.
        "totalAporte" to if (t.aporte != null && t.aporte != 0L) soles(t.aporte) else "",
        "totalRendicion" to soles(t.total)
    )
    val cuantas = maxOf(1, (filas.size + FILAS_EGRESOS - 1) / FILAS_EGRESOS)
    return List(cuantas) { i ->
        HojaB1(
            campos = if (i == cuantas - 1) encabezado + pie else encabezado.toMap(),
            filas = filas.subList(minOf(i * FILAS_EGRESOS, filas.size), minOf((i + 1) * FILAS_EGRESOS, filas.size)).toList()
        )
    }
}
