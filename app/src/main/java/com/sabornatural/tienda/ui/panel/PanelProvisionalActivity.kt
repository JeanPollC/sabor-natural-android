package com.sabornatural.tienda.ui.panel

import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.RepositorioUsuarios
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.datos.modelo.Rol
import com.sabornatural.tienda.databinding.ActivityPanelProvisionalBinding
import com.sabornatural.tienda.ui.comun.Navegador
import com.sabornatural.tienda.ui.comun.PantallaBase
import com.sabornatural.tienda.ui.comun.rellenarBarraDeEstado
import com.sabornatural.tienda.ui.comun.rellenarBarraDeNavegacion

/**
 * Panel provisional (fase 2). Muestra el usuario y su rol para comprobar la navegación por rol.
 * En las fases 3, 4 y 5 cada rol tendrá su propia Activity con barra de navegación inferior.
 */
class PanelProvisionalActivity : PantallaBase() {

    private lateinit var binding: ActivityPanelProvisionalBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPanelProvisionalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.encabezado.rellenarBarraDeEstado()
        binding.contenido.rellenarBarraDeNavegacion()

        RepositorioUsuarios.inicializar(this)
        val usuario = RepositorioUsuarios.buscarPorId(intent.getIntExtra(EXTRA_USUARIO_ID, -1))
        if (usuario == null) {
            Navegador.abrirBienvenida(this)
            return
        }

        val nombreRol = getString(usuario.rol.nombre)
        val (icono, fase) = when (usuario.rol) {
            Rol.COMPRADOR -> R.drawable.ic_carrito to 3
            Rol.VENDEDOR -> R.drawable.ic_tienda to 4
            Rol.ADMINISTRADOR -> R.drawable.ic_panel to 5
        }
        binding.avatar.text = usuario.iniciales
        binding.saludo.text = getString(R.string.panel_saludo, usuario.nombre)
        binding.etiquetaRol.text = nombreRol
        binding.iconoRol.setImageResource(icono)
        binding.textoPanel.text = getString(R.string.panel_texto, nombreRol.lowercase(), fase)

        binding.botonSalir.setOnClickListener { confirmarCierre() }
        binding.botonCerrarSesion.setOnClickListener { confirmarCierre() }
    }

    private fun confirmarCierre() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.panel_cerrar_sesion)
            .setMessage(R.string.panel_cerrar_sesion_confirmar)
            .setPositiveButton(R.string.panel_cerrar_sesion) { _, _ ->
                Sesion(this).cerrar()
                Navegador.abrirBienvenida(this)
            }
            .setNegativeButton(R.string.accion_cancelar, null)
            .show()
    }

    companion object {
        const val EXTRA_USUARIO_ID = "usuario_id"
    }
}
