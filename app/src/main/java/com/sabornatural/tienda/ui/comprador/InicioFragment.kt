package com.sabornatural.tienda.ui.comprador

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.RepositorioProductos
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.databinding.FragmentInicioBinding
import com.sabornatural.tienda.ui.comun.AdaptadorCategorias
import com.sabornatural.tienda.ui.comun.AdaptadorProductos
import com.sabornatural.tienda.ui.comun.ConMenuLateral
import com.sabornatural.tienda.ui.comun.FragmentoBase
import com.sabornatural.tienda.ui.comun.igualarAltoBarraDeEstado
import com.sabornatural.tienda.ui.comun.navegar
import com.sabornatural.tienda.ui.comun.rellenarBarraDeEstado

/** Inicio del comprador (mockup B-01): saludo, buscador, categorías, destacados y recomendados. */
class InicioFragment : FragmentoBase<FragmentInicioBinding>(FragmentInicioBinding::inflate) {

    private lateinit var destacados: AdaptadorProductos
    private lateinit var recomendados: AdaptadorProductos

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.encabezado.rellenarBarraDeEstado()
        binding.franjaBarraEstado.igualarAltoBarraDeEstado()

        val usuario = Sesion(requireContext()).usuarioActual
        binding.saludo.text = usuario?.let { getString(R.string.inicio_saludo, it.nombre) }
            ?: getString(R.string.inicio_saludo_invitado)
        binding.direccion.text = getString(R.string.inicio_entregar_en, getString(R.string.perfil_direccion_predeterminada))

        binding.botonMenu.setOnClickListener { (activity as? ConMenuLateral)?.abrirMenuLateral() }
        binding.buscador.setOnClickListener {
            navegar(R.id.catalogoFragment, bundleOf(Argumentos.ENFOCAR_BUSQUEDA to true))
        }
        binding.botonVerTodo.setOnClickListener { navegar(R.id.catalogoFragment) }

        // Categorías: al tocar una se abre el catálogo filtrado
        binding.listaCategorias.adapter = AdaptadorCategorias { categoria ->
            navegar(R.id.catalogoFragment, bundleOf(Argumentos.CATEGORIA to categoria.name))
        }

        val abrirDetalle = { id: Int -> navegar(R.id.detalleProductoFragment, bundleOf(Argumentos.PRODUCTO_ID to id)) }
        destacados = AdaptadorProductos(anchoFijo = true, alTocar = { abrirDetalle(it.id) }, alAgregar = { agregarAlCarrito(it) })
        recomendados = AdaptadorProductos(alTocar = { abrirDetalle(it.id) }, alAgregar = { agregarAlCarrito(it) })
        binding.listaDestacados.adapter = destacados
        binding.listaRecomendados.adapter = recomendados
    }

    override fun onResume() {
        super.onResume()
        // Se recarga al volver, por si cambiaron existencias o el vendedor publicó algo
        destacados.submitList(RepositorioProductos.destacados())
        recomendados.submitList(RepositorioProductos.publicados().filterNot { it.destacado }.sortedByDescending { it.calificacion })
    }
}
