package com.sabornatural.tienda.ui.admin

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import com.google.android.material.chip.Chip
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.RepositorioPedidos
import com.sabornatural.tienda.datos.modelo.Categoria
import com.sabornatural.tienda.datos.modelo.EstadoPedido
import com.sabornatural.tienda.datos.modelo.Pedido
import com.sabornatural.tienda.databinding.FragmentReporteVentasBinding
import com.sabornatural.tienda.databinding.ItemBarraProgresoBinding
import com.sabornatural.tienda.databinding.ItemLineaPedidoBinding
import com.sabornatural.tienda.ui.compartido.ModoPedidos
import com.sabornatural.tienda.ui.comprador.Argumentos
import com.sabornatural.tienda.ui.comun.AdaptadorPedidos
import com.sabornatural.tienda.ui.comun.Formatos
import com.sabornatural.tienda.ui.comun.FragmentoBase
import com.sabornatural.tienda.ui.comun.configurar
import com.sabornatural.tienda.ui.comun.navegar
import com.sabornatural.tienda.ui.vendedor.mostrar
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Reporte de ventas del administrador: totales del periodo, gráfico por categoría, ventas por
 * productor, productos más vendidos y últimos pedidos. Se calcula con los pedidos pagados y no
 * cancelados, que llegan en vivo desde Firestore.
 */
class ReporteVentasFragment : FragmentoBase<FragmentReporteVentasBinding>(FragmentReporteVentasBinding::inflate) {

    /** Periodos que se pueden consultar en el reporte. */
    private enum class Periodo(val nombre: Int) { SIETE_DIAS(R.string.reporte_7_dias), MES(R.string.reporte_mes), TODO(R.string.reporte_todo) }

    private var periodo = Periodo.MES
    private val adaptador = AdaptadorPedidos(verComprador = true) {
        navegar(R.id.detallePedidoFragment, bundleOf(Argumentos.PEDIDO_ID to it.id, Argumentos.MODO to ModoPedidos.ADMINISTRADOR.name))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.barra.configurar(this, getString(R.string.reporte_titulo), principal = true)
        Periodo.entries.forEach { p ->
            binding.fichasPeriodo.addView(Chip(requireContext()).apply {
                id = View.generateViewId()
                setText(p.nombre)
                isCheckable = true
                isChecked = p == periodo
                tag = p
            })
        }
        binding.fichasPeriodo.setOnCheckedStateChangeListener { grupo, ids ->
            periodo = ids.firstOrNull()?.let { grupo.findViewById<Chip>(it).tag as Periodo } ?: Periodo.MES
            mostrar()
        }
        binding.listaPedidos.adapter = adaptador
        observar(RepositorioPedidos.cambios) { mostrar() }
    }

    /** true si la fecha del pedido cae dentro del periodo elegido. */
    private fun enPeriodo(pedido: Pedido): Boolean {
        val fecha = runCatching { LocalDateTime.parse(pedido.fecha, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) }.getOrNull() ?: return false
        val hoy = LocalDate.now()
        return when (periodo) {
            Periodo.SIETE_DIAS -> !fecha.toLocalDate().isBefore(hoy.minusDays(6))
            Periodo.MES -> fecha.year == hoy.year && fecha.month == hoy.month
            Periodo.TODO -> true
        }
    }

    /** Recalcula el reporte para el periodo elegido. */
    private fun mostrar() {
        val delPeriodo = RepositorioPedidos.todos().filter(::enPeriodo)
        val validos = delPeriodo.filter { it.cuentaComoVenta }
        val total = validos.sumOf { it.total }
        val ticket = if (validos.isEmpty()) 0 else total / validos.size

        binding.indicador1.mostrar(R.drawable.ic_dinero, Formatos.precio(total), getString(R.string.reporte_ventas))
        binding.indicador2.mostrar(R.drawable.ic_pedidos, validos.size.toString(), getString(R.string.reporte_pedidos))
        binding.indicador3.mostrar(R.drawable.ic_grafico, Formatos.precio(ticket), getString(R.string.reporte_ticket))
        binding.indicador4.mostrar(R.drawable.ic_cerrar, delPeriodo.count { it.estado == EstadoPedido.CANCELADO }.toString(), getString(R.string.reporte_cancelados))

        // Gráfico: ventas (sin domicilio) por categoría
        val lineas = validos.flatMap { it.lineas }
        val porCategoria = Categoria.entries.map { cat -> cat to lineas.filter { it.categoria == cat }.sumOf { it.subtotal } }
        binding.grafico.mostrar(porCategoria.map { (cat, valor) ->
            GraficoBarras.Barra(
                etiqueta = getString(cat.nombre),
                valor = valor,
                textoValor = if (valor >= 1000) "${valor / 1000}k" else valor.toString(),
                color = ContextCompat.getColor(requireContext(), cat.color),
            )
        })

        // Ventas por productor, con barra proporcional al mayor
        binding.listaVendedores.removeAllViews()
        val porVendedor = validos.groupBy { it.vendedorNombre }.mapValues { (_, p) -> p.sumOf { it.total } }.toList().sortedByDescending { it.second }
        val maximo = porVendedor.maxOfOrNull { it.second } ?: 1
        porVendedor.forEach { (nombre, valor) ->
            val fila = ItemBarraProgresoBinding.inflate(layoutInflater, binding.listaVendedores, true)
            fila.nombre.text = nombre
            fila.valor.text = Formatos.precio(valor)
            fila.barra.max = 100
            fila.barra.setProgressCompat((valor * 100 / maximo).toInt(), true)
        }
        if (porVendedor.isEmpty()) agregarSinDatos(binding.listaVendedores)

        // Top 5 de productos por unidades
        binding.listaMasVendidos.removeAllViews()
        lineas.groupBy { it.nombre }
            .map { (nombre, ls) -> Triple(nombre, ls.sumOf { it.cantidad }, ls.sumOf { it.subtotal }) }
            .sortedByDescending { it.second }
            .take(5)
            .forEachIndexed { i, (nombre, unidades, valor) ->
                val fila = ItemLineaPedidoBinding.inflate(layoutInflater, binding.listaMasVendidos, true)
                fila.descripcion.text = "${i + 1}. $nombre"
                fila.subtotal.text = getString(R.string.reporte_unidades, unidades, Formatos.precio(valor))
            }
        if (lineas.isEmpty()) agregarSinDatos(binding.listaMasVendidos)

        adaptador.submitList(delPeriodo.take(5))
    }

    /** Fila "Sin datos" para una sección vacía del reporte. */
    private fun agregarSinDatos(contenedor: android.widget.LinearLayout) {
        val fila = ItemLineaPedidoBinding.inflate(layoutInflater, contenedor, true)
        fila.descripcion.setText(R.string.reporte_sin_datos)
        fila.subtotal.text = ""
    }
}
