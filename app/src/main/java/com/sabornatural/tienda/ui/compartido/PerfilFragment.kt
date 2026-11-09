package com.sabornatural.tienda.ui.compartido

import android.os.Bundle
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.Preferencias
import com.sabornatural.tienda.datos.RepositorioUsuarios
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.datos.modelo.Rol
import com.sabornatural.tienda.databinding.FragmentPerfilBinding
import com.sabornatural.tienda.ui.comprador.pedirIngreso
import com.sabornatural.tienda.ui.comun.Biometria
import com.sabornatural.tienda.ui.comun.ConMenuLateral
import com.sabornatural.tienda.ui.comun.FotosPerfil
import com.sabornatural.tienda.ui.comun.Formatos
import com.sabornatural.tienda.ui.comun.FragmentoBase
import com.sabornatural.tienda.ui.comun.Navegador
import com.sabornatural.tienda.ui.comun.PanelRolActivity
import com.sabornatural.tienda.ui.comun.confirmar
import com.sabornatural.tienda.ui.comun.igualarAltoBarraDeEstado
import com.sabornatural.tienda.ui.comun.navegar
import com.sabornatural.tienda.ui.comun.rellenarBarraDeEstado

/**
 * Perfil (mockups B-10 y B-12), el mismo Fragment para los tres roles: lo que no aplica
 * a un rol se oculta (por ejemplo, "Mi tienda" solo la ve el vendedor).
 */
class PerfilFragment : FragmentoBase<FragmentPerfilBinding>(FragmentPerfilBinding::inflate) {

    private lateinit var sesion: Sesion
    private lateinit var preferencias: Preferencias

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        sesion = Sesion(requireContext())
        preferencias = Preferencias(requireContext())
        binding.encabezado.rellenarBarraDeEstado()
        binding.franjaBarraEstado.igualarAltoBarraDeEstado()
        binding.botonMenu.setOnClickListener { (activity as? ConMenuLateral)?.abrirMenuLateral() }

        if (sesion.usuarioActual == null) {
            pedirIngreso() // Invitado: el perfil exige una cuenta
            return
        }

        binding.filaEditar.setOnClickListener { navegar(R.id.editarPerfilFragment) }
        binding.filaPedidos.setOnClickListener { navegar(R.id.misPedidosFragment) }
        binding.filaDirecciones.setOnClickListener { avisar(R.string.perfil_proximamente) }
        binding.filaPolitica.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.perfil_politica)
                .setMessage(R.string.perfil_politica_texto)
                .setPositiveButton(R.string.registro_entendido, null)
                .show()
        }
        binding.filaEliminar.setOnClickListener { eliminarCuenta() }
        binding.botonCerrarSesion.setOnClickListener { (activity as? PanelRolActivity)?.confirmarCierreDeSesion() }
        configurarInterruptores()
    }

    override fun onResume() {
        super.onResume()
        mostrarUsuario() // Al volver de "Editar perfil" se ven los cambios
    }

    private fun mostrarUsuario() {
        val usuario = sesion.usuarioActual ?: return
        with(binding) {
            avatar.text = usuario.iniciales
            val foto = FotosPerfil[usuario.id]
            fotoPerfil.setImageBitmap(foto)
            fotoPerfil.visibility = if (foto != null) View.VISIBLE else View.GONE
            nombre.text = usuario.nombreCompleto
            correo.text = usuario.correo
            rol.text = getString(usuario.rol.nombre)
            miembroDesde.text = getString(R.string.perfil_miembro_desde, Formatos.fecha(usuario.fechaRegistro))

            // Filas según el rol
            val esComprador = usuario.rol == Rol.COMPRADOR
            listOf(filaPedidos, divisorPedidos, filaDirecciones, divisorDirecciones).forEach {
                it.visibility = if (esComprador) View.VISIBLE else View.GONE
            }
            seccionTienda.visibility = if (usuario.rol == Rol.VENDEDOR) View.VISIBLE else View.GONE
            telefonoTienda.text = getString(R.string.formato_telefono, usuario.telefono)
            // El administrador no elimina su propia cuenta desde aquí (RF-ADU-04)
            val puedeEliminar = usuario.rol != Rol.ADMINISTRADOR
            filaEliminar.visibility = if (puedeEliminar) View.VISIBLE else View.GONE
            divisorEliminar.visibility = filaEliminar.visibility
        }
    }

    private fun configurarInterruptores() = with(binding) {
        // Las tres filas reutilizan el mismo layout (mismo id "interruptor"): si Android guardara
        // su estado, al volver les pondría a todas el de la última. El valor real sale de las preferencias.
        listOf(filaHuella, filaAvisos, filaPromociones).forEach { it.interruptor.isSaveEnabled = false }

        // Huella: solo se puede activar si el teléfono tiene huellas registradas
        filaHuella.icono.setImageResource(R.drawable.ic_huella)
        filaHuella.titulo.setText(R.string.perfil_huella)
        val disponible = Biometria.disponible(requireContext())
        filaHuella.detalle.setText(if (disponible) R.string.perfil_huella_detalle else R.string.perfil_huella_no_disponible)
        filaHuella.interruptor.isEnabled = disponible
        filaHuella.interruptor.isChecked = sesion.huellaHabilitada && disponible
        filaHuella.interruptor.setOnCheckedChangeListener { boton, activar ->
            if (!activar) {
                sesion.huellaHabilitada = false
                return@setOnCheckedChangeListener
            }
            // Activar exige confirmar con la huella
            Biometria.autenticar(
                actividad = requireActivity(),
                alExito = { sesion.huellaHabilitada = true; avisar(R.string.ingreso_huella_activada) },
                alUsarClave = { boton.isChecked = false },
                alError = { boton.isChecked = false; avisar(it.toString()) },
            )
        }

        filaAvisos.icono.setImageResource(R.drawable.ic_notificacion)
        filaAvisos.titulo.setText(R.string.perfil_notificaciones_pedidos)
        filaAvisos.detalle.visibility = View.GONE
        filaAvisos.interruptor.isChecked = preferencias.avisosPedidos
        filaAvisos.interruptor.setOnCheckedChangeListener { _, valor -> preferencias.avisosPedidos = valor }

        filaPromociones.icono.setImageResource(R.drawable.ic_dinero)
        filaPromociones.titulo.setText(R.string.perfil_notificaciones_promociones)
        filaPromociones.detalle.setText(R.string.perfil_notificaciones_promociones_detalle)
        filaPromociones.interruptor.isChecked = preferencias.promociones
        filaPromociones.interruptor.setOnCheckedChangeListener { _, valor -> preferencias.promociones = valor }
    }

    /** Eliminación de la cuenta desde la app (RF-PER-04, exigido por Google Play). */
    private fun eliminarCuenta() {
        val usuario = sesion.usuarioActual ?: return
        confirmar(getString(R.string.perfil_eliminar_cuenta), getString(R.string.perfil_eliminar_cuenta_confirmar), getString(R.string.accion_eliminar)) {
            RepositorioUsuarios.eliminar(usuario.id)
            sesion.cerrar()
            Navegador.abrirBienvenida(requireActivity())
        }
    }

    private fun avisar(mensaje: Int) = avisar(getString(mensaje))
    private fun avisar(mensaje: String) {
        view?.let { Snackbar.make(it, mensaje, Snackbar.LENGTH_SHORT).show() }
    }
}
