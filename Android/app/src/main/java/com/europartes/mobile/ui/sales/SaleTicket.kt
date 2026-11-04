package com.europartes.mobile.ui.sales

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.europartes.mobile.ui.components.MoneyFormat

data class SaleTicketLine(val product: String, val quantity: Int, val unitPrice: String?) {
    val total: String? get() = unitPrice?.toBigDecimalOrNull()?.multiply(quantity.toBigDecimal())?.toPlainString()
}

/** What the sale-confirmation ticket shows, the same content as the web's. */
data class SaleTicket(
    val invoiceId: Int,
    val issuedAtEpochMs: Long,
    val customer: String,
    val lines: List<SaleTicketLine>,
    val subtotal: String,
    val tax: String,
    val total: String,
)

private fun escape(value: String) = value
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

fun SaleTicket.issuedAtText(): String = java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.forLanguageTag("es")).format(java.util.Date(issuedAtEpochMs))

/** 80 mm receipt, the format the web sends to the printer. */
private fun SaleTicket.toHtml(money: MoneyFormat): String {
    val rows = lines.joinToString("") { line ->
        "<tr><td>${escape(line.product)}</td><td class='n'>${line.quantity}</td><td class='n'>${escape(money.format(line.unitPrice))}</td><td class='n'>${escape(money.format(line.total))}</td></tr>"
    }
    return """
        <!doctype html><html><head><meta charset="utf-8"><style>
        @page { size: 80mm auto; margin: 4mm; }
        body { font-family: 'Courier New', Courier, monospace; font-size: 11px; color: #000; margin: 0; }
        h1 { font-size: 15px; text-align: center; margin: 0 0 2px; }
        .c { text-align: center; }
        table { width: 100%; border-collapse: collapse; margin: 8px 0; }
        th, td { padding: 2px 1px; text-align: left; vertical-align: top; }
        th { border-bottom: 1px dashed #000; }
        .n { text-align: right; white-space: nowrap; }
        .t { display: flex; justify-content: space-between; }
        .g { font-weight: bold; font-size: 13px; border-top: 1px dashed #000; margin-top: 3px; padding-top: 3px; }
        </style></head><body>
        <h1>EUROPARTES</h1>
        <div class="c">Factura #$invoiceId</div>
        <div class="c">${escape(issuedAtText())}</div>
        <div class="c">Cliente: ${escape(customer)}</div>
        <table><thead><tr><th>Art.</th><th class="n">Cant</th><th class="n">P.U</th><th class="n">Total</th></tr></thead><tbody>$rows</tbody></table>
        <div class="t"><span>Subtotal</span><span>${escape(money.format(subtotal))}</span></div>
        <div class="t"><span>IVA</span><span>${escape(money.format(tax))}</span></div>
        <div class="t g"><span>TOTAL</span><span>${escape(money.format(total))}</span></div>
        <p class="c">¡Gracias por su compra!<br>Conserve este comprobante</p>
        </body></html>
    """.trimIndent()
}

/**
 * Opens the system print dialog, which also offers "Guardar como PDF". A strong reference to the
 * WebView is kept until the page has loaded, otherwise it can be collected before printing starts.
 */
object SaleTicketPrinter {
    private var printing: WebView? = null

    fun print(context: Context, ticket: SaleTicket, money: MoneyFormat) {
        val webView = WebView(context)
        printing = webView
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val attributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize("ticket80", "Ticket 80 mm", 3150, 11690))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                manager.print("Factura ${ticket.invoiceId}", view.createPrintDocumentAdapter("Factura ${ticket.invoiceId}"), attributes)
                printing = null
            }
        }
        webView.loadDataWithBaseURL(null, ticket.toHtml(money), "text/html", "UTF-8", null)
    }
}
