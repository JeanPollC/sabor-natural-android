package com.sabornatural.tienda.datos

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.sabornatural.tienda.datos.modelo.Producto

/** Una línea del carrito: el producto y cuántas unidades se llevan. */
data class LineaCarrito(val producto: Producto, val cantidad: Int) {
    val subtotal: Long get() = producto.precio * cantidad
}

/**
 * Carrito de compras (mockup B-03, RF-CAR-01 a RF-CAR-06).
 *
 * Expone las líneas como LiveData: las pantallas lo "observan" y se actualizan solas cuando
 * cambia (el contador de la barra inferior, la lista del carrito y el resumen).
 * Regla de negocio: un carrito pertenece a un solo vendedor (RF-CAR-05).
 */
object Carrito {

    enum class Resultado { AGREGADO, OTRO_VENDEDOR, SIN_EXISTENCIAS }

    private val _lineas = MutableLiveData<List<LineaCarrito>>(emptyList())
    val lineas: LiveData<List<LineaCarrito>> = _lineas

    private val actuales: List<LineaCarrito> get() = _lineas.value.orEmpty()

    val unidades: Int get() = actuales.sumOf { it.cantidad }
    val subtotal: Long get() = actuales.sumOf { it.subtotal }
    val vendedorNombre: String? get() = actuales.firstOrNull()?.producto?.vendedorNombre
    val vendedorId: Int? get() = actuales.firstOrNull()?.producto?.vendedorId

    /** Agrega unidades; si el producto es de otro vendedor, no lo agrega y lo informa. */
    fun agregar(producto: Producto, cantidad: Int = 1): Resultado {
        if (producto.agotado) return Resultado.SIN_EXISTENCIAS
        val vendedorActual = vendedorId
        if (vendedorActual != null && vendedorActual != producto.vendedorId) return Resultado.OTRO_VENDEDOR

        val existente = actuales.find { it.producto.id == producto.id }
        val nuevaCantidad = ((existente?.cantidad ?: 0) + cantidad).coerceAtMost(maximoPara(producto))
        _lineas.value = if (existente == null) {
            actuales + LineaCarrito(producto, nuevaCantidad)
        } else {
            actuales.map { if (it.producto.id == producto.id) it.copy(cantidad = nuevaCantidad) else it }
        }
        return Resultado.AGREGADO
    }

    /** Vacía el carrito y agrega el producto del nuevo vendedor (el usuario lo confirmó). */
    fun reemplazarCon(producto: Producto, cantidad: Int = 1) {
        _lineas.value = emptyList()
        agregar(producto, cantidad)
    }

    /** Cambia la cantidad respetando el mínimo (1) y las existencias (RF-CAR-03). */
    fun cambiarCantidad(productoId: Int, cantidad: Int) {
        _lineas.value = actuales.map {
            if (it.producto.id == productoId) it.copy(cantidad = cantidad.coerceIn(1, maximoPara(it.producto))) else it
        }
    }

    /** Quita una línea y la devuelve con su posición, para poder deshacer (RF-CAR-04). */
    fun quitar(productoId: Int): Pair<Int, LineaCarrito>? {
        val posicion = actuales.indexOfFirst { it.producto.id == productoId }
        if (posicion < 0) return null
        val linea = actuales[posicion]
        _lineas.value = actuales.filterIndexed { i, _ -> i != posicion }
        return posicion to linea
    }

    fun restaurar(posicion: Int, linea: LineaCarrito) {
        _lineas.value = actuales.toMutableList().apply { add(posicion.coerceIn(0, size), linea) }
    }

    fun vaciar() {
        _lineas.value = emptyList()
    }

    fun cantidadDe(productoId: Int): Int = actuales.find { it.producto.id == productoId }?.cantidad ?: 0

    private fun maximoPara(producto: Producto): Int = minOf(producto.existencias, MAXIMO_POR_LINEA)

    /** Límite por línea (RF-CAR-01: de 1 a 99 unidades, sin superar las existencias). */
    private const val MAXIMO_POR_LINEA = 99
}
