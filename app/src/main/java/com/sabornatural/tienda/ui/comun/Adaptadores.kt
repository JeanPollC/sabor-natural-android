package com.sabornatural.tienda.ui.comun

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.modelo.Categoria
import com.sabornatural.tienda.datos.modelo.Pedido
import com.sabornatural.tienda.datos.modelo.Producto
import com.sabornatural.tienda.databinding.ItemCategoriaBinding
import com.sabornatural.tienda.databinding.ItemPedidoBinding
import com.sabornatural.tienda.databinding.ItemProductoBinding

/*
 * Adaptadores de RecyclerView. Un adaptador convierte cada elemento de una lista (producto,
 * categoría, pedido) en una vista inflada desde su layout de ítem (res/layout/item_*.xml).
 * ListAdapter + DiffUtil calcula qué filas cambiaron y solo redibuja esas.
 */

/**
 * Compara elementos por su id para saber si son "el mismo" y por contenido para saber si cambió.
 * Solo se usa con data class (Producto, Pedido), que generan equals() comparando sus campos;
 * por eso se silencia el aviso de lint, que no puede saberlo en una función genérica.
 */
@SuppressLint("DiffUtilEquals")
private fun <T : Any> diferencias(id: (T) -> Any) = object : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(a: T, b: T) = id(a) == id(b)
    override fun areContentsTheSame(a: T, b: T) = a == b
}

/**
 * Tarjetas de producto (item_producto.xml).
 * @param anchoFijo true para la fila horizontal de destacados; false para la cuadrícula.
 */
class AdaptadorProductos(
    private val anchoFijo: Boolean = false,
    private val alTocar: (Producto) -> Unit,
    private val alAgregar: (Producto) -> Unit,
) : ListAdapter<Producto, AdaptadorProductos.Fila>(diferencias { it.id }) {

    class Fila(val binding: ItemProductoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(padre: ViewGroup, tipo: Int): Fila {
        val binding = ItemProductoBinding.inflate(LayoutInflater.from(padre.context), padre, false)
        if (anchoFijo) {
            binding.root.layoutParams.width = padre.resources.getDimensionPixelSize(R.dimen.ancho_tarjeta_horizontal)
        }
        return Fila(binding)
    }

    override fun onBindViewHolder(fila: Fila, posicion: Int) = with(fila.binding) {
        val producto = getItem(posicion)
        val contexto = root.context
        mostrarImagenProducto(marcoImagen, foto, icono, producto.categoria, producto.id)
        nombre.text = producto.nombre
        vendedor.text = producto.vendedorNombre
        precio.text = Formatos.precio(producto.precio)
        calificacion.text = contexto.getString(
            R.string.formato_calificacion, Formatos.calificacion(producto.calificacion), producto.resenas,
        )
        calificacion.visibility = if (producto.resenas > 0) View.VISIBLE else View.INVISIBLE

        // Etiqueta sobre la imagen: agotado o pocas unidades (RF-PRO-06)
        when {
            producto.agotado -> etiquetaExistencias.mostrarEtiqueta(contexto.getString(R.string.formato_agotado), Tono.ERROR)
            producto.pocasUnidades -> etiquetaExistencias.mostrarEtiqueta(
                contexto.getString(R.string.formato_pocas_unidades, producto.existencias), Tono.PENDIENTE,
            )
        }
        etiquetaExistencias.visibility = if (producto.agotado || producto.pocasUnidades) View.VISIBLE else View.GONE

        botonAgregar.isEnabled = !producto.agotado
        botonAgregar.setOnClickListener { alAgregar(producto) }
        root.setOnClickListener { alTocar(producto) }
    }
}

/** Círculos de categoría del inicio (item_categoria.xml). */
class AdaptadorCategorias(
    private val alTocar: (Categoria) -> Unit,
) : RecyclerView.Adapter<AdaptadorCategorias.Fila>() {

    private val categorias = Categoria.entries

    class Fila(val binding: ItemCategoriaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(padre: ViewGroup, tipo: Int) =
        Fila(ItemCategoriaBinding.inflate(LayoutInflater.from(padre.context), padre, false))

    override fun getItemCount() = categorias.size

    override fun onBindViewHolder(fila: Fila, posicion: Int) = with(fila.binding) {
        val categoria = categorias[posicion]
        val contexto = root.context
        circulo.backgroundTintList = ContextCompat.getColorStateList(contexto, categoria.fondo)
        icono.setImageResource(categoria.icono)
        icono.imageTintList = ContextCompat.getColorStateList(contexto, categoria.color)
        nombre.setText(categoria.nombre)
        root.setOnClickListener { alTocar(categoria) }
    }
}

/**
 * Pedidos (item_pedido.xml).
 * @param verComprador true para el vendedor y el administrador (muestra quién compró);
 *                     false para el comprador (muestra a qué productor le compró).
 */
class AdaptadorPedidos(
    private val verComprador: Boolean,
    private val alTocar: (Pedido) -> Unit,
) : ListAdapter<Pedido, AdaptadorPedidos.Fila>(diferencias { it.id }) {

    class Fila(val binding: ItemPedidoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(padre: ViewGroup, tipo: Int) =
        Fila(ItemPedidoBinding.inflate(LayoutInflater.from(padre.context), padre, false))

    override fun onBindViewHolder(fila: Fila, posicion: Int) = with(fila.binding) {
        val pedido = getItem(posicion)
        val contexto = root.context
        codigo.text = pedido.codigo
        estado.mostrarEtiqueta(contexto.getString(pedido.estado.nombre), pedido.estado.tono)
        contraparte.text = if (verComprador) pedido.compradorNombre else pedido.vendedorNombre
        detalle.text = contexto.getString(
            R.string.formato_pedido_resumen,
            Formatos.fechaHora(pedido.fecha),
            contexto.resources.getQuantityString(R.plurals.formato_productos, pedido.unidades, pedido.unidades),
        )
        total.text = Formatos.precio(pedido.total)
        root.setOnClickListener { alTocar(pedido) }
    }
}
