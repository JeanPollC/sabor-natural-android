package com.sabornatural.tienda.datos

import android.graphics.Bitmap
import com.sabornatural.tienda.datos.modelo.Categoria
import com.sabornatural.tienda.datos.modelo.EstadoPublicacion
import com.sabornatural.tienda.datos.modelo.Producto
import com.sabornatural.tienda.datos.modelo.Rol
import com.sabornatural.tienda.datos.modelo.Usuario
import com.sabornatural.tienda.datos.remoto.Archivos
import com.sabornatural.tienda.datos.remoto.ColeccionEnVivo
import com.sabornatural.tienda.datos.remoto.Nube
import com.sabornatural.tienda.datos.remoto.aMapa
import com.sabornatural.tienda.datos.remoto.aProducto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.text.Normalizer

/**
 * Productos en Firestore (`productos/{id}`), con su foto en Firebase Storage (CRUD RF-PRO).
 *
 * Qué productos se escuchan depende de quién usa la app (las reglas no dejan leer más):
 *  - comprador o invitado: solo los PUBLICADO (el catálogo público, RF-ADP-02);
 *  - vendedor: los suyos, en cualquier estado;
 *  - administrador: todos, para moderarlos.
 */
object RepositorioProductos {

    private val coleccion = ColeccionEnVivo { it.aProducto() }

    /** Estado de carga del catálogo o de la lista de gestión. */
    val estado: StateFlow<EstadoCarga> get() = coleccion.estado

    /** Emite cuando cambian los productos o su estado de carga. */
    val cambios: Flow<Unit> get() = coleccion.cambios

    /** Abre la escucha que corresponde al rol de [usuario] (null = invitado). */
    fun escuchar(usuario: Usuario?) {
        val productos = Nube.db.collection(Nube.PRODUCTOS)
        when (usuario?.rol) {
            Rol.ADMINISTRADOR -> coleccion.escuchar("todos", productos)
            Rol.VENDEDOR -> coleccion.escuchar("vendedor:${usuario.id}", productos.whereEqualTo("vendedorId", usuario.id))
            else -> coleccion.escuchar("publicados", productos.whereEqualTo("estado", EstadoPublicacion.PUBLICADO.name))
        }
    }

    /** Cierra la escucha de productos (al cerrar sesión o cambiar de rol). */
    fun detener() = coleccion.detener()

    /** Vuelve a abrir la escucha tras un error de carga. */
    fun reintentar() = coleccion.reintentar()

    /** Todos los productos que el usuario actual puede ver. */
    fun todos(): List<Producto> = coleccion.datos.value

    /** Producto con ese id en la copia local, o null. */
    fun buscarPorId(id: String): Producto? = coleccion.datos.value.find { it.id == id }

    /** Solo los productos PUBLICADO llegan al catálogo público (RF-ADP-02). */
    fun publicados(): List<Producto> = todos().filter { it.estado == EstadoPublicacion.PUBLICADO }

    /** Productos publicados marcados como destacados (fila del inicio). */
    fun destacados(): List<Producto> = publicados().filter { it.destacado }

    /** Búsqueda por texto (sin importar tildes ni mayúsculas) y filtro por categoría (RF-INI-03). */
    fun buscar(texto: String, categoria: Categoria?): List<Producto> {
        val consulta = normalizar(texto)
        return publicados().filter { p ->
            (categoria == null || p.categoria == categoria) &&
                (consulta.isEmpty() || normalizar(p.nombre).contains(consulta) || normalizar(p.vendedorNombre).contains(consulta))
        }
    }

    /** Productos de un vendedor, en cualquier estado. */
    fun delVendedor(vendedorId: String): List<Producto> = todos().filter { it.vendedorId == vendedorId }

    /** Id para un producto nuevo, generado por Firestore antes de guardarlo (sirve para la ruta de la foto). */
    fun nuevoId(): String = Nube.db.collection(Nube.PRODUCTOS).document().id

    /**
     * Crea o actualiza un producto (RF-PRO-01 y RF-PRO-04). Si hay [fotoNueva] (cámara o galería),
     * primero se sube a Storage y su enlace queda en el campo imagenUrl.
     */
    suspend fun guardar(producto: Producto, fotoNueva: Bitmap? = null): Producto {
        val final = if (fotoNueva != null) {
            producto.copy(imagenUrl = Archivos.subirFoto(Archivos.rutaProducto(producto.id), fotoNueva))
        } else {
            producto
        }
        Nube.db.collection(Nube.PRODUCTOS).document(final.id).set(final.aMapa()).await()
        coleccion.reemplazarLocal(final) { it.id == final.id }
        return final
    }

    /**
     * Cambia el estado de la publicación: moderación del administrador (aprobar, rechazar con
     * motivo, suspender) o pausa/reactivación del vendedor.
     */
    suspend fun cambiarEstado(producto: Producto, estado: EstadoPublicacion, motivoRechazo: String = "") {
        Nube.db.collection(Nube.PRODUCTOS).document(producto.id)
            .update(mapOf("estado" to estado.name, "motivoRechazo" to motivoRechazo)).await()
        coleccion.reemplazarLocal(producto.copy(estado = estado, motivoRechazo = motivoRechazo)) { it.id == producto.id }
    }

    /** Elimina el producto y su foto (RF-PRO-05). */
    suspend fun eliminar(producto: Producto) {
        Nube.db.collection(Nube.PRODUCTOS).document(producto.id).delete().await()
        if (producto.imagenUrl.isNotBlank()) runCatching { Archivos.borrar(Archivos.rutaProducto(producto.id)) }
    }

    /** Texto en minúsculas y sin tildes, para buscar sin importar cómo se escriba. */
    private fun normalizar(texto: String): String =
        Normalizer.normalize(texto.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
