package com.digiglobal.goldbill.data

import com.digiglobal.goldbill.util.Xlsx
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ReportPeriod(val label: String, val days: Int = 0, val months: Int = 0) {
    D7("7 days", days = 7), D15("15 days", days = 15), M1("1 month", months = 1), M3("3 months", months = 3),
    M6("6 months", months = 6), M9("9 months", months = 9), Y1("1 year", months = 12);

    fun since(now: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
        timeInMillis = now
        if (days > 0) add(Calendar.DAY_OF_YEAR, -(days - 1)) else add(Calendar.MONTH, -months)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val fileTag: String get() = label.replace(" ", "")
}

data class OrgBills(val org: Org, val bills: List<Invoice>)

/** Builds the Excel workbook of bills: Summary, Bills, Items, GST. */
object BillReport {
    private val stamp = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.ENGLISH)
    private fun n(v: Double) = Xlsx.num(r2(v))
    private fun g(v: Double) = Xlsx.num(r3(v))

    fun sheets(data: List<OrgBills>, period: ReportPeriod, since: Long, until: Long, generatedBy: String, showShop: Boolean): List<Xlsx.Sheet> {
        val all = data.flatMap { ob -> ob.bills.map { ob.org to it } }.sortedByDescending { it.second.at }
        val active = all.filter { !it.second.isCancelled }

        // ---------- Summary ----------
        val sumHeader = buildList { if (showShop) add("Shop"); addAll(listOf("Bill type", "Bills", "Amount (₹)", "Gold out (g)", "Silver out (g)",
            "Gold in (g)", "Silver in (g)", "Taxable (₹)", "GST (₹)")) }
        val sumRows = mutableListOf<List<Xlsx.Cell>>()
        val shops = if (showShop) active.groupBy { it.first.name } else mapOf("" to active)
        shops.toSortedMap().forEach { (shop, rows) ->
            InvoiceType.values().forEach { t ->
                val list = rows.map { it.second }.filter { it.kind == t }
                if (list.isEmpty()) return@forEach
                val tt = list.map { it.totals }
                sumRows.add(buildList {
                    if (showShop) add(Xlsx.text(shop))
                    add(Xlsx.text(t.label)); add(Xlsx.num(list.size))
                    add(n(if (t == InvoiceType.PURCHASE) tt.sumOf { it.oldTotal } else tt.sumOf { it.grandTotal }))
                    add(g(tt.sumOf { it.goldOut })); add(g(tt.sumOf { it.silverOut })); add(g(tt.sumOf { it.goldIn })); add(g(tt.sumOf { it.silverIn }))
                    add(n(tt.sumOf { it.taxable })); add(n(tt.sumOf { it.gst }))
                })
            }
        }
        // by billing user
        val userHeader = buildList { if (showShop) add("Shop"); addAll(listOf("Billed by", "Bills", "Sales (₹)", "Gold sold (g)", "Silver sold (g)", "Purchases (₹)", "Gold bought (g)")) }
        val userRows = active.groupBy { (if (showShop) it.first.name + " / " else "") + it.second.createdByName.ifBlank { "Unknown" } }
            .toSortedMap().map { (key, rows) ->
                val bills = rows.map { it.second }
                val sales = bills.filter { it.kind == InvoiceType.SALE || it.kind == InvoiceType.EXCHANGE }.map { it.totals }
                val purch = bills.filter { it.kind == InvoiceType.PURCHASE }.map { it.totals }
                buildList {
                    if (showShop) { add(Xlsx.text(key.substringBefore(" / "))); add(Xlsx.text(key.substringAfter(" / "))) } else add(Xlsx.text(key))
                    add(Xlsx.num(bills.size)); add(n(sales.sumOf { it.grandTotal })); add(g(sales.sumOf { it.goldOut })); add(g(sales.sumOf { it.silverOut }))
                    add(n(purch.sumOf { it.oldTotal })); add(g(purch.sumOf { it.goldIn }))
                }
            }

        // ---------- Bills ----------
        val billHeader = buildList {
            add("Date & time"); if (showShop) add("Shop")
            addAll(listOf("Bill no", "Type", "Status", "Customer", "Phone", "Customer GSTIN", "Billed by",
                "Gold out (g)", "Silver out (g)", "Gold in (g)", "Silver in (g)", "Items total", "Discount", "Taxable",
                "CGST", "SGST", "IGST", "Invoice total", "Old gold value", "Received (+) / Paid (−)", "Payment mode", "Amount paid", "Balance", "Remarks"))
        }
        val billRows = all.map { (org, b) ->
            val t = b.totals
            buildList {
                add(Xlsx.date(b.at)); if (showShop) add(Xlsx.text(org.name))
                add(Xlsx.text(b.number)); add(Xlsx.text(b.kind.label)); add(Xlsx.text(if (b.isCancelled) "Cancelled" else "Active"))
                add(Xlsx.text(b.customer.name)); add(Xlsx.text(b.customer.phone)); add(Xlsx.text(b.customer.gstin)); add(Xlsx.text(b.createdByName))
                add(g(t.goldOut)); add(g(t.silverOut)); add(g(t.goldIn)); add(g(t.silverIn))
                add(n(t.itemsTotal)); add(n(t.discount)); add(n(t.taxable)); add(n(t.cgst)); add(n(t.sgst)); add(n(t.igst))
                add(n(t.grandTotal)); add(n(t.oldTotal)); add(n(t.net)); add(Xlsx.text(b.payMode)); add(n(b.amountPaid)); add(n(t.balance))
                add(Xlsx.text(listOf(b.remarks, if (b.isCancelled) "Cancelled: ${b.cancelReason}" else "").filter { it.isNotBlank() }.joinToString(" | ")))
            }
        }

        // ---------- Items ----------
        val itemHeader = buildList {
            add("Date"); if (showShop) add("Shop")
            addAll(listOf("Bill no", "Bill type", "Direction", "Item", "Metal", "Purity", "HSN", "HUID", "Pcs", "Gross wt (g)", "Stone / less",
                "Net wt (g)", "Rate / g", "Making (₹)", "Stone ch. (₹)", "Amount (₹)"))
        }
        val itemRows = mutableListOf<List<Xlsx.Cell>>()
        active.forEach { (org, b) ->
            if (b.kind.hasNewItems) b.items.forEach { it ->
                itemRows.add(buildList {
                    add(Xlsx.date(b.at)); if (showShop) add(Xlsx.text(org.name))
                    add(Xlsx.text(b.number)); add(Xlsx.text(b.kind.label)); add(Xlsx.text(if (b.kind == InvoiceType.ESTIMATE) "Estimate" else "Sold"))
                    add(Xlsx.text(it.description)); add(Xlsx.text(it.metal)); add(Xlsx.text(it.purity)); add(Xlsx.text(it.hsn)); add(Xlsx.text(it.huid))
                    add(Xlsx.num(it.pcs)); add(g(it.grossWt)); add(Xlsx.text("${r3(it.stoneWt)} g stone")); add(g(it.netWt)); add(n(it.rate))
                    add(n(it.making)); add(n(it.stoneCharges)); add(n(it.amount))
                })
            }
            if (b.kind.hasOldItems) b.oldItems.forEach { o ->
                itemRows.add(buildList {
                    add(Xlsx.date(b.at)); if (showShop) add(Xlsx.text(org.name))
                    add(Xlsx.text(b.number)); add(Xlsx.text(b.kind.label)); add(Xlsx.text(if (b.kind == InvoiceType.PURCHASE) "Bought" else "Old received"))
                    add(Xlsx.text(o.description)); add(Xlsx.text(o.metal)); add(Xlsx.text(o.purity)); add(Xlsx.Cell.Empty); add(Xlsx.Cell.Empty)
                    add(Xlsx.Cell.Empty); add(g(o.grossWt)); add(Xlsx.text("${r2(o.lessPct)}% less")); add(g(o.netWt)); add(n(o.rate))
                    add(Xlsx.Cell.Empty); add(Xlsx.Cell.Empty); add(n(o.value))
                })
            }
        }

        // ---------- GST (tax invoices only) ----------
        val gstHeader = buildList {
            add("Date"); if (showShop) add("Shop")
            addAll(listOf("Invoice no", "Customer", "Customer GSTIN", "Place of supply", "Taxable value", "CGST", "SGST", "IGST", "Invoice value"))
        }
        val gstRows = active.filter { it.second.kind == InvoiceType.SALE || it.second.kind == InvoiceType.EXCHANGE }.map { (org, b) ->
            val t = b.totals
            buildList {
                add(Xlsx.date(b.at)); if (showShop) add(Xlsx.text(org.name))
                add(Xlsx.text(b.number)); add(Xlsx.text(b.customer.name)); add(Xlsx.text(b.customer.gstin))
                add(Xlsx.text(Billing.stateLabel(b.customer.stateCode)))
                add(n(t.taxable)); add(n(t.cgst)); add(n(t.sgst)); add(n(t.igst)); add(n(t.grandTotal))
            }
        }

        val info = listOf(
            listOf(Xlsx.text("Report"), Xlsx.text("Gold & silver billing report")),
            listOf(Xlsx.text("Shop"), Xlsx.text(if (data.size == 1) data[0].org.name else "All shops (${data.size})")),
            listOf(Xlsx.text("Period"), Xlsx.text("Last ${period.label}")),
            listOf(Xlsx.text("From"), Xlsx.text(stamp.format(Date(since)))),
            listOf(Xlsx.text("To"), Xlsx.text(stamp.format(Date(until)))),
            listOf(Xlsx.text("Bills"), Xlsx.num(all.size)),
            listOf(Xlsx.text("Cancelled bills"), Xlsx.num(all.size - active.size)),
            listOf(Xlsx.text("Generated by"), Xlsx.text(generatedBy)),
            listOf(Xlsx.text("Note"), Xlsx.text("Cancelled bills are listed in 'Bills' but left out of all totals."))
        )

        return listOf(
            Xlsx.Sheet("Summary", sumHeader, sumRows, buildList { if (showShop) add(24); addAll(listOf(14, 8, 15, 13, 14, 12, 13, 14, 12)) }),
            Xlsx.Sheet("By user", userHeader, userRows, buildList { if (showShop) add(24); addAll(listOf(20, 8, 14, 14, 14, 14, 15)) }),
            Xlsx.Sheet("Bills", billHeader, billRows, buildList { add(22); if (showShop) add(22)
                addAll(listOf(20, 11, 10, 22, 14, 18, 16, 12, 12, 12, 12, 13, 11, 13, 11, 11, 11, 13, 14, 18, 14, 12, 12, 36)) }),
            Xlsx.Sheet("Items", itemHeader, itemRows, buildList { add(22); if (showShop) add(22)
                addAll(listOf(20, 11, 13, 20, 9, 12, 8, 9, 6, 12, 14, 11, 11, 12, 12, 13)) }),
            Xlsx.Sheet("GST", gstHeader, gstRows, buildList { add(22); if (showShop) add(22); addAll(listOf(20, 22, 18, 22, 14, 11, 11, 11, 14)) }),
            Xlsx.Sheet("Report info", listOf("Item", "Value"), info, listOf(16, 50))
        )
    }
}
