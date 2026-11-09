package com.sabornatural.tienda

import android.app.Application
import com.sabornatural.tienda.datos.DatosDemo

/**
 * Clase Application: Android la crea antes que cualquier pantalla (se declara en el
 * AndroidManifest con android:name). Aquí se cargan una sola vez los datos de prueba.
 */
class SaborNaturalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DatosDemo.cargar(this)
    }
}
