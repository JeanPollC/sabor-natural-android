package com.sabornatural.tienda.datos.modelo

import androidx.annotation.StringRes
import com.sabornatural.tienda.R

/** Roles de la tienda (documento técnico, Tabla 1). Se guardan en Firestore por su nombre. */
enum class Rol(@param:StringRes val nombre: Int) {
    COMPRADOR(R.string.rol_comprador),
    VENDEDOR(R.string.rol_vendedor),
    ADMINISTRADOR(R.string.rol_administrador),
}

/** Estado de una cuenta: un vendedor nuevo queda PENDIENTE hasta que el administrador lo aprueba. */
enum class EstadoCuenta(@param:StringRes val nombre: Int) {
    ACTIVO(R.string.estado_activo),
    PENDIENTE(R.string.estado_pendiente),
    SUSPENDIDO(R.string.estado_suspendido),
}

/**
 * Perfil de un usuario, guardado en Firestore en `usuarios/{id}`.
 *
 * El [id] es el uid que asigna Firebase Authentication. La contraseña no forma parte del perfil:
 * la custodia Firebase Authentication y la app nunca la guarda.
 *
 * @property fechaRegistro fecha de alta en formato ISO (yyyy-MM-dd).
 * @property fotoUrl enlace de descarga de la foto de perfil en Firebase Storage, o vacío.
 */
data class Usuario(
    val id: String,
    val nombre: String,
    val apellido: String,
    val correo: String,
    val telefono: String,
    val rol: Rol,
    val estado: EstadoCuenta,
    val fechaRegistro: String,
    val fotoUrl: String = "",
) {
    /** Nombre y apellido juntos, como se muestran en las tarjetas y encabezados. */
    val nombreCompleto: String get() = "$nombre $apellido"

    /** Iniciales para el avatar, por ejemplo "LG" para Laura Gómez. */
    val iniciales: String
        get() = listOf(nombre, apellido)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
}
