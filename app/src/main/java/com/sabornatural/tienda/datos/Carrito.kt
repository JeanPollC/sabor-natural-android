package com.sabornatural.tienda.datos

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.FieldValue
import com.sabornatural.tienda.datos.modelo.Producto
import com.sabornatural.tienda.datos.remoto.Nube
import com.sabornatural.tienda.datos.remoto.aMapaConId
import com.sabornatural.tienda.datos.remoto.aProducto

/** Una línea del carrito: el producto y cuántas unidades se llevan. */
data class LineaCarrito(val producto: Producto, val cantidad: Int) {
    /** Precio por cantidad. */
    val subtotal: Long get() = producto.precio * cantidad
}

/**
 * Carrito de compras (mockup B-03, RF-CAR-01 a RF-CAR-06).
 *
 * Expone las líneas como LiveData: las pantallas lo "observan" y se actualizan solas cuando
 * cambia (el contador de la barra inferior, la lista del carrito y el resumen).
 *
 * Persistencia: cada cambio se guarda en Firestore en `carritos/{uid}`, así el carrito del
 * comprador se conserva si cierra la app, cierra sesión o cambia de teléfono. Firestore guarda
 * los cambios en cola si no hay internet y los envía al reconectar.
 *
 * Regla de negocio: un carrito pertenece a un solo vendedor (RF-CAR-05).
 */
object Carrito {

    /** Resultado de intentar agregar un producto. */
    enum class Resultado { AGREGADO, OTRO_VENDEDOR, SIN_EXISTENCIAS }

    private val _lineas = MutableLiveData<List<LineaCarrito>>(emptyList())
    val lineas: LiveData<List<LineaCarrito>> = _lineas

    /** uid del comprador dueño del carrito, o null si no hay comprador con sesión. */
    private var duenoId: String? = null

    private val actuales: List<LineaCarrito> get() = _lineas.value.orEmpty()

    val unidades: Int get() = actuales.sumOf { it.cantidad }
    val subtotal: Long get() = actuales.sumOf { it.subtotal }
    val vendedorNombre: String? get() = actuales.firstOrNull()?.producto?.vendedorNombre
    val vendedorId: String? get() = actuales.firstOrNull()?.producto?.vendedorId

    /**
     * Carga el carrito guardado del comprador [uid] (al ingresar o al abrir la app). Los datos de
     * cada producto se refrescan con el catálogo en vivo si ya está cargado.
     */
    fun sincronizar(uid: String) {
        if (duenoId == uid) return
        duenoId = uid
        Nube.db.collection(Nube.CARRITOS).document(uid).get()
            .addOnSuccessListener { doc ->
                if (duenoId != uid || actuales.isNotEmpty()) return@addOnSuccessListener
                val guardadas = (doc.get("lineas") as? List<*>).orEmpty().mapNotNull { elemento ->
                    val mapa = elemento as? Map<*, *> ?: return@mapNotNull null
                    val producto = (mapa["producto"] as? Map<*, *>)?.aProducto() ?: return@mapNotNull null
                    val vigente = RepositorioProductos.buscarPorId(producto.id) ?: producto
                    val cantidad = (mapa["cantidad"] as? Number)?.toInt() ?: 1
                    LineaCarrito(vigente, cantidad.coerceIn(1, maxOf(1, minOf(vigente.existencias, MAXIMO_POR_LINEA))))
                }
                _lineas.value = guardadas
            }
            .addOnFailureListener { Log.w("Carrito", "No se pudo leer el carrito guardado", it) }
    }

    /** Agrega unidades; si el producto es de otro vendedor, no lo agrega y lo informa. */
    fun agregar(producto: Producto, cantidad: Int = 1): Resultado {
        if (producto.agotado) return Resultado.SIN_EXISTENCIAS
        val vendedorActual = vendedorId
        if (vendedorActual != null && vendedorActual != producto.vendedorId) return Resultado.OTRO_VENDEDOR

        val existente = actuales.find { it.producto.id == producto.id }
        val nuevaCantidad = ((existente?.cantidad ?: 0) + cantidad).coerceAtMost(maximoPara(producto))
        cambiar(
            if (existente == null) {
                actuales + LineaCarrito(producto, nuevaCantidad)
            } else {
                actuales.map { if (it.producto.id == producto.id) it.copy(cantidad = nuevaCantidad) else it }
            },
        )
        return Resultado.AGREGADO
    }

    /** Vacía el carrito y agrega el producto del nuevo vendedor (el usuario lo confirmó). */
    fun reemplazarCon(producto: Producto, cantidad: Int = 1) {
        _lineas.value = emptyList()
        agregar(producto, cantidad)
    }

    /** Cambia la cantidad respetando el mínimo (1) y las existencias (RF-CAR-03). */
    fun cambiarCantidad(productoId: String, cantidad: Int) {
        cambiar(
            actuales.map {
                if (it.producto.id == productoId) it.copy(cantidad = cantidad.coerceIn(1, maximoPara(it.producto))) else it
            },
        )
    }

    /** Quita una línea y la devuelve con su posición, para poder deshacer (RF-CAR-04). */
    fun quitar(productoId: String): Pair<Int, LineaCarrito>? {
        val posicion = actuales.indexOfFirst { it.producto.id == productoId }
        if (posicion < 0) return null
        val linea = actuales[posicion]
        cambiar(actuales.filterIndexed { i, _ -> i != posicion })
        return posicion to linea
    }

    /** Devuelve una línea quitada a su posición original ("Deshacer"). */
    fun restaurar(posicion: Int, linea: LineaCarrito) {
        cambiar(actuales.toMutableList().apply { add(posicion.coerceIn(0, size), linea) })
    }

    /** Vacía el carrito, también en Firestore (al vaciarlo a mano o tras pagar). */
    fun vaciar() = cambiar(emptyList())

    /** Vacía solo la copia de este teléfono (al cerrar sesión); el carrito guardado se conserva. */
    fun vaciarLocal() {
        duenoId = null
        _lineas.value = emptyList()
    }

    /** Unidades de [productoId] que ya están en el carrito (0 si no está). */
    fun cantidadDe(productoId: String): Int = actuales.find { it.producto.id == productoId }?.cantidad ?: 0

    /** Aplica el cambio en memoria y lo guarda en `carritos/{uid}`. */
    private fun cambiar(nuevas: List<LineaCarrito>) {
        _lineas.value = nuevas
        val uid = duenoId ?: return
        val documento = Nube.db.collection(Nube.CARRITOS).document(uid)
        documento.set(
            mapOf(
                "lineas" to nuevas.map { mapOf("producto" to it.producto.aMapaConId(), "cantidad" to it.cantidad) },
                "actualizado" to FieldValue.serverTimestamp(),
            ),
        ).addOnFailureListener { Log.w("Carrito", "No se pudo guardar el carrito", it) }
    }

    /** Máximo de unidades por línea: las existencias, sin pasar de 99. */
    private fun maximoPara(producto: Producto): Int = minOf(producto.existencias, MAXIMO_POR_LINEA)

    /** Límite por línea (RF-CAR-01: de 1 a 99 unidades, sin superar las existencias). */
    private const val MAXIMO_POR_LINEA = 99
}
