package com.sabornatural.tienda.datos

import com.google.firebase.firestore.FieldValue
import com.sabornatural.tienda.datos.modelo.EstadoPago
import com.sabornatural.tienda.datos.modelo.EstadoPedido
import com.sabornatural.tienda.datos.modelo.LineaPedido
import com.sabornatural.tienda.datos.modelo.MedioPago
import com.sabornatural.tienda.datos.modelo.Pedido
import com.sabornatural.tienda.datos.modelo.Rol
import com.sabornatural.tienda.datos.modelo.TipoEntrega
import com.sabornatural.tienda.datos.modelo.Usuario
import com.google.firebase.firestore.Source
import com.sabornatural.tienda.datos.pagos.PasarelaWompi
import com.sabornatural.tienda.datos.remoto.ColeccionEnVivo
import com.sabornatural.tienda.datos.remoto.Nube
import com.sabornatural.tienda.datos.remoto.aMapa
import com.sabornatural.tienda.datos.remoto.aPedido
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * Pedidos en Firestore (`pedidos/{id}`): el historial de compras y ventas, con sus estados.
 *
 * Flujo de una compra:
 *  1. [crear]: el pedido nace en PAGO_PENDIENTE con los datos de entrega y la ubicación.
 *  2. La Cloud Function crearPago fija el total con los precios de Firestore y firma el pago;
 *     el comprador paga en el Web Checkout de Wompi.
 *  3. [confirmarPago]: la Cloud Function verificarPago consulta la transacción y guarda el
 *     resultado; si fue aprobado, el pedido pasa a CONFIRMADO y se descuentan las existencias
 *     (RF-PED-03); si fue rechazado, pasa a CANCELADO.
 *  4. El vendedor avanza el estado con [cambiarEstado] (RF-PED-07).
 */
object RepositorioPedidos {

    private val coleccion = ColeccionEnVivo { it.aPedido() }

    /** Valor fijo del domicilio; en una versión futura se calcularía por distancia (RF-PED-02). */
    const val VALOR_DOMICILIO = 6_000L

    val estado: StateFlow<EstadoCarga> get() = coleccion.estado

    val cambios: Flow<Unit> get() = coleccion.cambios

    /** Abre la escucha según el rol: los pedidos propios del comprador o del vendedor, o todos (administrador). */
    fun escuchar(usuario: Usuario) {
        val pedidos = Nube.db.collection(Nube.PEDIDOS)
        when (usuario.rol) {
            Rol.ADMINISTRADOR -> coleccion.escuchar("todos", pedidos)
            Rol.VENDEDOR -> coleccion.escuchar("vendedor:${usuario.id}", pedidos.whereEqualTo("vendedorId", usuario.id))
            Rol.COMPRADOR -> coleccion.escuchar("comprador:${usuario.id}", pedidos.whereEqualTo("compradorId", usuario.id))
        }
    }

    /** Cierra la escucha de pedidos (al cerrar sesión). */
    fun detener() = coleccion.detener()

    /** Vuelve a abrir la escucha tras un error de carga. */
    fun reintentar() = coleccion.reintentar()

    /** Más recientes primero (el texto de la fecha yyyy-MM-dd HH:mm se ordena como fecha). */
    fun todos(): List<Pedido> = coleccion.datos.value.sortedByDescending { it.fecha }

    /** Pedido con ese id en la copia local, o null. */
    fun buscarPorId(id: String): Pedido? = coleccion.datos.value.find { it.id == id }

    /** Historial de compras de un comprador, más recientes primero. */
    fun delComprador(compradorId: String): List<Pedido> = todos().filter { it.compradorId == compradorId }

    /** Pedidos recibidos por un vendedor, más recientes primero. */
    fun delVendedor(vendedorId: String): List<Pedido> = todos().filter { it.vendedorId == vendedorId }

    /** Lee un pedido directamente de Firestore (por ejemplo, si Android cerró la app durante el pago). */
    suspend fun obtener(id: String): Pedido? =
        buscarPorId(id) ?: Nube.db.collection(Nube.PEDIDOS).document(id).get().await().aPedido()

    /**
     * Crea el pedido antes de pagar, en estado PAGO_PENDIENTE. El código SN-XXXXXX es el que ve el
     * usuario; la referencia de pago para Wompi la genera después la función crearPago.
     *
     * @param latitud y [longitud] ubicación de entrega tomada del GPS, o null si se escribió a mano.
     */
    suspend fun crear(
        comprador: Usuario,
        vendedorId: String,
        vendedorNombre: String,
        lineas: List<LineaPedido>,
        medioPago: MedioPago,
        tipoEntrega: TipoEntrega,
        direccion: String,
        latitud: Double?,
        longitud: Double?,
    ): Pedido {
        val documento = Nube.db.collection(Nube.PEDIDOS).document()
        val pedido = Pedido(
            id = documento.id,
            codigo = "SN-" + Random.nextInt(100_000, 999_999),
            compradorId = comprador.id,
            compradorNombre = comprador.nombreCompleto,
            compradorTelefono = comprador.telefono,
            vendedorId = vendedorId,
            vendedorNombre = vendedorNombre,
            lineas = lineas,
            domicilio = if (tipoEntrega == TipoEntrega.DOMICILIO) VALOR_DOMICILIO else 0,
            estado = EstadoPedido.PAGO_PENDIENTE,
            medioPago = medioPago,
            tipoEntrega = tipoEntrega,
            direccion = direccion,
            referenciaPago = "",
            fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
            estadoPago = EstadoPago.PENDIENTE,
            latitud = latitud,
            longitud = longitud,
        )
        documento.set(pedido.aMapa() + ("creado" to FieldValue.serverTimestamp())).await()
        coleccion.reemplazarLocal(pedido) { it.id == pedido.id }
        return pedido
    }

    /**
     * Pide al servidor (Cloud Function verificarPago) que confirme la transacción en Wompi y
     * espera el resultado final; luego devuelve el pedido actualizado desde Firestore.
     *
     * La app no escribe el resultado del pago: lo hace la función, que también confirma el pedido
     * y descuenta las existencias si fue aprobado (RF-PED-03).
     */
    suspend fun confirmarPago(pedidoId: String, transaccionId: String): Pedido {
        PasarelaWompi.esperarResultado(pedidoId, transaccionId)
        val actualizado = Nube.db.collection(Nube.PEDIDOS).document(pedidoId).get(Source.SERVER).await().aPedido()
            ?: throw ErrorNegocio(com.sabornatural.tienda.R.string.error_generico)
        coleccion.reemplazarLocal(actualizado) { it.id == actualizado.id }
        return actualizado
    }

    /** Cambia el estado del pedido: avance del vendedor, rechazo o cancelación (RF-PED-06/07). */
    suspend fun cambiarEstado(pedido: Pedido, estado: EstadoPedido) {
        Nube.db.collection(Nube.PEDIDOS).document(pedido.id)
            .update(mapOf("estado" to estado.name, "actualizado" to FieldValue.serverTimestamp())).await()
        coleccion.reemplazarLocal(pedido.copy(estado = estado)) { it.id == pedido.id }
    }
}
