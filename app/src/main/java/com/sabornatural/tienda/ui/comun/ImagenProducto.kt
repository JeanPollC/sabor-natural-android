package com.sabornatural.tienda.ui.comun

import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import coil.dispose
import coil.load
import com.sabornatural.tienda.datos.modelo.Categoria

/**
 * Dibuja la imagen de un producto: la foto guardada en Firebase Storage, si el vendedor subió
 * una, o la ilustración de su categoría (fondo de color e ícono) mientras carga o si no hay foto.
 *
 * La foto se descarga con Coil, que la guarda en caché (memoria y disco) para no volver a
 * descargarla al desplazar las listas.
 *
 * @param marco     contenedor con fondo redondeado (drawable bg_imagen_producto)
 * @param foto      ImageView a pantalla completa del marco, para la foto real
 * @param icono     ImageView centrado, para el ícono de la categoría
 * @param imagenUrl enlace de descarga de Storage, o vacío/null si no hay foto
 */
fun mostrarImagenProducto(marco: View, foto: ImageView, icono: ImageView, categoria: Categoria, imagenUrl: String?) {
    val contexto = marco.context
    marco.backgroundTintList = ContextCompat.getColorStateList(contexto, categoria.fondo)
    icono.setImageResource(categoria.icono)
    icono.imageTintList = ContextCompat.getColorStateList(contexto, categoria.color)
    icono.visibility = View.VISIBLE
    marco.clipToOutline = true

    if (imagenUrl.isNullOrBlank()) {
        foto.dispose() // cancela una descarga anterior si la fila se reutiliza
        foto.setImageDrawable(null)
        foto.visibility = View.GONE
        return
    }
    foto.visibility = View.VISIBLE
    foto.load(imagenUrl) {
        crossfade(true)
        listener(
            onSuccess = { _, _ -> icono.visibility = View.GONE },
            onError = { _, _ -> foto.visibility = View.GONE },
        )
    }
}

/**
 * Muestra la foto de perfil guardada en Storage sobre el avatar con iniciales; si no hay foto,
 * deja ver las iniciales.
 */
fun mostrarFotoPerfil(foto: ImageView, fotoUrl: String?) {
    if (fotoUrl.isNullOrBlank()) {
        foto.dispose()
        foto.setImageDrawable(null)
        foto.visibility = View.GONE
        return
    }
    foto.visibility = View.VISIBLE
    foto.load(fotoUrl) {
        crossfade(true)
        listener(onError = { _, _ -> foto.visibility = View.GONE })
    }
}
