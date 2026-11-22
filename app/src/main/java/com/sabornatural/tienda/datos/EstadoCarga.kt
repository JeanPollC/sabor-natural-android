package com.sabornatural.tienda.datos

import androidx.annotation.StringRes

/**
 * Estado de una lista que llega desde Firestore. Lo exponen los repositorios y lo pintan las
 * pantallas con `mostrarCarga` (ui/comun/Estados.kt): indicador de carga, mensaje de error con
 * "Reintentar", o la lista.
 */
sealed interface EstadoCarga {
    /** Esperando la primera respuesta del servidor (o de la caché local de Firestore). */
    data object Cargando : EstadoCarga

    /** Los datos ya están disponibles. */
    data object Listo : EstadoCarga

    /** Falló la consulta; [mensaje] es un texto de res/values listo para mostrar. */
    data class Error(@param:StringRes val mensaje: Int) : EstadoCarga
}

/**
 * Error de una regla de negocio (no técnico) con un mensaje para el usuario, por ejemplo
 * "tu cuenta está pendiente de aprobación". Lo traduce `Errores.mensaje`.
 */
class ErrorNegocio(@param:StringRes val mensaje: Int) : Exception()
