package com.sabornatural.tienda.ui.comun

import android.graphics.Bitmap
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.sabornatural.tienda.datos.modelo.Categoria

/**
 * Fotos tomadas con la cámara o elegidas de la galería en el formulario del vendedor.
 * Se guardan en memoria mientras la app está abierta; con la API se subirán al almacenamiento.
 */
object FotosProductos {
    private val fotos = mutableMapOf<Int, Bitmap>()
    operator fun get(productoId: Int): Bitmap? = fotos[productoId]
    operator fun set(productoId: Int, foto: Bitmap?) {
        if (foto == null) fotos.remove(productoId) else fotos[productoId] = foto
    }
}

/** Fotos de perfil tomadas o elegidas en "Editar perfil" (en memoria, por usuario). */
object FotosPerfil {
    private val fotos = mutableMapOf<Int, Bitmap>()
    operator fun get(usuarioId: Int): Bitmap? = fotos[usuarioId]
    operator fun set(usuarioId: Int, foto: Bitmap?) {
        if (foto == null) fotos.remove(usuarioId) else fotos[usuarioId] = foto
    }
}

/**
 * Dibuja la imagen de un producto: la foto, si el vendedor la tomó, o la ilustración de su
 * categoría (fondo de color e ícono) mientras no haya foto.
 *
 * @param marco  contenedor con fondo redondeado (drawable bg_imagen_producto)
 * @param foto   ImageView a pantalla completa del marco, para la foto real
 * @param icono  ImageView centrado, para el ícono de la categoría
 */
fun mostrarImagenProducto(marco: View, foto: ImageView, icono: ImageView, categoria: Categoria, productoId: Int?) {
    val contexto = marco.context
    marco.backgroundTintList = ContextCompat.getColorStateList(contexto, categoria.fondo)
    val bitmap = productoId?.let { FotosProductos[it] }
    if (bitmap != null) {
        foto.setImageBitmap(bitmap)
        foto.visibility = View.VISIBLE
        icono.visibility = View.GONE
    } else {
        foto.visibility = View.GONE
        icono.visibility = View.VISIBLE
        icono.setImageResource(categoria.icono)
        icono.imageTintList = ContextCompat.getColorStateList(contexto, categoria.color)
    }
    marco.clipToOutline = true
}
