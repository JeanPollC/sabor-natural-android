package com.sabornatural.tienda.datos.modelo

import androidx.annotation.StringRes
import com.sabornatural.tienda.R

/** Roles de la tienda (documento técnico, Tabla 1). */
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
 * Usuario de la aplicación. La contraseña nunca se guarda en texto plano: solo su hash SHA-256.
 */
data class Usuario(
    val id: Int,
    val nombre: String,
    val apellido: String,
    val correo: String,
    val telefono: String,
    val rol: Rol,
    val estado: EstadoCuenta,
    val claveHash: String,
    val fechaRegistro: String,
) {
    val nombreCompleto: String get() = "$nombre $apellido"

    /** Iniciales para el avatar, por ejemplo "LG" para Laura Gómez. */
    val iniciales: String
        get() = listOf(nombre, apellido)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
}
