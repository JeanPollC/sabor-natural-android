package com.sabornatural.tienda.ui.inicio

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.RepositorioUsuarios
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.datos.modelo.EstadoCuenta
import com.sabornatural.tienda.datos.modelo.Usuario
import com.sabornatural.tienda.datos.remoto.Nube
import com.sabornatural.tienda.databinding.ActivitySplashBinding
import com.sabornatural.tienda.ui.auth.IngresoActivity
import com.sabornatural.tienda.ui.comun.Biometria
import com.sabornatural.tienda.ui.comun.Navegador
import com.sabornatural.tienda.ui.comun.PantallaBase
import com.sabornatural.tienda.ui.comun.rellenarBarrasDelSistema
import kotlinx.coroutines.launch

/**
 * Punto de entrada de la app (intent-filter MAIN/LAUNCHER en el AndroidManifest).
 *
 * 1. Android muestra su pantalla de bienvenida con el tema Theme.SaborNatural.Splash.
 * 2. Esta Activity continúa con la marca animada mientras carga los datos.
 * 3. Decide a dónde ir según la sesión de Firebase guardada (documento técnico, RF-INI-01).
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

        binding.version.text = getString(R.string.splash_version, versionDeLaApp())

        // Animaciones definidas en res/anim
        binding.logo.startAnimation(AnimationUtils.loadAnimation(this, R.anim.logo_entrada))
        val entradaTexto = AnimationUtils.loadAnimation(this, R.anim.texto_entrada)
        binding.nombre.startAnimation(entradaTexto)
        binding.eslogan.startAnimation(entradaTexto)

        binding.root.postDelayed(::continuar, DURACION_MS)
    }

    /** Elige la siguiente pantalla: falta configuración, bienvenida, huella o panel del rol. */
    private fun continuar() {
        if (isFinishing) return
        if (!Nube.configurado) {
            avisarSinFirebase()
            return
        }
        val sesion = Sesion(this)
        when {
            // Sin sesión de Firebase: bienvenida con ingresar, crear cuenta o explorar
            Nube.uidActual == null -> Navegador.abrirBienvenida(this)

            // Sesión guardada con huella: se pide la huella antes de mostrar datos
            sesion.huellaHabilitada && Biometria.disponible(this) -> {
                startActivity(
                    Intent(this, IngresoActivity::class.java)
                        .putExtra(IngresoActivity.EXTRA_DESBLOQUEAR_CON_HUELLA, true),
                )
                finish()
            }

            // Sesión vigente sin huella: se confirma el perfil y se abre el panel del rol
            else -> lifecycleScope.launch {
                val usuario = cargarPerfilVigente(this@SplashActivity)
                if (usuario == null) Navegador.abrirBienvenida(this@SplashActivity) else Navegador.abrirPanel(this@SplashActivity, usuario)
            }
        }
    }

    /** Explica que falta app/google-services.json (el proyecto se compiló sin Firebase). */
    private fun avisarSinFirebase() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.firebase_sin_configurar_titulo)
            .setMessage(R.string.firebase_sin_configurar_texto)
            .setPositiveButton(R.string.accion_cerrar) { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    /** Versión instalada (versionName) para mostrarla en el splash. */
    private fun versionDeLaApp(): String =
        packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

    companion object {
        private const val DURACION_MS = 1800L

        /**
         * Lee el perfil de la sesión guardada en Firestore para confirmar que la cuenta sigue
         * activa. Sin conexión se usa la copia local. Si la cuenta fue eliminada o suspendida, se
         * cierra la sesión y devuelve null.
         */
        suspend fun cargarPerfilVigente(actividad: android.app.Activity): Usuario? {
            val sesion = Sesion(actividad)
            val uid = Nube.uidActual ?: return null
            val perfil = runCatching { RepositorioUsuarios.obtener(uid) }
                .getOrElse { return sesion.usuarioActual } // sin internet: copia guardada
            if (perfil == null || perfil.estado != EstadoCuenta.ACTIVO) {
                sesion.cerrar()
                return null
            }
            sesion.iniciar(perfil)
            return perfil
        }
    }
}
