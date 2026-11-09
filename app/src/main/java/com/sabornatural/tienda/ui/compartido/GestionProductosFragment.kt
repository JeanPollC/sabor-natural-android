package com.sabornatural.tienda.ui.compartido

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.RepositorioProductos
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.datos.modelo.EstadoPublicacion
import com.sabornatural.tienda.datos.modelo.Producto
import com.sabornatural.tienda.datos.modelo.TipoProducto
import com.sabornatural.tienda.databinding.DialogoMotivoBinding
import com.sabornatural.tienda.databinding.FragmentProductosGestionBinding
import com.sabornatural.tienda.databinding.HojaModeracionBinding
import com.sabornatural.tienda.databinding.ItemProductoGestionBinding
import com.sabornatural.tienda.ui.comprador.Argumentos
import com.sabornatural.tienda.ui.comun.Formatos
import com.sabornatural.tienda.ui.comun.FotosProductos
import com.sabornatural.tienda.ui.comun.FragmentoBase
import com.sabornatural.tienda.ui.comun.Tono
import com.sabornatural.tienda.ui.comun.configurar
import com.sabornatural.tienda.ui.comun.confirmar
import com.sabornatural.tienda.ui.comun.mostrarEtiqueta
import com.sabornatural.tienda.ui.comun.mostrarImagenProducto
import com.sabornatural.tienda.ui.comun.navegar
import com.sabornatural.tienda.ui.comun.texto
import com.sabornatural.tienda.ui.comun.tono

/** Quién usa la gestión de productos. */
enum class ModoProductos { VENDEDOR, ADMINISTRADOR }

/** Rótulo completo: ingredientes y contenido; el empacado además exige registro sanitario (RF-ADP-03). */
val Producto.rotuloCompleto: Boolean
    get() = ingredientes.isNotBlank() && contenidoNeto.isNotBlank() &&
        (tipo == TipoProducto.PREPARADO || registroSanitario.isNotBlank())

/**
 * Gestión de productos.
 *  - Vendedor (V-02): CRUD de sus productos: crear (botón flotante), consultar con filtros,
 *    editar, pausar o reactivar y eliminar.
 *  - Administrador (A-05): moderación de las publicaciones de todos: aprobar, rechazar con
 *    motivo o suspender.
 */
class GestionProductosFragment : FragmentoBase<FragmentProductosGestionBinding>(FragmentProductosGestionBinding::inflate) {

    private class Filtro(val nombre: Int, val cumple: (Producto) -> Boolean)

    private lateinit var modo: ModoProductos
    private lateinit var filtros: List<Filtro>
    private var filtroActual = 0
    private lateinit var adaptador: AdaptadorGestionProductos

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        modo = ModoProductos.valueOf(arguments?.getString(Argumentos.MODO) ?: ModoProductos.VENDEDOR.name)
        val esVendedor = modo == ModoProductos.VENDEDOR
        binding.barra.configurar(
            this,
            getString(if (esVendedor) R.string.productos_titulo_vendedor else R.string.productos_titulo_admin),
            principal = true,
        )

        val porEstado = { estado: EstadoPublicacion -> { p: Producto -> p.estado == estado } }
        filtros = if (esVendedor) {
            listOf(
                Filtro(R.string.pedidos_todos) { true },
                Filtro(R.string.publicacion_publicado, porEstado(EstadoPublicacion.PUBLICADO)),
                Filtro(R.string.publicacion_revision, porEstado(EstadoPublicacion.EN_REVISION)),
                Filtro(R.string.publicacion_rechazado, porEstado(EstadoPublicacion.RECHAZADO)),
                Filtro(R.string.productos_filtro_pausados, porEstado(EstadoPublicacion.SUSPENDIDO)),
                Filtro(R.string.productos_filtro_agotados) { it.agotado },
            )
        } else {
            // El administrador empieza por la cola de moderación
            listOf(
                Filtro(R.string.publicacion_revision, porEstado(EstadoPublicacion.EN_REVISION)),
                Filtro(R.string.publicacion_publicado, porEstado(EstadoPublicacion.PUBLICADO)),
                Filtro(R.string.publicacion_rechazado, porEstado(EstadoPublicacion.RECHAZADO)),
                Filtro(R.string.productos_filtro_pausados, porEstado(EstadoPublicacion.SUSPENDIDO)),
                Filtro(R.string.pedidos_todos) { true },
            )
        }
        crearFichas()

        adaptador = AdaptadorGestionProductos(
            esVendedor = esVendedor,
            alTocar = { if (esVendedor) editar(it) else moderar(it) },
            alOpciones = { ancla, producto -> if (esVendedor) opcionesVendedor(ancla, producto) else moderar(producto) },
        )
        binding.listaProductos.adapter = adaptador

        binding.botonNuevo.visibility = if (esVendedor) View.VISIBLE else View.GONE
        binding.botonNuevo.setText(R.string.productos_nuevo)
        binding.botonNuevo.extend() // muestra ícono y texto ("Nuevo producto")
        binding.botonNuevo.setOnClickListener { navegar(R.id.formularioProductoFragment) }
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
        val base = if (modo == ModoProductos.VENDEDOR) {
            RepositorioProductos.delVendedor(Sesion(requireContext()).usuarioActual?.id ?: -1)
        } else {
            RepositorioProductos.todos()
        }
        val lista = base.filter(filtros[filtroActual].cumple).sortedBy { it.nombre }
        adaptador.submitList(lista)
        with(binding.estadoVacio) {
            root.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
            icono.setImageResource(R.drawable.ic_inventario)
            titulo.setText(R.string.productos_vacio)
            texto.setText(R.string.productos_vacio_texto)
            texto.visibility = if (modo == ModoProductos.VENDEDOR) View.VISIBLE else View.GONE
        }
    }

    private fun editar(producto: Producto) =
        navegar(R.id.formularioProductoFragment, bundleOf(Argumentos.PRODUCTO_ID to producto.id))

    /** Menú contextual del vendedor: editar, pausar o reactivar, y eliminar. */
    private fun opcionesVendedor(ancla: View, producto: Producto) {
        val menu = PopupMenu(requireContext(), ancla)
        menu.menu.add(0, 1, 0, R.string.accion_editar)
        if (producto.estado == EstadoPublicacion.PUBLICADO) menu.menu.add(0, 2, 1, R.string.producto_pausar)
        if (producto.estado == EstadoPublicacion.SUSPENDIDO) menu.menu.add(0, 3, 1, R.string.producto_reactivar)
        menu.menu.add(0, 4, 2, R.string.accion_eliminar)
        menu.setOnMenuItemClickListener { opcion ->
            when (opcion.itemId) {
                1 -> editar(producto)
                2 -> cambiarEstado(producto, EstadoPublicacion.SUSPENDIDO, R.string.producto_pausado)
                3 -> cambiarEstado(producto, EstadoPublicacion.PUBLICADO, R.string.producto_reactivado)
                4 -> confirmar(getString(R.string.accion_eliminar), getString(R.string.producto_eliminar_confirmar, producto.nombre), getString(R.string.accion_eliminar)) {
                    RepositorioProductos.eliminar(producto.id)
                    FotosProductos[producto.id] = null
                    avisar(R.string.producto_eliminado)
                    mostrar()
                }
            }
            true
        }
        menu.show()
    }

    /** Hoja de moderación del administrador (A-05). */
    private fun moderar(producto: Producto) {
        val hoja = BottomSheetDialog(requireContext())
        val vista = HojaModeracionBinding.inflate(layoutInflater)
        with(vista) {
            mostrarImagenProducto(marcoImagen, foto, icono, producto.categoria, producto.id)
            nombre.text = producto.nombre
            resumen.text = getString(R.string.producto_resumen_admin, producto.vendedorNombre, Formatos.precio(producto.precio))
            val completo = producto.rotuloCompleto
            etiquetaRotulo.mostrarEtiqueta(
                getString(if (completo) R.string.rotulo_completo else R.string.rotulo_incompleto),
                if (completo) Tono.EXITO else Tono.ERROR,
            )
            ingredientes.text = producto.ingredientes.ifBlank { "—" }
            contenido.text = producto.contenidoNeto.ifBlank { "—" }
            registro.text = producto.registroSanitario.ifBlank {
                getString(if (producto.tipo == TipoProducto.PREPARADO) R.string.detalle_no_aplica else R.string.detalle_sin_registro)
            }

            // Aprobar solo con el rótulo completo; cada acción aparece si tiene sentido en el estado actual
            avisoBloqueo.visibility = if (completo) View.GONE else View.VISIBLE
            botonAprobar.isEnabled = completo
            botonAprobar.visibility = if (producto.estado != EstadoPublicacion.PUBLICADO) View.VISIBLE else View.GONE
            botonRechazar.visibility = if (producto.estado == EstadoPublicacion.EN_REVISION) View.VISIBLE else View.GONE
            botonSuspender.visibility = if (producto.estado == EstadoPublicacion.PUBLICADO) View.VISIBLE else View.GONE

            botonAprobar.setOnClickListener {
                hoja.dismiss()
                cambiarEstado(producto, EstadoPublicacion.PUBLICADO, R.string.moderacion_aprobado)
            }
            botonSuspender.setOnClickListener {
                hoja.dismiss()
                cambiarEstado(producto, EstadoPublicacion.SUSPENDIDO, R.string.moderacion_suspendido)
            }
            botonRechazar.setOnClickListener {
                hoja.dismiss()
                pedirMotivo(producto)
            }
        }
        hoja.setContentView(vista.root)
        hoja.show()
    }

    /** El rechazo exige un motivo, que el vendedor verá en su lista (RF-ADP-02). */
    private fun pedirMotivo(producto: Producto) {
        val campo = DialogoMotivoBinding.inflate(layoutInflater)
        val dialogo = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.accion_rechazar)
            .setView(campo.root)
            .setPositiveButton(R.string.accion_rechazar, null)
            .setNegativeButton(R.string.accion_cancelar, null)
            .show()
        // Se reemplaza el clic para que el diálogo no se cierre si el motivo está vacío
        dialogo.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val motivo = campo.campoMotivo.texto
            if (motivo.length < 5) {
                campo.campoMotivo.error = getString(R.string.error_campo_obligatorio)
                return@setOnClickListener
            }
            dialogo.dismiss()
            RepositorioProductos.guardar(producto.copy(estado = EstadoPublicacion.RECHAZADO, motivoRechazo = motivo))
            avisar(R.string.moderacion_rechazado)
            mostrar()
        }
    }

    private fun cambiarEstado(producto: Producto, estado: EstadoPublicacion, mensaje: Int) {
        RepositorioProductos.guardar(producto.copy(estado = estado, motivoRechazo = if (estado == EstadoPublicacion.RECHAZADO) producto.motivoRechazo else ""))
        avisar(mensaje)
        mostrar()
    }

    private fun avisar(mensaje: Int) {
        Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_SHORT).show()
    }
}

/** Adaptador de item_producto_gestion.xml (vendedor y administrador). */
class AdaptadorGestionProductos(
    private val esVendedor: Boolean,
    private val alTocar: (Producto) -> Unit,
    private val alOpciones: (View, Producto) -> Unit,
) : ListAdapter<Producto, AdaptadorGestionProductos.Fila>(object : DiffUtil.ItemCallback<Producto>() {
    override fun areItemsTheSame(a: Producto, b: Producto) = a.id == b.id
    override fun areContentsTheSame(a: Producto, b: Producto) = a == b
}) {

    class Fila(val binding: ItemProductoGestionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(padre: ViewGroup, tipo: Int) =
        Fila(ItemProductoGestionBinding.inflate(LayoutInflater.from(padre.context), padre, false))

    override fun onBindViewHolder(fila: Fila, posicion: Int) = with(fila.binding) {
        val p = getItem(posicion)
        val contexto = root.context
        mostrarImagenProducto(marcoImagen, foto, icono, p.categoria, p.id)
        nombre.text = p.nombre
        resumen.text = if (esVendedor) {
            contexto.getString(R.string.producto_resumen_vendedor, Formatos.precio(p.precio), p.existencias)
        } else {
            contexto.getString(R.string.producto_resumen_admin, p.vendedorNombre, Formatos.precio(p.precio))
        }
        estado.mostrarEtiqueta(contexto.getString(p.estado.nombre), p.estado.tono)

        // Segunda etiqueta: existencias para el vendedor, rótulo para el administrador
        if (esVendedor) {
            etiquetaExtra.visibility = if (p.agotado || p.pocasUnidades) View.VISIBLE else View.GONE
            if (p.agotado) etiquetaExtra.mostrarEtiqueta(contexto.getString(R.string.formato_agotado), Tono.ERROR)
            else if (p.pocasUnidades) etiquetaExtra.mostrarEtiqueta(contexto.getString(R.string.indicador_existencias_bajas), Tono.PENDIENTE)
        } else {
            etiquetaExtra.visibility = View.VISIBLE
            etiquetaExtra.mostrarEtiqueta(
                contexto.getString(if (p.rotuloCompleto) R.string.rotulo_completo else R.string.rotulo_incompleto),
                if (p.rotuloCompleto) Tono.INFO else Tono.ERROR,
            )
        }

        aviso.visibility = if (p.estado == EstadoPublicacion.RECHAZADO && p.motivoRechazo.isNotBlank()) View.VISIBLE else View.GONE
        aviso.text = contexto.getString(R.string.producto_motivo, p.motivoRechazo)

        root.setOnClickListener { alTocar(p) }
        botonOpciones.setOnClickListener { alOpciones(it, p) }
    }
}
