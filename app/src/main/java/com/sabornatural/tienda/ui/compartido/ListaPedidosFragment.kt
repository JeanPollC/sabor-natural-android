package com.sabornatural.tienda.ui.compartido

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import com.google.android.material.chip.Chip
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.RepositorioPedidos
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.datos.modelo.EstadoPedido
import com.sabornatural.tienda.datos.modelo.Pedido
import com.sabornatural.tienda.databinding.FragmentListaPedidosBinding
import com.sabornatural.tienda.ui.comprador.Argumentos
import com.sabornatural.tienda.ui.comun.AdaptadorPedidos
import com.sabornatural.tienda.ui.comun.FragmentoBase
import com.sabornatural.tienda.ui.comun.configurar
import com.sabornatural.tienda.ui.comun.navegar

/** Cómo se usa la lista de pedidos: qué pedidos carga y qué filtros ofrece. */
enum class ModoPedidos { COMPRADOR, VENDEDOR, ADMINISTRADOR }

/**
 * Lista de pedidos con filtros (B-07 comprador, V-05 vendedor). El modo llega como argumento
 * desde el grafo de navegación de cada rol (valor por defecto del <argument> "modo").
 */
class ListaPedidosFragment : FragmentoBase<FragmentListaPedidosBinding>(FragmentListaPedidosBinding::inflate) {

    /** Filtro de la lista: un nombre visible y una condición sobre el pedido. */
    private class Filtro(val nombre: Int, val cumple: (Pedido) -> Boolean)

    private lateinit var modo: ModoPedidos
    private lateinit var filtros: List<Filtro>
    private var filtroActual = 0
    private lateinit var adaptador: AdaptadorPedidos

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        modo = ModoPedidos.valueOf(arguments?.getString(Argumentos.MODO) ?: ModoPedidos.COMPRADOR.name)
        val esVendedor = modo == ModoPedidos.VENDEDOR
        binding.barra.configurar(
            this,
            getString(if (esVendedor) R.string.pedidos_titulo_vendedor else R.string.pedidos_titulo_comprador),
            principal = esVendedor, // para el vendedor es un destino de la barra inferior
        )

        filtros = if (esVendedor) {
            listOf(Filtro(R.string.pedidos_todos) { true }) +
                EstadoPedido.entries.map { estado -> Filtro(estado.nombre) { it.estado == estado } }
        } else {
            listOf(
                Filtro(R.string.pedidos_todos) { true },
                Filtro(R.string.pedidos_en_curso) { it.estado.enCurso },
                Filtro(R.string.pedidos_finalizados) { !it.estado.enCurso },
            )
        }
        // Si llega un estado (p. ej. desde el panel del vendedor: "Nuevos"), se preselecciona
        arguments?.getString(Argumentos.ESTADO)?.let { nombre ->
            val estado = EstadoPedido.valueOf(nombre)
            filtroActual = filtros.indexOfFirst { it.nombre == estado.nombre }.coerceAtLeast(0)
        }
        crearFichas()

        adaptador = AdaptadorPedidos(verComprador = esVendedor) { pedido ->
            navegar(R.id.detallePedidoFragment, bundleOf(Argumentos.PEDIDO_ID to pedido.id, Argumentos.MODO to modo.name))
        }
        binding.listaPedidos.adapter = adaptador
    }

    override fun onResume() {
        super.onResume()
        mostrar()
    }

    private fun crearFichas() {
        filtros.forEachIndexed { i, filtro ->
            binding.fichasEstado.addView(Chip(requireContext()).apply {
                id = View.generateViewId()
                setText(filtro.nombre)
                isCheckable = true
                isChecked = i == filtroActual
                tag = i
            })
        }
        binding.fichasEstado.setOnCheckedStateChangeListener { grupo, ids ->
            filtroActual = ids.firstOrNull()?.let { grupo.findViewById<Chip>(it).tag as Int } ?: 0
            mostrar()
        }
    }

    private fun mostrar() {
        val usuario = Sesion(requireContext()).usuarioActual ?: return
        val todos = if (modo == ModoPedidos.VENDEDOR) RepositorioPedidos.delVendedor(usuario.id) else RepositorioPedidos.delComprador(usuario.id)
        val lista = todos.filter(filtros[filtroActual].cumple)
        adaptador.submitList(lista)
        with(binding.estadoVacio) {
            root.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
            icono.setImageResource(R.drawable.ic_pedidos)
            titulo.setText(R.string.vacio_sin_pedidos)
            texto.setText(R.string.vacio_sin_pedidos_texto)
        }
    }
}
