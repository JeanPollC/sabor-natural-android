package com.sabornatural.tienda.ui.comun

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

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

    /**
     * Observa un Flow (por ejemplo, los cambios de un repositorio de Firestore) mientras la
     * pantalla está visible: cada valor nuevo llama a [accion]. Al ocultarse la pantalla se deja
     * de observar y al volver se retoma con el valor más reciente (repeatOnLifecycle).
     */
    protected fun <T> observar(flujo: Flow<T>, accion: (T) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flujo.collect { accion(it) }
            }
        }
    }
}
