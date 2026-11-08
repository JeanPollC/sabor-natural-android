package com.sabornatural.tienda.ui.auth

import android.os.Bundle
import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.sabornatural.tienda.R
import com.sabornatural.tienda.databinding.ActivityRecuperarClaveBinding
import com.sabornatural.tienda.ui.comun.PantallaBase
import com.sabornatural.tienda.ui.comun.Validaciones
import com.sabornatural.tienda.ui.comun.ocultarTeclado
import com.sabornatural.tienda.ui.comun.rellenarBarraDeEstado
import com.sabornatural.tienda.ui.comun.rellenarBarraDeNavegacion
import com.sabornatural.tienda.ui.comun.texto
import com.sabornatural.tienda.ui.comun.validar

/**
 * Recuperación de la contraseña mediante un enlace al correo (RF-AUT-06).
 * En la versión conectada, el envío lo hace el proveedor de identidad; aquí se simula la respuesta.
 */
class RecuperarClaveActivity : PantallaBase() {

    private lateinit var binding: ActivityRecuperarClaveBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecuperarClaveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        with(binding.encabezado) {
            root.rellenarBarraDeEstado()
            titulo.setText(R.string.recuperar_titulo)
            subtitulo.setText(R.string.recuperar_texto)
            botonVolver.setOnClickListener { finish() }
        }
        binding.desplazable.rellenarBarraDeNavegacion()

        // Si el usuario ya había escrito su correo en el ingreso, se reutiliza
        intent.getStringExtra(EXTRA_CORREO)?.let { binding.campoCorreo.editText?.setText(it) }

        binding.botonEnviar.setOnClickListener { enviarEnlace() }
        binding.botonVolverIngreso.setOnClickListener { finish() }
        binding.botonReenviar.setOnClickListener {
            Snackbar.make(binding.root, R.string.recuperar_reenviado, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun enviarEnlace() {
        val correo = binding.campoCorreo.texto
        val valido = binding.campoCorreo.validar(
            correo.isNotEmpty() && Validaciones.esCorreoValido(correo),
            getString(if (correo.isEmpty()) R.string.error_campo_obligatorio else R.string.error_correo_invalido),
        )
        if (!valido) return
        ocultarTeclado()

        // Misma respuesta exista o no la cuenta (RF-AUT-06)
        binding.textoConfirmacion.text = getString(R.string.recuperar_enviado_texto, correo)
        binding.formulario.visibility = View.GONE
        binding.confirmacion.visibility = View.VISIBLE
        binding.encabezado.subtitulo.visibility = View.GONE
    }

    companion object {
        const val EXTRA_CORREO = "correo"
    }
}
