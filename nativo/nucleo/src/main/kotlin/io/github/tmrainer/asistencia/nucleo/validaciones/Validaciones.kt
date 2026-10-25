// Señalan y explican; NUNCA corrigen. Los mensajes son los mismos que en la web.
package io.github.tmrainer.asistencia.nucleo.validaciones

import io.github.tmrainer.asistencia.nucleo.calculos.TRAMOS_ETARIOS
import io.github.tmrainer.asistencia.nucleo.calculos.activoEn
import io.github.tmrainer.asistencia.nucleo.calculos.asistenciasDelDia
import io.github.tmrainer.asistencia.nucleo.calculos.descuadreFila
import io.github.tmrainer.asistencia.nucleo.calculos.desgloseDelDia
import io.github.tmrainer.asistencia.nucleo.calculos.duracionPeriodo
import io.github.tmrainer.asistencia.nucleo.calculos.gastoPorOrigen
import io.github.tmrainer.asistencia.nucleo.calculos.grupoEtario
import io.github.tmrainer.asistencia.nucleo.calculos.Origen
import io.github.tmrainer.asistencia.nucleo.calculos.totalRendicionGastos
import io.github.tmrainer.asistencia.nucleo.calculos.totales
import io.github.tmrainer.asistencia.nucleo.modelo.Afiliado
import io.github.tmrainer.asistencia.nucleo.modelo.Atencion
import io.github.tmrainer.asistencia.nucleo.modelo.Calendario
import io.github.tmrainer.asistencia.nucleo.modelo.Config
import io.github.tmrainer.asistencia.nucleo.modelo.Egreso
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.modelo.Rendicion
import io.github.tmrainer.asistencia.nucleo.modelo.aFecha
import io.github.tmrainer.asistencia.nucleo.modelo.aIso
import io.github.tmrainer.asistencia.nucleo.modelo.cuantos
import io.github.tmrainer.asistencia.nucleo.modelo.esIso
import io.github.tmrainer.asistencia.nucleo.modelo.formatearSoles
import io.github.tmrainer.asistencia.nucleo.modelo.hoyIso
import io.github.tmrainer.asistencia.nucleo.modelo.nombreCompleto
import io.github.tmrainer.asistencia.nucleo.modelo.normalizarNombre

enum class Severidad(val clave: String) { ERROR("error"), ADVERTENCIA("advertencia") }

/**
 * Un hallazgo. `falta`: el dato todavía no se escribió (no es que esté mal).
 * La interfaz lo cuenta aparte, en tono neutro: «Faltan 3 datos», no «3 errores».
 */
data class Hallazgo(val severidad: Severidad, val campo: String, val mensaje: String, val falta: Boolean)

private val ERROR = Severidad.ERROR
private val AVISO = Severidad.ADVERTENCIA

private val FALTA = Regex("^(Compra \\d+: )?(Falta|falta|No hay ningún)")

private fun h(severidad: Severidad, campo: String, mensaje: String) =
    Hallazgo(severidad, campo, mensaje, FALTA.containsMatchIn(mensaje))

private val DIGITOS = Regex("^\\d+$")
private val DNI = Regex("^\\d{8}$")
private val CELULAR = Regex("^9\\d{8}$")
private val RUC = Regex("^\\d{11}$")
private val RUC_INICIO = Regex("^(10|15|17|20)")
private val CE = Regex("^[A-Za-z0-9]{8,12}$")

private fun soloDigitos(v: String?) = DIGITOS.matches(v ?: "")
private fun vacio(v: String?) = (v ?: "").trim().isEmpty()

// Encabezado

private fun validarEncabezado(r: Rendicion): List<Hallazgo> {
    val hs = ArrayList<Hallazgo>()
    if (r.tipoCentro != "comedor" && r.tipoCentro != "olla_comun") {
        hs.add(h(ERROR, "tipoCentro", "Falta marcar si es comedor u olla común."))
    }
    val obligatorios = listOf(
        Triple("nombreCentro", r.nombreCentro, "el nombre del centro de atención"),
        Triple("codigoPca", r.codigoPca, "el código de PCA"),
        Triple("fechaRendicion", r.fechaRendicion, "la fecha de rendición"),
        Triple("nombrePresidenta", r.nombrePresidenta, "el nombre de la presidenta"),
        Triple("dniPresidenta", r.dniPresidenta, "el DNI de la presidenta"),
        Triple("celular", r.celular, "el número de celular")
    )
    for ((campo, valor, nombre) in obligatorios) {
        if (vacio(valor)) hs.add(h(ERROR, campo, "Falta $nombre."))
    }
    if (vacio(r.periodo.etiqueta)) {
        hs.add(h(ERROR, "periodo.etiqueta", "Falta lo que va escrito en \"MES DE RENDICIÓN\"."))
    }
    if (!esIso(r.periodo.inicio) || !esIso(r.periodo.fin)) {
        hs.add(h(ERROR, "periodo.inicio", "Falta el inicio o el fin del periodo que se rinde."))
    } else if (r.periodo.inicio > r.periodo.fin) {
        hs.add(h(ERROR, "periodo.inicio", "El inicio del periodo es posterior a su fin."))
    } else {
        val dias = duracionPeriodo(r.periodo)
        val rango = when (r.periodo.tipo) {
            "semanal" -> 7 to 7
            "quincenal" -> 14 to 16
            "mensual" -> 28 to 31
            else -> null
        }
        if (rango != null && dias != null && (dias < rango.first || dias > rango.second)) {
            hs.add(h(AVISO, "periodo.inicio",
                "El periodo dura ${cuantos(dias, "día")}, que no es lo habitual para una entrega " +
                    "${r.periodo.tipo} (${rango.first}–${rango.second} días). Revísalo si no fue a propósito."))
        }
    }
    if (!vacio(r.dniPresidenta) && !DNI.matches(r.dniPresidenta)) {
        hs.add(h(ERROR, "dniPresidenta", "El DNI debe tener exactamente 8 dígitos."))
    }
    if (!vacio(r.celular) && !CELULAR.matches(r.celular)) {
        hs.add(h(ERROR, "celular", "El celular debe tener 9 dígitos y empezar en 9."))
    }
    if (!vacio(r.codigoPca) && !soloDigitos(r.codigoPca)) {
        hs.add(h(AVISO, "codigoPca", "El código de PCA suele ser solo dígitos. Verifica lo escrito."))
    }
    return hs
}

// Ingresos

private fun validarIngresos(r: Rendicion): List<Hallazgo> {
    val hs = ArrayList<Hallazgo>()
    val subsidio = r.montoSubsidioCent
    if (subsidio == null) {
        hs.add(h(ERROR, "montoSubsidio", "Falta el monto del subsidio."))
    } else if (subsidio <= 0) {
        hs.add(h(ERROR, "montoSubsidio", "El monto del subsidio debe ser mayor que cero."))
    }
    if (!esIso(r.fechaAsignacion)) {
        hs.add(h(ERROR, "fechaAsignacion", "Falta la fecha de la asignación."))
    } else if (esIso(r.fechaRendicion) && r.fechaAsignacion > r.fechaRendicion) {
        hs.add(h(ERROR, "fechaAsignacion", "La fecha de asignación es posterior a la fecha de rendición."))
    } else if (esIso(r.periodo.inicio) && esIso(r.periodo.fin)) {
        val base = aFecha(r.periodo.inicio)
        if (base != null) {
            val hace30 = aIso(base.minusDays(30))
            if (r.fechaAsignacion < hace30 || r.fechaAsignacion > r.periodo.fin) {
                hs.add(h(AVISO, "fechaAsignacion", "La fecha de asignación queda lejos del periodo que estás rindiendo."))
            }
        }
    }
    return hs
}

// Egresos

private fun filaTieneAlgo(f: Egreso): Boolean =
    !vacio(f.fechaCompra) || !vacio(f.descripcion) || !vacio(f.rucProveedor) ||
        !vacio(f.boletaSerie) || !vacio(f.boletaCorrelativo) ||
        f.montoTotalCent != null || f.cantidad != null || f.precioUnitarioCent != null

private fun validarEgresos(r: Rendicion): List<Hallazgo> {
    val hs = ArrayList<Hallazgo>()
    val filas = r.egresos
    if (filas.none { filaTieneAlgo(it) }) {
        hs.add(h(ERROR, "egresos", "No hay ningún egreso registrado."))
        return hs
    }

    val vistas = HashMap<String, Int>()
    filas.forEachIndexed { i, f ->
        if (!filaTieneAlgo(f)) return@forEachIndexed
        val n = i + 1
        val campo = "egreso.${f.id}"

        if (!esIso(f.fechaCompra)) hs.add(h(ERROR, campo, "Compra $n: falta la fecha."))
        if (vacio(f.descripcion)) hs.add(h(ERROR, campo, "Compra $n: falta la descripción."))
        if (vacio(f.rucProveedor)) hs.add(h(ERROR, campo, "Compra $n: falta el RUC del proveedor."))
        if (vacio(f.boletaSerie) || vacio(f.boletaCorrelativo)) {
            hs.add(h(ERROR, campo, "Compra $n: falta la serie o el correlativo de la boleta."))
        }
        val monto = f.montoTotalCent
        if (monto == null) {
            hs.add(h(ERROR, campo, "Compra $n: falta el monto total."))
        } else if (monto <= 0) {
            hs.add(h(ERROR, campo, "Compra $n: el monto total debe ser mayor que cero."))
        }

        if (!vacio(f.rucProveedor)) {
            if (!RUC.matches(f.rucProveedor)) {
                hs.add(h(ERROR, campo, "Compra $n: el RUC debe tener exactamente 11 dígitos."))
            } else if (!RUC_INICIO.containsMatchIn(f.rucProveedor)) {
                hs.add(h(AVISO, campo, "Compra $n: los RUC peruanos suelen empezar en 10, 15, 17 o 20. Verifica el número."))
            }
        }

        if (esIso(f.fechaCompra)) {
            if (esIso(r.fechaAsignacion) && f.fechaCompra < r.fechaAsignacion) {
                hs.add(h(ERROR, campo, "Compra $n: la fecha es anterior a la asignación del subsidio."))
            }
            if (esIso(r.fechaRendicion) && f.fechaCompra > r.fechaRendicion) {
                hs.add(h(ERROR, campo, "Compra $n: la fecha es posterior a la rendición."))
            }
            if (esIso(r.periodo.inicio) && esIso(r.periodo.fin) &&
                (f.fechaCompra < r.periodo.inicio || f.fechaCompra > r.periodo.fin)) {
                hs.add(h(AVISO, campo, "Compra $n: la fecha cae fuera del periodo que se rinde."))
            }
        }

        if (!vacio(f.boletaSerie) && !vacio(f.boletaCorrelativo)) {
            val llave = "${f.boletaSerie.trim().uppercase()}-${f.boletaCorrelativo.trim()}"
            val previa = vistas[llave]
            if (previa != null) {
                hs.add(h(AVISO, campo, "Compra $n: la boleta $llave ya figura en la compra $previa."))
            } else {
                vistas[llave] = n
            }
        }

        val d = descuadreFila(f)
        if (d != null) {
            hs.add(h(AVISO, campo,
                "Compra $n: cantidad × precio unitario da S/ ${formatearSoles(d.esperado)}, " +
                    "pero el monto total dice S/ ${formatearSoles(d.declarado)}. " +
                    "Revisa cuál de los tres números es el correcto; la app no cambia ninguno."))
        }
    }
    return hs
}

// Conjunto

const val FILAS_EGRESOS = 17 // provisional

private fun validarConjunto(r: Rendicion): List<Hallazgo> {
    val hs = ArrayList<Hallazgo>()
    val t = totales(r)

    if (t.total != null && t.conSubsidio!! + t.aporte!! != t.total) {
        hs.add(h(ERROR, "totales",
            "Error interno: los tres totales no cuadran entre sí. Esto es un fallo del " +
                "programa, no de tus datos. Repórtalo."))
    }
    val porOrigen = gastoPorOrigen(r)
    if (porOrigen.suma != totalRendicionGastos(r)) {
        hs.add(h(ERROR, "totales", "Error interno: la suma por origen de fondo no coincide con el total. Repórtalo."))
    }
    val subsidio = r.montoSubsidioCent
    if (subsidio != null && t.total != null && t.total < subsidio) {
        hs.add(h(AVISO, "totales",
            "Queda S/ ${formatearSoles(subsidio - t.total)} del subsidio sin rendir. " +
                "El formato no tiene un renglón para ese saldo: consulta en la Municipalidad qué corresponde hacer."))
    }
    if (subsidio != null && porOrigen.subsidio > subsidio) {
        hs.add(h(AVISO, "totales", "Las compras marcadas como pagadas con el subsidio suman más que el subsidio recibido."))
    }
    if (!vacio(r.nombrePresidentaFirma) && !vacio(r.nombrePresidenta) &&
        normalizarNombre(r.nombrePresidentaFirma) != normalizarNombre(r.nombrePresidenta)) {
        hs.add(h(AVISO, "nombrePresidentaFirma", "El nombre de la firma no coincide con el del encabezado."))
    }
    if (!vacio(r.dniPresidentaFirma) && !vacio(r.dniPresidenta) && r.dniPresidentaFirma != r.dniPresidenta) {
        hs.add(h(AVISO, "dniPresidentaFirma", "El DNI de la firma no coincide con el del encabezado."))
    }
    if (vacio(r.nombreTesorera) && vacio(r.dniTesorera)) {
        hs.add(h(AVISO, "nombreTesorera", "Falta la tesorera. El formato lleva dos firmas: presidenta y tesorera."))
    }
    if (!vacio(r.dniTesorera) && !DNI.matches(r.dniTesorera)) {
        hs.add(h(ERROR, "dniTesorera", "El DNI de la tesorera debe tener 8 dígitos."))
    }

    val conDatos = r.egresos.count { filaTieneAlgo(it) }
    if (conDatos > FILAS_EGRESOS) {
        hs.add(h(AVISO, "egresos",
            "Hay $conDatos compras y la hoja del formato tiene $FILAS_EGRESOS filas. Harán falta dos hojas."))
    }
    return hs
}

fun validarRendicion(rendicion: Rendicion?): List<Hallazgo> {
    if (rendicion == null) return emptyList()
    return validarEncabezado(rendicion) + validarIngresos(rendicion) +
        validarEgresos(rendicion) + validarConjunto(rendicion)
}

// Padrón

/**
 * Índice del padrón para validar sin comparar a cada persona con todas las
 * demás. Se arma una vez por pasada de validación; no se guarda.
 */
class IndicePadron(
    val porId: Map<String, Afiliado>,
    val porDocumento: Map<String, List<Afiliado>>,   // solo activas, como pide la regla
    val porNombre: Map<String, List<Afiliado>>,
    val porFecha: Map<String, List<Atencion>>
)

fun indicePadron(padron: Padron): IndicePadron {
    val porId = HashMap<String, Afiliado>()
    val porDocumento = HashMap<String, MutableList<Afiliado>>()
    val porNombre = HashMap<String, MutableList<Afiliado>>()
    val porFecha = HashMap<String, MutableList<Atencion>>()
    for (o in padron.afiliados) {
        porId[o.id] = o
        if (o.activo && !vacio(o.numeroDocumento)) porDocumento.getOrPut(o.numeroDocumento) { ArrayList() }.add(o)
        porNombre.getOrPut(normalizarNombre(nombreCompleto(o))) { ArrayList() }.add(o)
    }
    for (at in padron.atenciones) porFecha.getOrPut(at.fecha) { ArrayList() }.add(at)
    return IndicePadron(porId, porDocumento, porNombre, porFecha)
}

/**
 * `hoy` existe para poder probar con una fecha fija; por defecto es la de
 * hoy.
 */
fun validarAfiliado(
    afiliado: Afiliado,
    padron: Padron,
    indice: IndicePadron = indicePadron(padron),
    hoy: String = hoyIso()
): List<Hallazgo> {
    val hs = ArrayList<Hallazgo>()
    val a = afiliado
    val campo = "afiliado.${a.id}"

    if (vacio(a.apellidoPaterno)) hs.add(h(ERROR, campo, "Falta el apellido paterno."))
    if (vacio(a.nombres)) hs.add(h(ERROR, campo, "Faltan los nombres."))

    // El número de documento es OBLIGATORIO.
    if (vacio(a.numeroDocumento)) {
        hs.add(h(ERROR, campo, "Falta el número de documento. Es obligatorio para el padrón."))
    } else {
        if (a.tipoDocumento == "dni" && !DNI.matches(a.numeroDocumento)) {
            hs.add(h(ERROR, campo, "El DNI debe tener exactamente 8 dígitos."))
        }
        if (a.tipoDocumento == "ce" && !CE.matches(a.numeroDocumento)) {
            hs.add(h(ERROR, campo, "El carné de extranjería debe tener entre 8 y 12 caracteres."))
        }
        val repetido = indice.porDocumento[a.numeroDocumento]?.firstOrNull { it.id != a.id }
        if (repetido != null) {
            hs.add(h(ERROR, campo, "El documento ${a.numeroDocumento} ya está registrado en ${nombreCompleto(repetido)}."))
        }
    }

    val mismoNombre = indice.porNombre[normalizarNombre(nombreCompleto(a))]?.firstOrNull { it.id != a.id }
    if (mismoNombre != null && !vacio(nombreCompleto(a))) {
        hs.add(h(AVISO, campo, "Ya hay otra persona registrada con el mismo nombre completo."))
    }

    if (!vacio(a.fechaNacimiento)) {
        if (!esIso(a.fechaNacimiento)) {
            hs.add(h(ERROR, campo, "La fecha de nacimiento no tiene un formato válido."))
        } else if (a.fechaNacimiento > hoy) {
            hs.add(h(ERROR, campo, "La fecha de nacimiento está en el futuro."))
        } else if (a.fechaNacimiento.substring(0, 4).toInt() < hoy.substring(0, 4).toInt() - 120) {
            hs.add(h(ERROR, campo, "La fecha de nacimiento es de hace más de 120 años."))
        }
    }

    // Grupo etario OBLIGATORIO: por fecha de nacimiento, o elegido a mano.
    if (grupoEtario(a, hoy) == null) {
        hs.add(h(ERROR, campo, "Falta el grupo de edad. Elígelo, o anota la fecha de nacimiento y sale solo."))
    }
    val manual = a.grupoEtarioManual
    if (!manual.isNullOrEmpty() && TRAMOS_ETARIOS.none { it.clave == manual }) {
        hs.add(h(ERROR, campo, "El grupo de edad elegido no es uno de los cuatro válidos."))
    }

    // Tipo de afiliada OBLIGATORIO: define el menú que le toca por defecto.
    if (a.tipoAfiliado != "habitual" && a.tipoAfiliado != "caso_social") {
        hs.add(h(ERROR, campo, "Falta indicar si es habitual o de ayuda social: de ahí sale su menú."))
    }

    if (esIso(a.altaEn) && a.altaEn > hoy) {
        hs.add(h(ERROR, campo, "La fecha de alta está en el futuro."))
    }
    if (esIso(a.bajaEn) && esIso(a.altaEn) && a.bajaEn < a.altaEn) {
        hs.add(h(ERROR, campo, "La fecha de baja es anterior a la de alta."))
    }
    if (a.origenDato == "escaneo_dni" && vacio(a.verificadoPor)) {
        hs.add(h(AVISO, campo, "Estos datos vinieron de un escaneo y nadie los ha confirmado todavía."))
    }
    return hs
}

// Raciones

fun validarAtencion(atencion: Atencion, padron: Padron, indice: IndicePadron = indicePadron(padron)): List<Hallazgo> {
    val hs = ArrayList<Hallazgo>()
    val at = atencion
    val campo = "atencion.${at.id}"

    if (!esIso(at.fecha)) {
        hs.add(h(ERROR, campo, "Falta la fecha de la atención."))
        return hs
    }

    val d = desgloseDelDia(at)

    if (d.total == 0) {
        hs.add(h(AVISO, campo, "Este día no tiene a nadie marcado en la asistencia, así que cuenta cero raciones."))
    }

    // Si hubo raciones de un tipo de menú, hace falta su precio para el promedio.
    if (d.normal > 0 && at.precioMenuNormalCent == null) {
        hs.add(h(ERROR, campo, "Falta el precio del menú normal de ese día. Puedes fijarlo más abajo, en «Precio del menú»."))
    }
    if (d.ayudaSocial > 0 && at.precioMenuAyudaSocialCent == null) {
        hs.add(h(ERROR, campo, "Falta el precio del menú de ayuda social de ese día. Puedes fijarlo más abajo, en «Precio del menú»."))
    }
    val normal = at.precioMenuNormalCent
    val ayuda = at.precioMenuAyudaSocialCent
    if (normal != null && ayuda != null && ayuda > normal) {
        hs.add(h(AVISO, campo, "El menú de ayuda social cuesta más que el normal. Puede ser correcto; solo revísalo."))
    }

    if (d.origen == Origen.LEGADO) {
        hs.add(h(AVISO, campo,
            "Este día viene de la versión anterior: ${cuantos(d.total, "ración", "raciones")} anotadas a mano, " +
                "sin asistencia marcada. Siguen contando. Si quieres, marca la asistencia y pasarán " +
                "a contarse de ahí."))
    }

    val vistos = HashSet<String>()
    for (a in asistenciasDelDia(at)) {
        if (a.afiliadoId.isEmpty()) continue
        if (a.afiliadoId in vistos) {
            hs.add(h(ERROR, campo, "Hay una persona marcada dos veces en la asistencia de ese día."))
        }
        vistos.add(a.afiliadoId)
        val persona = indice.porId[a.afiliadoId]
        if (persona == null) {
            hs.add(h(AVISO, campo, "La asistencia incluye a alguien que ya no está en el padrón."))
        } else if (!activoEn(persona, at.fecha)) {
            hs.add(h(AVISO, campo, "${nombreCompleto(persona)} figura en la asistencia pero no estaba activa ese día."))
        }
    }

    if (d.origen == Origen.LEGADO) {
        val activosEseDia = padron.afiliados.count { activoEn(it, at.fecha) }
        if (d.total > activosEseDia) {
            hs.add(h(AVISO, campo,
                "Ese día cuenta ${cuantos(d.total, "ración", "raciones")} y el padrón tenía " +
                    "${cuantos(activosEseDia, "persona activa", "personas activas")}."))
        }
    }

    val repetida = indice.porFecha[at.fecha]?.firstOrNull { it.id != at.id }
    if (repetida != null) {
        hs.add(h(AVISO, campo, "Ya hay otro registro de atención con esta misma fecha."))
    }
    return hs
}

fun validarPreciosMenu(config: Config?, hayRaciones: Boolean = true): List<Hallazgo> {
    val hs = ArrayList<Hallazgo>()
    if (config == null) return hs
    // Sin raciones registradas todavía no hay nada que valorar.
    val normal = config.precioMenuNormalCent
    if (normal == null) {
        if (hayRaciones) {
            hs.add(h(AVISO, "precio.normal", "Falta el precio del menú normal. Sin él no se puede sacar el promedio por ración."))
        }
    } else if (normal <= 0) {
        hs.add(h(ERROR, "precio.normal", "El precio del menú normal debe ser mayor que cero."))
    }
    val ayuda = config.precioMenuAyudaSocialCent
    if (ayuda != null && ayuda < 0) {
        hs.add(h(ERROR, "precio.ayuda", "El precio del menú de ayuda social no puede ser negativo."))
    }
    return hs
}

fun validarCalendario(calendario: Calendario?): List<Hallazgo> {
    val hs = ArrayList<Hallazgo>()
    if (calendario == null) return hs
    if (vacio(calendario.fechaLimiteBase)) {
        hs.add(h(AVISO, "calendario.base", "Falta una fecha de entrega: sin ella no hay avisos."))
    } else if (!esIso(calendario.fechaLimiteBase)) {
        hs.add(h(ERROR, "calendario.base", "La fecha de entrega no tiene un formato válido."))
    }
    if (calendario.periodicidad !in listOf("semanal", "quincenal", "mensual")) {
        hs.add(h(ERROR, "calendario.periodicidad", "Falta indicar cada cuánto se entrega."))
    }
    return hs
}

fun validarPadron(padron: Padron, hoy: String = hoyIso()): List<Hallazgo> {
    val indice = indicePadron(padron)
    return validarAfiliados(padron, indice, hoy) + validarAtenciones(padron, indice)
}

/** Solo las personas del padrón (la página Padrón no mira los días). */
fun validarAfiliados(padron: Padron, indice: IndicePadron = indicePadron(padron), hoy: String = hoyIso()): List<Hallazgo> =
    padron.afiliados.flatMap { validarAfiliado(it, padron, indice, hoy) }

/** Solo los días de asistencia (la página Asistencia no mira las fichas). */
fun validarAtenciones(padron: Padron, indice: IndicePadron = indicePadron(padron)): List<Hallazgo> =
    padron.atenciones.flatMap { validarAtencion(it, padron, indice) }

data class Conteo(val errores: Int, val advertencias: Int)

fun contar(hallazgos: List<Hallazgo>) = Conteo(
    errores = hallazgos.count { it.severidad == ERROR },
    advertencias = hallazgos.count { it.severidad == AVISO }
)
