package com.sabornatural.tienda.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.RepositorioUsuarios
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.datos.modelo.EstadoCuenta
import com.sabornatural.tienda.datos.modelo.Rol
import com.sabornatural.tienda.datos.modelo.Usuario
import com.sabornatural.tienda.databinding.FragmentFormularioUsuarioBinding
import com.sabornatural.tienda.ui.comprador.Argumentos
import com.sabornatural.tienda.ui.comun.Formatos
import com.sabornatural.tienda.ui.comun.FragmentoBase
import com.sabornatural.tienda.ui.comun.Validaciones
import com.sabornatural.tienda.ui.comun.configurar
import com.sabornatural.tienda.ui.comun.confirmar
import com.sabornatural.tienda.ui.comun.ocultarTeclado
import com.sabornatural.tienda.ui.comun.rellenarBarraDeNavegacion
import com.sabornatural.tienda.ui.comun.texto
import com.sabornatural.tienda.ui.comun.validar

/**
 * Crear, consultar, editar y eliminar usuarios (mockups A-03 y A-04, RF-ADU-02 a RF-ADU-06).
 * Reglas: el administrador no asigna contraseñas; no puede cambiar su propio rol o estado ni
 * eliminarse; un vendedor pendiente se aprueba o se rechaza desde la tarjeta de solicitud.
 */
class FormularioUsuarioFragment : FragmentoBase<FragmentFormularioUsuarioBinding>(FragmentFormularioUsuarioBinding::inflate) {

    private var usuario: Usuario? = null
    private var rol = Rol.COMPRADOR

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        usuario = arguments?.getInt(Argumentos.USUARIO_ID, -1)?.takeIf { it > 0 }?.let { RepositorioUsuarios.buscarPorId(it) }
        val editando = usuario != null
        val esPropio = usuario?.id == Sesion(requireContext()).usuarioActual?.id
        binding.barra.configurar(
            this,
            getString(if (editando) R.string.usuario_formulario_editar else R.string.usuario_formulario_nuevo),
            principal = false,
        )
        binding.barraAccion.rellenarBarraDeNavegacion()

        val nombresRol = Rol.entries.map { getString(it.nombre) }
        binding.listaRol.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, nombresRol))
        binding.listaRol.setOnItemClickListener { _, _, posicion, _ -> rol = Rol.entries[posicion] }

        val u = usuario
        if (u != null) {
            rol = u.rol
            binding.campoNombre.editText?.setText(u.nombre)
            binding.campoApellido.editText?.setText(u.apellido)
            binding.campoCorreo.editText?.setText(u.correo)
            binding.campoCorreo.isEnabled = false
            binding.campoTelefono.editText?.setText(u.telefono)
            binding.grupoEstado.check(idEstado(u.estado))
            binding.registrado.text = getString(R.string.usuario_registrado, Formatos.fecha(u.fechaRegistro))
            binding.avisoClave.visibility = View.GONE
            binding.tarjetaSolicitud.visibility =
                if (u.rol == Rol.VENDEDOR && u.estado == EstadoCuenta.PENDIENTE) View.VISIBLE else View.GONE
        } else {
            binding.grupoEstado.check(binding.opcionActiva.id)
            binding.registrado.visibility = View.GONE
            binding.botonEliminar.visibility = View.GONE
        }
        binding.listaRol.setText(getString(rol.nombre), false)

        // Sobre su propia cuenta, el administrador no cambia rol ni estado ni se elimina (RF-ADU-04)
        if (esPropio) {
            binding.campoRol.isEnabled = false
            listOf(binding.opcionActiva, binding.opcionPendiente, binding.opcionSuspendida).forEach { it.isEnabled = false }
            binding.botonEliminar.visibility = View.GONE
            binding.campoRol.helperText = getString(R.string.usuario_no_propio)
        }

        binding.botonAprobarSolicitud.setOnClickListener { decidirSolicitud(aprobar = true) }
        binding.botonRechazarSolicitud.setOnClickListener { decidirSolicitud(aprobar = false) }
        binding.botonEliminar.setOnClickListener { eliminar() }
        binding.botonGuardar.setOnClickListener { guardar() }
    }

    private fun idEstado(estado: EstadoCuenta) = when (estado) {
        EstadoCuenta.ACTIVO -> binding.opcionActiva.id
        EstadoCuenta.PENDIENTE -> binding.opcionPendiente.id
        EstadoCuenta.SUSPENDIDO -> binding.opcionSuspendida.id
    }

    private val estadoElegido: EstadoCuenta
        get() = when (binding.grupoEstado.checkedButtonId) {
            binding.opcionPendiente.id -> EstadoCuenta.PENDIENTE
            binding.opcionSuspendida.id -> EstadoCuenta.SUSPENDIDO
            else -> EstadoCuenta.ACTIVO
        }

    private fun guardar() {
        val obligatorio = getString(R.string.error_campo_obligatorio)
        val correo = binding.campoCorreo.texto
        val validos = listOf(
            binding.campoNombre.validar(binding.campoNombre.texto.isNotEmpty(), obligatorio),
            binding.campoApellido.validar(binding.campoApellido.texto.isNotEmpty(), obligatorio),
            binding.campoCorreo.validar(Validaciones.esCorreoValido(correo), getString(R.string.error_correo_invalido)) &&
                binding.campoCorreo.validar(!RepositorioUsuarios.existeCorreo(correo, usuario?.id), getString(R.string.error_correo_registrado)),
            binding.campoTelefono.validar(Validaciones.esTelefonoValido(binding.campoTelefono.texto), getString(R.string.error_telefono_invalido)),
        ).all { it }
        if (!validos) return

        val existente = usuario
        if (existente == null) {
            RepositorioUsuarios.crearDesdeAdministracion(
                binding.campoNombre.texto, binding.campoApellido.texto, correo, binding.campoTelefono.texto, rol, estadoElegido,
            )
        } else {
            RepositorioUsuarios.actualizar(
                existente.copy(
                    nombre = binding.campoNombre.texto,
                    apellido = binding.campoApellido.texto,
                    telefono = binding.campoTelefono.texto,
                    rol = rol,
                    estado = estadoElegido,
                ),
            )
        }
        terminar(if (existente == null) R.string.usuario_creado else R.string.usuario_guardado)
    }

    /** Aprobar o rechazar la solicitud de un vendedor (RF-ADU-06). */
    private fun decidirSolicitud(aprobar: Boolean) {
        val u = usuario ?: return
        RepositorioUsuarios.actualizar(u.copy(estado = if (aprobar) EstadoCuenta.ACTIVO else EstadoCuenta.SUSPENDIDO))
        terminar(if (aprobar) R.string.usuario_aprobado else R.string.usuario_rechazado)
    }

    private fun eliminar() {
        val u = usuario ?: return
        confirmar(getString(R.string.usuario_eliminar), getString(R.string.usuario_eliminar_confirmar, u.nombreCompleto), getString(R.string.accion_eliminar)) {
            RepositorioUsuarios.eliminar(u.id)
            terminar(R.string.usuario_eliminado)
        }
    }

    private fun terminar(mensaje: Int) {
        requireActivity().ocultarTeclado()
        Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_SHORT).show()
        findNavController().navigateUp()
    }
}
