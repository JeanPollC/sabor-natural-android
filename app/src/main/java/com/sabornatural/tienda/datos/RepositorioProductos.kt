package com.sabornatural.tienda.datos

import com.sabornatural.tienda.datos.modelo.Categoria
import com.sabornatural.tienda.datos.modelo.EstadoPublicacion
import com.sabornatural.tienda.datos.modelo.Producto
import java.text.Normalizer

/** Productos de todos los vendedores, en memoria (datos de assets/datos_demo/productos.json). */
object RepositorioProductos {

    private val productos = mutableListOf<Producto>()

    fun reemplazarTodos(lista: List<Producto>) {
        productos.clear()
        productos += lista
    }

    fun todos(): List<Producto> = productos.toList()

    fun buscarPorId(id: Int): Producto? = productos.find { it.id == id }

    /** Solo los productos PUBLICADO llegan al catálogo público (RF-ADP-02). */
    fun publicados(): List<Producto> = productos.filter { it.estado == EstadoPublicacion.PUBLICADO }

    fun destacados(): List<Producto> = publicados().filter { it.destacado }

    /** Búsqueda por texto (sin importar tildes ni mayúsculas) y filtro por categoría (RF-INI-03). */
    fun buscar(texto: String, categoria: Categoria?): List<Producto> {
        val consulta = normalizar(texto)
        return publicados().filter { p ->
            (categoria == null || p.categoria == categoria) &&
                (consulta.isEmpty() || normalizar(p.nombre).contains(consulta) || normalizar(p.vendedorNombre).contains(consulta))
        }
    }

    fun delVendedor(vendedorId: Int): List<Producto> = productos.filter { it.vendedorId == vendedorId }

    fun siguienteId(): Int = (productos.maxOfOrNull { it.id } ?: 0) + 1

    /** Crea o actualiza un producto (RF-PRO-01 y RF-PRO-04). */
    fun guardar(producto: Producto) {
        val i = productos.indexOfFirst { it.id == producto.id }
        if (i >= 0) productos[i] = producto else productos += producto
    }

    fun eliminar(id: Int) {
        productos.removeAll { it.id == id }
    }

    /** Descuenta las existencias vendidas al confirmar un pedido (RF-PED-03). */
    fun descontar(productoId: Int, cantidad: Int) {
        buscarPorId(productoId)?.let { guardar(it.copy(existencias = (it.existencias - cantidad).coerceAtLeast(0))) }
    }

    private fun normalizar(texto: String): String =
        Normalizer.normalize(texto.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
