package com.sabornatural.tienda.datos.remoto

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.sabornatural.tienda.datos.EstadoCarga
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Copia local, siempre actualizada, del resultado de una consulta de Firestore.
 *
 * Usa un "snapshot listener": Firestore avisa cada vez que cambia un documento de la consulta
 * (lo cambie este teléfono u otro) y la lista se reemplaza. Las pantallas observan [cambios]
 * como Flow y se redibujan solas; además pueden leer [datos] en cualquier momento.
 *
 * @param convertir pasa un documento de Firestore al modelo de la app (null si está dañado).
 */
class ColeccionEnVivo<T>(private val convertir: (DocumentSnapshot) -> T?) {

    private val _datos = MutableStateFlow<List<T>>(emptyList())
    private val _estado = MutableStateFlow<EstadoCarga>(EstadoCarga.Cargando)

    /** Elementos de la consulta, en el orden en que los entrega Firestore. */
    val datos: StateFlow<List<T>> = _datos.asStateFlow()

    /** Cargando, listo o error. */
    val estado: StateFlow<EstadoCarga> = _estado.asStateFlow()

    /** Emite cada vez que cambian los datos o el estado: sirve para redibujar una pantalla. */
    val cambios: Flow<Unit> = combine(_datos, _estado) { _, _ -> }

    private var registro: ListenerRegistration? = null
    private var clave: String? = null
    private var consulta: Query? = null

    /**
     * Empieza a escuchar [consulta]. Si ya se escuchaba la misma [claveConsulta], no hace nada
     * (así se puede llamar en cada onCreate sin abrir escuchas repetidas).
     */
    fun escuchar(claveConsulta: String, consulta: Query) {
        if (claveConsulta == clave && registro != null) return
        detener()
        clave = claveConsulta
        this.consulta = consulta
        conectar(consulta)
    }

    /** Registra el listener de Firestore y traduce cada respuesta a datos + estado. */
    private fun conectar(consulta: Query) {
        _estado.value = EstadoCarga.Cargando
        registro = consulta.addSnapshotListener { instantanea, error ->
            if (error != null) {
                _estado.value = EstadoCarga.Error(Errores.mensaje(error))
                return@addSnapshotListener
            }
            _datos.value = instantanea?.documents.orEmpty().mapNotNull(convertir)
            _estado.value = EstadoCarga.Listo
        }
    }

    /** Vuelve a abrir la escucha tras un error (botón "Reintentar"). */
    fun reintentar() {
        val actual = consulta ?: return
        registro?.remove()
        conectar(actual)
    }

    /** Cierra la escucha y vacía la lista (al cerrar sesión o cambiar de usuario). */
    fun detener() {
        registro?.remove()
        registro = null
        clave = null
        consulta = null
        _datos.value = emptyList()
        _estado.value = EstadoCarga.Cargando
    }

    /**
     * Agrega o reemplaza un elemento en la copia local sin esperar a Firestore. Se usa justo
     * después de escribir, para que la pantalla siguiente ya lo encuentre.
     */
    fun reemplazarLocal(elemento: T, mismoElemento: (T) -> Boolean) {
        val lista = _datos.value
        val i = lista.indexOfFirst(mismoElemento)
        _datos.value = if (i >= 0) lista.toMutableList().also { it[i] = elemento } else lista + elemento
    }
}
