// PasarDatos.kt — pasar datos a otro teléfono por QR, y recibirlos
// (ui-compartir.js), sin internet. Enviar: el padrón, la asistencia de los
// días que se elijan o las entregas. Recibir: se lee el envío y, ANTES de
// guardar, se muestra la comparación con lo de este teléfono: qué se agrega,
// qué ya estaba y qué no coincide. Lo que no coincide se queda como aquí salvo
// que se elija lo recibido. Así dos teléfonos no cruzan datos sin que nadie lo
// vea. Guardar ofrece «Deshacer».
package io.github.tmrainer.asistencia.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.tmrainer.asistencia.nucleo.calculos.asistenciasDelDia
import io.github.tmrainer.asistencia.nucleo.calculos.etiquetaGrupoEtario
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.nucleo.modelo.cuantos
import io.github.tmrainer.asistencia.nucleo.modelo.fechaLarga
import io.github.tmrainer.asistencia.nucleo.modelo.formatearSoles
import io.github.tmrainer.asistencia.nucleo.modelo.nombreCompleto
import io.github.tmrainer.asistencia.nucleo.qr.ComparacionDia
import io.github.tmrainer.asistencia.nucleo.qr.EleccionAsistencia
import io.github.tmrainer.asistencia.nucleo.qr.EleccionEntregas
import io.github.tmrainer.asistencia.nucleo.qr.EleccionPadron
import io.github.tmrainer.asistencia.nucleo.qr.Paquete
import io.github.tmrainer.asistencia.nucleo.qr.PaqueteAsistencia
import io.github.tmrainer.asistencia.nucleo.qr.PaqueteEntregas
import io.github.tmrainer.asistencia.nucleo.qr.PaquetePadron
import io.github.tmrainer.asistencia.nucleo.qr.PreciosDia
import io.github.tmrainer.asistencia.nucleo.qr.aplicarAsistencia
import io.github.tmrainer.asistencia.nucleo.qr.aplicarEntregas
import io.github.tmrainer.asistencia.nucleo.qr.aplicarPadron
import io.github.tmrainer.asistencia.nucleo.qr.codificarParaQr
import io.github.tmrainer.asistencia.nucleo.qr.compararAsistencia
import io.github.tmrainer.asistencia.nucleo.qr.compararEntregas
import io.github.tmrainer.asistencia.nucleo.qr.compararPadron
import io.github.tmrainer.asistencia.nucleo.qr.paqueteDeAsistencia
import io.github.tmrainer.asistencia.nucleo.qr.paqueteDeEntregas
import io.github.tmrainer.asistencia.nucleo.qr.paqueteDelPadron
import io.github.tmrainer.asistencia.nucleo.calculos.ConteoSinInscribir
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.camara.LeerQr
import io.github.tmrainer.asistencia.ui.camara.MostrarQr
import io.github.tmrainer.asistencia.ui.componentes.Acciones
import io.github.tmrainer.asistencia.ui.componentes.Boton
import io.github.tmrainer.asistencia.ui.componentes.Casilla
import io.github.tmrainer.asistencia.ui.componentes.Chip
import io.github.tmrainer.asistencia.ui.componentes.Chips
import io.github.tmrainer.asistencia.ui.componentes.Opciones
import io.github.tmrainer.asistencia.ui.componentes.Pista
import io.github.tmrainer.asistencia.ui.componentes.Separador
import io.github.tmrainer.asistencia.ui.componentes.Superficie
import io.github.tmrainer.asistencia.ui.componentes.TipoBoton
import io.github.tmrainer.asistencia.ui.componentes.TituloSeccion
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos

private const val AVISO_PRIVADO = "Lleva nombres y DNI: muéstralo solo al otro teléfono de la olla."
private const val DONDE_RECIBIR = "En el otro teléfono: Tus datos → «Recibir por QR»."

// ----------------------------------------------------------------- enviar

/** Muestra el QR del padrón, de la asistencia de [fechas] o de las entregas; si no hay nada, lo dice. */
fun enviarPorQr(ctx: Contexto, tipo: String, fechas: List<String> = emptyList()) {
    val estado = ctx.estado
    val (paquete: Paquete, titulo: String, detalle: String) = when (tipo) {
        "padron" -> {
            val p = paqueteDelPadron(estado.padron.value)
            if (p.personas.isEmpty()) { ctx.avisos.mostrar("El padrón está vacío: no hay nada que pasar."); return }
            Triple(p, "Pasar el padrón", "${cuantos(p.personas.size, "persona")}. $DONDE_RECIBIR $AVISO_PRIVADO")
        }
        "asistencia" -> {
            val r = paqueteDeAsistencia(estado.padron.value, fechas)
            if (r.personas == 0) {
                ctx.avisos.mostrar(if (r.sinDocumento > 0) "Las personas marcadas no tienen documento: no se pueden pasar por QR." else "No hay nadie marcado en esos días.")
                return
            }
            Triple(
                r.paquete, if (fechas.size == 1) "Pasar la asistencia del día" else "Pasar la asistencia de ${fechas.size} días",
                "${if (fechas.size == 1) aDdMmAa(fechas[0]) else cuantos(r.paquete.dias.size, "día")}: ${cuantos(r.personas, "marca")}. $DONDE_RECIBIR" +
                    if (r.sinDocumento > 0) " ${cuantos(r.sinDocumento, "persona")} sin documento no van en el código." else ""
            )
        }
        else -> {
            val p = paqueteDeEntregas(estado.calendario.value)
            if (p.config.fechaLimiteBase.isEmpty() && p.entregas.isEmpty()) { ctx.avisos.mostrar("Todavía no hay entregas que pasar."); return }
            Triple(p, "Pasar las entregas", "Cada cuánto se entrega, la fecha base, los avisos y ${cuantos(p.entregas.size, "entrega")} anotadas. $DONDE_RECIBIR")
        }
    }
    val partes = codificarParaQr(paquete)
    if (partes.isEmpty()) { ctx.avisos.mostrar("Son demasiados datos para un QR. Elige menos días o guarda una copia."); return }
    ctx.abrirCapa { MostrarQr(partes, titulo, detalle, ctx::cerrarCapa) }
}

/** Elegir qué días de asistencia pasar: los que tienen marcas, del más nuevo al más viejo. */
fun elegirDiasYEnviar(ctx: Contexto) {
    val dias = ctx.estado.padron.value.atenciones.map { it.fecha to asistenciasDelDia(it).size }.filter { it.second > 0 }.sortedByDescending { it.first }
    if (dias.isEmpty()) { ctx.avisos.mostrar("Todavía no hay días con asistencia."); return }
    ctx.abrirCapa { ElegirDias(ctx, dias) }
}

@Composable
private fun ElegirDias(ctx: Contexto, dias: List<Pair<String, Int>>) {
    val c = colores
    val marcados = remember { mutableStateMapOf(dias[0].first to true) }
    val n = marcados.count { it.value }
    PantallaEncima {
        Text("Qué días pasar", style = tipos.titulo, color = c.titulo)
        Pista("Marca los días. Mientras más días, más códigos.", Modifier.padding(vertical = Medidas.e3))
        Superficie {
            dias.forEachIndexed { i, (fecha, cuantas) ->
                Casilla(marcados[fecha] == true, { marcados[fecha] = it }, fechaLarga(fecha), cuantos(cuantas, "persona"))
                if (i < dias.size - 1) Separador()
            }
        }
        Spacer(Modifier.height(Medidas.e4))
        Acciones {
            Boton(if (n > 1) "Mostrar el código ($n días)" else "Mostrar el código", {
                val fechas = dias.map { it.first }.filter { marcados[it] == true }
                ctx.cerrarCapa()
                enviarPorQr(ctx, "asistencia", fechas)
            }, tipo = TipoBoton.PRINCIPAL, habilitado = n > 0)
            Boton("Cancelar", ctx::cerrarCapa)
        }
    }
}

// ---------------------------------------------------------------- recibir

/** Abre la cámara, lee el envío y muestra la comparación antes de guardar. */
fun recibirPorQr(ctx: Contexto) {
    ctx.abrirCapa { LeerQr(alRecibir = { paquete -> ctx.abrirCapa { CompararRecibido(ctx, paquete) } }, alCerrar = ctx::cerrarCapa) }
}

/** Un fondo de pantalla completa, desplazable, para lo que se abre encima de la app. */
@Composable
private fun PantallaEncima(contenido: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(colores.fondo).systemBarsPadding().verticalScroll(rememberScrollState()).padding(Medidas.e4)) {
        Column(Modifier.widthIn(max = Medidas.anchoColumna).align(Alignment.CenterHorizontally).fillMaxWidth()) { contenido() }
    }
}

private val PALABRAS = mapOf(
    "tipoDocumento" to mapOf("dni" to "DNI", "ce" to "Carné de extranjería"),
    "tipoAfiliado" to mapOf("habitual" to "Habitual", "caso_social" to "Caso social"),
    "periodicidad" to mapOf("semanal" to "Semanal", "quincenal" to "Quincenal", "mensual" to "Mensual"),
    "estado" to mapOf("pendiente" to "Pendiente", "entregada" to "Entregada", "observada" to "Observada", "conforme" to "Conforme"),
    "menu" to mapOf("normal" to "Menú normal", "ayuda_social" to "Ayuda social")
)

/** Lo que se muestra de cada valor: en palabras, nunca la clave interna (enPalabras de la web). */
private fun enPalabras(campo: String, v: Any?): String {
    if (v == null || v == "" || (v is List<*> && v.isEmpty())) return "(vacío)"
    if (campo == "activo") return if (v == false || v == 0) "No" else "Sí"
    if (campo == "grupoEtarioManual") return etiquetaGrupoEtario(v.toString())
    if (campo == "diasDeAviso" && v is List<*>) return "${v.joinToString(", ")} días antes"
    if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(v.toString())) return aDdMmAa(v.toString())
    return PALABRAS[campo]?.get(v.toString()) ?: v.toString()
}

/** «Dejar lo de aquí» o «Usar lo recibido», con lo que dice cada uno. */
@Composable
private fun ElegirLado(aqui: String, recibido: String, usarRecibido: Boolean, alCambiar: (Boolean) -> Unit) {
    Opciones(listOf(false to "Dejar lo de aquí: $aqui", true to "Usar lo recibido: $recibido"), { it == usarRecibido }, alCambiar)
}

@Composable
private fun Seccion(titulo: String, pista: String? = null, aviso: Boolean = false, contenido: @Composable () -> Unit) {
    Spacer(Modifier.height(Medidas.e4))
    TituloSeccion(titulo)
    if (pista != null) Text(pista, style = tipos.chico, color = if (aviso) colores.aviso else colores.tintaSuave, modifier = Modifier.padding(bottom = Medidas.e2))
    Superficie { Column(Modifier.padding(Medidas.e3)) { contenido() } }
}

/** La comparación de lo recibido con lo de este teléfono, y guardar lo elegido. */
@Composable
private fun CompararRecibido(ctx: Contexto, paquete: Paquete) {
    when (paquete) {
        is PaquetePadron -> CompararPadron(ctx, paquete)
        is PaqueteAsistencia -> CompararAsistencia(ctx, paquete)
        is PaqueteEntregas -> CompararEntregas(ctx, paquete)
    }
}

/** El marco de la comparación: título, resumen, secciones y «Guardar los cambios»; sin cambios, solo «Listo». */
@Composable
private fun Marco(ctx: Contexto, titulo: String, resumen: List<Pair<String, String>>, hayCambios: Boolean, sinCambios: String, guardar: () -> String?, secciones: @Composable () -> Unit) {
    val c = colores
    PantallaEncima {
        Text(titulo, style = tipos.titulo, color = c.titulo)
        Chips(Modifier.padding(vertical = Medidas.e3)) { for ((e, v) in resumen) Chip(e, v) }
        if (!hayCambios) {
            Text(sinCambios, style = tipos.texto, color = c.tinta)
            Pista("No se guardó nada porque no hacía falta.", Modifier.padding(top = Medidas.e2))
            Spacer(Modifier.height(Medidas.e4))
            Boton("Listo", ctx::cerrarCapa, tipo = TipoBoton.PRINCIPAL)
            return@PantallaEncima
        }
        Pista("Revisa antes de guardar. Lo distinto se queda como aquí, salvo que elijas lo recibido.")
        secciones()
        Spacer(Modifier.height(Medidas.e5))
        Acciones {
            Boton("Guardar los cambios", {
                val mensaje = guardar()
                ctx.cerrarCapa()
                if (mensaje == null) ctx.avisos.mostrar("No elegiste nada para guardar: todo quedó como estaba.")
            }, tipo = TipoBoton.PRINCIPAL)
            Boton("Cancelar", ctx::cerrarCapa)
        }
    }
}

@Composable
private fun CompararPadron(ctx: Contexto, paquete: PaquetePadron) {
    val estado = ctx.estado
    val c = colores
    // La comparación guarda a las personas de ESTE padrón: se aplica sobre el mismo.
    val padron = remember { estado.padron.value }
    val k = remember { compararPadron(padron, paquete) }
    val nuevas = remember { mutableStateMapOf<Int, Boolean>().apply { k.nuevas.indices.forEach { put(it, true) } } }
    val dudosas = remember { mutableStateMapOf<Int, Boolean>() }
    val distintas = remember { mutableStateMapOf<Int, Boolean>().apply { k.distintas.forEachIndexed { i, d -> put(i, d.soloCompleta && !d.nombreDistinto) } } }
    Marco(
        ctx, "Padrón recibido",
        listOfNotNull(
            "Nuevas" to "${k.nuevas.size}", "Iguales" to "${k.iguales}", "Distintas" to "${k.distintas.size}",
            if (k.dudosas.isNotEmpty()) "Para revisar" to "${k.dudosas.size}" else null,
            if (k.soloAqui > 0) "Solo aquí (no se tocan)" to "${k.soloAqui}" else null
        ),
        hayCambios = k.nuevas.size + k.dudosas.size + k.distintas.size > 0,
        sinCambios = "Ya tenías a todas las personas del padrón recibido (${k.iguales} iguales).",
        guardar = {
            val agregar = k.nuevas.filterIndexed { i, _ -> nuevas[i] == true } + k.dudosas.filterIndexed { i, _ -> dudosas[i] == true }.map { it.recibida }
            val usar = k.distintas.filterIndexed { i, _ -> distintas[i] == true }
            if (agregar.isEmpty() && usar.isEmpty()) null else {
                val antes = estado.padron.value
                estado.cambiarPadron { aplicarPadron(padron, EleccionPadron(agregar, usar)) }
                estado.guardarYa()
                val m = listOfNotNull(
                    if (agregar.isNotEmpty()) "Se ${if (agregar.size == 1) "agregó" else "agregaron"} ${cuantos(agregar.size, "persona")}" else null,
                    if (usar.isNotEmpty()) "${cuantos(usar.size, "persona")} con lo recibido" else null
                ).joinToString(", ") + "."
                ctx.avisos.mostrar(m) { estado.cambiarPadron { antes }; estado.guardarYa() }
                m
            }
        }
    ) {
        if (k.nuevas.isNotEmpty()) Seccion("Personas nuevas (${k.nuevas.size})", "No están en este teléfono. Se agregan las marcadas.") {
            k.nuevas.forEachIndexed { i, p ->
                Casilla(nuevas[i] == true, { nuevas[i] = it }, nombreCompleto(p), "${p.numeroDocumento.ifEmpty { "sin documento" }}${if (p.tipoAfiliado == "caso_social") " · Caso social" else ""}")
            }
        }
        if (k.dudosas.isNotEmpty()) Seccion("Mismo nombre, otro DNI (${k.dudosas.size})", "Puede ser la misma persona con el DNI mal escrito. Revisa su DNI.", aviso = true) {
            k.dudosas.forEachIndexed { i, d ->
                Text("Aquí: DNI ${d.mia.numeroDocumento}. Recibido: DNI ${d.recibida.numeroDocumento}.", style = tipos.chico, color = c.tinta)
                Casilla(dudosas[i] == true, { dudosas[i] = it }, "Agregar a ${nombreCompleto(d.recibida)} como persona nueva")
            }
        }
        if (k.distintas.isNotEmpty()) Seccion("Con datos distintos (${k.distintas.size})") {
            k.distintas.forEachIndexed { i, d ->
                Text("${nombreCompleto(d.mia)} · ${d.mia.numeroDocumento}", style = tipos.textoNegrita, color = c.titulo, modifier = Modifier.padding(top = Medidas.e2))
                if (d.nombreDistinto) Text("Mismo DNI con otro nombre: puede ser otra persona.", style = tipos.chico, color = c.aviso)
                Text("Cambia: ${d.campos.joinToString(", ") { it.etiqueta.lowercase() }}.", style = tipos.chico, color = c.tintaSuave)
                ElegirLado(d.campos.joinToString(" · ") { enPalabras(it.campo, it.mio) }, d.campos.joinToString(" · ") { enPalabras(it.campo, it.recibido) }, distintas[i] == true) { distintas[i] = it }
                if (i < k.distintas.size - 1) Separador()
            }
        }
    }
}

@Composable
private fun CompararAsistencia(ctx: Contexto, paquete: PaqueteAsistencia) {
    val estado = ctx.estado
    val c = colores
    val padron = remember { estado.padron.value }
    val dias = remember { compararAsistencia(padron, paquete) }
    // Un día que aquí no existe solo se crea si trae a alguien que sumar.
    fun cambia(d: ComparacionDia) = d.agregar.isNotEmpty() || d.menuDistinto.isNotEmpty() || d.preciosDistintos != null || d.fueraDistinto != null || (!d.existe && d.fuera.total > 0)
    val incluir = remember { mutableStateMapOf<String, Boolean>().apply { dias.forEach { put(it.fecha, cambia(it)) } } }
    val lado = remember { mutableStateMapOf<String, Boolean>() }   // «m<fecha>|<id>», «p<fecha>», «f<fecha>»: usar lo recibido
    fun precio(p: PreciosDia) = "normal S/ ${p.normal?.let(::formatearSoles) ?: "—"}, ayuda social S/ ${p.ayuda?.let(::formatearSoles) ?: "—"}"
    fun cuenta(f: ConteoSinInscribir) = "${f.normal} con menú normal y ${f.ayudaSocial} con ayuda social"
    Marco(
        ctx, if (dias.size == 1) "Asistencia recibida" else "Asistencia recibida (${dias.size} días)",
        listOf("Días" to "${dias.size}", "Por sumar" to "${dias.sumOf { it.agregar.size }}"),
        hayCambios = dias.any { cambia(it) },
        sinCambios = if (dias.any { it.desconocidos > 0 }) "No hay nadie que sumar: las personas recibidas no están en tu padrón. Pasa primero el padrón."
        else "Ya tenías esa asistencia: no hay nadie nuevo que sumar.",
        guardar = {
            val elegidos = dias.filter { incluir[it.fecha] == true }
            if (elegidos.isEmpty()) null else {
                val eleccion = EleccionAsistencia(
                    dias = elegidos.map { it.fecha }.toSet(),
                    menuRecibido = elegidos.flatMap { d -> d.menuDistinto.map { "${d.fecha}|${it.afiliado.id}" } }.filter { lado["m$it"] == true }.toSet(),
                    preciosRecibidos = elegidos.filter { lado["p${it.fecha}"] == true }.map { it.fecha }.toSet(),
                    fueraRecibido = elegidos.filter { lado["f${it.fecha}"] == true }.map { it.fecha }.toSet()
                )
                val antes = estado.padron.value
                estado.cambiarPadron { aplicarAsistencia(padron, dias, eleccion, estado.preciosActuales()) }
                estado.guardarYa()
                val suman = elegidos.sumOf { it.agregar.size }
                val m = "${if (suman == 1) "Se sumó" else "Se sumaron"} ${cuantos(suman, "persona")} en ${cuantos(elegidos.size, "día")}."
                ctx.avisos.mostrar(m) { estado.cambiarPadron { antes }; estado.guardarYa() }
                m
            }
        }
    ) {
        for (d in dias) Seccion("${fechaLarga(d.fecha)}${if (d.existe) "" else " (día nuevo aquí)"}") {
            val nombres = d.agregar.map { nombreCompleto(it.afiliado) + if (it.tipoMenu == "ayuda_social") " (ayuda social)" else "" }
            Text(
                if (nombres.isEmpty()) "No hay nadie nuevo que sumar."
                else "${if (nombres.size == 1) "Se suma" else "Se suman"} ${cuantos(nombres.size, "persona")}: ${nombres.take(8).joinToString(", ")}${if (nombres.size > 8) " y ${nombres.size - 8} más" else ""}.",
                style = tipos.texto, color = if (nombres.isEmpty()) c.tintaSuave else c.tinta
            )
            if (d.yaEstaban > 0) Text("${cuantos(d.yaEstaban, "persona")} ya ${if (d.yaEstaban == 1) "estaba" else "estaban"} igual.", style = tipos.chico, color = c.tintaSuave)
            if (d.soloAqui > 0) Text("${cuantos(d.soloAqui, "persona marcada", "personas marcadas")} solo aquí: se ${if (d.soloAqui == 1) "queda" else "quedan"}.", style = tipos.chico, color = c.tintaSuave)
            if (d.desconocidos > 0) Text("${cuantos(d.desconocidos, "persona")} no ${if (d.desconocidos == 1) "está" else "están"} en tu padrón: no se suman. Pasa primero el padrón.", style = tipos.chico, color = c.aviso)
            if (d.inactivos > 0) Text("${cuantos(d.inactivos, "persona")} no ${if (d.inactivos == 1) "estaba activa" else "estaban activas"} ese día aquí: no se suman.", style = tipos.chico, color = c.aviso)
            for (m in d.menuDistinto) {
                val clave = "m${d.fecha}|${m.afiliado.id}"
                Text("${nombreCompleto(m.afiliado)}: el menú no coincide.", style = tipos.chico, color = c.aviso, modifier = Modifier.padding(top = Medidas.e2))
                ElegirLado(enPalabras("menu", m.mio), enPalabras("menu", m.recibido), lado[clave] == true) { lado[clave] = it }
            }
            d.preciosDistintos?.let { p ->
                Text("Los precios del día no coinciden.", style = tipos.chico, color = c.aviso, modifier = Modifier.padding(top = Medidas.e2))
                ElegirLado(precio(p.mio), precio(p.recibido), lado["p${d.fecha}"] == true) { lado["p${d.fecha}"] = it }
            }
            if (!d.existe && d.fuera.total > 0) Text("Sin estar en el padrón comieron ${cuenta(d.fuera)}.", style = tipos.chico, color = c.tinta)
            d.fueraDistinto?.let { f ->
                Text("No coincide cuántos comieron sin estar en el padrón. Elige uno.", style = tipos.chico, color = c.aviso, modifier = Modifier.padding(top = Medidas.e2))
                ElegirLado(cuenta(f.mio), cuenta(f.recibido), lado["f${d.fecha}"] ?: (f.mio.total == 0)) { lado["f${d.fecha}"] = it }
            }
            Casilla(incluir[d.fecha] == true, { incluir[d.fecha] = it }, if (d.existe) "Sumar a este día" else "Agregar este día")
        }
    }
}

@Composable
private fun CompararEntregas(ctx: Contexto, paquete: PaqueteEntregas) {
    val estado = ctx.estado
    val c = colores
    val calendario = remember { estado.calendario.value }
    val k = remember { compararEntregas(calendario, paquete) }
    fun vacio(v: Any?) = v == null || v == "" || (v is List<*> && v.isEmpty())
    // Sin fecha base, aquí nunca se configuraron las entregas: se propone lo recibido.
    val config = remember { mutableStateMapOf<String, Boolean>().apply { k.config.forEach { put(it.campo, calendario.fechaLimiteBase.isEmpty() || vacio(it.mio)) } } }
    val nuevas = remember { mutableStateMapOf<String, Boolean>().apply { k.nuevas.forEach { put(it.fechaLimite, true) } } }
    val distintas = remember { mutableStateMapOf<String, Boolean>() }
    Marco(
        ctx, "Entregas recibidas",
        listOf("Nuevas" to "${k.nuevas.size}", "Iguales" to "${k.iguales}", "Distintas" to "${k.distintas.size + k.config.size}"),
        hayCambios = k.config.isNotEmpty() || k.nuevas.isNotEmpty() || k.distintas.isNotEmpty(),
        sinCambios = "Ya tenías las entregas iguales: no hay nada que cambiar.",
        guardar = {
            val eleccion = EleccionEntregas(
                config = k.config.filter { config[it.campo] == true }.map { it.campo }.toSet(),
                nuevas = k.nuevas.filter { nuevas[it.fechaLimite] == true }.map { it.fechaLimite }.toSet(),
                distintas = k.distintas.filter { distintas[it.fechaLimite] == true }.map { it.fechaLimite }.toSet()
            )
            if (eleccion.config.isEmpty() && eleccion.nuevas.isEmpty() && eleccion.distintas.isEmpty()) null else {
                val antes = estado.calendario.value
                estado.cambiarCalendario { aplicarEntregas(calendario, k, eleccion) }
                estado.guardarYa()
                val m = "Entregas actualizadas con lo que elegiste."
                ctx.avisos.mostrar(m) { estado.cambiarCalendario { antes }; estado.guardarYa() }
                m
            }
        }
    ) {
        if (k.config.isNotEmpty()) Seccion("Cómo se calculan las fechas") {
            for (x in k.config) {
                Text(x.etiqueta, style = tipos.textoNegrita, color = c.titulo, modifier = Modifier.padding(top = Medidas.e2))
                ElegirLado(enPalabras(x.campo, x.mio), enPalabras(x.campo, x.recibido), config[x.campo] == true) { config[x.campo] = it }
            }
        }
        if (k.nuevas.isNotEmpty()) Seccion("Entregas anotadas solo allá (${k.nuevas.size})") {
            for (n in k.nuevas) Casilla(nuevas[n.fechaLimite] == true, { nuevas[n.fechaLimite] = it }, "${aDdMmAa(n.fechaLimite)} · ${enPalabras("estado", n.estado)}", n.nota.ifEmpty { null })
        }
        if (k.distintas.isNotEmpty()) Seccion("Entregas que no coinciden (${k.distintas.size})") {
            for (d in k.distintas) {
                Text("Entrega del ${aDdMmAa(d.fechaLimite)}", style = tipos.textoNegrita, color = c.titulo, modifier = Modifier.padding(top = Medidas.e2))
                ElegirLado(
                    enPalabras("estado", d.mio.estado.ifEmpty { "pendiente" }) + if (d.mio.nota.isNotEmpty()) " · ${d.mio.nota}" else "",
                    enPalabras("estado", d.recibido.estado) + if (d.recibido.nota.isNotEmpty()) " · ${d.recibido.nota}" else "",
                    distintas[d.fechaLimite] == true
                ) { distintas[d.fechaLimite] = it }
            }
        }
    }
}
