package com.sabornatural.tienda.ui.compartido

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.RepositorioUsuarios
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.databinding.FragmentEditarPerfilBinding
import com.sabornatural.tienda.ui.comun.FotosPerfil
import com.sabornatural.tienda.ui.comun.FragmentoBase
import com.sabornatural.tienda.ui.comun.SelectorFoto
import com.sabornatural.tienda.ui.comun.Validaciones
import com.sabornatural.tienda.ui.comun.configurar
import com.sabornatural.tienda.ui.comun.ocultarTeclado
import com.sabornatural.tienda.ui.comun.rellenarBarraDeNavegacion
import com.sabornatural.tienda.ui.comun.texto
import com.sabornatural.tienda.ui.comun.validar

/** Editar perfil (mockup B-11): foto desde la cámara o la galería, nombre y teléfono. */
class EditarPerfilFragment : FragmentoBase<FragmentEditarPerfilBinding>(FragmentEditarPerfilBinding::inflate) {

    private var fotoNueva: Bitmap? = null
    private var quitarFoto = false

    // Se registra al crear el Fragment, como exige la API de resultados de Activity
    private val selectorFoto = SelectorFoto(this) { foto ->
        fotoNueva = foto
        quitarFoto = false
        mostrarFoto(foto)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val usuario = Sesion(requireContext()).usuarioActual ?: run {
            findNavController().navigateUp()
            return
        }
        binding.barra.configurar(this, getString(R.string.perfil_editar), principal = false)
        binding.barraAccion.rellenarBarraDeNavegacion()

        binding.avatar.text = usuario.iniciales
        mostrarFoto(FotosPerfil[usuario.id])
        binding.campoNombre.editText?.setText(usuario.nombre)
        binding.campoApellido.editText?.setText(usuario.apellido)
        binding.campoTelefono.editText?.setText(usuario.telefono)
        binding.campoCorreo.editText?.setText(usuario.correo)

        val abrirOpciones = View.OnClickListener {
            val hayFoto = !quitarFoto && (fotoNueva != null || FotosPerfil[usuario.id] != null)
            selectorFoto.mostrarOpciones(hayFoto) {
                quitarFoto = true
                fotoNueva = null
                mostrarFoto(null)
            }
        }
        binding.botonCamara.setOnClickListener(abrirOpciones)
        binding.botonCambiarFoto.setOnClickListener(abrirOpciones)
        binding.botonGuardar.setOnClickListener { guardar() }
    }

    private fun mostrarFoto(foto: Bitmap?) {
        binding.fotoPerfil.setImageBitmap(foto)
        binding.fotoPerfil.visibility = if (foto != null) View.VISIBLE else View.GONE
    }

    private fun guardar() {
        val usuario = Sesion(requireContext()).usuarioActual ?: return
        val obligatorio = getString(R.string.error_campo_obligatorio)
        val validos = listOf(
            binding.campoNombre.validar(binding.campoNombre.texto.isNotEmpty(), obligatorio),
            binding.campoApellido.validar(binding.campoApellido.texto.isNotEmpty(), obligatorio),
            binding.campoTelefono.validar(Validaciones.esTelefonoValido(binding.campoTelefono.texto), getString(R.string.error_telefono_invalido)),
        ).all { it }
        if (!validos) return

        RepositorioUsuarios.actualizar(
            usuario.copy(
                nombre = binding.campoNombre.texto,
                apellido = binding.campoApellido.texto,
                telefono = binding.campoTelefono.texto,
            ),
        )
        when {
            quitarFoto -> FotosPerfil[usuario.id] = null
            fotoNueva != null -> FotosPerfil[usuario.id] = fotoNueva
        }
        requireActivity().ocultarTeclado()
        Snackbar.make(binding.root, R.string.editar_perfil_guardado, Snackbar.LENGTH_SHORT).show()
        findNavController().navigateUp()
    }
}
