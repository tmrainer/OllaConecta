package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.modelo.Asistencia
import io.github.tmrainer.asistencia.nucleo.modelo.Calendario
import io.github.tmrainer.asistencia.nucleo.modelo.Entrega
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.modelo.PreciosMenu
import io.github.tmrainer.asistencia.nucleo.modelo.SinInscribir
import io.github.tmrainer.asistencia.nucleo.modelo.nuevaAtencion
import io.github.tmrainer.asistencia.nucleo.modelo.nuevoAfiliado
import io.github.tmrainer.asistencia.nucleo.modelo.padronVacio
import io.github.tmrainer.asistencia.nucleo.qr.ConfigEntregasQr
import io.github.tmrainer.asistencia.nucleo.qr.CuentaFuera
import io.github.tmrainer.asistencia.nucleo.qr.DiaQr
import io.github.tmrainer.asistencia.nucleo.qr.EleccionAsistencia
import io.github.tmrainer.asistencia.nucleo.qr.EleccionEntregas
import io.github.tmrainer.asistencia.nucleo.qr.EleccionPadron
import io.github.tmrainer.asistencia.nucleo.qr.EntregaQr
import io.github.tmrainer.asistencia.nucleo.qr.FilaPersonaQr
import io.github.tmrainer.asistencia.nucleo.qr.MarcaLista
import io.github.tmrainer.asistencia.nucleo.qr.MarcaQr
import io.github.tmrainer.asistencia.nucleo.qr.Paquete
import io.github.tmrainer.asistencia.nucleo.qr.PaqueteAsistencia
import io.github.tmrainer.asistencia.nucleo.qr.PaqueteEntregas
import io.github.tmrainer.asistencia.nucleo.qr.PaquetePadron
import io.github.tmrainer.asistencia.nucleo.qr.aBase45
import io.github.tmrainer.asistencia.nucleo.qr.aplicarAsistencia
import io.github.tmrainer.asistencia.nucleo.qr.aplicarEntregas
import io.github.tmrainer.asistencia.nucleo.qr.aplicarPadron
import io.github.tmrainer.asistencia.nucleo.qr.codificarListaQr
import io.github.tmrainer.asistencia.nucleo.qr.codificarParaQr
import io.github.tmrainer.asistencia.nucleo.qr.compararAsistencia
import io.github.tmrainer.asistencia.nucleo.qr.compararEntregas
import io.github.tmrainer.asistencia.nucleo.qr.compararPadron
import io.github.tmrainer.asistencia.nucleo.qr.deBase45
import io.github.tmrainer.asistencia.nucleo.qr.juntarPartesQr
import io.github.tmrainer.asistencia.nucleo.qr.leerParteQr
import io.github.tmrainer.asistencia.nucleo.qr.paqueteDeAsistencia
import io.github.tmrainer.asistencia.nucleo.qr.paqueteDeEntregas
import io.github.tmrainer.asistencia.nucleo.qr.paqueteDelPadron
import io.github.tmrainer.asistencia.nucleo.qr.textoJson
import kotlin.test.Test
import kotlin.test.assertIs

/** codificarParaQr → leerParteQr → juntarPartesQr, como entre dos teléfonos. */
private fun idaYVuelta(paquete: Paquete): Paquete? = juntarPartesQr(codificarParaQr(paquete).map { leerParteQr(it)!! })

class CompartirQrTest {

    @Test
    fun `base45 (RFC 9285) - el ejemplo de la norma y la vuelta`() {
        igual(aBase45("AB".toByteArray()), "BB8")
        igual(aBase45("Hello!!".toByteArray()), "%69 VD92EX0")
        val bytes = byteArrayOf(0, 1, 255.toByte(), 254.toByte(), 128.toByte(), 7, 200.toByte())
        igual(deBase45(aBase45(bytes))!!.toList(), bytes.toList())
        igual(deBase45("GGW"), null, "fuera de rango")
        igual(deBase45("a"), null)
    }

    @Test
    fun `Un padrón grande va comprimido en varios códigos y vuelve igual`() {
        val padron = padronVacio().copy(afiliados = List(150) {
            nuevoAfiliado().copy(apellidoPaterno = "PRUEBA", apellidoMaterno = "ÑANDÚ", nombres = "PERSONA $it",
                numeroDocumento = (10000000 + it).toString(), tipoAfiliado = "habitual", grupoEtarioManual = "adulto")
        })
        val partes = codificarParaQr(paqueteDelPadron(padron), porParte = 300)
        ok(partes.size > 1, "varias partes")
        val largos = partes.map { it.length }
        ok(largos.max() - largos.min() <= 1, "partes parejas: se muestran juntas, del mismo tamaño")
        ok(largos.max() <= 300 + 12, "ninguna pasa del máximo pedido")
        val forma = Regex("P3P[0-9A-Z]{4}\\d{4}[0-9A-Z \$%*+\\-./:]*:")
        ok(partes.all { forma.matches(it) }, "solo el alfabeto del QR")
        val leidas = partes.map { leerParteQr(it)!! }.reversed()      // en cualquier orden
        val vuelta = assertIs<PaquetePadron>(juntarPartesQr(leidas))
        igual(vuelta.personas.size, 150)
        igual(vuelta.personas[3].apellidoMaterno, "ÑANDÚ", "tildes y ñ intactas")
        igual(juntarPartesQr(leidas.drop(1)), null, "falta una parte")
        igual(leerParteQr("hola"), null)
        igual(leerParteQr("P3PABCD0101XX"), null, "sin cierre")
    }

    @Test
    fun `Padrón recibido - nuevas, iguales, distintas y «mismo nombre, otro DNI», lo de aquí no se pierde`() {
        fun p(doc: String, ap: String, nom: String, sexo: String = "") = nuevoAfiliado().copy(
            apellidoPaterno = ap, nombres = nom, numeroDocumento = doc, tipoAfiliado = "habitual",
            grupoEtarioManual = "adulto", sexo = sexo)
        val aqui = padronVacio().copy(afiliados = listOf(p("00000001", "ALFA", "ANA"), p("00000002", "BETA", "BEA"),
            p("00000003", "GAMA", "GIL"), p("00000004", "DELTA", "DORA"), p("00000009", "SOLO", "AQUI")))
        val alla = padronVacio().copy(afiliados = listOf(p("00000001", "ALFA", "ANA"), p("00000002", "OTRO", "NOMBRE"),
            p("00000003", "GAMA", "GIL", sexo = "M"), p("00000044", "DELTA", "DORA"), p("00000005", "NUEVA", "NORA"),
            p("00000005", "NUEVA", "NORA")))
        val c = compararPadron(aqui, paqueteDelPadron(alla))
        igual(c.iguales, 1)
        igual(c.nuevas.size, 1, "la repetida cuenta una vez")
        igual(c.dudosas.size, 1, "DELTA DORA con otro DNI")
        igual(c.distintas.size, 2)
        val beta = c.distintas.first { it.mia.numeroDocumento == "00000002" }
        ok(beta.nombreDistinto && !beta.soloCompleta, "mismo DNI, otro nombre: se avisa")
        val gama = c.distintas.first { it.mia.numeroDocumento == "00000003" }
        ok(gama.soloCompleta, "solo llena el sexo vacío")
        igual(c.soloAqui, 2)
        val nuevo = aplicarPadron(aqui, EleccionPadron(agregar = c.nuevas, usarRecibida = listOf(gama)))
        igual(nuevo.afiliados.size, 6)
        igual(nuevo.afiliados[2].sexo, "M")
        igual(nuevo.afiliados[1].apellidoPaterno, "BETA", "lo no elegido queda como aquí")
        igual(nuevo.afiliados[5].origenDato, "qr")
        ok(nuevo.afiliados[5].id.isNotEmpty(), "la agregada tiene id propio")
        igual(aqui.afiliados.size, 5, "no toca el original")
    }

    @Test
    fun `Asistencia recibida - suma por DNI, respeta menús y precios de aquí salvo elección`() {
        fun a(doc: String) = nuevoAfiliado().copy(numeroDocumento = doc, altaEn = "2026-01-01")
        val misAfiliados = listOf(a("00000001"), a("00000002"), a("00000003"))
        val aqui = Padron(afiliados = misAfiliados, atenciones = listOf(
            nuevaAtencion(PreciosMenu(500, 0)).copy(fecha = "2026-10-10", asistencias = listOf(
                Asistencia(misAfiliados[0].id, "normal"), Asistencia(misAfiliados[1].id, "normal")))
        ))
        val susAfiliados = listOf(a("00000001"), a("00000002"), a("00000003"), a("00000099"))
        val ids = susAfiliados.map { it.id }
        val otros = Padron(afiliados = susAfiliados, atenciones = listOf(
            nuevaAtencion(PreciosMenu(600, 0)).copy(fecha = "2026-10-10", asistencias = listOf(
                Asistencia(ids[0], "normal"), Asistencia(ids[1], "ayuda_social"),
                Asistencia(ids[2], "normal"), Asistencia(ids[3], "normal"))),
            nuevaAtencion(null).copy(fecha = "2026-10-11", asistencias = listOf(Asistencia(ids[2], "normal")))
        ))
        val paquete = paqueteDeAsistencia(otros, listOf("2026-10-10", "2026-10-11")).paquete
        val vuelta = assertIs<PaqueteAsistencia>(idaYVuelta(paquete))
        val (d10, d11) = compararAsistencia(aqui, vuelta)
        igual(d10.agregar.size, 1)
        igual(d10.yaEstaban, 1)
        igual(d10.menuDistinto.size, 1)
        igual(d10.desconocidos, 1, "no está en el padrón de aquí")
        ok(d10.preciosDistintos != null, "precio 6.00 contra 5.00")
        ok(!d11.existe)
        val eleccion = EleccionAsistencia(dias = setOf("2026-10-10", "2026-10-11"))
        val nuevo = aplicarAsistencia(aqui, listOf(d10, d11), eleccion, PreciosMenu(500, 0))
        val at10 = nuevo.atenciones.first { it.fecha == "2026-10-10" }
        igual(at10.asistencias.size, 3)
        igual(at10.asistencias[1].tipoMenu, "normal", "el menú de aquí se queda")
        igual(at10.precioMenuNormalCent, 500L, "el precio de aquí se queda")
        igual(nuevo.atenciones.first { it.fecha == "2026-10-11" }.precioMenuNormalCent, 500L, "día nuevo: precio de aquí")
        igual(aqui.atenciones[0].asistencias.size, 2, "no toca el original")
    }

    @Test
    fun `Quienes comieron sin estar en el padrón viajan por menú y no se suman dos veces`() {
        fun a(doc: String) = nuevoAfiliado().copy(numeroDocumento = doc, altaEn = "2026-01-01")
        val aqui = Padron(afiliados = listOf(a("00000001")), atenciones = listOf(
            nuevaAtencion(null).copy(fecha = "2026-10-10", sinInscribir = SinInscribir(1, 0))))
        val otros = Padron(afiliados = listOf(a("00000001")), atenciones = listOf(
            nuevaAtencion(null).copy(fecha = "2026-10-10", sinInscribir = SinInscribir(2, 3)),
            nuevaAtencion(null).copy(fecha = "2026-10-11", sinInscribir = SinInscribir(0, 4)),
            nuevaAtencion(null).copy(fecha = "2026-10-12")
        ))
        val paquete = paqueteDeAsistencia(otros, listOf("2026-10-10", "2026-10-11", "2026-10-12")).paquete
        igual(paquete.dias[2].fuera, null, "sin nadie de fuera, el día va como antes")
        ok(textoJson(paquete).endsWith("""["2026-10-12",null,null,[]]]}"""), "cuatro campos, como la versión anterior")
        val vuelta = assertIs<PaqueteAsistencia>(idaYVuelta(paquete), "el paquete con la cuenta se lee")
        val (d10, d11) = compararAsistencia(aqui, vuelta)
        igual(d10.fuera.ayudaSocial, 3)
        ok(d10.fueraDistinto != null, "1 y 0 aquí contra 2 y 3 allá")
        val base = EleccionAsistencia(dias = setOf("2026-10-10", "2026-10-11"))
        val quedo = aplicarAsistencia(aqui, listOf(d10, d11), base, null)
        igual(quedo.atenciones[0].sinInscribir!!.normal, 1, "sin elegir, queda la de aquí (no se suma)")
        igual(quedo.atenciones.first { it.fecha == "2026-10-11" }.sinInscribir!!.ayudaSocial, 4, "día nuevo: va la recibida")
        val elegido = aplicarAsistencia(aqui, listOf(d10, d11), base.copy(fueraRecibido = setOf("2026-10-10")), null)
        igual(elegido.atenciones[0].sinInscribir!!.ayudaSocial, 3, "elegida la recibida")
        igual(aqui.atenciones[0].sinInscribir!!.normal, 1, "no toca el original")
        val malo = vuelta.copy(dias = listOf(vuelta.dias[0].copy(fuera = CuentaFuera(-1, 2))) + vuelta.dias.drop(1))
        igual(idaYVuelta(malo), null, "una cuenta negativa no se acepta")
    }

    @Test
    fun `Lo recibido que no es un valor de la app no se guarda tal cual`() {
        val fila = FilaPersonaQr("RARO", "", "PRUEBA", "00000077", "pasaporte", "ayer", "bebe", "X", "beneficiario", 1,
            "2026-01-01", "nunca", "")
        val c = compararPadron(padronVacio(), PaquetePadron(listOf(fila)))
        val p = c.nuevas[0]
        igual(p.tipoAfiliado, "", "tipo desconocido: vacío (queda en «Les faltan datos»)")
        igual(p.tipoDocumento, "dni")
        igual(p.grupoEtarioManual, null)
        igual(p.sexo, "")
        igual(p.fechaNacimiento, "")
        igual(p.bajaEn, "")
        val conRaro = padronVacio().copy(afiliados = listOf(nuevoAfiliado().copy(numeroDocumento = "00000077",
            nombres = "PRUEBA", apellidoPaterno = "RARO", tipoAfiliado = "beneficiario")))
        ok(compararPadron(conRaro, PaquetePadron(listOf(fila.copy(tipoAfiliado = "habitual")))).distintas[0].soloCompleta,
            "un tipo inválido aquí cuenta como vacío")
        val e = compararEntregas(Calendario(entregas = emptyList()),
            PaqueteEntregas(ConfigEntregasQr("diaria", "", emptyList()), listOf(EntregaQr("2026-10-20", "perdida", ""))))
        igual(e.config.size, 0, "periodicidad desconocida: no se ofrece")
        igual(e.nuevas[0].estado, "pendiente")
    }

    @Test
    fun `El QR de antes (solo cifras) se sigue leyendo`() {
        val partes = codificarListaQr("2026-10-12", listOf(MarcaLista("00000001", "ayuda_social")))
        val p = assertIs<PaqueteAsistencia>(juntarPartesQr(partes.map { leerParteQr(it)!! }))
        igual(p.dias, listOf(DiaQr("2026-10-12", null, null, listOf(MarcaQr("00000001", true)))))
        igual(textoJson(p), """{"t":"A","v":1,"d":[["2026-10-12",null,null,[["00000001",1]]]]}""")
    }

    @Test
    fun `Entregas recibidas - nuevas, distintas y la configuración, se aplica solo lo elegido`() {
        val aqui = Calendario(periodicidad = "mensual", fechaLimiteBase = "", diasDeAviso = listOf(7, 3, 1),
            entregas = listOf(Entrega(id = "x", fechaLimite = "2026-10-15", estado = "pendiente", nota = "")))
        val alla = Calendario(periodicidad = "quincenal", fechaLimiteBase = "2026-10-15", diasDeAviso = listOf(7, 3, 1),
            entregas = listOf(Entrega(fechaLimite = "2026-10-15", estado = "entregada", nota = ""),
                Entrega(fechaLimite = "2026-09-30", estado = "conforme", nota = "ok")))
        val c = compararEntregas(aqui, paqueteDeEntregas(alla))
        igual(c.config.joinToString(",") { it.campo }, "periodicidad,fechaLimiteBase")
        igual(c.nuevas.size, 1)
        igual(c.distintas.size, 1)
        val nuevo = aplicarEntregas(aqui, c, EleccionEntregas(config = setOf("fechaLimiteBase"), nuevas = setOf("2026-09-30")))
        igual(nuevo.periodicidad, "mensual")
        igual(nuevo.fechaLimiteBase, "2026-10-15")
        igual(nuevo.entregas.size, 2)
        igual(nuevo.entregas[0].estado, "pendiente")
        igual(aqui.entregas.size, 1, "no toca el original")
    }

    @Test
    fun `Hasta 1 400 caracteres, un solo QR, si no, cuadros de hasta 900 para la animación`() {
        fun padron(n: Int) = padronVacio().copy(afiliados = List(n) {
            nuevoAfiliado().copy(apellidoPaterno = "PRUEBA", nombres = "PERSONA $it",
                numeroDocumento = (10000000 + ((it * 7919) % 89999999)).toString(),
                tipoAfiliado = "habitual", grupoEtarioManual = "adulto")
        })
        val chico = codificarParaQr(paqueteDelPadron(padron(15)))
        igual(chico.size, 1, "15 personas: un solo código")
        ok(chico[0].length <= 1400 + 12, "el código solo no pasa de 1 400")
        val grande = codificarParaQr(paqueteDelPadron(padron(150)))
        ok(grande.size > 1, "150 personas: varios cuadros")
        ok(grande.all { it.length <= 900 + 12 }, "los cuadros animados no pasan de 900")
    }
}
