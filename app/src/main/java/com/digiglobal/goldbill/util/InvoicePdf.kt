package com.digiglobal.goldbill.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.digiglobal.goldbill.data.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Draws an A4 invoice (tax invoice, purchase voucher or estimate) to a PDF file:
 * shop header with logo and GSTIN, customer, items, old gold, GST breakup, amount in words,
 * bank details, remarks, terms and signatures. Long invoices continue on more pages.
 */
class InvoicePdf(private val shop: ShopSettings, private val shopName: String, private val logo: String,
                 private val photos: Map<String, String> = emptyMap()) {

    private val W = 595f
    private val H = 842f
    private val M = 28f
    private val CW = W - 2 * M
    private val bottom = H - 36f

    private val red = Color.rgb(0xE3, 0x26, 0x2F)
    private val ink = Color.rgb(0x14, 0x14, 0x14)
    private val muted = Color.rgb(0x5C, 0x5C, 0x63)
    private val line = Color.rgb(0xD9, 0xD9, 0xDE)
    private val shade = Color.rgb(0xF4, 0xF4, 0xF5)
    private val pink = Color.rgb(0xFD, 0xE8, 0xE9)

    private lateinit var doc: PdfDocument
    private lateinit var page: PdfDocument.Page
    private lateinit var c: Canvas
    private var y = 0f
    private var pageNo = 0
    private lateinit var inv: Invoice

    private fun tp(size: Float, bold: Boolean = false, color: Int = ink) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private val date = SimpleDateFormat("dd-MMM-yyyy, hh:mm a", Locale.ENGLISH)

    fun write(invoice: Invoice, out: File): File {
        inv = invoice
        doc = PdfDocument()
        newPage()
        header()
        parties()
        if (inv.kind.hasNewItems && inv.items.isNotEmpty()) itemsTable()
        if (inv.kind.hasOldItems && inv.oldItems.isNotEmpty()) oldTable()
        totalsBlock()
        termsAndSign()
        finishPage()
        out.parentFile?.mkdirs()
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }

    // ---------------- page handling ----------------

    private fun newPage() {
        pageNo++
        page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pageNo).create())
        c = page.canvas
        c.drawRect(0f, 0f, W, 5f, Paint().apply { color = red })
        y = M
        if (pageNo > 1) {
            text("${shopName} · ${inv.kind.docTitle} ${inv.number} (continued)", M, y, tp(8.5f, color = muted))
            y += 18f
        }
    }

    private fun finishPage() {
        val p = tp(7.5f, color = muted)
        text("This is a computer-generated document.", M, H - 26f, p)
        text("Page $pageNo", W - M, H - 26f, p, Paint.Align.RIGHT)
        if (inv.isCancelled) watermark()
        doc.finishPage(page)
    }

    private fun ensure(h: Float) {
        if (y + h > bottom) { finishPage(); newPage() }
    }

    private fun watermark() {
        c.save()
        c.rotate(-30f, W / 2, H / 2)
        val p = tp(90f, true, Color.argb(45, 0xE3, 0x26, 0x2F))
        text("CANCELLED", W / 2, H / 2, p, Paint.Align.CENTER)
        c.restore()
    }

    // ---------------- drawing helpers ----------------

    private fun text(s: String, x: Float, top: Float, p: TextPaint, align: Paint.Align = Paint.Align.LEFT) {
        p.textAlign = align
        c.drawText(s, x, top - p.fontMetrics.ascent, p)
        p.textAlign = Paint.Align.LEFT
    }

    /** Wrapped text; returns its height. Draws only when draw = true. */
    private fun block(s: String, x: Float, top: Float, width: Float, p: TextPaint, draw: Boolean = true,
                      align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): Float {
        if (s.isEmpty()) return 0f
        val l = StaticLayout.Builder.obtain(s, 0, s.length, p, width.toInt().coerceAtLeast(10))
            .setAlignment(align).setLineSpacing(0f, 1.1f).setIncludePad(false).build()
        if (draw) { c.save(); c.translate(x, top); l.draw(c); c.restore() }
        return l.height.toFloat()
    }

    private fun hline(yy: Float, color: Int = line, w: Float = 0.7f, x1: Float = M, x2: Float = W - M) {
        c.drawLine(x1, yy, x2, yy, Paint().apply { this.color = color; strokeWidth = w })
    }

    private fun rect(l: Float, t: Float, r: Float, b: Float, color: Int) =
        c.drawRect(l, t, r, b, Paint().apply { this.color = color })

    private fun money(v: Double) = Billing.inr(v)
    private fun g(v: Double) = "%.3f".format(Locale.US, v)

    // ---------------- sections ----------------

    private fun header() {
        val logoSize = 58f
        var textX = M
        Images.decode(logo)?.let { bmp ->
            val scale = minOf(logoSize / bmp.width, logoSize / bmp.height)
            val w = bmp.width * scale; val h = bmp.height * scale
            c.drawBitmap(bmp, null, RectF(M, y, M + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
            textX = M + logoSize + 10f
        }
        val nameW = CW * 0.62f - (textX - M)
        var hy = y
        hy += block(shopName.ifBlank { shop.name }, textX, hy, nameW, tp(16f, true)) + 2f
        val lines = listOfNotNull(
            shop.address.takeIf { it.isNotBlank() },
            listOf(shop.phone.takeIf { it.isNotBlank() }?.let { "Ph: $it" }, shop.email.takeIf { it.isNotBlank() })
                .filterNotNull().joinToString("  ·  ").takeIf { it.isNotBlank() },
            listOf(shop.gstin.takeIf { it.isNotBlank() }?.let { "GSTIN: $it" }, shop.pan.takeIf { it.isNotBlank() }?.let { "PAN: $it" })
                .filterNotNull().joinToString("  ·  ").takeIf { it.isNotBlank() },
            Billing.stateLabel(shop.stateCode).takeIf { it.isNotBlank() }?.let { "State: $it" }
        )
        lines.forEach { hy += block(it, textX, hy, nameW, tp(8.5f, color = muted)) + 1f }

        // title on the right
        val tx = W - M
        text(inv.kind.docTitle, tx, y, tp(18f, true, red), Paint.Align.RIGHT)
        var ry = y + 24f
        val sub = when (inv.kind) {
            InvoiceType.SALE, InvoiceType.EXCHANGE -> if (inv.kind == InvoiceType.EXCHANGE) "Exchange — Original for Recipient" else "Original for Recipient"
            InvoiceType.PURCHASE -> "Purchase of old gold / silver"
            InvoiceType.ESTIMATE -> "Not a tax invoice"
        }
        text(sub, tx, ry, tp(8.5f, color = muted), Paint.Align.RIGHT); ry += 13f
        if (inv.isCancelled) { text("CANCELLED", tx, ry, tp(10f, true, red), Paint.Align.RIGHT); ry += 13f }
        y = maxOf(hy, ry, y + logoSize) + 8f
        hline(y, red, 1.2f)
        y += 8f
    }

    private fun parties() {
        val colW = CW / 2 - 6
        val cu = inv.customer
        val left = buildList {
            add(cu.name.ifBlank { "Walk-in customer" })
            if (cu.phone.isNotBlank()) add("Phone: ${cu.phone}")
            if (cu.email.isNotBlank()) add("Email: ${cu.email}")
            if (cu.address.isNotBlank()) add(cu.address)
            if (cu.gstin.isNotBlank()) add("GSTIN: ${cu.gstin}")
            if (cu.pan.isNotBlank()) add("PAN: ${cu.pan}")
            if (inv.kind != InvoiceType.ESTIMATE) IdProof.printable(cu).takeIf { it.isNotEmpty() }?.let { add(it.joinToString("  ·  ")) }
            Billing.stateLabel(cu.stateCode.ifBlank { shop.stateCode }).takeIf { it.isNotBlank() }?.let { add("State: $it") }
        }
        val pos = Billing.stateLabel(inv.customer.stateCode.ifBlank { shop.stateCode })
        val right = buildList {
            add("${if (inv.kind == InvoiceType.PURCHASE) "Voucher" else if (inv.kind == InvoiceType.ESTIMATE) "Estimate" else "Invoice"} No: ${inv.number}")
            add("Date: ${date.format(Date(inv.at))}")
            if (inv.kind != InvoiceType.PURCHASE && pos.isNotBlank()) add("Place of supply: $pos")
            add("Payment: ${inv.payMode}")
            if (inv.createdByName.isNotBlank()) add("Billed by: ${inv.createdByName}")
            if (inv.editCount > 0 && inv.editedAt > 0) add("Revised: ${date.format(Date(inv.editedAt))}")
        }
        val pBody = tp(9f)
        fun height(lines: List<String>) = 14f + lines.sumOf { block(it, 0f, 0f, colW - 16, pBody, draw = false).toDouble() + 2.0 }.toFloat()
        val h = maxOf(height(left), height(right)) + 10f
        ensure(h)
        rect(M, y, M + colW, y + h, shade)
        rect(M + colW + 12, y, W - M, y + h, shade)
        text(if (inv.kind == InvoiceType.PURCHASE) "PURCHASED FROM" else "BILL TO", M + 8, y + 7, tp(7.5f, true, red))
        text("DETAILS", M + colW + 20, y + 7, tp(7.5f, true, red))
        var ly = y + 20f
        left.forEachIndexed { i, s -> ly += block(s, M + 8, ly, colW - 16, if (i == 0) tp(10f, true) else pBody) + 2f }
        var ry = y + 20f
        right.forEach { s -> ry += block(s, M + colW + 20, ry, colW - 16, pBody) + 2f }
        y += h + 10f
    }

    private data class Col(val title: String, val w: Float, val right: Boolean = true)

    private fun tableHeader(cols: List<Col>, title: String) {
        ensure(40f)
        text(title, M, y, tp(9f, true, ink)); y += 14f
        rect(M, y, W - M, y + 18f, red)
        var x = M
        cols.forEach { col ->
            val p = tp(8f, true, Color.WHITE)
            if (col.right) text(col.title, x + col.w - 4, y + 5, p, Paint.Align.RIGHT) else text(col.title, x + 4, y + 5, p)
            x += col.w
        }
        y += 18f
    }

    private val thumb = 38f

    private fun row(cols: List<Col>, cells: List<String>, sub: String?, shaded: Boolean, photoId: String = "") {
        val p = tp(8.5f)
        val subP = tp(7.5f, color = muted)
        val pic = photos[photoId]?.let { Images.decode(it) }
        val shift = if (pic != null) thumb + 6f else 0f
        val descW = cols[1].w - 8 - shift
        val textH = block(cells[1], 0f, 0f, descW, p, false) + (sub?.let { block(it, 0f, 0f, descW, subP, false) + 2f } ?: 0f)
        val h = maxOf(14f, textH, if (pic != null) thumb else 0f) + 8f
        ensure(h)
        if (shaded) rect(M, y, W - M, y + h, shade)
        var x = M
        cols.forEachIndexed { i, col ->
            if (i == 1) {
                if (pic != null) {
                    // Product photo, centre-cropped to a square.
                    val side = minOf(pic.width, pic.height)
                    val src = android.graphics.Rect((pic.width - side) / 2, (pic.height - side) / 2, (pic.width + side) / 2, (pic.height + side) / 2)
                    c.drawBitmap(pic, src, RectF(x + 4, y + 4, x + 4 + thumb, y + 4 + thumb), Paint(Paint.FILTER_BITMAP_FLAG))
                    c.drawRect(x + 4, y + 4, x + 4 + thumb, y + 4 + thumb, Paint().apply { style = Paint.Style.STROKE; color = line; strokeWidth = 0.6f })
                }
                var yy = y + 4
                yy += block(cells[i], x + 4 + shift, yy, descW, p)
                if (sub != null) block(sub, x + 4 + shift, yy + 2, descW, subP)
            } else if (col.right) text(cells[i], x + col.w - 4, y + 4, p, Paint.Align.RIGHT)
            else text(cells[i], x + 4, y + 4, p)
            x += col.w
        }
        y += h
        hline(y)
    }

    private fun itemsTable() {
        val cols = listOf(Col("#", 18f, false), Col("Description", 165f, false), Col("Pcs", 26f), Col("Gross wt", 52f),
            Col("Net wt", 52f), Col("Rate/g", 62f), Col("Making", 70f), Col("Amount ₹", CW - 445f))
        tableHeader(cols, "ITEMS")
        inv.items.forEachIndexed { i, it ->
            val desc = listOf(it.description.ifBlank { "${it.metal} item" }, "${it.metal} ${it.purity}").joinToString(" — ")
            val sub = buildList {
                if (it.hsn.isNotBlank()) add("HSN ${it.hsn}")
                if (it.huid.isNotBlank()) add("HUID ${it.huid}")
                if (it.stoneWt > 0) add("Stone wt ${g(it.stoneWt)} g")
                if (it.wastagePct > 0) add("Wastage ${it.wastagePct}% (${g(it.chargeableWt)} g)")
                if (it.stoneCharges > 0) add("Stone ₹${money(it.stoneCharges)}")
                add("Metal ₹${money(it.metalValue)}")
            }.joinToString(" · ")
            row(cols, listOf("${i + 1}", desc, "${it.pcs}", g(it.grossWt), g(it.netWt), money(it.rate), money(it.making), money(it.amount)), sub, i % 2 == 1, it.photo)
        }
        y += 8f
    }

    private fun oldTable() {
        val cols = listOf(Col("#", 18f, false), Col("Old gold / silver", 165f, false), Col("Purity", 60f, false), Col("Gross wt", 52f),
            Col("Less %", 44f), Col("Net wt", 56f), Col("Rate/g", 62f), Col("Value ₹", CW - 457f))
        tableHeader(cols, if (inv.kind == InvoiceType.PURCHASE) "ITEMS PURCHASED" else "OLD GOLD / SILVER RECEIVED IN EXCHANGE")
        inv.oldItems.forEachIndexed { i, o ->
            row(cols, listOf("${i + 1}", o.description.ifBlank { "Old ${o.metal.lowercase()}" } + " (${o.metal})", o.purity, g(o.grossWt),
                if (o.lessPct > 0) "${o.lessPct}" else "-", g(o.netWt), money(o.rate), money(o.value)), null, i % 2 == 1, o.photo)
        }
        y += 8f
    }

    private fun totalsBlock() {
        val t = inv.totals
        val rows = mutableListOf<Triple<String, String, Boolean>>()   // label, value, bold
        if (inv.kind.hasNewItems) {
            rows += Triple("Items total", money(t.itemsTotal), false)
            if (t.discount > 0) rows += Triple("Discount", "- " + money(t.discount), false)
            rows += Triple("Taxable value", money(t.taxable), false)
            if (t.igst > 0) rows += Triple("IGST @ ${fmtPct(inv.gstRate)}%", money(t.igst), false)
            if (t.cgst > 0) {
                rows += Triple("CGST @ ${fmtPct(inv.gstRate / 2)}%", money(t.cgst), false)
                rows += Triple("SGST @ ${fmtPct(inv.gstRate / 2)}%", money(t.sgst), false)
            }
            if (t.roundOff != 0.0) rows += Triple("Round off", money(t.roundOff), false)
            rows += Triple(if (inv.kind == InvoiceType.ESTIMATE) "Estimated total" else "Invoice total", "₹ " + money(t.grandTotal), true)
        }
        if (inv.kind == InvoiceType.EXCHANGE) rows += Triple("Less: old gold / silver value", "- " + money(t.oldTotal), false)
        if (inv.kind == InvoiceType.PURCHASE) rows += Triple("Total value of items purchased", money(t.oldTotal), false)
        val netLabel = when {
            t.net < 0 -> "Amount payable to customer"
            inv.kind == InvoiceType.EXCHANGE -> "Net amount payable"
            else -> "Amount payable"
        }
        rows += Triple(netLabel, "₹ " + money(kotlin.math.abs(t.net)), true)
        if (inv.amountPaid > 0) {
            rows += Triple(if (t.net < 0) "Paid to customer" else "Amount received", money(inv.amountPaid), false)
            rows += Triple("Balance", money(t.balance), true)
        }

        val rightW = 236f
        val leftW = CW - rightW - 14f
        val rowH = 15f
        val rightH = rows.size * rowH + 8f

        // left: amount in words, bank, remarks
        val words = "Amount in words: " + Billing.rupeesInWords(kotlin.math.abs(t.net))
        val bank = listOfNotNull(
            shop.bankName.takeIf { it.isNotBlank() }?.let { "Bank: $it" },
            shop.accountNo.takeIf { it.isNotBlank() }?.let { "A/c No: $it" },
            shop.ifsc.takeIf { it.isNotBlank() }?.let { "IFSC: $it" },
            shop.upiId.takeIf { it.isNotBlank() }?.let { "UPI: $it" }
        ).joinToString("  ·  ")
        val pW = tp(8.5f, true); val pS = tp(8.5f)
        val leftH = block(words, 0f, 0f, leftW, pW, false) + 8f +
            (if (bank.isNotBlank()) block(bank, 0f, 0f, leftW, pS, false) + 18f else 0f) +
            (if (inv.remarks.isNotBlank()) block(inv.remarks, 0f, 0f, leftW, pS, false) + 18f else 0f)
        ensure(maxOf(leftH, rightH) + 6f)
        val top = y

        var ly = top
        ly += block(words, M, ly, leftW, pW) + 8f
        if (bank.isNotBlank()) {
            text("BANK DETAILS", M, ly, tp(7.5f, true, red)); ly += 11f
            ly += block(bank, M, ly, leftW, pS) + 6f
        }
        if (inv.remarks.isNotBlank()) {
            text("REMARKS", M, ly, tp(7.5f, true, red)); ly += 11f
            ly += block(inv.remarks, M, ly, leftW, pS) + 6f
        }

        val rx = W - M - rightW
        var ry = top
        rows.forEach { (label, value, bold) ->
            if (bold) rect(rx, ry, W - M, ry + rowH, pink)
            val p = tp(8.8f, bold)
            text(label, rx + 6, ry + 3, p)
            text(value, W - M - 6, ry + 3, p, Paint.Align.RIGHT)
            ry += rowH
        }
        y = maxOf(ly, ry) + 10f
    }

    private fun fmtPct(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

    private fun termsAndSign() {
        val terms = inv.terms.ifBlank { shop.terms }.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (terms.isNotEmpty()) {
            val p = tp(8f, color = muted)
            val textW = CW - 14
            val h = 14f + terms.sumOf { block(it, 0f, 0f, textW, p, false).toDouble() + 2.0 }.toFloat()
            ensure(h + 4)
            text("TERMS & CONDITIONS", M, y, tp(7.5f, true, red)); y += 12f
            terms.forEachIndexed { i, s ->
                text("${i + 1}.", M, y, p)
                y += block(s, M + 14, y, textW, p) + 2f
            }
            y += 6f
        }
        val sig = if (shop.showSignature) Images.decode(shop.signature) else null
        val hasName = shop.signatoryName.isNotBlank()
        ensure(if (sig != null) 80f else 64f)
        y += 8f
        text("For ${shopName.ifBlank { shop.name }}", W - M, y, tp(8.5f, true), Paint.Align.RIGHT)
        val sigW = 170f
        if (sig != null) {
            // Uploaded signature (or signature + stamp), fitted into the space above the right-hand line.
            val boxH = 44f; val boxW = sigW - 10f
            val scale = minOf(boxW / sig.width, boxH / sig.height)
            val w = sig.width * scale; val h = sig.height * scale
            val left = W - M - (sigW + w) / 2; val top = y + 14f + (boxH - h)
            c.drawBitmap(sig, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
            y += 14f + boxH + 2f
        } else y += 32f
        hline(y, ink, 0.6f, M, M + sigW)
        hline(y, ink, 0.6f, W - M - sigW, W - M)
        text(if (inv.kind == InvoiceType.PURCHASE) "Seller's signature" else "Customer's signature", M, y + 4, tp(8f, color = muted))
        if (hasName) {
            text(shop.signatoryName, W - M, y + 4, tp(8.5f, true), Paint.Align.RIGHT)
            text(shop.signatoryTitle.ifBlank { "Authorised Signatory" }, W - M, y + 16, tp(8f, color = muted), Paint.Align.RIGHT)
            y += 34f
        } else {
            text(shop.signatoryTitle.ifBlank { "Authorised Signatory" }, W - M, y + 4, tp(8f, color = muted), Paint.Align.RIGHT)
            y += 22f
        }
        if (shop.footerNote.isNotBlank() && inv.kind != InvoiceType.PURCHASE) {
            // The one-line thank-you may sit a little into the bottom margin rather than start a new page.
            if (y + 12f > H - 26f) { finishPage(); newPage() }
            text(shop.footerNote, W / 2, y, tp(8.5f, true, red), Paint.Align.CENTER)
            y += 14f
        }
    }

    companion object {
        /** Builds the PDF in the app's cache folder and returns it. */
        fun create(ctx: Context, inv: Invoice, shop: ShopSettings, shopName: String, logo: String, photos: Map<String, String> = emptyMap()): File {
            val safe = inv.number.ifBlank { "invoice" }.replace(Regex("[^A-Za-z0-9-]+"), "_")
            val dir = File(ctx.cacheDir, "invoices").apply { mkdirs() }
            dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
            return InvoicePdf(shop, shopName, logo, photos).write(inv, File(dir, "$safe.pdf"))
        }

    }
}
