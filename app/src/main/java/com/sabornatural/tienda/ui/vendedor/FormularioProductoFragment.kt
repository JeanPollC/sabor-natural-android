package com.sabornatural.tienda.ui.vendedor

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.RepositorioProductos
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.datos.modelo.Categoria
import com.sabornatural.tienda.datos.modelo.EstadoPublicacion
import com.sabornatural.tienda.datos.modelo.Producto
import com.sabornatural.tienda.datos.modelo.TipoProducto
import com.sabornatural.tienda.databinding.FragmentFormularioProductoBinding
import com.sabornatural.tienda.ui.comprador.Argumentos
import com.sabornatural.tienda.ui.comun.FotosProductos
import com.sabornatural.tienda.ui.comun.FragmentoBase
import com.sabornatural.tienda.ui.comun.configurar
import com.sabornatural.tienda.ui.comun.mostrarImagenProducto
import com.sabornatural.tienda.ui.comun.ocultarTeclado
import com.sabornatural.tienda.ui.comun.rellenarBarraDeNavegacion
import com.sabornatural.tienda.ui.comun.texto
import com.sabornatural.tienda.ui.comun.validar

/**
 * Crear o editar producto (mockups V-03 y V-04).
 * Reglas (RF-PRO-01 y RF-PRO-04): todo producto nuevo queda EN_REVISION; al editar, si cambian
 * el nombre, la categoría o el rótulo, vuelve a revisión; precio y existencias se cambian directo.
 */
class FormularioProductoFragment : FragmentoBase<FragmentFormularioProductoBinding>(FragmentFormularioProductoBinding::inflate) {

    private var original: Producto? = null
    private var categoria = Categoria.BEBIDAS
    private var fotoNueva: Bitmap? = null

    // Cámara y galería del sistema (RF-PRO-02). Se registran al crear el Fragment.
    // La cámara la abre la app de cámara del teléfono; la galería es el selector de fotos del
    // sistema, que no pide permiso de almacenamiento.
    private val camara = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { foto ->
        foto?.let { fotoNueva = it; mostrarImagen() }
    }
    private val galeria = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { leerDesdeGaleria(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        original = arguments?.getInt(Argumentos.PRODUCTO_ID, -1)?.takeIf { it > 0 }?.let { RepositorioProductos.buscarPorId(it) }
        val editando = original != null
        binding.barra.configurar(
            this,
            getString(if (editando) R.string.formulario_producto_editar else R.string.formulario_producto_nuevo),
            principal = false,
        )
        binding.barraAccion.rellenarBarraDeNavegacion()
        binding.botonGuardar.setText(if (editando) R.string.formulario_guardar_cambios else R.string.formulario_enviar)

        // Lista desplegable de categorías
        val nombres = Categoria.entries.map { getString(it.nombre) }
        binding.listaCategoria.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, nombres))
        binding.listaCategoria.setOnItemClickListener { _, _, posicion, _ ->
            categoria = Categoria.entries[posicion]
            mostrarImagen()
        }

        original?.let { llenar(it) } ?: binding.listaCategoria.setText(getString(categoria.nombre), false)
        mostrarImagen()

        binding.botonCamara.setOnClickListener { camara.launch(null) }
        binding.botonGaleria.setOnClickListener {
            galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.grupoTipo.addOnButtonCheckedListener { _, _, _ -> binding.campoRegistro.error = null }
        binding.botonGuardar.setOnClickListener { guardar() }
    }

    private fun llenar(p: Producto) = with(binding) {
        categoria = p.categoria
        campoNombre.editText?.setText(p.nombre)
        campoDescripcion.editText?.setText(p.descripcion)
        campoPrecio.editText?.setText(p.precio.toString())
        campoExistencias.editText?.setText(p.existencias.toString())
        listaCategoria.setText(getString(p.categoria.nombre), false)
        grupoTipo.check(if (p.tipo == TipoProducto.EMPACADO) opcionEmpacado.id else opcionPreparado.id)
        campoIngredientes.editText?.setText(p.ingredientes)
        campoContenido.editText?.setText(p.contenidoNeto)
        campoRegistro.editText?.setText(p.registroSanitario)
    }

    /** Foto nueva, foto guardada o ilustración de la categoría elegida. */
    private fun mostrarImagen() = with(binding) {
        mostrarImagenProducto(marcoImagen, foto, icono, categoria, original?.id)
        fotoNueva?.let {
            foto.setImageBitmap(it)
            foto.visibility = View.VISIBLE
            icono.visibility = View.GONE
        }
    }

    private fun leerDesdeGaleria(uri: android.net.Uri) {
        val resolver = requireContext().contentResolver
        val imagen = runCatching {
            resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return
        val escala = 1024f / maxOf(imagen.width, imagen.height)
        fotoNueva = if (escala < 1f) {
            Bitmap.createScaledBitmap(imagen, (imagen.width * escala).toInt(), (imagen.height * escala).toInt(), true)
        } else {
            imagen
        }
        mostrarImagen()
    }

    private fun guardar() {
        val vendedor = Sesion(requireContext()).usuarioActual ?: return
        val obligatorio = getString(R.string.error_campo_obligatorio)
        val precio = binding.campoPrecio.texto.toLongOrNull()
        val existencias = binding.campoExistencias.texto.toIntOrNull()
        val tipo = if (binding.grupoTipo.checkedButtonId == binding.opcionPreparado.id) TipoProducto.PREPARADO else TipoProducto.EMPACADO

        val validos = listOf(
            binding.campoNombre.validar(binding.campoNombre.texto.length >= 3, obligatorio),
            binding.campoDescripcion.validar(binding.campoDescripcion.texto.length >= 10, obligatorio),
            binding.campoPrecio.validar(precio != null && precio > 0, getString(R.string.error_precio)),
            binding.campoExistencias.validar(existencias != null && existencias >= 0, getString(R.string.error_existencias)),
            binding.campoIngredientes.validar(binding.campoIngredientes.texto.isNotEmpty(), obligatorio),
            binding.campoContenido.validar(binding.campoContenido.texto.isNotEmpty(), obligatorio),
            binding.campoRegistro.validar(
                tipo == TipoProducto.PREPARADO || binding.campoRegistro.texto.isNotEmpty(),
                getString(R.string.error_registro_empacado),
            ),
        ).all { it }
        if (!validos || precio == null || existencias == null) return

        val anterior = original
        val datos = Producto(
            id = anterior?.id ?: RepositorioProductos.siguienteId(),
            nombre = binding.campoNombre.texto,
            descripcion = binding.campoDescripcion.texto,
            precio = precio,
            existencias = existencias,
            categoria = categoria,
            tipo = tipo,
            vendedorId = vendedor.id,
            vendedorNombre = anterior?.vendedorNombre ?: vendedor.nombreCompleto,
            calificacion = anterior?.calificacion ?: 0.0,
            resenas = anterior?.resenas ?: 0,
            estado = EstadoPublicacion.EN_REVISION,
            ingredientes = binding.campoIngredientes.texto,
            contenidoNeto = binding.campoContenido.texto,
            registroSanitario = binding.campoRegistro.texto,
            destacado = anterior?.destacado ?: false,
        )
        // Cambios que exigen nueva revisión: nombre, categoría, tipo o rótulo
        val vuelveARevision = anterior == null ||
            anterior.nombre != datos.nombre || anterior.categoria != datos.categoria || anterior.tipo != datos.tipo ||
            anterior.ingredientes != datos.ingredientes || anterior.contenidoNeto != datos.contenidoNeto ||
            anterior.registroSanitario != datos.registroSanitario
        val final = if (vuelveARevision || anterior == null) datos else datos.copy(estado = anterior.estado, motivoRechazo = anterior.motivoRechazo)

        RepositorioProductos.guardar(final)
        fotoNueva?.let { FotosProductos[final.id] = it }
        requireActivity().ocultarTeclado()
        Snackbar.make(
            binding.root,
            if (final.estado == EstadoPublicacion.EN_REVISION) R.string.formulario_enviado else R.string.formulario_guardado,
            Snackbar.LENGTH_SHORT,
        ).show()
        findNavController().navigateUp()
    }
}
