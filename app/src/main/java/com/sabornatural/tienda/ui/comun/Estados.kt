package com.sabornatural.tienda.ui.comun

import android.view.View
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.sabornatural.tienda.R
import com.sabornatural.tienda.databinding.IncludeEstadoVacioBinding
import com.sabornatural.tienda.datos.EstadoCarga
import com.sabornatural.tienda.datos.modelo.EstadoCuenta
import com.sabornatural.tienda.datos.modelo.EstadoPago
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
        EstadoPedido.PAGO_PENDIENTE, EstadoPedido.EN_PREPARACION, EstadoPedido.DESPACHADO -> Tono.PENDIENTE
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

/** Tono del resultado del pago (aprobado, pendiente o rechazado). */
val EstadoPago.tono: Tono
    get() = when (this) {
        EstadoPago.APROBADO -> Tono.EXITO
        EstadoPago.PENDIENTE -> Tono.PENDIENTE
        EstadoPago.RECHAZADO -> Tono.ERROR
    }

/**
 * Pinta el estado de carga de una lista que viene de Firestore sobre el bloque de estado vacío
 * (include_estado_vacio.xml), para no dejar la pantalla en blanco:
 *  - Cargando: indicador circular y "Cargando…";
 *  - Error: ícono de alerta, el mensaje y el botón "Reintentar";
 *  - Listo: oculta el indicador y devuelve true para que la pantalla muestre la lista
 *    (y, si está vacía, su propio mensaje de estado vacío).
 *
 * @return true si los datos están listos para mostrarse.
 */
fun IncludeEstadoVacioBinding.mostrarCarga(estado: EstadoCarga, alReintentar: () -> Unit): Boolean {
    val contexto = root.context
    when (estado) {
        EstadoCarga.Cargando -> {
            root.visibility = View.VISIBLE
            progreso.visibility = View.VISIBLE
            icono.visibility = View.INVISIBLE
            titulo.setText(R.string.carga_cargando)
            texto.visibility = View.GONE
            boton.visibility = View.GONE
        }
        is EstadoCarga.Error -> {
            root.visibility = View.VISIBLE
            progreso.visibility = View.GONE
            icono.visibility = View.VISIBLE
            icono.setImageResource(R.drawable.ic_alerta)
            titulo.setText(R.string.carga_error_titulo)
            texto.visibility = View.VISIBLE
            texto.text = contexto.getString(estado.mensaje)
            boton.visibility = View.VISIBLE
            boton.setText(R.string.carga_reintentar)
            boton.setOnClickListener { alReintentar() }
        }
        EstadoCarga.Listo -> {
            progreso.visibility = View.GONE
            icono.visibility = View.VISIBLE
            texto.visibility = View.VISIBLE
            boton.visibility = View.GONE
        }
    }
    return estado == EstadoCarga.Listo
}
