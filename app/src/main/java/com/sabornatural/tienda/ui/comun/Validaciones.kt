package com.sabornatural.tienda.ui.comun

import android.util.Patterns
import com.google.android.material.textfield.TextInputLayout

/** Reglas de validación de formularios (documento técnico, RF-AUT-01 y RF-AUT-03). */
object Validaciones {

    fun esCorreoValido(correo: String): Boolean = Patterns.EMAIL_ADDRESS.matcher(correo.trim()).matches()

    /** Teléfono celular colombiano: 10 dígitos que empiezan por 3. */
    fun esTelefonoValido(telefono: String): Boolean = Regex("^3\\d{9}$").matches(telefono.trim())

    /** Mínimo 8 caracteres, con al menos una mayúscula y un número. */
    fun esClaveValida(clave: String): Boolean =
        clave.length >= 8 && clave.any { it.isUpperCase() } && clave.any { it.isDigit() }

    /**
     * Fortaleza de la contraseña de 0 a 4: un punto por longitud (8+), mayúscula, número
     * y símbolo. Alimenta el indicador de la pantalla de registro.
     */
    fun fortalezaClave(clave: String): Int = listOf(
        clave.length >= 8,
        clave.any { it.isUpperCase() },
        clave.any { it.isDigit() },
        clave.any { !it.isLetterOrDigit() },
    ).count { it }
}

/** Cierra el teclado en pantalla (al enviar un formulario). */
fun android.app.Activity.ocultarTeclado() {
    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        .hide(androidx.core.view.WindowInsetsCompat.Type.ime())
}

/** Texto del campo, sin espacios al inicio ni al final. */
val TextInputLayout.texto: String get() = editText?.text?.toString()?.trim().orEmpty()

/** Muestra o quita el mensaje de error del campo. Devuelve true si el campo es válido. */
fun TextInputLayout.validar(esValido: Boolean, mensaje: String): Boolean {
    error = if (esValido) null else mensaje
    // Sin error se libera el espacio reservado para el mensaje, así el formulario no deja huecos
    isErrorEnabled = !esValido
    return esValido
}
