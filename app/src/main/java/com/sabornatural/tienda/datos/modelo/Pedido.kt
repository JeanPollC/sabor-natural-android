package com.sabornatural.tienda.datos.modelo

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sabornatural.tienda.R

/**
 * Estados del pedido y su secuencia (RF-PED-07).
 *
 * PAGO_PENDIENTE es el estado con el que nace el pedido mientras la pasarela confirma el pago:
 * el vendedor lo ve, pero no puede prepararlo hasta que el pago quede aprobado (CONFIRMADO).
 * Si la pasarela rechaza el pago, el pedido pasa a CANCELADO.
 */
enum class EstadoPedido(@param:StringRes val nombre: Int) {
    PAGO_PENDIENTE(R.string.pedido_pago_pendiente),
    CONFIRMADO(R.string.pedido_confirmado),
    EN_PREPARACION(R.string.pedido_en_preparacion),
    DESPACHADO(R.string.pedido_despachado),
    ENTREGADO(R.string.pedido_entregado),
    CANCELADO(R.string.pedido_cancelado),
    ;

    /** true mientras el pedido no ha terminado (ni entregado ni cancelado). */
    val enCurso: Boolean get() = this == PAGO_PENDIENTE || this == CONFIRMADO || this == EN_PREPARACION || this == DESPACHADO

    /** Estado al que avanza el pedido cuando el vendedor lo actualiza, o null si no puede avanzar. */
    val siguiente: EstadoPedido?
        get() = when (this) {
            CONFIRMADO -> EN_PREPARACION
            EN_PREPARACION -> DESPACHADO
            DESPACHADO -> ENTREGADO
            PAGO_PENDIENTE, ENTREGADO, CANCELADO -> null
        }
}

/**
 * Resultado del pago según la pasarela Wompi (campo status de la transacción):
 * APPROVED → APROBADO; PENDING → PENDIENTE; DECLINED, VOIDED y ERROR → RECHAZADO.
 * Solo lo escribe la Cloud Function verificarPago (las reglas no dejan escribirlo a la app).
 */
enum class EstadoPago(@param:StringRes val nombre: Int) {
    PENDIENTE(R.string.pago_estado_pendiente),
    APROBADO(R.string.pago_estado_aprobado),
    RECHAZADO(R.string.pago_estado_rechazado),
}

/** Medios de pago que ofrece la pasarela (documento técnico, Tabla 2). */
enum class MedioPago(@param:StringRes val nombre: Int, @param:DrawableRes val icono: Int) {
    TARJETA(R.string.pago_tarjeta, R.drawable.ic_tarjeta),
    PSE(R.string.pago_pse, R.drawable.ic_banco),
    NEQUI(R.string.pago_nequi, R.drawable.ic_celular),
}

/** Forma de entrega del pedido. */
enum class TipoEntrega(@param:StringRes val nombre: Int) {
    DOMICILIO(R.string.entrega_domicilio),
    RECOGER(R.string.entrega_recoger),
}

/** Línea de un pedido: guarda el precio unitario del momento de la compra (RF-PED-03). */
data class LineaPedido(
    val productoId: String,
    val nombre: String,
    val categoria: Categoria,
    val cantidad: Int,
    val precioUnitario: Long,
) {
    /** Precio unitario por cantidad. */
    val subtotal: Long get() = precioUnitario * cantidad
}

/**
 * Pedido, guardado en Firestore en `pedidos/{id}`. Es el historial de compras del comprador y
 * la lista de ventas del vendedor.
 *
 * @property codigo código legible para el usuario (SN-123456).
 * @property referenciaPago referencia del pago en Wompi (la genera la función crearPago), vacía hasta pagar.
 * @property transaccionId id de la transacción en Wompi, que se recibe al volver del checkout.
 * @property metodoPago medio con el que se pagó en Wompi (CARD, NEQUI, PSE...).
 * @property latitud y [longitud] ubicación de entrega capturada con el GPS, o null si la
 *           dirección se escribió a mano.
 * @property fecha fecha y hora local en formato yyyy-MM-dd HH:mm.
 */
data class Pedido(
    val id: String,
    val codigo: String,
    val compradorId: String,
    val compradorNombre: String,
    val compradorTelefono: String,
    val vendedorId: String,
    val vendedorNombre: String,
    val lineas: List<LineaPedido>,
    val domicilio: Long,
    val estado: EstadoPedido,
    val medioPago: MedioPago,
    val tipoEntrega: TipoEntrega,
    val direccion: String,
    val referenciaPago: String,
    val fecha: String,
    val estadoPago: EstadoPago = EstadoPago.PENDIENTE,
    val detallePago: String = "",
    val latitud: Double? = null,
    val longitud: Double? = null,
    val transaccionId: String = "",
    val metodoPago: String = "",
) {
    /** Suma de las líneas, sin domicilio. */
    val subtotal: Long get() = lineas.sumOf { it.subtotal }

    /** Lo que paga el comprador: subtotal más domicilio. */
    val total: Long get() = subtotal + domicilio

    /** Total de unidades del pedido. */
    val unidades: Int get() = lineas.sumOf { it.cantidad }

    /** true si el pedido suma en los reportes de ventas: pagado y no cancelado. */
    val cuentaComoVenta: Boolean get() = estado != EstadoPedido.CANCELADO && estado != EstadoPedido.PAGO_PENDIENTE
}
