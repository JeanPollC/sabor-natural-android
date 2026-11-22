package com.sabornatural.tienda.ui.comprador

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.pagos.PasarelaWompi
import com.sabornatural.tienda.databinding.ActivityPagoWompiBinding
import com.sabornatural.tienda.ui.comun.PantallaBase
import com.sabornatural.tienda.ui.comun.rellenarBarraDeEstado
import com.sabornatural.tienda.ui.comun.rellenarBarraDeNavegacion

/**
 * Web Checkout de Wompi dentro de la app (sandbox).
 *
 * Abre en un WebView la URL del checkout que arma [PasarelaWompi.urlCheckout]. El comprador
 * elige el medio (tarjeta, Nequi, PSE...) y paga en la página de Wompi. Cuando Wompi redirige a
 * la URL de regreso, el WebView la intercepta sin cargarla, toma el id de la transacción y lo
 * devuelve a PasarelaFragment, que pide al servidor verificar el pago.
 *
 * Si el comprador sale con "atrás" o la X, se confirma y la Activity termina como cancelada.
 */
class PagoWompiActivity : PantallaBase() {

    private lateinit var binding: ActivityPagoWompiBinding
    private var terminado = false

    @SuppressLint("SetJavaScriptEnabled") // el checkout de Wompi funciona con JavaScript
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPagoWompiBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.barra.rellenarBarraDeEstado()
        binding.web.rellenarBarraDeNavegacion()
        binding.barra.setNavigationOnClickListener { confirmarSalida() }
        if (PasarelaWompi.pruebas) binding.barra.subtitle = getString(R.string.wompi_modo_pruebas)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.web.canGoBack()) binding.web.goBack() else confirmarSalida()
            }
        })

        with(binding.web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
        }
        binding.web.webViewClient = Cliente()

        if (savedInstanceState != null) {
            binding.web.restoreState(savedInstanceState)
        } else {
            binding.web.loadUrl(intent.getStringExtra(EXTRA_URL).orEmpty())
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.web.saveState(outState)
    }

    override fun onDestroy() {
        binding.web.destroy()
        super.onDestroy()
    }

    /** Intercepta la URL de regreso y abre fuera de la app los enlaces que no son web (bancos, Nequi). */
    private inner class Cliente : WebViewClient() {

        override fun shouldOverrideUrlLoading(vista: WebView, solicitud: WebResourceRequest): Boolean =
            manejar(solicitud.url.toString())

        override fun onPageStarted(vista: WebView, url: String, favicon: Bitmap?) {
            // Algunas redirecciones no pasan por shouldOverrideUrlLoading: se revisan aquí también
            if (manejar(url)) {
                vista.stopLoading()
                return
            }
            binding.progreso.visibility = View.VISIBLE
        }

        override fun onPageFinished(vista: WebView, url: String) {
            binding.progreso.visibility = View.GONE
        }

        /** @return true si la URL se atendió aquí y el WebView no debe cargarla. */
        private fun manejar(url: String): Boolean {
            PasarelaWompi.transaccionDe(url)?.let { transaccion ->
                terminar(transaccion)
                return true
            }
            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:") || url.startsWith("data:")) return false
            // intent://, nequi://, etc.: los abre la app correspondiente si está instalada
            try {
                val destino = if (url.startsWith("intent:")) Intent.parseUri(url, Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, Uri.parse(url))
                destino.addCategory(Intent.CATEGORY_BROWSABLE)
                destino.component = null
                destino.selector = null
                startActivity(destino)
            } catch (_: ActivityNotFoundException) {
                // Sin app que lo abra: se queda en la página de Wompi
            } catch (_: Exception) {
            }
            return true
        }
    }

    /** Devuelve el id de la transacción a la pasarela (puede venir vacío si Wompi no lo envió). */
    private fun terminar(transaccion: String) {
        if (terminado) return
        terminado = true
        setResult(RESULT_OK, Intent().putExtra(RESULTADO_TRANSACCION, transaccion))
        finish()
    }

    /** Confirma antes de abandonar el pago. */
    private fun confirmarSalida() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.wompi_salir_titulo)
            .setMessage(R.string.wompi_salir_texto)
            .setPositiveButton(R.string.wompi_salir_si) { _, _ ->
                terminado = true
                setResult(RESULT_CANCELED)
                finish()
            }
            .setNegativeButton(R.string.wompi_salir_no, null)
            .show()
    }

    companion object {
        private const val EXTRA_URL = "url"

        /** Extra del resultado con el id de la transacción de Wompi. */
        const val RESULTADO_TRANSACCION = "transaccion_id"

        /** Intent para abrir el checkout en [urlCheckout]. */
        fun intent(contexto: Context, urlCheckout: String): Intent =
            Intent(contexto, PagoWompiActivity::class.java).putExtra(EXTRA_URL, urlCheckout)
    }
}
