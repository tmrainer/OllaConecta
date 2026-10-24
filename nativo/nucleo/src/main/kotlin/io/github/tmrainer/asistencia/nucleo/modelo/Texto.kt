// Texto.kt — utilidades de texto de web/js/modelo.js: plurales, nombres, orden.
package io.github.tmrainer.asistencia.nucleo.modelo

import java.text.Collator
import java.text.Normalizer
import java.text.RuleBasedCollator
import java.util.Locale
import java.util.UUID

fun uuid(): String = UUID.randomUUID().toString()

/**
 * «1 persona», «3 personas»: el plural de verdad, no «persona(s)».
 * @param varios el plural, si no es singular + «s»
 */
fun cuantos(n: Int, uno: String, varios: String = uno + "s"): String =
    "$n ${if (n == 1) uno else varios}"

/** Nombre completo para mostrar y ordenar. Solo une lo que hay. */
fun nombreCompleto(afiliado: Afiliado): String =
    listOf(afiliado.apellidoPaterno, afiliado.apellidoMaterno, afiliado.nombres)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")

/**
 * Orden alfabético en castellano (la ñ va después de la n), para listas de
 * nombres. Equivale al Intl.Collator('es') de la web (ICU).
 *
 * En Android, java.text.Collator ya es ICU y ordena igual que el navegador.
 * En la JVM de escritorio (las pruebas), el collator del JDK ignora los
 * espacios a nivel primario e ICU no: «Mamani  Óscar» (apellido materno
 * vacío) iría después de «Mamani Huamán». Si se detecta eso, se agrega una
 * regla que hace del espacio una diferencia primaria, junto al «0», antes
 * de las letras: verificado contra ICU en nativo/herramientas (c1).
 */
val ORDEN_NOMBRES: Collator = run {
    val base = Collator.getInstance(Locale.forLanguageTag("es"))
    val ignoraEspacios = base.compare("m  o", "m h") > 0
    if (ignoraEspacios && base is RuleBasedCollator) RuleBasedCollator(base.rules + "& '0' ; ' '") else base
}

private val MARCAS_COMBINANTES = Regex("[̀-ͯ]")

/** Normaliza para comparar duplicados: sin tildes, sin dobles espacios, minúsculas. */
fun normalizarNombre(texto: String?): String =
    Normalizer.normalize(texto ?: "", Normalizer.Form.NFD)
        .replace(MARCAS_COMBINANTES, "")
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()
