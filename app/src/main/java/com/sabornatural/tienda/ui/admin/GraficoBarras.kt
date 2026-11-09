package com.sabornatural.tienda.ui.admin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.sabornatural.tienda.R

/**
 * Gráfico de barras verticales hecho a mano (vista personalizada): se dibuja en onDraw con
 * Canvas y Paint, sin bibliotecas externas. Cada barra tiene una etiqueta abajo, su valor
 * arriba y un color propio.
 */
class GraficoBarras @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Una barra del gráfico. */
    data class Barra(val etiqueta: String, val valor: Long, val textoValor: String, @param:ColorInt val color: Int)

    private var barras: List<Barra> = emptyList()

    private val pincelBarra = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pincelEtiqueta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.texto_secundario)
        textSize = sp(11f)
        textAlign = Paint.Align.CENTER
    }
    private val pincelValor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.texto_principal)
        textSize = sp(11f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val pincelBase = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.borde)
        strokeWidth = dp(1f)
    }
    private val rect = RectF()

    fun mostrar(nuevas: List<Barra>) {
        barras = nuevas
        invalidate() // pide a Android que vuelva a llamar a onDraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (barras.isEmpty()) return

        val altoEtiqueta = sp(28f)
        val altoValor = sp(18f)
        val base = height - altoEtiqueta
        val altoUtil = base - altoValor
        val ancho = width.toFloat() / barras.size
        val maximo = barras.maxOf { it.valor }.coerceAtLeast(1)

        canvas.drawLine(0f, base, width.toFloat(), base, pincelBase)
        barras.forEachIndexed { i, barra ->
            val centro = ancho * i + ancho / 2
            val alto = altoUtil * barra.valor / maximo
            val mitad = ancho * 0.28f
            rect.set(centro - mitad, base - alto, centro + mitad, base)
            pincelBarra.color = barra.color
            canvas.drawRoundRect(rect, dp(6f), dp(6f), pincelBarra)
            if (barra.valor > 0) canvas.drawText(barra.textoValor, centro, base - alto - dp(6f), pincelValor)
            canvas.drawText(barra.etiqueta, centro, base + sp(16f), pincelEtiqueta)
        }
    }

    private fun dp(valor: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valor, resources.displayMetrics)
    private fun sp(valor: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, valor, resources.displayMetrics)
}
