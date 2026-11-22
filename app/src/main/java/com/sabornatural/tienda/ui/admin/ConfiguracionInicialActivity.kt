package com.sabornatural.tienda.ui.admin

import android.os.Bundle
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.Semilla
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.datos.Sincronizacion
import com.sabornatural.tienda.datos.modelo.Rol
import com.sabornatural.tienda.databinding.ActivityConfiguracionInicialBinding
import com.sabornatural.tienda.datos.remoto.Errores
import com.sabornatural.tienda.ui.comun.Navegador
import com.sabornatural.tienda.ui.comun.PantallaBase
import com.sabornatural.tienda.ui.comun.Validaciones
import com.sabornatural.tienda.ui.comun.ejecutar
import com.sabornatural.tienda.ui.comun.ocultarTeclado
import com.sabornatural.tienda.ui.comun.rellenarBarraDeEstado
import com.sabornatural.tienda.ui.comun.rellenarBarraDeNavegacion
import com.sabornatural.tienda.ui.comun.validar

/**
 * Pantalla oculta de configuración inicial de un proyecto de Firebase nuevo (ver Semilla.kt).
 *
 * Se abre manteniendo presionado el avatar en el Perfil (cualquier rol) o en el panel del
 * administrador. Tiene dos pasos:
 *  1. "Convertirme en el primer administrador": solo funciona una vez por proyecto.
 *  2. "Sembrar datos de ejemplo": solo para el administrador; crea categorías, cuentas de
 *     prueba con la contraseña que se escriba aquí, productos y pedidos de ejemplo.
 */
class ConfiguracionInicialActivity : PantallaBase() {

    private lateinit var binding: ActivityConfiguracionInicialBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfiguracionInicialBinding.inflate(layoutInflater)
        setContentView(binding.root)
        with(binding.encabezado) {
            root.rellenarBarraDeEstado()
            titulo.setText(R.string.configuracion_titulo)
            subtitulo.setText(R.string.configuracion_subtitulo)
            botonVolver.setOnClickListener { finish() }
        }
        binding.desplazable.rellenarBarraDeNavegacion()
        binding.botonAdministrador.setOnClickListener { reclamarAdministrador() }
        binding.botonSembrar.setOnClickListener { sembrar() }
        mostrarCuenta()
    }

    /** Muestra con qué cuenta y rol se está usando la pantalla y habilita los pasos que aplican. */
    private fun mostrarCuenta() {
        val usuario = Sesion(this).usuarioActual
        binding.cuenta.text = if (usuario == null) {
            getString(R.string.configuracion_sin_sesion)
        } else {
            getString(R.string.configuracion_cuenta, usuario.correo, getString(usuario.rol.nombre))
        }
        binding.botonAdministrador.isEnabled = usuario != null && usuario.rol != Rol.ADMINISTRADOR
        val esAdmin = usuario?.rol == Rol.ADMINISTRADOR
        binding.botonSembrar.isEnabled = esAdmin
        binding.campoClaveDemo.isEnabled = esAdmin
    }

    /** Paso 1: el usuario con sesión pasa a ser el primer administrador. */
    private fun reclamarAdministrador() {
        val usuario = Sesion(this).usuarioActual ?: return
        ejecutar(
            alFallar = { e ->
                // Si ya hay administrador, las reglas de seguridad rechazan el cambio (permiso denegado)
                aviso(getString(R.string.configuracion_ya_hay_admin) + "\n\n" + getString(Errores.mensaje(e)))
            },
        ) {
            val admin = Semilla.reclamarAdministrador(usuario)
            Sesion(this).iniciar(admin)
            Sincronizacion.detener()
            mostrarCuenta()
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.configuracion_admin_listo_titulo)
                .setMessage(R.string.configuracion_admin_listo_texto)
                .setPositiveButton(R.string.configuracion_abrir_panel) { _, _ -> Navegador.abrirPanel(this, admin) }
                .setNegativeButton(R.string.configuracion_seguir_aqui, null)
                .show()
        }
    }

    /** Paso 2: siembra los datos de ejemplo con la contraseña de prueba escrita. */
    private fun sembrar() {
        val clave = binding.campoClaveDemo.editText?.text?.toString().orEmpty()
        if (!binding.campoClaveDemo.validar(Validaciones.esClaveValida(clave), getString(R.string.error_clave_debil))) return
        ocultarTeclado()
        binding.registro.text = ""
        binding.registro.visibility = View.VISIBLE
        ejecutar(R.string.configuracion_sembrando, alFallar = { e -> anotar("✗ " + getString(Errores.mensaje(e))) }) {
            Semilla.sembrar(this, clave) { mensaje -> anotar("✓ $mensaje") }
            anotar(getString(R.string.configuracion_sembrado_listo))
        }
    }

    /** Agrega una línea al registro de lo que se va creando. */
    private fun anotar(linea: String) {
        binding.registro.append(linea + "\n")
    }

    /** Diálogo simple con un mensaje. */
    private fun aviso(mensaje: String) {
        MaterialAlertDialogBuilder(this).setMessage(mensaje).setPositiveButton(R.string.registro_entendido, null).show()
    }
}
