// OraculoTest.kt — el criterio de éxito de la fase 1: un respaldo exportado
// por la web se lee en Kotlin, se recalcula todo, y da los MISMOS números que
// los módulos de la web (nativo/herramientas/oraculo.mjs escribe lo esperado).
//
// Dos entradas:
//  - respaldo-ficticio.json (versionado, datos inventados): corre siempre.
//  - un respaldo REAL, nunca versionado:
//      node nativo/herramientas/oraculo.mjs /ruta/respaldo.json
//      ./gradlew :nucleo:test -Dnativo.respaldoReal=/ruta/respaldo.json
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.alertas.agendaEntregas
import io.github.tmrainer.asistencia.nucleo.alertas.alertaProximaEntrega
import io.github.tmrainer.asistencia.nucleo.alertas.programaDeAvisos
import io.github.tmrainer.asistencia.nucleo.calculos.afiliadosActivos
import io.github.tmrainer.asistencia.nucleo.calculos.afiliadosSinAsistencia
import io.github.tmrainer.asistencia.nucleo.calculos.asistenciaPorAfiliado
import io.github.tmrainer.asistencia.nucleo.calculos.costoPorRacionCent
import io.github.tmrainer.asistencia.nucleo.calculos.descuadreFila
import io.github.tmrainer.asistencia.nucleo.calculos.desgloseDelDia
import io.github.tmrainer.asistencia.nucleo.calculos.diasAtendidos
import io.github.tmrainer.asistencia.nucleo.calculos.filasPadron
import io.github.tmrainer.asistencia.nucleo.calculos.gastoPorOrigen
import io.github.tmrainer.asistencia.nucleo.calculos.promedioRaciones
import io.github.tmrainer.asistencia.nucleo.calculos.racionesPeriodo
import io.github.tmrainer.asistencia.nucleo.calculos.recaudacionDelDiaCent
import io.github.tmrainer.asistencia.nucleo.calculos.resumenEconomicoRaciones
import io.github.tmrainer.asistencia.nucleo.calculos.resumenEtario
import io.github.tmrainer.asistencia.nucleo.calculos.totales
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.modelo.Respaldo
import io.github.tmrainer.asistencia.nucleo.respaldo.LecturaRespaldo
import io.github.tmrainer.asistencia.nucleo.respaldo.leerRespaldo
import io.github.tmrainer.asistencia.nucleo.respaldo.revisarRespaldo
import io.github.tmrainer.asistencia.nucleo.sugerencias.descripcionesUsadas
import io.github.tmrainer.asistencia.nucleo.sugerencias.sugerenciasEncabezado
import io.github.tmrainer.asistencia.nucleo.sugerencias.sugerirRucProveedor
import io.github.tmrainer.asistencia.nucleo.validaciones.Hallazgo
import io.github.tmrainer.asistencia.nucleo.validaciones.contar
import io.github.tmrainer.asistencia.nucleo.validaciones.validarAfiliados
import io.github.tmrainer.asistencia.nucleo.validaciones.validarAtenciones
import io.github.tmrainer.asistencia.nucleo.validaciones.validarCalendario
import io.github.tmrainer.asistencia.nucleo.validaciones.validarPreciosMenu
import io.github.tmrainer.asistencia.nucleo.validaciones.validarRendicion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.fail

class OraculoTest {

    @Test
    fun `El respaldo ficticio da los mismos números que la web`() {
        comparar(recurso("respaldo-ficticio.json"), recurso("respaldo-ficticio.esperado.json"), "respaldo-ficticio.json")
    }

    @Test
    fun `Un respaldo real da los mismos números que la web (si se indica uno)`() {
        val ruta = System.getProperty("nativo.respaldoReal")
        if (ruta.isNullOrEmpty()) {
            println("Sin respaldo real: -Dnativo.respaldoReal=/ruta/respaldo.json para probarlo.")
            return
        }
        val esperado = File(ruta.removeSuffix(".json") + ".esperado.json")
        ok(esperado.exists(), "Falta ${esperado.path}: corre antes node nativo/herramientas/oraculo.mjs $ruta")
        comparar(File(ruta).readText(), esperado.readText(), ruta)
    }

    private fun comparar(textoRespaldo: String, textoEsperado: String, nombre: String) {
        val esperado = Json.parseToJsonElement(textoEsperado).jsonObject
        val hoy = esperado["hoy"]!!.jsonPrimitive.content
        val generadoEn = esperado["generadoEn"]!!.jsonPrimitive.content   // «hoy» de validarAfiliado en la web
        val lectura = assertIs<LecturaRespaldo.Lista>(leerRespaldo(textoRespaldo))
        // Los periodos a comparar son los que el oráculo eligió (rango completo, mes en curso, pedido).
        val periodos = (esperado["padron"] as? JsonObject)?.get("periodos")?.let { it as JsonArray }
            ?.map { it.jsonObject["inicio"]!!.jsonPrimitive.content to it.jsonObject["fin"]!!.jsonPrimitive.content }
            ?: emptyList()
        val obtenido = calcularTodo(lectura.respaldo, Json.parseToJsonElement(textoRespaldo), hoy, generadoEn, periodos)
        val diferencias = diferencias(esperado, obtenido, "")
        if (diferencias.isNotEmpty()) {
            fail("$nombre: ${diferencias.size} diferencias con la web:\n" + diferencias.take(40).joinToString("\n"))
        }
    }
}

// ----------------------------------------------------------- lo que se calcula

private fun calcularTodo(r: Respaldo, crudo: JsonElement, hoy: String, hoyValidacion: String, periodos: List<Pair<String, String>>): JsonObject = buildJsonObject {
    put("hoy", hoy)
    put("generadoEn", hoyValidacion)
    val revision = revisarRespaldo(crudo)
    put("revision", buildJsonObject {
        put("error", revision.error)
        put("partes", lista(revision.partes) { JsonPrimitive(it) })
        put("resumen", revision.resumen)
    })

    val padron = r.padron
    if (padron != null) {
        put("padron", buildJsonObject {
            put("personas", padron.afiliados.size)
            put("dias", padron.atenciones.size)
            put("periodos", lista(periodos) { (inicio, fin) -> periodoJson(padron, inicio, fin) })
            put("hoy", buildJsonObject {
                put("filasPadron", filas(padron, hoy))
                put("resumenEtario", resumenEtarioJson(padron, hoy))
                put("activos", afiliadosActivos(padron, hoy).size)
            })
            put("porDia", lista(padron.atenciones) { at ->
                val d = desgloseDelDia(at)
                buildJsonObject {
                    put("id", at.id); put("fecha", at.fecha)
                    put("desglose", buildJsonObject {
                        put("normal", d.normal); put("ayudaSocial", d.ayudaSocial); put("total", d.total)
                        put("origen", d.origen.clave); put("sinInscribir", d.sinInscribir)
                    })
                    put("recaudacionCent", recaudacionDelDiaCent(at))
                }
            })
            val hAf = validarAfiliados(padron, hoy = hoyValidacion)
            val hAt = validarAtenciones(padron)
            put("hallazgosAfiliados", hallazgos(hAf))
            put("hallazgosAtenciones", hallazgos(hAt))
            put("conteo", conteo(hAf + hAt))
        })
    }

    if (r.rendiciones.isNotEmpty()) {
        put("rendiciones", lista(r.rendiciones) { rd ->
            val t = totales(rd)
            val po = gastoPorOrigen(rd)
            val hs = validarRendicion(rd)
            buildJsonObject {
                put("id", rd.id)
                put("totales", buildJsonObject { put("total", t.total); put("conSubsidio", t.conSubsidio); put("aporte", t.aporte) })
                put("gastoPorOrigen", buildJsonObject { put("subsidio", po.subsidio); put("ayuda_social", po.ayudaSocial); put("aporte_propio", po.aportePropio) })
                put("costoPorRacionCent", if (padron != null) costoPorRacionCent(rd, padron) else null)
                put("descuadres", lista(rd.egresos) { e ->
                    val d = descuadreFila(e)
                    if (d == null) JsonNull else buildJsonObject { put("esperado", d.esperado); put("declarado", d.declarado); put("diferencia", d.diferencia) }
                })
                put("hallazgos", hallazgos(hs))
                put("conteo", conteo(hs))
            }
        })
        val nombres = r.rendiciones.flatMap { it.egresos.map { e -> e.proveedorNombre } }.filter { it.isNotEmpty() }.distinct()
        put("sugerenciasRuc", buildJsonObject {
            for (n in nombres) {
                val s = sugerirRucProveedor(r.rendiciones, n)
                put(n, if (s == null) JsonNull else buildJsonObject { put("valor", s.valor); put("procedencia", s.procedencia) })
            }
        })
        put("descripcionesUsadas", lista(descripcionesUsadas(r.rendiciones)) { buildJsonObject { put("valor", it.valor); put("procedencia", it.procedencia) } })
    }

    val cal = r.calendario
    if (cal != null) {
        put("calendario", buildJsonObject {
            val a = alertaProximaEntrega(cal, hoy)
            put("alerta", if (a == null) JsonNull else buildJsonObject {
                put("configurado", a.configurado); put("fechaLimite", a.fechaLimite); put("diasRestantes", a.diasRestantes)
                put("nivel", a.nivel.clave); put("estado", a.estado); put("mensaje", a.mensaje)
            })
            put("agenda", lista(agendaEntregas(cal, hoy, 6)) { e ->
                buildJsonObject { put("fechaLimite", e.fechaLimite); put("diasRestantes", e.diasRestantes); put("nivel", e.nivel.clave); put("estado", e.estado) }
            })
            put("avisos", lista(programaDeAvisos(cal, hoy)) { av ->
                buildJsonObject { put("id", av.id); put("fecha", av.fecha); put("fechaEntrega", av.fechaEntrega); put("dias", av.dias); put("titulo", av.titulo); put("texto", av.texto) }
            })
            put("hallazgos", hallazgos(validarCalendario(cal)))
        })
    }
    val config = r.config
    if (config != null) {
        put("config", buildJsonObject {
            put("hallazgosPrecios", hallazgos(validarPreciosMenu(config)))
            put("sugerencias", buildJsonObject {
                for ((campo, p) in sugerenciasEncabezado(config)) put(campo, buildJsonObject { put("valor", p.valor); put("procedencia", p.procedencia) })
            })
        })
    }
}

private fun periodoJson(padron: Padron, inicio: String, fin: String): JsonObject = buildJsonObject {
    val res = resumenEconomicoRaciones(padron, inicio, fin)
    put("inicio", inicio); put("fin", fin)
    put("resumen", buildJsonObject {
        put("racionesTotales", res.racionesTotales); put("racionesNormales", res.racionesNormales)
        put("racionesAyudaSocial", res.racionesAyudaSocial); put("recaudacionCent", res.recaudacionCent)
        put("racionesConPrecio", res.racionesConPrecio); put("diasSinPrecio", res.diasSinPrecio)
        put("diasDeLegado", res.diasDeLegado); put("precioPromedioRacionCent", res.precioPromedioRacionCent)
    })
    put("racionesPeriodo", racionesPeriodo(padron, inicio, fin))
    put("diasAtendidos", diasAtendidos(padron, inicio, fin))
    put("promedioRaciones", promedioRaciones(padron, inicio, fin))
    put("asistenciaPorAfiliado", lista(asistenciaPorAfiliado(padron, inicio, fin)) { a ->
        buildJsonObject { put("afiliadoId", a.afiliadoId); put("dias", a.dias); put("normal", a.normal); put("ayudaSocial", a.ayudaSocial) }
    })
    put("sinAsistencia", lista(afiliadosSinAsistencia(padron, inicio, fin)) { JsonPrimitive(it.id) })
    put("filasPadron", filas(padron, fin))
    put("resumenEtario", resumenEtarioJson(padron, fin))
    put("activos", afiliadosActivos(padron, fin).size)
}

private fun filas(padron: Padron, ref: String) = lista(filasPadron(padron, ref)) { f ->
    buildJsonObject {
        put("n", f.n); put("afiliadoId", f.afiliadoId); put("tipoDocumento", f.tipoDocumento); put("grupoEtario", f.grupoEtario)
        put("grupoEtarioEtiqueta", f.grupoEtarioEtiqueta); put("edad", f.edad); put("tipoAfiliadoEtiqueta", f.tipoAfiliadoEtiqueta)
    }
}

private fun resumenEtarioJson(padron: Padron, ref: String): JsonObject {
    val r = resumenEtario(padron, ref)
    return buildJsonObject {
        put("nino", r.nino); put("adolescente", r.adolescente); put("adulto", r.adulto)
        put("adulto_mayor", r.adultoMayor); put("sin_dato", r.sinDato)
    }
}

private fun hallazgos(hs: List<Hallazgo>) = lista(hs) { h ->
    buildJsonObject { put("severidad", h.severidad.clave); put("campo", h.campo); put("mensaje", h.mensaje); put("falta", h.falta) }
}

private fun conteo(hs: List<Hallazgo>): JsonObject {
    val c = contar(hs)
    return buildJsonObject { put("errores", c.errores); put("advertencias", c.advertencias) }
}

private fun <T> lista(xs: Iterable<T>, f: (T) -> JsonElement): JsonArray = buildJsonArray { for (x in xs) add(f(x)) }

// ------------------------------------------------------------- comparar

/** Diferencias entre lo que dijo la web y lo que dio Kotlin. Una clave ausente vale null. */
private fun diferencias(esperado: JsonElement?, obtenido: JsonElement?, ruta: String): List<String> {
    val e = esperado ?: JsonNull
    val o = obtenido ?: JsonNull
    val salida = ArrayList<String>()
    when {
        e is JsonObject && o is JsonObject -> {
            for (k in (e.keys + o.keys).toSortedSet()) salida.addAll(diferencias(e[k], o[k], "$ruta.$k"))
        }
        e is JsonArray && o is JsonArray -> {
            if (e.size != o.size) salida.add("$ruta: la web da ${e.size} elementos y Kotlin ${o.size}")
            for (i in 0 until minOf(e.size, o.size)) salida.addAll(diferencias(e[i], o[i], "$ruta[$i]"))
        }
        e is JsonNull && o is JsonNull -> {}
        e is JsonPrimitive && o is JsonPrimitive -> {
            val ne = e.doubleOrNull
            val no = o.doubleOrNull
            val iguales = if (ne != null && no != null && !e.isString && !o.isString) abs(ne - no) < 1e-9 else e.content == o.content
            if (!iguales) salida.add("$ruta: la web da $e y Kotlin $o")
        }
        else -> salida.add("$ruta: la web da $e y Kotlin $o")
    }
    return salida
}
