package com.sabornatural.tienda.ui.comprador

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.Carrito
import com.sabornatural.tienda.datos.LineaCarrito
import com.sabornatural.tienda.datos.RepositorioPedidos
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.databinding.FragmentCarritoBinding
import com.sabornatural.tienda.databinding.IncludeTotalesBinding
import com.sabornatural.tienda.databinding.ItemLineaCarritoBinding
import com.sabornatural.tienda.ui.comun.Formatos
import com.sabornatural.tienda.ui.comun.FragmentoBase
import com.sabornatural.tienda.ui.comun.configurar
import com.sabornatural.tienda.ui.comun.confirmar
import com.sabornatural.tienda.ui.comun.mostrarImagenProducto
import com.sabornatural.tienda.ui.comun.navegar

/** Carrito de compras (mockup B-03): consultar, cambiar cantidades, quitar con deshacer y vaciar. */
class CarritoFragment : FragmentoBase<FragmentCarritoBinding>(FragmentCarritoBinding::inflate) {

    private val adaptador = AdaptadorCarrito(
        alCambiar = { linea, cantidad -> Carrito.cambiarCantidad(linea.producto.id, cantidad) },
        alQuitar = ::quitar,
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.barra.configurar(this, getString(R.string.carrito_titulo), principal = true)
        binding.barra.barraSuperior.inflateMenu(R.menu.menu_carrito)
        binding.barra.barraSuperior.setOnMenuItemClickListener {
            confirmar(getString(R.string.carrito_vaciar), getString(R.string.carrito_vaciar_confirmar), getString(R.string.carrito_vaciar)) {
                Carrito.vaciar()
            }
            true
        }
        binding.listaLineas.adapter = adaptador
        binding.botonContinuar.setOnClickListener { navegar(R.id.pasarelaFragment) }

        // Observa el carrito: cada cambio redibuja la lista, los totales y el estado vacío
        Carrito.lineas.observe(viewLifecycleOwner) { lineas -> mostrar(lineas) }
    }

    /** Dibuja las líneas, el total y el estado vacío (o el aviso para invitados). */
    private fun mostrar(lineas: List<LineaCarrito>) {
        val invitado = Sesion(requireContext()).usuarioActual == null
        val vacio = lineas.isEmpty()
        binding.desplazable.visibility = if (vacio) View.GONE else View.VISIBLE
        binding.barraAccion.visibility = if (vacio) View.GONE else View.VISIBLE
        binding.barra.barraSuperior.menu.findItem(R.id.accion_vaciar)?.isVisible = !vacio

        with(binding.estadoVacio) {
            root.visibility = if (vacio) View.VISIBLE else View.GONE
            icono.setImageResource(R.drawable.ic_carrito)
            titulo.setText(if (invitado) R.string.vacio_invitado else R.string.carrito_vacio)
            texto.setText(if (invitado) R.string.vacio_invitado_texto else R.string.carrito_vacio_texto)
            boton.visibility = View.VISIBLE
            boton.setText(if (invitado) R.string.vacio_invitado_boton else R.string.carrito_ir_catalogo)
            boton.setOnClickListener { if (invitado) pedirIngreso() else navegar(R.id.catalogoFragment) }
        }
        if (vacio) return

        adaptador.submitList(lineas)
        binding.avisoVendedor.text = getString(R.string.carrito_aviso_vendedor, Carrito.vendedorNombre)
        mostrarTotales(binding.totales, Carrito.subtotal, RepositorioPedidos.VALOR_DOMICILIO)
    }

    /** Quita la línea y ofrece deshacer durante unos segundos (RF-CAR-04). */
    private fun quitar(linea: LineaCarrito) {
        val quitada = Carrito.quitar(linea.producto.id) ?: return
        Snackbar.make(binding.root, R.string.carrito_producto_quitado, Snackbar.LENGTH_LONG)
            .setAnchorView(binding.barraAccion.takeIf { Carrito.unidades > 0 })
            .setAction(R.string.accion_deshacer) { Carrito.restaurar(quitada.first, quitada.second) }
            .show()
    }
}

/** Llena el resumen de totales (include_totales.xml); lo usan el carrito y la pasarela. */
fun mostrarTotales(totales: IncludeTotalesBinding, subtotal: Long, domicilio: Long) {
    totales.subtotal.text = Formatos.precio(subtotal)
    totales.domicilio.text = if (domicilio == 0L) {
        totales.root.context.getString(R.string.carrito_domicilio_gratis)
    } else {
        Formatos.precio(domicilio)
    }
    totales.total.text = Formatos.precio(subtotal + domicilio)
}

/** Adaptador de las líneas del carrito (item_linea_carrito.xml). */
class AdaptadorCarrito(
    private val alCambiar: (LineaCarrito, Int) -> Unit,
    private val alQuitar: (LineaCarrito) -> Unit,
) : ListAdapter<LineaCarrito, AdaptadorCarrito.Fila>(object : DiffUtil.ItemCallback<LineaCarrito>() {
    override fun areItemsTheSame(a: LineaCarrito, b: LineaCarrito) = a.producto.id == b.producto.id
    override fun areContentsTheSame(a: LineaCarrito, b: LineaCarrito) = a == b
}) {

    /** Vista de una línea del carrito. */
    class Fila(val binding: ItemLineaCarritoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(padre: ViewGroup, tipo: Int) =
        Fila(ItemLineaCarritoBinding.inflate(LayoutInflater.from(padre.context), padre, false))

    override fun onBindViewHolder(fila: Fila, posicion: Int) = with(fila.binding) {
        val linea = getItem(posicion)
        val p = linea.producto
        mostrarImagenProducto(marcoImagen, foto, icono, p.categoria, p.imagenUrl)
        nombre.text = p.nombre
        precioUnitario.text = root.context.getString(R.string.formato_pedido_resumen, Formatos.precio(p.precio), p.vendedorNombre)
        selector.cantidad.text = linea.cantidad.toString()
        selector.botonMenos.isEnabled = linea.cantidad > 1
        selector.botonMas.isEnabled = linea.cantidad < p.existencias
        selector.botonMenos.setOnClickListener { alCambiar(linea, linea.cantidad - 1) }
        selector.botonMas.setOnClickListener { alCambiar(linea, linea.cantidad + 1) }
        subtotal.text = Formatos.precio(linea.subtotal)
        botonQuitar.setOnClickListener { alQuitar(linea) }
    }
}
