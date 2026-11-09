package com.sabornatural.tienda.datos.modelo

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sabornatural.tienda.R

/**
 * Categorías del catálogo (documento técnico, sección 2.1). Cada una trae su ícono y sus colores,
 * que se usan como imagen del producto mientras no haya fotos reales.
 */
enum class Categoria(
    @param:StringRes val nombre: Int,
    @param:DrawableRes val icono: Int,
    @param:ColorRes val fondo: Int,
    @param:ColorRes val color: Int,
) {
    BEBIDAS(R.string.categoria_bebidas, R.drawable.ic_bebida, R.color.categoria_bebidas_fondo, R.color.categoria_bebidas),
    SEMILLAS(R.string.categoria_semillas, R.drawable.ic_hoja, R.color.categoria_semillas_fondo, R.color.categoria_semillas),
    GRANOLAS(R.string.categoria_granolas, R.drawable.ic_tazon, R.color.categoria_granolas_fondo, R.color.categoria_granolas),
    PANADERIA(R.string.categoria_panaderia, R.drawable.ic_pastel, R.color.categoria_panaderia_fondo, R.color.categoria_panaderia),
    ENDULZANTES(R.string.categoria_endulzantes, R.drawable.ic_taza, R.color.categoria_endulzantes_fondo, R.color.categoria_endulzantes),
    PREPARADOS(R.string.categoria_preparados, R.drawable.ic_restaurante, R.color.categoria_preparados_fondo, R.color.categoria_preparados),
}

/** Ciclo de una publicación (RF-ADP-02): solo los productos PUBLICADO aparecen en el catálogo. */
enum class EstadoPublicacion(@param:StringRes val nombre: Int) {
    BORRADOR(R.string.publicacion_borrador),
    EN_REVISION(R.string.publicacion_revision),
    PUBLICADO(R.string.publicacion_publicado),
    RECHAZADO(R.string.publicacion_rechazado),
    SUSPENDIDO(R.string.publicacion_suspendido),
}

/** Tipo de producto: el empacado exige registro sanitario en su rótulo (Resolución 5109 de 2005). */
enum class TipoProducto(@param:StringRes val nombre: Int) {
    EMPACADO(R.string.tipo_empacado),
    PREPARADO(R.string.tipo_preparado),
}

/** Producto del catálogo. El precio se guarda en pesos colombianos, sin decimales. */
data class Producto(
    val id: Int,
    val nombre: String,
    val descripcion: String,
    val precio: Long,
    val existencias: Int,
    val categoria: Categoria,
    val tipo: TipoProducto,
    val vendedorId: Int,
    val vendedorNombre: String,
    val calificacion: Double,
    val resenas: Int,
    val estado: EstadoPublicacion,
    val ingredientes: String,
    val contenidoNeto: String,
    val registroSanitario: String,
    val destacado: Boolean,
    val motivoRechazo: String = "",
) {
    val agotado: Boolean get() = existencias <= 0
    val pocasUnidades: Boolean get() = existencias in 1..UMBRAL_EXISTENCIAS_BAJAS

    companion object {
        /** Umbral de alerta de existencias bajas (RF-PRO-06). */
        const val UMBRAL_EXISTENCIAS_BAJAS = 5
    }
}
