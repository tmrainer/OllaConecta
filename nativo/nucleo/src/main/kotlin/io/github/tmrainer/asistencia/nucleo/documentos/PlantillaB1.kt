// Dónde cae cada dato en la hoja oficial del Formato B-1: ÚNICA fuente de
// coordenadas (recalibrar es cambiar números aquí, nunca en las pantallas).
// Todo en mm desde la esquina superior izquierda de una A4 vertical; `yMm` es
// el borde de arriba de la caja y el texto se apoya abajo, sobre la punteada.
//
// Desde el 05-11-2026 la hoja es la de Pachacámac 2026 (formato de rendición
// de gastos del subsidio económico): una fila más en el encabezado (distrito)
// y cuatro renglones al pie (monto total de compra, declaración jurada, aporte
// del centro, total de rendición). Aquí se diverge de la web, que sigue con la
// hoja de VMT: el PDF del B-1 ya no se compara byte a byte con ella.
//
// ESTIMADO: trazado a partir de una foto en ángulo de la hoja llenada, no de
// un escaneo plano. La app dibuja el formato completo (no imprime encima de la
// hoja oficial), así que lo que importa es que se lea; se afina con un
// escaneo a 300 ppp y entonces `estimado` pasa a false.
package io.github.tmrainer.asistencia.nucleo.documentos

/** La caja de un dato. `align`: "left" | "center" | "right". */
data class CampoB1(val xMm: Double, val yMm: Double, val anchoMm: Double, val altoMm: Double, val align: String)

/** Una columna de la tabla de compras: la `y` y el alto salen de la fila. */
data class ColumnaB1(val xMm: Double, val anchoMm: Double, val align: String)

data class HojaA4(val anchoMm: Double, val altoMm: Double)

data class EgresosB1(
    val primeraFilaYMm: Double,
    val altoFilaMm: Double,
    val filas: Int,
    /** En el orden de la hoja, de izquierda a derecha. */
    val columnas: Map<String, ColumnaB1>
)

data class PlantillaB1(
    val estimado: Boolean,
    val hoja: HojaA4,
    /** Tamaño de letra de los datos, en pt. */
    val letraPt: Double,
    val letraTablaPt: Double,
    val letraFirmasPt: Double,
    /** En el orden en que se dibujan. */
    val campos: Map<String, CampoB1>,
    val egresos: EgresosB1
)

val PLANTILLA_B1 = PlantillaB1(
    estimado = true,
    hoja = HojaA4(210.0, 297.0),
    letraPt = 9.0,
    letraTablaPt = 7.5,
    letraFirmasPt = 8.0, // el espacio junto a «Nombres y apellidos» es corto
    campos = linkedMapOf(
        // Centro de atención: una X dentro del recuadro que corresponde (siempre «Olla común»).
        "marcaComedor" to CampoB1(129.4, 45.7, 8.9, 6.4, "center"),
        "marcaOllaComun" to CampoB1(177.6, 45.4, 14.1, 6.6, "center"),
        "nombreCentro" to CampoB1(65.0, 51.3, 136.0, 4.5, "left"),
        "codigoPca" to CampoB1(82.0, 58.5, 50.0, 4.5, "left"),
        "distrito" to CampoB1(157.0, 58.5, 44.0, 4.5, "left"),
        "periodo" to CampoB1(44.0, 65.9, 67.0, 4.5, "left"),
        "fechaRendicion" to CampoB1(169.0, 65.9, 33.0, 4.5, "left"),
        "nombrePresidenta" to CampoB1(113.0, 72.5, 88.0, 4.5, "left"),
        "dniPresidenta" to CampoB1(30.0, 79.1, 45.0, 4.5, "left"),
        "celular" to CampoB1(156.5, 79.1, 45.5, 4.5, "left"),
        // Ingresos: el «S/» ya está impreso.
        "montoSubsidio" to CampoB1(152.5, 95.3, 42.5, 4.5, "right"),
        "fechaAsignacion" to CampoB1(149.0, 102.5, 46.0, 4.5, "center"),
        // Pie de egresos: los cuatro renglones, en la columna «Monto».
        "totalCompra" to CampoB1(171.7, 196.2, 28.5, 3.9, "right"),
        "totalDeclaracionJurada" to CampoB1(171.7, 200.1, 28.5, 3.9, "right"),
        "totalAporte" to CampoB1(171.7, 204.0, 28.5, 3.9, "right"),
        "totalRendicion" to CampoB1(171.7, 207.9, 28.5, 3.7, "right"),
        // Firmas: nombre y DNI junto a sus rótulos. La firma y el sello van a mano.
        "nombrePresidentaFirma" to CampoB1(46.0, 252.2, 47.0, 4.2, "left"),
        "dniPresidentaFirma" to CampoB1(26.5, 256.2, 45.0, 4.2, "left"),
        "nombreTesorera" to CampoB1(169.0, 252.2, 36.0, 4.2, "left"),
        "dniTesorera" to CampoB1(149.0, 256.2, 51.0, 4.2, "left")
    ),
    egresos = EgresosB1(
        primeraFilaYMm = 130.3,
        altoFilaMm = 3.876,
        filas = 17, // contadas en la hoja de VMT; la de Pachacámac trae menos, pero el papel se llena a mano
        columnas = linkedMapOf(
            "fechaCompra" to ColumnaB1(15.1, 17.4, "center"),
            "descripcion" to ColumnaB1(32.5, 40.3, "left"),
            "cantidad" to ColumnaB1(72.8, 9.7, "center"),
            "unidadMedida" to ColumnaB1(82.5, 16.4, "center"),
            "rucProveedor" to ColumnaB1(98.9, 30.3, "center"),
            "boleta" to ColumnaB1(129.2, 26.9, "center"),
            "precioUnitario" to ColumnaB1(156.1, 15.5, "right"),
            "montoTotal" to ColumnaB1(171.6, 29.1, "right")
        )
    )
)

val FILAS_EGRESOS = PLANTILLA_B1.egresos.filas
