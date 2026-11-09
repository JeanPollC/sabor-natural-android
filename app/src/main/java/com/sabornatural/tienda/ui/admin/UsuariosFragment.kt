package com.sabornatural.tienda.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.RepositorioUsuarios
import com.sabornatural.tienda.datos.modelo.EstadoCuenta
import com.sabornatural.tienda.datos.modelo.Rol
import com.sabornatural.tienda.datos.modelo.Usuario
import com.sabornatural.tienda.databinding.FragmentUsuariosBinding
import com.sabornatural.tienda.databinding.ItemUsuarioBinding
import com.sabornatural.tienda.ui.comprador.Argumentos
import com.sabornatural.tienda.ui.comun.FragmentoBase
import com.sabornatural.tienda.ui.comun.Tono
import com.sabornatural.tienda.ui.comun.configurar
import com.sabornatural.tienda.ui.comun.mostrarEtiqueta
import com.sabornatural.tienda.ui.comun.navegar
import com.sabornatural.tienda.ui.comun.tono

/** Administración de usuarios (mockup A-02): buscar, filtrar por rol o estado, crear y editar. */
class UsuariosFragment : FragmentoBase<FragmentUsuariosBinding>(FragmentUsuariosBinding::inflate) {

    private class Filtro(val nombre: Int, val cumple: (Usuario) -> Boolean)

    private val filtros = listOf(
        Filtro(R.string.pedidos_todos) { true },
        Filtro(R.string.usuarios_filtro_compradores) { it.rol == Rol.COMPRADOR },
        Filtro(R.string.usuarios_filtro_vendedores) { it.rol == Rol.VENDEDOR },
        Filtro(R.string.usuarios_filtro_admin) { it.rol == Rol.ADMINISTRADOR },
        Filtro(R.string.usuarios_filtro_pendientes) { it.estado == EstadoCuenta.PENDIENTE },
    )
    private var filtroActual = 0
    private val adaptador = AdaptadorUsuarios { navegar(R.id.formularioUsuarioFragment, bundleOf(Argumentos.USUARIO_ID to it.id)) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.barra.configurar(this, getString(R.string.usuarios_titulo), principal = true)
        // Desde el panel ("solicitudes de vendedor") se abre con el filtro de pendientes
        if (arguments?.getString(Argumentos.ESTADO) == EstadoCuenta.PENDIENTE.name) filtroActual = filtros.lastIndex

        filtros.forEachIndexed { i, filtro ->
            binding.fichasFiltro.addView(Chip(requireContext()).apply {
                id = View.generateViewId()
                setText(filtro.nombre)
                isCheckable = true
                isChecked = i == filtroActual
                tag = i
            })
        }
        binding.fichasFiltro.setOnCheckedStateChangeListener { grupo, ids ->
            filtroActual = ids.firstOrNull()?.let { grupo.findViewById<Chip>(it).tag as Int } ?: 0
            mostrar()
        }
        binding.textoBuscar.doAfterTextChanged { mostrar() }
        binding.listaUsuarios.adapter = adaptador
        binding.botonNuevo.setOnClickListener { navegar(R.id.formularioUsuarioFragment) }
    }

    override fun onResume() {
        super.onResume()
        mostrar()
    }

    private fun mostrar() {
        val consulta = binding.textoBuscar.text?.toString()?.trim()?.lowercase().orEmpty()
        val lista = RepositorioUsuarios.todos()
            .filter(filtros[filtroActual].cumple)
            .filter { consulta.isEmpty() || it.nombreCompleto.lowercase().contains(consulta) || it.correo.contains(consulta) }
            .sortedBy { it.nombre }
        adaptador.submitList(lista)
        with(binding.estadoVacio) {
            root.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
            icono.setImageResource(R.drawable.ic_usuarios)
            titulo.setText(R.string.usuarios_vacio)
            texto.visibility = View.GONE
        }
    }
}

/** Adaptador de item_usuario.xml. El círculo del avatar toma un color distinto por rol. */
class AdaptadorUsuarios(private val alTocar: (Usuario) -> Unit) :
    ListAdapter<Usuario, AdaptadorUsuarios.Fila>(object : DiffUtil.ItemCallback<Usuario>() {
        override fun areItemsTheSame(a: Usuario, b: Usuario) = a.id == b.id
        override fun areContentsTheSame(a: Usuario, b: Usuario) = a == b
    }) {

    class Fila(val binding: ItemUsuarioBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(padre: ViewGroup, tipo: Int) =
        Fila(ItemUsuarioBinding.inflate(LayoutInflater.from(padre.context), padre, false))

    override fun onBindViewHolder(fila: Fila, posicion: Int) = with(fila.binding) {
        val u = getItem(posicion)
        val contexto = root.context
        val (fondo, texto) = when (u.rol) {
            Rol.COMPRADOR -> R.color.verde_claro to R.color.verde_bosque
            Rol.VENDEDOR -> R.color.dorado_claro to R.color.categoria_granolas
            Rol.ADMINISTRADOR -> R.color.categoria_bebidas_fondo to R.color.categoria_bebidas
        }
        avatar.text = u.iniciales
        avatar.backgroundTintList = ContextCompat.getColorStateList(contexto, fondo)
        avatar.setTextColor(ContextCompat.getColor(contexto, texto))
        nombre.text = u.nombreCompleto
        correo.text = u.correo
        rol.mostrarEtiqueta(contexto.getString(u.rol.nombre), Tono.NEUTRO)
        estado.mostrarEtiqueta(contexto.getString(u.estado.nombre), u.estado.tono)
        root.setOnClickListener { alTocar(u) }
    }
}
