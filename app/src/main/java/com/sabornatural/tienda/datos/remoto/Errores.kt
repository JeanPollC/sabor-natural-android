package com.sabornatural.tienda.datos.remoto

import androidx.annotation.StringRes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import android.content.Context
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.StorageException
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.ErrorNegocio
import java.io.IOException

/**
 * Traduce las excepciones de Firebase (y de red) a mensajes en español para el usuario.
 * Nunca se muestra el texto técnico de la excepción.
 */
object Errores {

    /** Devuelve el id del texto (res/values) que explica el error [e]. */
    @StringRes
    fun mensaje(e: Throwable): Int = when (e) {
        is ErrorNegocio -> e.mensaje
        // Correo inexistente o contraseña equivocada: mismo mensaje, para no revelar qué falló (RF-AUT-03).
        // WeakPassword hereda de InvalidCredentials, por eso se revisa antes.
        is FirebaseAuthWeakPasswordException -> R.string.error_clave_debil
        is FirebaseAuthInvalidUserException, is FirebaseAuthInvalidCredentialsException -> R.string.ingreso_error_credenciales
        is FirebaseAuthUserCollisionException -> R.string.error_correo_registrado
        is FirebaseAuthRecentLoginRequiredException -> R.string.error_reautenticar
        is FirebaseTooManyRequestsException -> R.string.error_demasiados_intentos
        is FirebaseNetworkException, is IOException -> R.string.error_sin_conexion
        is FirebaseFirestoreException -> when (e.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> R.string.error_permiso
            FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> R.string.error_sin_conexion
            else -> R.string.error_generico
        }
        is StorageException -> R.string.error_imagen
        is FirebaseFunctionsException -> when (e.code) {
            FirebaseFunctionsException.Code.UNAVAILABLE, FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> R.string.error_sin_conexion
            FirebaseFunctionsException.Code.PERMISSION_DENIED -> R.string.error_permiso
            else -> R.string.error_pago_servicio
        }
        else -> R.string.error_generico
    }

    /**
     * Texto listo para mostrar. Las Cloud Functions envían mensajes propios en español para los
     * errores de negocio (por ejemplo "No hay suficientes unidades de Granola"); esos se muestran
     * tal cual. Para el resto se usa [mensaje].
     */
    fun texto(contexto: Context, e: Throwable): String {
        if (e is FirebaseFunctionsException && !e.message.isNullOrBlank() && e.code in CODIGOS_CON_MENSAJE) {
            return e.message.orEmpty()
        }
        return contexto.getString(mensaje(e))
    }

    /** Códigos de las funciones cuyo mensaje está escrito para el usuario. */
    private val CODIGOS_CON_MENSAJE = setOf(
        FirebaseFunctionsException.Code.FAILED_PRECONDITION,
        FirebaseFunctionsException.Code.INVALID_ARGUMENT,
        FirebaseFunctionsException.Code.PERMISSION_DENIED,
        FirebaseFunctionsException.Code.UNAUTHENTICATED,
    )

    /** true si el error significa "correo o contraseña incorrectos" (cuenta como intento fallido). */
    fun esCredencialInvalida(e: Throwable): Boolean =
        (e is FirebaseAuthInvalidUserException || e is FirebaseAuthInvalidCredentialsException) &&
            e !is FirebaseAuthWeakPasswordException ||
            (e is ErrorNegocio && e.mensaje == R.string.ingreso_error_credenciales)
}
