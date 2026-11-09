package com.sabornatural.tienda.datos

import com.sabornatural.tienda.datos.modelo.EstadoPedido
import com.sabornatural.tienda.datos.modelo.LineaPedido
import com.sabornatural.tienda.datos.modelo.MedioPago
import com.sabornatural.tienda.datos.modelo.Pedido
import com.sabornatural.tienda.datos.modelo.TipoEntrega
import com.sabornatural.tienda.datos.modelo.Usuario
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/** Pedidos de la tienda, en memoria (datos de assets/datos_demo/pedidos.json). */
object RepositorioPedidos {

    private val pedidos = mutableListOf<Pedido>()

    /** Valor fijo del domicilio en la demostración; con la API se calculará por distancia (RF-PED-02). */
    const val VALOR_DOMICILIO = 6_000L

    fun reemplazarTodos(lista: List<Pedido>) {
        pedidos.clear()
        pedidos += lista
    }

    /** Más recientes primero. */
    fun todos(): List<Pedido> = pedidos.sortedByDescending { it.fecha }

    fun buscarPorId(id: Int): Pedido? = pedidos.find { it.id == id }

    fun delComprador(compradorId: Int): List<Pedido> = todos().filter { it.compradorId == compradorId }

    fun delVendedor(vendedorId: Int): List<Pedido> = todos().filter { it.vendedorId == vendedorId }

    /**
     * Crea el pedido cuando la pasarela aprueba el pago y descuenta las existencias (RF-PED-03).
     * El código SN-XXXXXX y la referencia de pago se simulan.
     */
    fun crear(
        comprador: Usuario,
        vendedorId: Int,
        vendedorNombre: String,
        lineas: List<LineaPedido>,
        medioPago: MedioPago,
        tipoEntrega: TipoEntrega,
        direccion: String,
    ): Pedido {
        val pedido = Pedido(
            id = (pedidos.maxOfOrNull { it.id } ?: 0) + 1,
            codigo = "SN-" + Random.nextInt(100_000, 999_999),
            compradorId = comprador.id,
            compradorNombre = comprador.nombreCompleto,
            compradorTelefono = comprador.telefono,
            vendedorId = vendedorId,
            vendedorNombre = vendedorNombre,
            lineas = lineas,
            domicilio = if (tipoEntrega == TipoEntrega.DOMICILIO) VALOR_DOMICILIO else 0,
            estado = EstadoPedido.CONFIRMADO,
            medioPago = medioPago,
            tipoEntrega = tipoEntrega,
            direccion = direccion,
            referenciaPago = "WMP-" + Random.nextInt(100_000, 999_999),
            fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
        )
        pedidos += pedido
        lineas.forEach { RepositorioProductos.descontar(it.productoId, it.cantidad) }
        return pedido
    }

    fun cambiarEstado(id: Int, estado: EstadoPedido) {
        val i = pedidos.indexOfFirst { it.id == id }
        if (i >= 0) pedidos[i] = pedidos[i].copy(estado = estado)
    }
}
