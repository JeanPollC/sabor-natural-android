package com.sabornatural.tienda.ui.vendedor

import android.os.Bundle
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.os.bundleOf
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.RepositorioPedidos
import com.sabornatural.tienda.datos.RepositorioProductos
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.datos.modelo.EstadoPedido
import com.sabornatural.tienda.datos.modelo.EstadoPublicacion
import com.sabornatural.tienda.databinding.FragmentPanelVendedorBinding
import com.sabornatural.tienda.databinding.IncludeIndicadorBinding
import com.sabornatural.tienda.ui.compartido.AdaptadorGestionProductos
import com.sabornatural.tienda.ui.compartido.ModoPedidos
import com.sabornatural.tienda.ui.comprador.Argumentos
import com.sabornatural.tienda.ui.comun.AdaptadorPedidos
import com.sabornatural.tienda.ui.comun.ConMenuLateral
import com.sabornatural.tienda.ui.comun.Formatos
import com.sabornatural.tienda.ui.comun.FragmentoBase
import com.sabornatural.tienda.ui.comun.igualarAltoBarraDeEstado
import com.sabornatural.tienda.ui.comun.navegar
import com.sabornatural.tienda.ui.comun.rellenarBarraDeEstado
import kotlinx.coroutines.flow.merge
import java.time.LocalDate

/** Llena una tarjeta de indicador (include_indicador.xml). */
fun IncludeIndicadorBinding.mostrar(@DrawableRes icono: Int, valor: String, etiqueta: String) {
    this.icono.setImageResource(icono)
    this.valor.text = valor
    this.etiqueta.text = etiqueta
}

/**
 * Panel del vendedor (mockup V-01): indicadores, pedidos por atender y publicaciones con novedades.
 * Se recalcula solo cuando cambian sus pedidos o productos en Firestore (por ejemplo, una compra nueva).
 */
class PanelVendedorFragment : FragmentoBase<FragmentPanelVendedorBinding>(FragmentPanelVendedorBinding::inflate) {

    private lateinit var pedidos: AdaptadorPedidos
    private lateinit var publicaciones: AdaptadorGestionProductos

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.encabezado.root.rellenarBarraDeEstado()
        binding.franjaBarraEstado.igualarAltoBarraDeEstado()
        binding.encabezado.botonMenu.setOnClickListener { (activity as? ConMenuLateral)?.abrirMenuLateral() }
        binding.encabezado.subtitulo.setText(R.string.panel_vendedor_subtitulo)

        pedidos = AdaptadorPedidos(verComprador = true) {
            navegar(R.id.detallePedidoFragment, bundleOf(Argumentos.PEDIDO_ID to it.id, Argumentos.MODO to ModoPedidos.VENDEDOR.name))
        }
        publicaciones = AdaptadorGestionProductos(
            esVendedor = true,
            alTocar = { navegar(R.id.formularioProductoFragment, bundleOf(Argumentos.PRODUCTO_ID to it.id)) },
            alOpciones = { _, p -> navegar(R.id.formularioProductoFragment, bundleOf(Argumentos.PRODUCTO_ID to p.id)) },
        )
        binding.listaPedidos.adapter = pedidos
        binding.listaPublicaciones.adapter = publicaciones
        binding.botonVerPedidos.setOnClickListener { navegar(R.id.pedidosVendedorFragment) }
        observar(merge(RepositorioPedidos.cambios, RepositorioProductos.cambios)) { mostrar() }
    }

    /** Calcula indicadores y listas con los datos en vivo. */
    private fun mostrar() {
        val vendedor = Sesion(requireContext()).usuarioActual ?: return
        binding.encabezado.saludo.text = getString(R.string.inicio_saludo, vendedor.nombre)
        binding.encabezado.avatar.text = vendedor.iniciales

        val misPedidos = RepositorioPedidos.delVendedor(vendedor.id)
        val misProductos = RepositorioProductos.delVendedor(vendedor.id)
        val mes = LocalDate.now().toString().substring(0, 7) // "2026-11"
        val ventasMes = misPedidos.filter { it.fecha.startsWith(mes) && it.cuentaComoVenta }.sumOf { it.total }

        binding.indicador1.mostrar(R.drawable.ic_dinero, Formatos.precio(ventasMes), getString(R.string.indicador_ventas_mes))
        binding.indicador2.mostrar(R.drawable.ic_pedidos, misPedidos.count { it.estado == EstadoPedido.CONFIRMADO }.toString(), getString(R.string.indicador_pedidos_nuevos))
        binding.indicador3.mostrar(R.drawable.ic_inventario, misProductos.count { it.estado == EstadoPublicacion.PUBLICADO }.toString(), getString(R.string.indicador_publicados))
        binding.indicador4.mostrar(R.drawable.ic_alerta, misProductos.count { it.agotado || it.pocasUnidades }.toString(), getString(R.string.indicador_existencias_bajas))

        val porAtender = misPedidos.filter { it.estado.enCurso }.take(3)
        pedidos.submitList(porAtender)
        binding.sinPedidos.visibility = if (porAtender.isEmpty()) View.VISIBLE else View.GONE

        val conNovedades = misProductos.filter { it.estado == EstadoPublicacion.EN_REVISION || it.estado == EstadoPublicacion.RECHAZADO }
        publicaciones.submitList(conNovedades)
        binding.sinPublicaciones.visibility = if (conNovedades.isEmpty()) View.VISIBLE else View.GONE
    }
}
