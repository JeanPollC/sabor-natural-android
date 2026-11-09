package com.sabornatural.tienda.ui.comun

import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.modelo.EstadoCuenta
import com.sabornatural.tienda.datos.modelo.EstadoPedido
import com.sabornatural.tienda.datos.modelo.EstadoPublicacion

/**
 * Tonos de las etiquetas de estado (documento técnico, sección 5.1): el estado se comunica con
 * color y con texto, nunca solo con color.
 */
enum class Tono(@param:ColorRes val fondo: Int, @param:ColorRes val texto: Int) {
    EXITO(R.color.estado_exito_fondo, R.color.estado_exito_texto),
    PENDIENTE(R.color.estado_pendiente_fondo, R.color.estado_pendiente_texto),
    ERROR(R.color.estado_error_fondo, R.color.estado_error_texto),
    INFO(R.color.estado_info_fondo, R.color.estado_info_texto),
    NEUTRO(R.color.crema_oscuro, R.color.texto_secundario),
}

/** Pinta una etiqueta (estilo Widget.SaborNatural.Etiqueta) con el texto y el tono indicados. */
fun TextView.mostrarEtiqueta(texto: CharSequence, tono: Tono) {
    text = texto
    backgroundTintList = ContextCompat.getColorStateList(context, tono.fondo)
    setTextColor(ContextCompat.getColor(context, tono.texto))
}

val EstadoPedido.tono: Tono
    get() = when (this) {
        EstadoPedido.CONFIRMADO -> Tono.INFO
        EstadoPedido.EN_PREPARACION, EstadoPedido.DESPACHADO -> Tono.PENDIENTE
        EstadoPedido.ENTREGADO -> Tono.EXITO
        EstadoPedido.CANCELADO -> Tono.ERROR
    }

val EstadoPublicacion.tono: Tono
    get() = when (this) {
        EstadoPublicacion.PUBLICADO -> Tono.EXITO
        EstadoPublicacion.EN_REVISION -> Tono.PENDIENTE
        EstadoPublicacion.RECHAZADO -> Tono.ERROR
        EstadoPublicacion.SUSPENDIDO, EstadoPublicacion.BORRADOR -> Tono.NEUTRO
    }

val EstadoCuenta.tono: Tono
    get() = when (this) {
        EstadoCuenta.ACTIVO -> Tono.EXITO
        EstadoCuenta.PENDIENTE -> Tono.PENDIENTE
        EstadoCuenta.SUSPENDIDO -> Tono.ERROR
    }
