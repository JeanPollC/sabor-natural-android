package com.sabornatural.tienda.ui.comun

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formatos colombianos para precios y fechas. */
object Formatos {

    private val colombia = Locale.Builder().setLanguage("es").setRegion("CO").build()
    private val pesos = DecimalFormat("$ #,##0", DecimalFormatSymbols(colombia))
    private val decimal = DecimalFormat("0.0", DecimalFormatSymbols(colombia))
    private val entrada = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val salidaFechaHora = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", colombia)
    private val salidaFecha = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", colombia)

    /** 14900 → "$ 14.900" */
    fun precio(valor: Long): String = pesos.format(valor)

    /** 4.8 → "4,8" */
    fun calificacion(valor: Double): String = decimal.format(valor)

    /** "2026-11-07 18:05" → "7 nov 2026, 6:05 p. m." */
    fun fechaHora(texto: String): String =
        runCatching { LocalDateTime.parse(texto, entrada).format(salidaFechaHora) }.getOrDefault(texto)

    /** "2026-08-03" → "3 de agosto de 2026" */
    fun fecha(texto: String): String =
        runCatching { LocalDate.parse(texto).format(salidaFecha) }.getOrDefault(texto)
}
