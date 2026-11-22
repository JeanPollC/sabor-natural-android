package com.sabornatural.tienda.datos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.sabornatural.tienda.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Geolocalización de la dirección de entrega (RF-PED-02), sin Google Maps ni clave de API:
 *  1. FusedLocationProviderClient (Google Play Services) da la latitud y longitud actuales.
 *  2. Geocoder (incluido en Android) convierte esas coordenadas en una dirección escrita.
 *
 * El permiso de ubicación lo pide la pantalla (PasarelaFragment) en tiempo de ejecución; si el
 * usuario lo niega, escribe la dirección a mano.
 */
object Ubicacion {

    /** Ubicación capturada: coordenadas y, si se pudo, la dirección legible. */
    data class Resultado(val latitud: Double, val longitud: Double, val direccion: String?)

    /** Permisos que se piden: precisa (GPS) y aproximada (redes), el usuario elige cuál conceder. */
    val PERMISOS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** true si el usuario ya concedió alguno de los dos permisos de ubicación. */
    fun tienePermiso(contexto: Context): Boolean = PERMISOS.any {
        ContextCompat.checkSelfPermission(contexto, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Obtiene la ubicación actual y la convierte en dirección.
     *
     * @throws ErrorNegocio si no hay permiso o la ubicación del teléfono está apagada.
     */
    @SuppressLint("MissingPermission") // el permiso se revisa con tienePermiso() justo antes
    suspend fun obtener(contexto: Context): Resultado {
        if (!tienePermiso(contexto)) throw ErrorNegocio(R.string.ubicacion_sin_permiso)
        val cliente = LocationServices.getFusedLocationProviderClient(contexto)
        val token = CancellationTokenSource()
        val ubicacion = try {
            cliente.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).await()
                ?: cliente.lastLocation.await()
        } finally {
            token.cancel()
        }
            ?: throw ErrorNegocio(R.string.ubicacion_apagada)
        val direccion = runCatching { geocodificar(contexto, ubicacion.latitude, ubicacion.longitude) }.getOrNull()
        return Resultado(ubicacion.latitude, ubicacion.longitude, direccion)
    }

    /** Coordenadas → dirección escrita, con el Geocoder del sistema (necesita internet). */
    private suspend fun geocodificar(contexto: Context, latitud: Double, longitud: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(contexto, Locale.Builder().setLanguage("es").setRegion("CO").build())
        val direcciones: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: versión asíncrona con listener
            suspendCancellableCoroutine { continuacion ->
                geocoder.getFromLocation(latitud, longitud, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(resultado: MutableList<Address>) = continuacion.resume(resultado)
                    override fun onError(mensaje: String?) = continuacion.resume(emptyList())
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitud, longitud, 1).orEmpty()
            }
        }
        val a = direcciones.firstOrNull() ?: return null
        return a.getAddressLine(0)
            ?: listOfNotNull(a.thoroughfare, a.subThoroughfare, a.locality).joinToString(" ").ifBlank { null }
    }
}
