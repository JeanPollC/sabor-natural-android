package com.sabornatural.tienda.ui.estilos

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.GridLayout
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.sabornatural.tienda.R
import com.sabornatural.tienda.databinding.ActivityGuiaEstilosBinding
import com.sabornatural.tienda.databinding.ItemMuestraColorBinding
import com.sabornatural.tienda.ui.comun.PantallaBase
import com.sabornatural.tienda.ui.comun.rellenarBarraDeEstado

/**
 * Pantalla de referencia del sistema de diseño (fase 1).
 * Muestra la paleta, los botones, los campos, las etiquetas y una tarjeta de producto
 * con los estilos definidos en res/values, para validarlos en el emulador.
 * Se abre manteniendo presionado el logo de la bienvenida.
 */
class GuiaEstilosActivity : PantallaBase() {

    private lateinit var binding: ActivityGuiaEstilosBinding

    /** Colores de la paleta (documento técnico, Tabla 17) con el nombre que se muestra. */
    private val paleta = listOf(
        Muestra(R.color.verde_bosque, R.string.guia_color_principal),
        Muestra(R.color.verde_claro, R.string.guia_color_claro),
        Muestra(R.color.lima, R.string.guia_color_lima),
        Muestra(R.color.dorado_tierra, R.string.guia_color_dorado),
        Muestra(R.color.crema, R.string.guia_color_crema),
        Muestra(R.color.rojo, R.string.guia_color_rojo),
        Muestra(R.color.ambar, R.string.guia_color_ambar),
        Muestra(R.color.gris_carbon, R.string.guia_color_carbon),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuiaEstilosBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.encabezado.rellenarBarraDeEstado()

        llenarPaleta()
    }

    /** Infla una muestra por color y la agrega a la grilla de 4 columnas. */
    private fun llenarPaleta() {
        paleta.forEach { muestra ->
            val item = ItemMuestraColorBinding.inflate(layoutInflater, binding.grillaPaleta, false)
            // Relleno del color y borde gris: así los tonos claros (crema) se distinguen del fondo
            item.muestra.background = GradientDrawable().apply {
                cornerRadius = resources.getDimension(R.dimen.radio_m)
                setColor(ContextCompat.getColor(this@GuiaEstilosActivity, muestra.color))
                setStroke(
                    resources.getDimensionPixelSize(R.dimen.borde_fino),
                    ContextCompat.getColor(this@GuiaEstilosActivity, R.color.borde),
                )
            }
            item.nombre.setText(muestra.nombre)

            // Cada celda ocupa una columna con el mismo peso, para repartir el ancho por igual
            item.root.layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
            ).apply { width = 0 }

            binding.grillaPaleta.addView(item.root)
        }
    }

    private data class Muestra(@param:ColorRes val color: Int, @param:StringRes val nombre: Int)
}
