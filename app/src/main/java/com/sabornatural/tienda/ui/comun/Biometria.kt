package com.sabornatural.tienda.ui.comun

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.Sesion
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Ingreso con huella (documento técnico, RF-AUT-05).
 *
 * La huella la lee y la verifica el sistema operativo con el diálogo BiometricPrompt: la app nunca
 * recibe la huella, solo el resultado. Se exige biometría fuerte (Clase 3).
 *
 * Cómo se protege la sesión:
 *  - Firebase Authentication ya guarda la sesión en el teléfono (un token que se renueva solo);
 *    la huella es el candado que se pide antes de reabrirla. La contraseña nunca se guarda.
 *  - Al activar la huella se crea una llave AES en el Android Keystore (hardware seguro del
 *    teléfono) que solo se puede usar justo después de poner una huella válida, y que el sistema
 *    invalida si se registra una huella nueva. Con ella se cifra el uid de la cuenta.
 *  - Al desbloquear, BiometricPrompt habilita la llave (CryptoObject); si el uid descifrado
 *    coincide con la sesión de Firebase guardada, se entra. Sin la huella, nadie puede usar la llave.
 */
object Biometria {

    private const val ALIAS_LLAVE = "sabornatural_huella"
    private const val TRANSFORMACION = "AES/GCM/NoPadding"

    /** true si el equipo tiene lector y al menos una huella registrada. */
    fun disponible(contexto: Context): Boolean =
        BiometricManager.from(contexto).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Activa la huella para la cuenta [uid]: pide la huella una vez y guarda el uid cifrado.
     * @param alExito se llama cuando quedó activada.
     * @param alCancelar se llama si el usuario cerró el diálogo.
     * @param alError se llama con un mensaje si el sensor o el Keystore fallan.
     */
    fun activar(
        actividad: FragmentActivity,
        uid: String,
        alExito: () -> Unit,
        alCancelar: () -> Unit,
        alError: (CharSequence) -> Unit,
    ) {
        val cifrador = runCatching {
            Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, crearLlave()) }
        }.getOrElse {
            alError(actividad.getString(R.string.huella_error_llave))
            return
        }
        mostrarDialogo(
            actividad,
            BiometricPrompt.CryptoObject(cifrador),
            alExito = { resultado ->
                val c = resultado.cryptoObject?.cipher
                val cifrado = runCatching { c?.doFinal(uid.toByteArray()) }.getOrNull()
                if (c == null || cifrado == null) {
                    alError(actividad.getString(R.string.huella_error_llave))
                } else {
                    val sesion = Sesion(actividad)
                    sesion.huellaCifrado = Base64.encodeToString(cifrado, Base64.NO_WRAP)
                    sesion.huellaIv = Base64.encodeToString(c.iv, Base64.NO_WRAP)
                    sesion.huellaHabilitada = true
                    alExito()
                }
            },
            alUsarClave = alCancelar,
            alError = alError,
        )
    }

    /**
     * Pide la huella para desbloquear la sesión guardada.
     * @param alExito recibe el uid descifrado (la pantalla lo compara con la sesión de Firebase).
     * @param alUsarClave se llama cuando el usuario elige "Usar contraseña" o cierra el diálogo.
     * @param alError se llama con un mensaje (bloqueo por intentos, huellas nuevas registradas...).
     */
    fun desbloquear(
        actividad: FragmentActivity,
        alExito: (uid: String) -> Unit,
        alUsarClave: () -> Unit,
        alError: (CharSequence) -> Unit,
    ) {
        val sesion = Sesion(actividad)
        val iv = sesion.huellaIv?.let { Base64.decode(it, Base64.NO_WRAP) }
        val cifrado = sesion.huellaCifrado?.let { Base64.decode(it, Base64.NO_WRAP) }
        val llave = obtenerLlave()
        if (iv == null || cifrado == null || llave == null) {
            desactivar(actividad)
            alError(actividad.getString(R.string.huella_invalidada))
            return
        }
        val descifrador = try {
            Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.DECRYPT_MODE, llave, GCMParameterSpec(128, iv)) }
        } catch (_: KeyPermanentlyInvalidatedException) {
            // Se registró una huella nueva en el teléfono: por seguridad hay que volver a activarla
            desactivar(actividad)
            alError(actividad.getString(R.string.huella_invalidada))
            return
        } catch (_: Exception) {
            alError(actividad.getString(R.string.huella_error_llave))
            return
        }
        mostrarDialogo(
            actividad,
            BiometricPrompt.CryptoObject(descifrador),
            alExito = { resultado ->
                val uid = runCatching { resultado.cryptoObject?.cipher?.doFinal(cifrado)?.let { String(it) } }.getOrNull()
                if (uid == null) alError(actividad.getString(R.string.huella_error_llave)) else alExito(uid)
            },
            alUsarClave = alUsarClave,
            alError = alError,
        )
    }

    /**
     * Pide la huella sin cifrado (para confirmar una acción). Se conserva por compatibilidad con
     * las pantallas que solo necesitan saber si la huella coincide.
     */
    fun autenticar(actividad: FragmentActivity, alExito: () -> Unit, alUsarClave: () -> Unit, alError: (CharSequence) -> Unit) =
        mostrarDialogo(actividad, null, { alExito() }, alUsarClave, alError)

    /** Apaga la huella: borra la preferencia, el dato cifrado y la llave del Keystore. */
    fun desactivar(contexto: Context) {
        val sesion = Sesion(contexto)
        sesion.huellaHabilitada = false
        sesion.huellaCifrado = null
        sesion.huellaIv = null
        runCatching { almacen().deleteEntry(ALIAS_LLAVE) }
    }

    /** Muestra el diálogo del sistema y reparte el resultado. */
    private fun mostrarDialogo(
        actividad: FragmentActivity,
        cripto: BiometricPrompt.CryptoObject?,
        alExito: (BiometricPrompt.AuthenticationResult) -> Unit,
        alUsarClave: () -> Unit,
        alError: (CharSequence) -> Unit,
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(resultado: BiometricPrompt.AuthenticationResult) = alExito(resultado)

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

        val prompt = BiometricPrompt(actividad, ContextCompat.getMainExecutor(actividad), callback)
        if (cripto != null) prompt.authenticate(dialogo, cripto) else prompt.authenticate(dialogo)
    }

    /** Abre el Android Keystore, donde vive la llave protegida por huella. */
    private fun almacen(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    /** Llave de huella guardada, o null si no existe. */
    private fun obtenerLlave(): SecretKey? = runCatching { almacen().getKey(ALIAS_LLAVE, null) as? SecretKey }.getOrNull()

    /** Crea (o reemplaza) la llave AES protegida por huella en el Android Keystore. */
    private fun crearLlave(): SecretKey {
        runCatching { almacen().deleteEntry(ALIAS_LLAVE) }
        val especificacion = KeyGenParameterSpec.Builder(ALIAS_LLAVE, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Cada uso de la llave exige una huella (0 segundos de validez)
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(especificacion) }
            .generateKey()
    }
}
