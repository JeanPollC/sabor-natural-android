package com.sabornatural.tienda.ui.inicio

import android.content.Intent
import android.os.Bundle
import com.sabornatural.tienda.databinding.ActivityBienvenidaBinding
import com.sabornatural.tienda.ui.auth.IngresoActivity
import com.sabornatural.tienda.ui.auth.RegistroActivity
import com.sabornatural.tienda.ui.comun.Navegador
import com.sabornatural.tienda.ui.comun.PantallaBase
import com.sabornatural.tienda.ui.comun.rellenarBarrasDelSistema
import com.sabornatural.tienda.ui.estilos.GuiaEstilosActivity

/** Bienvenida (mockup C-01): presenta la marca y ofrece ingresar, crear cuenta o explorar. */
class BienvenidaActivity : PantallaBase() {

    override val fondoOscuroCompleto = true

    private lateinit var binding: ActivityBienvenidaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBienvenidaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.raiz.rellenarBarrasDelSistema()

        binding.botonIngresar.setOnClickListener {
            startActivity(Intent(this, IngresoActivity::class.java))
        }
        binding.botonCrearCuenta.setOnClickListener {
            startActivity(Intent(this, RegistroActivity::class.java))
        }
        // Explorar sin cuenta: catálogo del comprador como invitado (RF-INI-02)
        binding.botonExplorar.setOnClickListener { Navegador.explorarComoInvitado(this) }

        // Acceso de desarrollo: mantener presionado el logo abre la guía de estilos
        binding.logo.setOnLongClickListener {
            startActivity(Intent(this, GuiaEstilosActivity::class.java))
            true
        }
    }
}
