package com.sabornatural.tienda.ui.comun

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.sabornatural.tienda.R

/**
 * Ingreso con huella (documento técnico, RF-AUT-05).
 *
 * La huella la lee y la verifica el sistema operativo con el diálogo BiometricPrompt:
 * la aplicación nunca recibe la huella, solo el resultado (éxito, fallo o cancelación).
 * Se exige biometría fuerte (Clase 3).
 */
object Biometria {

    /** true si el equipo tiene lector y al menos una huella registrada. */
    fun disponible(contexto: Context): Boolean =
        BiometricManager.from(contexto).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Muestra el diálogo del sistema.
     * @param alExito se llama cuando la huella coincide.
     * @param alUsarClave se llama cuando el usuario elige "Usar contraseña" o cierra el diálogo.
     * @param alError se llama con un mensaje si hay un error del sensor (por ejemplo, bloqueo por intentos).
     */
    fun autenticar(
        actividad: FragmentActivity,
        alExito: () -> Unit,
        alUsarClave: () -> Unit,
        alError: (CharSequence) -> Unit,
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(resultado: BiometricPrompt.AuthenticationResult) = alExito()

            override fun onAuthenticationError(codigo: Int, mensaje: CharSequence) {
                when (codigo) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED -> alUsarClave()
                    else -> alError(mensaje)
                }
            }
            // onAuthenticationFailed (huella no reconocida) lo muestra el propio diálogo del sistema.
        }

        val dialogo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(actividad.getString(R.string.huella_titulo))
            .setSubtitle(actividad.getString(R.string.huella_subtitulo))
            .setNegativeButtonText(actividad.getString(R.string.huella_usar_clave))
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()

        BiometricPrompt(actividad, ContextCompat.getMainExecutor(actividad), callback).authenticate(dialogo)
    }
}
