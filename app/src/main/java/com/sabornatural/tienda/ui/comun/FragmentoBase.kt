package com.sabornatural.tienda.ui.comun

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

/**
 * Base de todas las pantallas que son Fragments. Se encarga del ViewBinding: infla el layout
 * y libera la referencia cuando la vista se destruye (evita fugas de memoria).
 *
 * Uso: class InicioFragment : FragmentoBase<FragmentInicioBinding>(FragmentInicioBinding::inflate)
 */
abstract class FragmentoBase<VB : ViewBinding>(
    private val inflar: (LayoutInflater, ViewGroup?, Boolean) -> VB,
) : Fragment() {

    private var _binding: VB? = null

    /** Acceso a las vistas del layout. Solo es válido entre onCreateView y onDestroyView. */
    protected val binding: VB get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflar(inflater, container, false).also { _binding = it }.root

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
