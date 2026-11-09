package com.sabornatural.tienda.datos.modelo

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sabornatural.tienda.R

/** Estados del pedido y su secuencia (RF-PED-07). */
enum class EstadoPedido(@param:StringRes val nombre: Int) {
    CONFIRMADO(R.string.pedido_confirmado),
    EN_PREPARACION(R.string.pedido_en_preparacion),
    DESPACHADO(R.string.pedido_despachado),
    ENTREGADO(R.string.pedido_entregado),
    CANCELADO(R.string.pedido_cancelado),
    ;

    val enCurso: Boolean get() = this == CONFIRMADO || this == EN_PREPARACION || this == DESPACHADO

    /** Estado al que avanza el pedido cuando el vendedor lo actualiza, o null si ya terminó. */
    val siguiente: EstadoPedido?
        get() = when (this) {
            CONFIRMADO -> EN_PREPARACION
            EN_PREPARACION -> DESPACHADO
            DESPACHADO -> ENTREGADO
            ENTREGADO, CANCELADO -> null
        }
}

/** Medios de pago que ofrece la pasarela (documento técnico, Tabla 2). */
enum class MedioPago(@param:StringRes val nombre: Int, @param:DrawableRes val icono: Int) {
    TARJETA(R.string.pago_tarjeta, R.drawable.ic_tarjeta),
    PSE(R.string.pago_pse, R.drawable.ic_banco),
    NEQUI(R.string.pago_nequi, R.drawable.ic_celular),
}

enum class TipoEntrega(@param:StringRes val nombre: Int) {
    DOMICILIO(R.string.entrega_domicilio),
    RECOGER(R.string.entrega_recoger),
}

/** Línea de un pedido: guarda el precio unitario del momento de la compra (RF-PED-03). */
data class LineaPedido(
    val productoId: Int,
    val nombre: String,
    val categoria: Categoria,
    val cantidad: Int,
    val precioUnitario: Long,
) {
    val subtotal: Long get() = precioUnitario * cantidad
}

data class Pedido(
    val id: Int,
    val codigo: String,
    val compradorId: Int,
    val compradorNombre: String,
    val compradorTelefono: String,
    val vendedorId: Int,
    val vendedorNombre: String,
    val lineas: List<LineaPedido>,
    val domicilio: Long,
    val estado: EstadoPedido,
    val medioPago: MedioPago,
    val tipoEntrega: TipoEntrega,
    val direccion: String,
    val referenciaPago: String,
    val fecha: String,
) {
    val subtotal: Long get() = lineas.sumOf { it.subtotal }
    val total: Long get() = subtotal + domicilio
    val unidades: Int get() = lineas.sumOf { it.cantidad }
}
