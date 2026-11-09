package com.sabornatural.tienda.ui.comprador

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.chip.Chip
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.RepositorioProductos
import com.sabornatural.tienda.datos.modelo.Categoria
import com.sabornatural.tienda.databinding.FragmentCatalogoBinding
import com.sabornatural.tienda.ui.comun.AdaptadorProductos
import com.sabornatural.tienda.ui.comun.FragmentoBase
import com.sabornatural.tienda.ui.comun.configurar
import com.sabornatural.tienda.ui.comun.navegar

/** Catálogo con búsqueda por texto y filtro por categoría (RF-INI-03). */
class CatalogoFragment : FragmentoBase<FragmentCatalogoBinding>(FragmentCatalogoBinding::inflate) {

    private lateinit var adaptador: AdaptadorProductos
    private var categoria: Categoria? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.barra.configurar(this, getString(R.string.catalogo_titulo), principal = true)

        categoria = arguments?.getString(Argumentos.CATEGORIA)?.let { Categoria.valueOf(it) }
        crearFichas()

        adaptador = AdaptadorProductos(
            alTocar = { navegar(R.id.detalleProductoFragment, bundleOf(Argumentos.PRODUCTO_ID to it.id)) },
            alAgregar = { agregarAlCarrito(it) },
        )
        binding.listaProductos.adapter = adaptador

        // Se filtra mientras el usuario escribe
        binding.textoBuscar.doAfterTextChanged { filtrar() }

        // Desde el buscador del inicio: el campo llega enfocado y con el teclado abierto
        if (arguments?.getBoolean(Argumentos.ENFOCAR_BUSQUEDA) == true && savedInstanceState == null) {
            binding.textoBuscar.requestFocus()
            binding.textoBuscar.post {
                requireContext().getSystemService<InputMethodManager>()
                    ?.showSoftInput(binding.textoBuscar, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        filtrar()
    }

    /** Una ficha "Todos" y una por categoría; la selección única la controla el ChipGroup. */
    private fun crearFichas() {
        val grupo = binding.fichasCategorias
        val opciones = listOf<Categoria?>(null) + Categoria.entries
        opciones.forEach { opcion ->
            val ficha = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = getString(opcion?.nombre ?: R.string.categoria_todos)
                isCheckable = true
                isChecked = opcion == categoria
                tag = opcion
            }
            grupo.addView(ficha)
        }
        grupo.setOnCheckedStateChangeListener { g, ids ->
            categoria = ids.firstOrNull()?.let { g.findViewById<Chip>(it).tag as? Categoria }
            filtrar()
        }
    }

    private fun filtrar() {
        val lista = RepositorioProductos.buscar(binding.textoBuscar.text?.toString().orEmpty(), categoria)
        adaptador.submitList(lista)
        binding.resultados.text = resources.getQuantityString(R.plurals.formato_resultados, lista.size, lista.size)
        with(binding.estadoVacio) {
            root.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
            icono.setImageResource(R.drawable.ic_buscar)
            titulo.setText(R.string.vacio_sin_resultados)
            texto.setText(R.string.vacio_sin_resultados_texto)
        }
    }
}
