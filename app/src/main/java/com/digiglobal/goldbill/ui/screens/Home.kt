package com.digiglobal.goldbill.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.digiglobal.goldbill.data.*
import com.digiglobal.goldbill.ui.*
import com.digiglobal.goldbill.ui.theme.GoldColor
import com.digiglobal.goldbill.ui.theme.SilverColor
import com.digiglobal.goldbill.util.Fmt
import java.util.Calendar
import kotlin.math.abs

enum class DashPeriod(val label: String) { TODAY("Today"), D7("7 days"), MONTH("This month"), M3("3 months"), FY("This year") ;
    fun since(): Long {
        val c = Calendar.getInstance().apply { timeInMillis = Fmt.startOfDay() }
        when (this) {
            TODAY -> Unit
            D7 -> c.add(Calendar.DAY_OF_YEAR, -6)
            MONTH -> c.set(Calendar.DAY_OF_MONTH, 1)
            M3 -> c.add(Calendar.MONTH, -3)
            FY -> { if (c.get(Calendar.MONTH) < Calendar.APRIL) c.add(Calendar.YEAR, -1); c.set(Calendar.MONTH, Calendar.APRIL); c.set(Calendar.DAY_OF_MONTH, 1) }
        }
        return c.timeInMillis
    }
}

fun money(v: Double) = "₹" + Billing.inr(v, decimals = false)

/** Totals for a set of invoices (cancelled ones are left out). */
data class Summary(
    val salesCount: Int, val salesAmount: Double, val goldSold: Double, val silverSold: Double, val gst: Double,
    val purchaseCount: Int, val purchaseAmount: Double, val goldBought: Double, val silverBought: Double,
    val exchangeCount: Int, val oldGoldIn: Double, val oldSilverIn: Double, val oldValue: Double,
    val estimates: Int, val cashIn: Double, val cashOut: Double
) {
    companion object {
        fun of(list: List<Invoice>): Summary {
            val a = list.filter { !it.isCancelled }
            val sales = a.filter { it.kind == InvoiceType.SALE || it.kind == InvoiceType.EXCHANGE }
            val purchases = a.filter { it.kind == InvoiceType.PURCHASE }
            val exch = a.filter { it.kind == InvoiceType.EXCHANGE }
            val t = a.associateWith { it.totals }
            return Summary(
                salesCount = sales.size, salesAmount = sales.sumOf { t[it]!!.grandTotal },
                goldSold = sales.sumOf { t[it]!!.goldOut }, silverSold = sales.sumOf { t[it]!!.silverOut },
                gst = sales.sumOf { t[it]!!.gst },
                purchaseCount = purchases.size, purchaseAmount = purchases.sumOf { t[it]!!.oldTotal },
                goldBought = purchases.sumOf { t[it]!!.goldIn }, silverBought = purchases.sumOf { t[it]!!.silverIn },
                exchangeCount = exch.size, oldGoldIn = exch.sumOf { t[it]!!.goldIn }, oldSilverIn = exch.sumOf { t[it]!!.silverIn },
                oldValue = exch.sumOf { t[it]!!.oldTotal },
                estimates = a.count { it.kind == InvoiceType.ESTIMATE },
                cashIn = a.filter { it.kind != InvoiceType.ESTIMATE }.sumOf { maxOf(0.0, t[it]!!.net) },
                cashOut = a.filter { it.kind != InvoiceType.ESTIMATE }.sumOf { maxOf(0.0, -t[it]!!.net) }
            )
        }
    }
}

// ============================ Admin dashboard (admin only) ============================

@Composable
fun AdminDashboardScreen(me: UserProfile, org: Org, rates: Rates, team: List<UserProfile>, nav: NavController) {
    var period by remember { mutableStateOf(DashPeriod.TODAY) }
    val since = remember(period) { period.since() }
    val invoices by remember(since) { Repo.shopInvoicesFlow(since) }.collectAsState(initial = emptyList())
    val s = remember(invoices) { Summary.of(invoices) }

    Screen(title = "Hi, ${me.firstName}") { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ShopHeader(org, me) }
            item { RatesCard(rates, me.isAdmin) { nav.navigate("rates") } }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DashPeriod.values().forEach { p ->
                        FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.label, maxLines = 1) })
                    }
                }
            }
            item {
                MetalCard("Sales", Icons.Filled.TrendingUp, MaterialTheme.colorScheme.primary, s.salesCount, s.salesAmount,
                    s.goldSold, s.silverSold, "sold", extra = "GST collected ${money(s.gst)}")
            }
            item {
                MetalCard("Purchases (old gold / silver bought)", Icons.Filled.TrendingDown, Color(0xFF2E7D32), s.purchaseCount, s.purchaseAmount,
                    s.goldBought, s.silverBought, "bought")
            }
            item {
                MetalCard("Exchange (old received)", Icons.Filled.SwapHoriz, MaterialTheme.colorScheme.tertiary, s.exchangeCount, s.oldValue,
                    s.oldGoldIn, s.oldSilverIn, "received", extra = "Value of old items adjusted against new bills")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiTile("Cash in", money(s.cashIn), Icons.Filled.ArrowDownward, Color(0xFF2E7D32), Modifier.weight(1f))
                    KpiTile("Paid out", money(s.cashOut), Icons.Filled.ArrowUpward, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                    KpiTile("Estimates", s.estimates.toString(), Icons.Filled.Description, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                }
            }
            item {
                SectionCard("By billing user") {
                    val byUser = invoices.filter { !it.isCancelled }.groupBy { it.createdByName.ifBlank { "Unknown" } }
                    if (byUser.isEmpty()) Text("No bills in this period.", style = MaterialTheme.typography.bodySmall)
                    byUser.entries.sortedByDescending { it.value.size }.forEach { (name, list) ->
                        val su = Summary.of(list)
                        val photo = team.firstOrNull { it.name == name }?.photo.orEmpty()
                        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(name, 32, photo)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, fontWeight = FontWeight.Medium)
                                Text("${list.size} bills · gold ${Billing.grams(su.goldSold)} sold", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(money(su.salesAmount), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item { Text("Recent bills", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
            val recent = invoices.sortedByDescending { it.at }.take(15)
            if (recent.isEmpty()) item { EmptyState("No bills in this period yet.", Icons.Filled.ReceiptLong) }
            items(recent, key = { it.id }) { inv -> InvoiceRow(inv, showUser = true) { nav.navigate("invoice/${inv.id}") } }
        }
    }
}

@Composable
private fun MetalCard(title: String, icon: ImageVector, tint: Color, count: Int, amount: Double,
                      gold: Double, silver: Double, verb: String, extra: String = "") {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(tint.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("$count bills", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(money(amount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GramBox("Gold $verb", gold, GoldColor, Modifier.weight(1f))
            GramBox("Silver $verb", silver, SilverColor, Modifier.weight(1f))
        }
        if (extra.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(extra, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GramBox(label: String, grams: Double, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f)).padding(10.dp)) {
        Text(Billing.grams(grams), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

// ============================ Billing user home (no shop totals) ============================

@Composable
fun UserHomeScreen(me: UserProfile, org: Org, rates: Rates, nav: NavController) {
    val mine by remember(me.uid) { Repo.myInvoicesFlow(me.uid) }.collectAsState(initial = emptyList())
    val today = remember { Fmt.startOfDay() }
    Screen(title = "Hi, ${me.firstName}") { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ShopHeader(org, me) }
            item { RatesCard(rates, false) { nav.navigate("rates") } }
            item { Text("Create a bill", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
            item { QuickBillGrid(nav) }
            item {
                Text("Your bills today: ${mine.count { it.at >= today && !it.isCancelled }}",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            val recent = mine.sortedByDescending { it.at }.take(10)
            if (recent.isEmpty()) item { EmptyState("Bills you create will appear here.", Icons.Filled.ReceiptLong) }
            items(recent, key = { it.id }) { inv -> InvoiceRow(inv, showUser = false) { nav.navigate("invoice/${inv.id}") } }
        }
    }
}

@Composable
fun QuickBillGrid(nav: NavController) {
    val opts = listOf(
        Triple(InvoiceType.SALE, Icons.Filled.Sell, "New gold / silver sale with GST"),
        Triple(InvoiceType.EXCHANGE, Icons.Filled.SwapHoriz, "Old gold adjusted against new"),
        Triple(InvoiceType.PURCHASE, Icons.Filled.ShoppingBag, "Buy old gold / silver from customer"),
        Triple(InvoiceType.ESTIMATE, Icons.Filled.Description, "Price estimate for a customer")
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        opts.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (type, icon, sub) ->
                    Card(onClick = { nav.navigate("new/${type.code}") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(6.dp))
                            Text(type.label, fontWeight = FontWeight.SemiBold)
                            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NewBillPicker(nav: NavController) {
    Screen("New bill") { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Text("What kind of bill?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            QuickBillGrid(nav)
        }
    }
}

// ============================ shared pieces ============================

@Composable
fun ShopHeader(org: Org, me: UserProfile) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CompanyLogo(org.name, org.logo, 44)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(org.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (me.isAdmin) "Admin" else "Billing user", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Avatar(me.name, 36, me.photo)
    }
}

@Composable
fun RatesCard(r: Rates, canEdit: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today's rates (per gram)", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (r.updatedAt > 0) "Updated ${Fmt.ago(r.updatedAt)}" else if (canEdit) "Tap to set" else "Not set",
                    style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                listOf("24K" to r.gold24, "22K" to r.gold22, "18K" to r.gold18, "Silver" to r.silver).forEach { (l, v) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (v > 0) "₹" + Billing.inr(v, false) else "—", fontWeight = FontWeight.Bold,
                            color = if (l == "Silver") SilverColor else GoldColor)
                        Text(l, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun TypePill(t: InvoiceType) {
    val c = when (t) {
        InvoiceType.SALE -> MaterialTheme.colorScheme.primary
        InvoiceType.EXCHANGE -> GoldColor
        InvoiceType.PURCHASE -> Color(0xFF2E7D32)
        InvoiceType.ESTIMATE -> MaterialTheme.colorScheme.secondary
    }
    Pill(t.label, c)
}

@Composable
fun InvoiceRow(inv: Invoice, showUser: Boolean, onClick: () -> Unit) {
    val t = inv.totals
    Card(onClick = onClick, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TypePill(inv.kind)
                    if (inv.isCancelled) Pill("Cancelled", MaterialTheme.colorScheme.error)
                    Text(inv.number, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(3.dp))
                Text(inv.customer.name.ifBlank { "Walk-in customer" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val grams = listOf(
                    if (t.goldOut > 0) "Gold ${Billing.grams(t.goldOut)}" else null,
                    if (t.silverOut > 0) "Silver ${Billing.grams(t.silverOut)}" else null,
                    if (t.goldIn > 0) "Old gold ${Billing.grams(t.goldIn)}" else null,
                    if (t.silverIn > 0) "Old silver ${Billing.grams(t.silverIn)}" else null
                ).filterNotNull().joinToString(" · ")
                Text(listOf(Fmt.dateTime(inv.at), if (showUser) inv.createdByName else "", grams).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(money(abs(t.net)), fontWeight = FontWeight.Bold,
                    color = if (t.net < 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface)
                Text(if (t.net < 0) "paid out" else if (inv.kind == InvoiceType.ESTIMATE) "estimate" else "received",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

