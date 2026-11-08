package com.sabornatural.tienda.ui.inicio

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.RepositorioUsuarios
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.databinding.ActivitySplashBinding
import com.sabornatural.tienda.ui.auth.IngresoActivity
import com.sabornatural.tienda.ui.comun.Biometria
import com.sabornatural.tienda.ui.comun.Navegador
import com.sabornatural.tienda.ui.comun.PantallaBase
import com.sabornatural.tienda.ui.comun.rellenarBarrasDelSistema

/**
 * Punto de entrada de la app (intent-filter MAIN/LAUNCHER en el AndroidManifest).
 *
 * 1. Android muestra su pantalla de bienvenida con el tema Theme.SaborNatural.Splash.
 * 2. Esta Activity continúa con la marca animada mientras carga los datos.
 * 3. Decide a dónde ir según la sesión guardada (documento técnico, RF-INI-01).
 */
@SuppressLint("CustomSplashScreen") // Se usa la API SplashScreen; esta pantalla es su continuación animada.
class SplashActivity : PantallaBase() {

    override val fondoOscuroCompleto = true

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // Debe ir antes de super.onCreate()
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.raiz.rellenarBarrasDelSistema()

        RepositorioUsuarios.inicializar(this)
        binding.version.text = getString(R.string.splash_version, versionDeLaApp())

        // Animaciones definidas en res/anim
        binding.logo.startAnimation(AnimationUtils.loadAnimation(this, R.anim.logo_entrada))
        val entradaTexto = AnimationUtils.loadAnimation(this, R.anim.texto_entrada)
        binding.nombre.startAnimation(entradaTexto)
        binding.eslogan.startAnimation(entradaTexto)

        binding.root.postDelayed(::continuar, DURACION_MS)
    }

    private fun continuar() {
        if (isFinishing) return
        val sesion = Sesion(this)
        val usuario = sesion.usuarioActual
        when {
            // Sin sesión: bienvenida con ingresar, crear cuenta o explorar
            usuario == null -> Navegador.abrirBienvenida(this)

            // Sesión guardada con huella: se pide la huella antes de mostrar datos
            sesion.huellaHabilitada && Biometria.disponible(this) -> {
                startActivity(
                    Intent(this, IngresoActivity::class.java)
                        .putExtra(IngresoActivity.EXTRA_DESBLOQUEAR_CON_HUELLA, true),
                )
                finish()
            }

            // Sesión vigente sin huella: directo al panel del rol
            else -> Navegador.abrirPanel(this, usuario)
        }
    }

    private fun versionDeLaApp(): String =
        packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

    private companion object {
        const val DURACION_MS = 1800L
    }
}
