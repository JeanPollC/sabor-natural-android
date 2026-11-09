package com.sabornatural.tienda.ui.comprador

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.RepositorioProductos
import com.sabornatural.tienda.datos.modelo.Producto
import com.sabornatural.tienda.datos.modelo.TipoProducto
import com.sabornatural.tienda.databinding.FragmentDetalleProductoBinding
import com.sabornatural.tienda.ui.comun.Formatos
import com.sabornatural.tienda.ui.comun.FragmentoBase
import com.sabornatural.tienda.ui.comun.iconosBarraDeEstadoOscuros
import com.sabornatural.tienda.ui.comun.Tono
import com.sabornatural.tienda.ui.comun.mostrarEtiqueta
import com.sabornatural.tienda.ui.comun.mostrarImagenProducto
import com.sabornatural.tienda.ui.comun.navegar
import com.sabornatural.tienda.ui.comun.rellenarBarraDeEstado
import com.sabornatural.tienda.ui.comun.rellenarBarraDeNavegacion

/** Detalle del producto (mockup B-02): datos, rótulo, cantidad y botón para agregar. */
class DetalleProductoFragment : FragmentoBase<FragmentDetalleProductoBinding>(FragmentDetalleProductoBinding::inflate) {

    private var cantidad = 1

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.barraFlotante.rellenarBarraDeEstado()
        binding.barraAccion.rellenarBarraDeNavegacion()

        val producto = RepositorioProductos.buscarPorId(requireArguments().getInt(Argumentos.PRODUCTO_ID))
        if (producto == null) {
            findNavController().navigateUp()
            return
        }
        mostrar(producto)

        binding.botonVolver.setOnClickListener { findNavController().navigateUp() }
        binding.botonCarrito.setOnClickListener { navegar(R.id.carritoFragment) }
        binding.selector.botonMenos.setOnClickListener { cambiarCantidad(producto, cantidad - 1) }
        binding.selector.botonMas.setOnClickListener { cambiarCantidad(producto, cantidad + 1) }
        binding.botonAgregar.setOnClickListener { agregarAlCarrito(producto, cantidad) }
        cambiarCantidad(producto, 1)
    }

    private fun mostrar(p: Producto) = with(binding) {
        mostrarImagenProducto(marcoImagen, foto, icono, p.categoria, p.id)
        // La imagen grande no lleva esquinas: el color se aplica directo al fondo
        marcoImagen.setBackgroundColor(ContextCompat.getColor(requireContext(), p.categoria.fondo))

        categoria.mostrarEtiqueta(getString(p.categoria.nombre), Tono.INFO)
        nombre.text = p.nombre
        vendedor.text = getString(R.string.formato_pedido_resumen, p.vendedorNombre, getString(R.string.detalle_productor_verificado))
        calificacion.text = if (p.resenas > 0) {
            getString(R.string.formato_pedido_resumen, Formatos.calificacion(p.calificacion), getString(R.string.detalle_resenas, p.resenas))
        } else {
            getString(R.string.detalle_sin_resenas)
        }
        precio.text = Formatos.precio(p.precio)
        when {
            p.agotado -> existencias.mostrarEtiqueta(getString(R.string.formato_agotado), Tono.ERROR)
            p.pocasUnidades -> existencias.mostrarEtiqueta(getString(R.string.formato_pocas_unidades, p.existencias), Tono.PENDIENTE)
            else -> existencias.mostrarEtiqueta(getString(R.string.formato_disponibles, p.existencias), Tono.EXITO)
        }
        descripcion.text = p.descripcion
        ingredientes.text = p.ingredientes
        contenidoNeto.text = p.contenidoNeto
        registroSanitario.text = p.registroSanitario.ifBlank {
            getString(if (p.tipo == TipoProducto.PREPARADO) R.string.detalle_no_aplica else R.string.detalle_sin_registro)
        }
        tipo.setText(p.tipo.nombre)
        botonAgregar.isEnabled = !p.agotado
        selector.root.visibility = if (p.agotado) View.INVISIBLE else View.VISIBLE
    }

    // Sobre la imagen clara del producto, los íconos de la barra de estado van oscuros
    override fun onResume() {
        super.onResume()
        iconosBarraDeEstadoOscuros(true)
    }

    override fun onPause() {
        iconosBarraDeEstadoOscuros(false)
        super.onPause()
    }

    /** Cantidad entre 1 y las existencias; el botón muestra el total que se agregará. */
    private fun cambiarCantidad(producto: Producto, nueva: Int) {
        cantidad = nueva.coerceIn(1, producto.existencias.coerceAtLeast(1))
        binding.selector.cantidad.text = cantidad.toString()
        binding.selector.botonMenos.isEnabled = cantidad > 1
        binding.selector.botonMas.isEnabled = cantidad < producto.existencias
        binding.botonAgregar.text = if (producto.agotado) {
            getString(R.string.formato_agotado)
        } else {
            getString(R.string.detalle_agregar, Formatos.precio(producto.precio * cantidad))
        }
    }
}
