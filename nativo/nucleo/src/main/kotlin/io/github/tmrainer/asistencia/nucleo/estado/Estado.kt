// Los datos en memoria y su guardado diferido. No dibuja nada.
// Nunca se dice «Guardado» sin haber escrito: un fallo queda en `guardado` (FALLO).
package io.github.tmrainer.asistencia.nucleo.estado

import io.github.tmrainer.asistencia.nucleo.almacen.Almacen
import io.github.tmrainer.asistencia.nucleo.almacen.Clave
import io.github.tmrainer.asistencia.nucleo.modelo.Calendario
import io.github.tmrainer.asistencia.nucleo.modelo.Config
import io.github.tmrainer.asistencia.nucleo.modelo.JSON
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.modelo.PreciosMenu
import io.github.tmrainer.asistencia.nucleo.modelo.Rendicion
import io.github.tmrainer.asistencia.nucleo.modelo.Respaldo
import io.github.tmrainer.asistencia.nucleo.modelo.ahoraIso
import io.github.tmrainer.asistencia.nucleo.modelo.calendarioVacio
import io.github.tmrainer.asistencia.nucleo.modelo.configVacia
import io.github.tmrainer.asistencia.nucleo.modelo.diasEntre
import io.github.tmrainer.asistencia.nucleo.modelo.hoyIso
import io.github.tmrainer.asistencia.nucleo.modelo.migrarPadron
import io.github.tmrainer.asistencia.nucleo.modelo.padronVacio
import io.github.tmrainer.asistencia.nucleo.respaldo.LecturaRespaldo
import io.github.tmrainer.asistencia.nucleo.respaldo.armarRespaldo
import io.github.tmrainer.asistencia.nucleo.sugerencias.instantaneaCentro
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Cómo va el guardado, para la marca de «Guardado» / «NO SE GUARDÓ». */
enum class Guardado { GUARDADO, PENDIENTE, FALLO }

/** Un rango de fechas ISO, ambas incluidas. */
data class Lapso(val inicio: String, val fin: String)

const val DIAS_ENTRE_RESPALDOS = 14

class Estado(
    private val almacen: Almacen,
    private val alcance: CoroutineScope,
    private val hoy: () -> String = ::hoyIso,
    private val ahora: () -> String = ::ahoraIso,
    private val esperaMs: Long = 400
) {
    private val _padron = MutableStateFlow(padronVacio())
    private val _config = MutableStateFlow(configVacia())
    private val _calendario = MutableStateFlow(calendarioVacio())
    private val _rendiciones = MutableStateFlow<List<Rendicion>>(emptyList())
    private val _guardado = MutableStateFlow(Guardado.GUARDADO)

    val padron: StateFlow<Padron> = _padron.asStateFlow()
    val config: StateFlow<Config> = _config.asStateFlow()
    val calendario: StateFlow<Calendario> = _calendario.asStateFlow()
    /** La primera es el B-1 en curso; las demás, las de meses anteriores. */
    val rendiciones: StateFlow<List<Rendicion>> = _rendiciones.asStateFlow()
    val guardado: StateFlow<Guardado> = _guardado.asStateFlow()

    // Claves con un cambio que el guardado diferido todavía no escribió.
    private val pendientes = HashSet<Clave>()
    private var diferido: Job? = null

    fun avisosAlmacenamiento(): List<String> = almacen.avisosAlmacenamiento()

    /** Lee todo del almacén. Lo ausente o ilegible arranca vacío (y se avisa). */
    fun cargar(): Estado {
        _padron.value = migrarPadron(almacen.leer(Clave.PADRON))
        _config.value = decodificar(Clave.CONFIG, Config.serializer()) ?: configVacia()
        _calendario.value = decodificar(Clave.CALENDARIO, Calendario.serializer()) ?: calendarioVacio()
        _rendiciones.value = decodificar(Clave.RENDICIONES, ListSerializer(Rendicion.serializer())) ?: emptyList()
        hoySigueAlPrecio()
        return this
    }

    private fun <T> decodificar(clave: Clave, serializador: KSerializer<T>): T? {
        val json = almacen.leer(clave) ?: return null
        return try {
            JSON.decodeFromJsonElement(serializador, json)
        } catch (e: IllegalArgumentException) {   // SerializationException es subclase
            almacen.apartarIlegible(clave)
            null
        }
    }

    /**
     * El día de HOY, mientras no tenga precio propio, lleva los DOS precios de
     * la configuración. Repara también un hoy que quedó con un precio viejo.
     * Devuelve ese día o null. Solo toca la memoria.
     */
    fun hoySigueAlPrecio(): io.github.tmrainer.asistencia.nucleo.modelo.Atencion? {
        val fecha = hoy()
        val precios = preciosActuales()
        val p = _padron.value
        val i = p.atenciones.indexOfFirst { it.fecha == fecha && it.precioTomadoDeConfig }
        if (i < 0) return null
        val actual = p.atenciones[i]
        val arreglado = actual.copy(
            precioMenuNormalCent = precios.precioMenuNormalCent,
            precioMenuAyudaSocialCent = precios.precioMenuAyudaSocialCent
        )
        if (arreglado != actual) {
            _padron.value = p.copy(atenciones = p.atenciones.toMutableList().also { it[i] = arreglado })
        }
        return arreglado
    }

    fun cambiarPadron(cambio: (Padron) -> Padron) {
        _padron.value = cambio(_padron.value)
        marcar(Clave.PADRON)
    }

    /** Al cambiar el precio del menú, el día de hoy (si lo tomó de aquí) lo sigue. */
    fun cambiarConfig(cambio: (Config) -> Config) {
        _config.value = cambio(_config.value)
        val antes = _padron.value
        hoySigueAlPrecio()
        marcar(Clave.CONFIG)
        if (_padron.value !== antes) marcar(Clave.PADRON)
    }

    fun cambiarCalendario(cambio: (Calendario) -> Calendario) {
        _calendario.value = cambio(_calendario.value)
        marcar(Clave.CALENDARIO)
    }

    /** Agregar o quitar rendiciones (p. ej. «Empezar el B-1 del mes siguiente»). */
    fun cambiarRendiciones(cambio: (List<Rendicion>) -> List<Rendicion>) {
        _rendiciones.value = cambio(_rendiciones.value)
        marcar(Clave.RENDICIONES)
    }

    /**
     * Editar una rendición: anota cuándo se cambió y, si es la que está en
     * curso, deja su encabezado en config.ultimoCentro para proponerlo la
     * próxima vez. Si el id no existe, no hace nada.
     */
    fun cambiarRendicion(id: String, cambio: (Rendicion) -> Rendicion) {
        val lista = _rendiciones.value
        val i = lista.indexOfFirst { it.id == id }
        if (i < 0) return
        val nueva = cambio(lista[i]).copy(actualizadoEn = ahora())
        _rendiciones.value = lista.toMutableList().also { it[i] = nueva }
        marcar(Clave.RENDICIONES)
        if (i == 0) {
            _config.value = _config.value.copy(ultimoCentro = instantaneaCentro(nueva))
            marcar(Clave.CONFIG)
        }
    }

    private fun marcar(clave: Clave) {
        synchronized(pendientes) { pendientes.add(clave) }
        _guardado.value = Guardado.PENDIENTE
        guardarPronto()
    }

    /**
     * Pide guardar lo pendiente `esperaMs` después del ÚLTIMO pedido: varios
     * toques seguidos producen una sola escritura. Al terminar, `guardado`
     * dice si se escribió.
     */
    fun guardarPronto() {
        diferido?.cancel()
        diferido = alcance.launch {
            delay(esperaMs)
            // Si no había nada pendiente, ya lo escribió guardarYa(): solo se avisa.
            _guardado.value = if (guardarPendiente()) Guardado.GUARDADO else Guardado.FALLO
        }
    }

    /** Escribe lo pendiente ahora mismo, si lo hay. true si no quedó nada sin escribir. */
    fun guardarPendiente(): Boolean {
        val claves = synchronized(pendientes) { pendientes.toSet() }
        if (claves.isEmpty()) return true
        return escribir(claves)
    }

    /**
     * Guarda YA todas las partes, sin esperar. Para arrancar (consolidar una
     * migración), tras una importación, y al ocultarse la app. Deja sin
     * efecto un guardarPronto pendiente: cuando se cumpla su plazo solo avisará.
     */
    fun guardarYa(): Boolean {
        val ok = escribir(setOf(Clave.PADRON, Clave.CONFIG, Clave.CALENDARIO, Clave.RENDICIONES))
        _guardado.value = if (ok) Guardado.GUARDADO else Guardado.FALLO
        return ok
    }

    /** Olvida lo pendiente sin escribirlo: justo antes de restaurar una copia o borrar todo. */
    fun descartarPendiente() {
        diferido?.cancel()
        synchronized(pendientes) { pendientes.clear() }
    }

    private fun escribir(claves: Set<Clave>): Boolean {
        var ok = true
        for (clave in claves) {
            val texto = when (clave) {
                Clave.PADRON -> JSON.encodeToString(Padron.serializer(), _padron.value)
                Clave.CONFIG -> JSON.encodeToString(Config.serializer(), _config.value)
                Clave.CALENDARIO -> JSON.encodeToString(Calendario.serializer(), _calendario.value)
                Clave.RENDICIONES -> JSON.encodeToString(ListSerializer(Rendicion.serializer()), _rendiciones.value)
                Clave.RESPALDO -> continue
            }
            if (almacen.guardar(clave, texto)) synchronized(pendientes) { pendientes.remove(clave) } else ok = false
        }
        if (!ok) _guardado.value = Guardado.FALLO
        return ok
    }

    /** Precio vigente del menú, que se copia a cada día nuevo. */
    fun preciosActuales(): PreciosMenu = _config.value.precios()

    /** Periodo por defecto de los resúmenes: el mes en curso. */
    fun periodoPorDefecto(): Lapso {
        val h = hoy()
        return Lapso(h.substring(0, 8) + "01", h)
    }

    /** El respaldo completo con lo que hay ahora (escribe antes lo pendiente). */
    fun armarCopia(): Respaldo {
        guardarPendiente()
        return armarRespaldo(_rendiciones.value, _padron.value, _calendario.value, _config.value, ahora())
    }

    /** Anota que hoy (o en la fecha `en`) se guardó una copia, para el recordatorio. */
    fun registrarCopia(en: String = hoy()): Boolean =
        almacen.guardar(Clave.RESPALDO, buildJsonObject { put("ultimoEn", en) })

    /** Días desde la última copia guardada, o null si nunca se guardó una. */
    fun diasDesdeUltimaCopia(): Int? {
        val ultimo = (almacen.leer(Clave.RESPALDO)?.jsonObject?.get("ultimoEn") as? JsonPrimitive)?.contentOrNull
            ?: return null
        return diasEntre(ultimo, hoy())
    }

    /** Hay algo que perder: personas en el padrón o algún B-1. */
    fun hayDatosGuardados(): Boolean = _padron.value.afiliados.isNotEmpty() || _rendiciones.value.isNotEmpty()

    /** Con datos y sin copia en 14 días (o nunca), toca recordarlo. */
    fun tocaRecordarCopia(): Boolean {
        if (!hayDatosGuardados()) return false
        val dias = diasDesdeUltimaCopia() ?: return true
        return dias >= DIAS_ENTRE_RESPALDOS
    }

    /**
     * Escribe en el teléfono TODAS las partes que trae la copia (padrón,
     * rendiciones, calendario y config) y recarga. Lo que la copia no trae se
     * queda como está. Devuelve false si alguna escritura falló.
     */
    fun restaurar(lectura: LecturaRespaldo.Lista): Boolean {
        descartarPendiente()
        val r = lectura.respaldo
        var ok = true
        fun escribirParte(clave: Clave, json: JsonElement) { ok = almacen.guardar(clave, json) && ok }
        if ("padron" in lectura.revision.partes && r.padron != null) escribirParte(Clave.PADRON, JSON.encodeToJsonElement(Padron.serializer(), r.padron))
        if ("rendiciones" in lectura.revision.partes) escribirParte(Clave.RENDICIONES, JSON.encodeToJsonElement(ListSerializer(Rendicion.serializer()), r.rendiciones))
        if ("calendario" in lectura.revision.partes && r.calendario != null) escribirParte(Clave.CALENDARIO, JSON.encodeToJsonElement(Calendario.serializer(), r.calendario))
        if ("config" in lectura.revision.partes && r.config != null) escribirParte(Clave.CONFIG, JSON.encodeToJsonElement(Config.serializer(), r.config))
        cargar()
        _guardado.value = if (ok) Guardado.GUARDADO else Guardado.FALLO
        return ok
    }

    /** Borra todo lo guardado y arranca vacío. */
    fun borrarTodo(): Boolean {
        descartarPendiente()
        val ok = almacen.borrarTodo()
        cargar()
        return ok
    }
}
