// Dónde cae cada dato en la hoja oficial del Formato B-1: ÚNICA fuente de
// coordenadas (recalibrar es cambiar números aquí, nunca en las pantallas).
// Todo en mm desde la esquina superior izquierda de una A4 vertical; `yMm` es
// el borde de arriba de la caja y el texto se apoya abajo, sobre la punteada.
//
// Los números son los de la web tal cual: DocumentosOraculoTest exige el mismo
// PDF byte a byte.
//
// ESTIMADO: medido sobre una foto de la hoja en blanco enderezada a A4, no
// sobre la hoja física con regla. DECISIÓN ABIERTA: se cierra midiendo
// la hoja física o un escaneo plano a 300 ppp; entonces `estimado` pasa a false.
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
    letraFirmasPt = 8.0, // el espacio junto a «Nombre y apellido» es corto
    campos = linkedMapOf(
        // Centro de atención: una X dentro del recuadro que corresponde.
        "marcaComedor" to CampoB1(129.4, 45.7, 8.9, 6.4, "center"),
        "marcaOllaComun" to CampoB1(177.6, 45.4, 14.1, 6.6, "center"),
        "nombreCentro" to CampoB1(65.0, 51.3, 72.5, 4.5, "left"),
        "codigoPca" to CampoB1(166.0, 51.3, 35.0, 4.5, "left"),
        "periodo" to CampoB1(44.0, 58.5, 67.0, 4.5, "left"),
        "fechaRendicion" to CampoB1(169.0, 58.5, 33.0, 4.5, "left"),
        "nombrePresidenta" to CampoB1(58.5, 65.9, 142.5, 4.5, "left"),
        "dniPresidenta" to CampoB1(23.0, 72.5, 52.0, 4.5, "left"),
        "celular" to CampoB1(156.5, 72.2, 45.5, 4.5, "left"),
        // Ingresos: el «S/» ya está impreso.
        "montoSubsidio" to CampoB1(152.5, 88.3, 42.5, 4.5, "right"),
        "fechaAsignacion" to CampoB1(149.0, 95.5, 46.0, 4.5, "center"),
        // Pie de egresos: los tres totales, en la columna «Monto total».
        "totalConSubsidio" to CampoB1(171.7, 189.2, 28.5, 3.9, "right"),
        "totalAporte" to CampoB1(171.7, 193.1, 28.5, 3.9, "right"),
        "totalRendicion" to CampoB1(171.7, 197.0, 28.5, 3.7, "right"),
        // Firmas: nombre y DNI junto a sus rótulos. La firma y el sello van a mano.
        "nombrePresidentaFirma" to CampoB1(45.0, 248.2, 45.0, 4.2, "left"),
        "dniPresidentaFirma" to CampoB1(26.5, 252.2, 45.0, 4.2, "left"),
        "nombreTesorera" to CampoB1(167.5, 248.8, 36.0, 4.2, "left"),
        "dniTesorera" to CampoB1(149.0, 252.6, 51.0, 4.2, "left")
    ),
    egresos = EgresosB1(
        primeraFilaYMm = 123.3,
        altoFilaMm = 3.876,
        filas = 17, // contadas en la hoja
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
