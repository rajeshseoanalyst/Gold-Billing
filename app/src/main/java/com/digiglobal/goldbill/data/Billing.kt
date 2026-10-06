package com.digiglobal.goldbill.data

import java.util.Calendar
import kotlin.math.abs

// =====================================================================
//  Plain billing model + maths. No Android or Firebase code in this file.
// =====================================================================

object Metal {
    const val GOLD = "Gold"
    const val SILVER = "Silver"
    const val OTHER = "Other"
    val all = listOf(GOLD, SILVER, OTHER)

    fun purities(metal: String): List<String> = when (metal) {
        GOLD -> listOf("24K (999)", "22K (916)", "20K (833)", "18K (750)", "14K (585)")
        SILVER -> listOf("999", "925", "900", "800")
        else -> listOf("-")
    }
}

enum class InvoiceType(val code: String, val label: String, val docTitle: String) {
    SALE("sale", "Sale", "TAX INVOICE"),
    EXCHANGE("exchange", "Exchange", "TAX INVOICE"),
    PURCHASE("purchase", "Purchase", "PURCHASE VOUCHER"),
    ESTIMATE("estimate", "Estimate", "ESTIMATE");

    val hasNewItems: Boolean get() = this != PURCHASE
    val hasOldItems: Boolean get() = this == EXCHANGE || this == PURCHASE

    companion object {
        fun of(code: String) = values().firstOrNull { it.code == code } ?: SALE
    }
}

enum class MakingType(val code: String, val label: String) {
    PER_GRAM("per_gram", "₹ per gram"),
    PERCENT("percent", "% of metal value"),
    FIXED("fixed", "Fixed ₹");

    companion object {
        fun of(code: String) = values().firstOrNull { it.code == code } ?: PER_GRAM
    }
}

fun r2(v: Double): Double = Math.round(v * 100.0) / 100.0
fun r3(v: Double): Double = Math.round(v * 1000.0) / 1000.0

/** An item being sold (new jewellery, coin or bullion). */
data class SaleItem(
    val description: String = "",
    val metal: String = Metal.GOLD,
    val purity: String = "22K (916)",
    val hsn: String = "7113",
    val huid: String = "",                // hallmark unique ID
    val pcs: Int = 1,
    val grossWt: Double = 0.0,            // grams
    val stoneWt: Double = 0.0,            // grams
    val wastagePct: Double = 0.0,         // value addition / wastage %
    val rate: Double = 0.0,               // ₹ per gram
    val makingType: String = MakingType.PER_GRAM.code,
    val makingValue: Double = 0.0,
    val stoneCharges: Double = 0.0
) {
    val netWt: Double get() = r3((grossWt - stoneWt).coerceAtLeast(0.0))
    /** Net weight plus wastage, the weight that is charged. */
    val chargeableWt: Double get() = r3(netWt * (1 + wastagePct / 100.0))
    val metalValue: Double get() = r2(chargeableWt * rate)
    val making: Double get() = r2(
        when (MakingType.of(makingType)) {
            MakingType.PER_GRAM -> netWt * makingValue
            MakingType.PERCENT -> metalValue * makingValue / 100.0
            MakingType.FIXED -> makingValue
        }
    )
    val amount: Double get() = r2(metalValue + making + stoneCharges)
}

/** Old gold / silver taken from the customer (purchase or exchange). */
data class OldItem(
    val description: String = "Old ornament",
    val metal: String = Metal.GOLD,
    val purity: String = "22K (916)",
    val grossWt: Double = 0.0,
    val lessPct: Double = 0.0,            // melting / dirt / stone deduction %
    val rate: Double = 0.0
) {
    val netWt: Double get() = r3((grossWt * (1 - lessPct / 100.0)).coerceAtLeast(0.0))
    val value: Double get() = r2(netWt * rate)
}

data class Customer(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val gstin: String = "",
    val pan: String = "",
    val stateCode: String = "",
    val remarks: String = ""
)

data class Invoice(
    val id: String = "",
    val type: String = InvoiceType.SALE.code,
    val number: String = "",
    val at: Long = 0L,
    val customer: Customer = Customer(),
    val items: List<SaleItem> = emptyList(),
    val oldItems: List<OldItem> = emptyList(),
    val discount: Double = 0.0,
    val gstRate: Double = 3.0,
    val includeGst: Boolean = true,       // only matters for estimates
    val interState: Boolean = false,      // IGST instead of CGST + SGST
    val payMode: String = "Cash",
    val amountPaid: Double = 0.0,
    val remarks: String = "",
    val terms: String = "",
    val createdBy: String = "",
    val createdByName: String = "",
    val status: String = "active",        // active | cancelled
    val cancelReason: String = ""
) {
    val kind: InvoiceType get() = InvoiceType.of(type)
    val isCancelled: Boolean get() = status == "cancelled"
    val totals: Totals get() = Billing.compute(this)
}

data class Totals(
    val itemsTotal: Double,
    val discount: Double,
    val taxable: Double,
    val cgst: Double,
    val sgst: Double,
    val igst: Double,
    val gst: Double,
    val roundOff: Double,
    val grandTotal: Double,      // new items incl. GST, rounded
    val oldTotal: Double,        // value of old gold/silver taken
    /** Positive: customer pays the shop. Negative: shop pays the customer. */
    val net: Double,
    val balance: Double,         // still due after amountPaid (always ≥ 0)
    val goldOut: Double, val silverOut: Double,   // grams sold (net)
    val goldIn: Double, val silverIn: Double      // grams received (net)
)

object Billing {

    fun compute(inv: Invoice): Totals {
        val t = inv.kind
        val items = if (t.hasNewItems) inv.items else emptyList()
        val old = if (t.hasOldItems) inv.oldItems else emptyList()

        val itemsTotal = r2(items.sumOf { it.amount })
        val discount = r2(inv.discount.coerceIn(0.0, itemsTotal))
        val taxable = r2(itemsTotal - discount)
        val gstApplies = t == InvoiceType.SALE || t == InvoiceType.EXCHANGE || (t == InvoiceType.ESTIMATE && inv.includeGst)
        var cgst = 0.0; var sgst = 0.0; var igst = 0.0
        if (gstApplies && taxable > 0) {
            if (inv.interState) igst = r2(taxable * inv.gstRate / 100.0)
            else { cgst = r2(taxable * inv.gstRate / 200.0); sgst = cgst }
        }
        val gst = r2(cgst + sgst + igst)
        val before = r2(taxable + gst)
        val grand = if (items.isEmpty()) 0.0 else Math.round(before).toDouble()
        val roundOff = r2(grand - before)
        val oldTotal = r2(old.sumOf { it.value })
        val net = when (t) {
            InvoiceType.PURCHASE -> -Math.round(oldTotal).toDouble()
            InvoiceType.EXCHANGE -> Math.round(grand - oldTotal).toDouble()
            else -> grand
        }
        val balance = r2((abs(net) - inv.amountPaid).coerceAtLeast(0.0))
        val sold = t != InvoiceType.ESTIMATE
        return Totals(
            itemsTotal, discount, taxable, cgst, sgst, igst, gst, roundOff, grand, oldTotal, net, balance,
            goldOut = if (sold) r3(items.filter { it.metal == Metal.GOLD }.sumOf { it.netWt }) else 0.0,
            silverOut = if (sold) r3(items.filter { it.metal == Metal.SILVER }.sumOf { it.netWt }) else 0.0,
            goldIn = r3(old.filter { it.metal == Metal.GOLD }.sumOf { it.netWt }),
            silverIn = r3(old.filter { it.metal == Metal.SILVER }.sumOf { it.netWt })
        )
    }

    // ---------------- invoice numbers ----------------

    /** Indian financial year, April to March, e.g. "2026-27". */
    fun financialYear(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        val y = c.get(Calendar.YEAR)
        val start = if (c.get(Calendar.MONTH) >= Calendar.APRIL) y else y - 1
        return "$start-${((start + 1) % 100).toString().padStart(2, '0')}"
    }

    /** Sale and exchange share one tax-invoice series, as GST requires. */
    fun seriesPrefix(type: InvoiceType, s: ShopSettings): String = when (type) {
        InvoiceType.SALE, InvoiceType.EXCHANGE -> s.invoicePrefix.ifBlank { "INV" }
        InvoiceType.PURCHASE -> s.purchasePrefix.ifBlank { "PUR" }
        InvoiceType.ESTIMATE -> s.estimatePrefix.ifBlank { "EST" }
    }

    fun formatNumber(prefix: String, fy: String, n: Long) = "$prefix/$fy/${n.toString().padStart(4, '0')}"

    // ---------------- money ----------------

    /** 1234567.5 -> "12,34,567.50" (Indian grouping). */
    fun inr(v: Double, decimals: Boolean = true): String {
        val neg = v < 0
        val cents = Math.round(abs(v) * 100)
        val whole = if (decimals) cents / 100 else Math.round(abs(v))
        val s = whole.toString()
        val grouped = if (s.length <= 3) s else {
            var rest = s.dropLast(3)
            val parts = mutableListOf<String>()
            while (rest.length > 2) { parts.add(0, rest.takeLast(2)); rest = rest.dropLast(2) }
            if (rest.isNotEmpty()) parts.add(0, rest)
            parts.joinToString(",") + "," + s.takeLast(3)
        }
        return (if (neg && cents > 0) "-" else "") + grouped + if (decimals) "." + (cents % 100).toString().padStart(2, '0') else ""
    }

    fun grams(v: Double): String = "%.3f g".format(java.util.Locale.US, v)

    private val ones = arrayOf("", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen")
    private val tens = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

    private fun twoDigits(n: Int): String = if (n < 20) ones[n] else (tens[n / 10] + if (n % 10 != 0) " " + ones[n % 10] else "")
    private fun threeDigits(n: Int): String {
        val h = n / 100; val r = n % 100
        return listOfNotNull(if (h > 0) ones[h] + " Hundred" else null, if (r > 0) twoDigits(r) else null).joinToString(" ")
    }

    /** Indian system: crore, lakh, thousand. */
    fun numberInWords(n: Long): String {
        if (n == 0L) return "Zero"
        var x = n
        val parts = mutableListOf<String>()
        val crore = x / 10_000_000; x %= 10_000_000
        val lakh = x / 100_000; x %= 100_000
        val thousand = x / 1000; x %= 1000
        if (crore > 0) parts += numberInWords(crore) + " Crore"
        if (lakh > 0) parts += twoDigits(lakh.toInt()) + " Lakh"
        if (thousand > 0) parts += twoDigits(thousand.toInt()) + " Thousand"
        if (x > 0) parts += threeDigits(x.toInt())
        return parts.joinToString(" ")
    }

    fun rupeesInWords(amount: Double): String {
        val cents = Math.round(abs(amount) * 100)
        val r = cents / 100
        val p = (cents % 100).toInt()
        return "Rupees " + numberInWords(r) + (if (p > 0) " and " + twoDigits(p) + " Paise" else "") + " Only"
    }

    // ---------------- GST helpers ----------------

    val states: List<Pair<String, String>> = listOf(
        "01" to "Jammu & Kashmir", "02" to "Himachal Pradesh", "03" to "Punjab", "04" to "Chandigarh",
        "05" to "Uttarakhand", "06" to "Haryana", "07" to "Delhi", "08" to "Rajasthan", "09" to "Uttar Pradesh",
        "10" to "Bihar", "11" to "Sikkim", "12" to "Arunachal Pradesh", "13" to "Nagaland", "14" to "Manipur",
        "15" to "Mizoram", "16" to "Tripura", "17" to "Meghalaya", "18" to "Assam", "19" to "West Bengal",
        "20" to "Jharkhand", "21" to "Odisha", "22" to "Chhattisgarh", "23" to "Madhya Pradesh", "24" to "Gujarat",
        "26" to "Dadra & Nagar Haveli and Daman & Diu", "27" to "Maharashtra", "29" to "Karnataka", "30" to "Goa",
        "31" to "Lakshadweep", "32" to "Kerala", "33" to "Tamil Nadu", "34" to "Puducherry",
        "35" to "Andaman & Nicobar Islands", "36" to "Telangana", "37" to "Andhra Pradesh", "38" to "Ladakh",
        "97" to "Other Territory"
    )

    fun stateLabel(code: String): String = states.firstOrNull { it.first == code }?.let { "${it.second} (${it.first})" } ?: ""
    fun stateCodeFromLabel(label: String): String = Regex("\\((\\d{2})\\)$").find(label)?.groupValues?.get(1) ?: ""

    private val gstinRegex = Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$")
    fun isValidGstin(g: String) = gstinRegex.matches(g.trim().uppercase())
    fun stateFromGstin(g: String): String = g.trim().take(2).takeIf { it.length == 2 && it.all(Char::isDigit) } ?: ""

    /** Today's rate for a purity, derived from the 24K / 22K / 18K / silver rates. */
    fun rateFor(metal: String, purity: String, r: Rates): Double = when (metal) {
        Metal.GOLD -> when {
            purity.startsWith("24") -> r.gold24
            purity.startsWith("22") -> r.gold22
            purity.startsWith("18") -> r.gold18
            purity.startsWith("20") -> r2(r.gold24 * 0.833)
            purity.startsWith("14") -> r2(r.gold24 * 0.585)
            else -> r.gold22
        }
        Metal.SILVER -> {
            val fine = purity.filter(Char::isDigit).toIntOrNull() ?: 999
            if (fine >= 999) r.silver else r2(r.silver * fine / 999.0)
        }
        else -> 0.0
    }
}

/** Shop details printed on every invoice, set by the company admin. */
data class ShopSettings(
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val gstin: String = "",
    val stateCode: String = "",
    val pan: String = "",
    val bankName: String = "",
    val accountNo: String = "",
    val ifsc: String = "",
    val upiId: String = "",
    val terms: String = DEFAULT_TERMS,
    val footerNote: String = "Thank you for your purchase. Visit again!",
    val gstRate: Double = 3.0,
    val invoicePrefix: String = "INV",
    val purchasePrefix: String = "PUR",
    val estimatePrefix: String = "EST",
    val hsnGold: String = "7113",
    val hsnSilver: String = "7113"
) {
    companion object {
        val DEFAULT_TERMS = """
            Goods once sold will not be taken back. Exchange as per shop policy only.
            Gold jewellery is BIS hallmarked; HUID is mentioned where applicable.
            Making charges, stone charges and GST are not refundable on exchange or return.
            Old gold / silver is accepted at the prevailing rate after purity testing.
            Please check weight and items before leaving the counter.
            Subject to local jurisdiction. E. & O.E.
        """.trimIndent()
    }
}

/** Today's rates per gram, set by the admin. */
data class Rates(
    val gold24: Double = 0.0,
    val gold22: Double = 0.0,
    val gold18: Double = 0.0,
    val silver: Double = 0.0,
    val updatedAt: Long = 0L,
    val updatedBy: String = ""
)
