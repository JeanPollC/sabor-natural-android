package com.sabornatural.tienda.datos.remoto

import android.graphics.Bitmap
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Subida y borrado de fotos en Firebase Storage (RF-PRO-02, RF-PER-03).
 *
 * Rutas usadas (las protege storage.rules):
 *  - productos/{productoId}.jpg   foto del producto (la sube su vendedor)
 *  - usuarios/{uid}/perfil.jpg    foto de perfil (la sube su dueño)
 */
object Archivos {

    /** Ruta de la foto de un producto. */
    fun rutaProducto(productoId: String) = "productos/$productoId.jpg"

    /** Ruta de la foto de perfil de un usuario. */
    fun rutaPerfil(uid: String) = "usuarios/$uid/perfil.jpg"

    /**
     * Comprime [foto] a JPEG (calidad 85), la sube a [ruta] y devuelve su enlace de descarga,
     * que es lo que se guarda en Firestore y lo que carga la app para mostrarla.
     */
    suspend fun subirFoto(ruta: String, foto: Bitmap): String {
        val bytes = withContext(Dispatchers.Default) {
            ByteArrayOutputStream().use { salida ->
                foto.compress(Bitmap.CompressFormat.JPEG, 85, salida)
                salida.toByteArray()
            }
        }
        val referencia = Nube.storage.reference.child(ruta)
        referencia.putBytes(bytes, storageMetadata { contentType = "image/jpeg" }).await()
        return referencia.downloadUrl.await().toString()
    }

    /** Borra el archivo de [ruta]; si no existía, no es un error. */
    suspend fun borrar(ruta: String) {
        try {
            Nube.storage.reference.child(ruta).delete().await()
        } catch (e: StorageException) {
            if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
        }
    }
}
