package com.sabornatural.tienda.ui.comun

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.scale
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.sabornatural.tienda.databinding.HojaOpcionesFotoBinding

/**
 * Elegir una foto con la cámara o la galería (RF-PRO-02, RF-PER-03), usando las herramientas
 * del sistema:
 *  - Cámara: ActivityResultContracts.TakePicturePreview abre la app de cámara del teléfono.
 *    Como la toma otra app, esta no necesita pedir el permiso de cámara.
 *  - Galería: el selector de fotos del sistema (Photo Picker), que no requiere permiso de
 *    almacenamiento: el usuario elige solo la foto que quiere compartir.
 *
 * Debe crearse como propiedad del Fragment (antes de que la pantalla se muestre).
 */
class SelectorFoto(private val fragmento: Fragment, private val alElegir: (Bitmap) -> Unit) {

    private val camara = fragmento.registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { foto ->
        foto?.let(alElegir)
    }

    private val galeria = fragmento.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { leer(it) }?.let(alElegir)
    }

    /** Hoja inferior con las opciones; "Quitar foto" solo aparece si hay una foto puesta. */
    fun mostrarOpciones(hayFoto: Boolean, alQuitar: () -> Unit) {
        val hoja = BottomSheetDialog(fragmento.requireContext())
        val vista = HojaOpcionesFotoBinding.inflate(fragmento.layoutInflater)
        vista.opcionCamara.setOnClickListener { hoja.dismiss(); camara.launch(null) }
        vista.opcionGaleria.setOnClickListener {
            hoja.dismiss()
            galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        vista.opcionQuitar.visibility = if (hayFoto) View.VISIBLE else View.GONE
        vista.opcionQuitar.setOnClickListener { hoja.dismiss(); alQuitar() }
        hoja.setContentView(vista.root)
        hoja.show()
    }

    /** Lee la imagen elegida y la reduce a máximo 1024 px (RF-PRO-02: fotos livianas). */
    private fun leer(uri: Uri): Bitmap? = runCatching {
        val resolver = fragmento.requireContext().contentResolver
        val original = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decodificador, _, _ ->
                decodificador.isMutableRequired = false
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(resolver, uri)
        }
        val escala = 1024f / maxOf(original.width, original.height)
        if (escala < 1f) original.scale((original.width * escala).toInt(), (original.height * escala).toInt()) else original
    }.getOrNull()
}
