package com.digiglobal.goldbill.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.digiglobal.goldbill.data.*
import com.digiglobal.goldbill.ui.*
import com.digiglobal.goldbill.util.Fmt
import com.digiglobal.goldbill.util.InvoicePdf
import com.digiglobal.goldbill.util.Share
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import kotlin.math.abs

// ============================ invoice list ============================

@Composable
fun InvoicesScreen(me: UserProfile, nav: NavController) {
    val yearAgo = remember { Calendar.getInstance().apply { timeInMillis = Fmt.startOfDay(); add(Calendar.YEAR, -1) }.timeInMillis }
    val flow = remember(me.uid, me.isAdmin) { if (me.isAdmin) Repo.shopInvoicesFlow(yearAgo) else Repo.myInvoicesFlow(me.uid) }
    val all by flow.collectAsState(initial = emptyList())
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    val filters = listOf("All") + InvoiceType.values().map { it.label } + "Cancelled"
    val shown = all.filter { inv ->
        val okType = when (filter) {
            "All" -> true
            "Cancelled" -> inv.isCancelled
            else -> inv.kind.label == filter
        }
        val okQ = q.isBlank() || inv.number.contains(q, true) || inv.customer.name.contains(q, true) ||
            inv.customer.phone.contains(q) || inv.createdByName.contains(q, true)
        okType && okQ
    }.sortedByDescending { it.at }

    Screen(if (me.isAdmin) "All bills" else "My bills") { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Search bill no, customer, phone") },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                filters.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) }) }
            }
            if (!me.isAdmin) Text("You can see only the bills you created.", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            if (shown.isEmpty()) EmptyState(if (all.isEmpty()) "No bills yet." else "No bills match.", Icons.Filled.ReceiptLong)
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { inv -> InvoiceRow(inv, showUser = me.isAdmin) { nav.navigate("invoice/${inv.id}") } }
            }
        }
    }
}

// ============================ invoice detail + share ============================

@Composable
fun InvoiceViewScreen(id: String, me: UserProfile, org: Org, shop: ShopSettings, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by remember(id) { Repo.invoiceFlow(id) }.collectAsState(initial = Invoice(id = "__loading"))
    var busy by remember { mutableStateOf(false) }
    var cancelAsk by remember { mutableStateOf(false) }
    var viewPhoto by remember { mutableStateOf<String?>(null) }
    val inv = state
    if (inv == null) {
        Screen("Bill", onBack = { nav.popBackStack() }) { pad -> Box(Modifier.padding(pad)) { EmptyState("This bill isn't available.") } }
        return
    }
    if (inv.id == "__loading") { Loading(); return }
    val t = inv.totals
    val shopName = org.name.ifBlank { shop.name }

    /** Builds the PDF (in the background) and hands it to [use]. */
    fun withPdf(use: (File) -> Unit) {
        busy = true
        scope.launch {
            try {
                val photos = Repo.photosOf(inv)
                val f = withContext(Dispatchers.IO) { InvoicePdf.create(ctx, inv, shop, shopName, org.logo, photos) }
                use(f)
            } catch (e: Exception) {
                Share.toast(ctx, e.message ?: "Couldn't create the PDF")
            } finally { busy = false }
        }
    }

    val docName = when (inv.kind) { InvoiceType.PURCHASE -> "purchase voucher"; InvoiceType.ESTIMATE -> "estimate"; else -> "invoice" }
    val message = buildString {
        append("Dear ${inv.customer.name.ifBlank { "Customer" }},\n")
        append("Thank you for visiting $shopName. Please find your $docName ${inv.number} attached.\n")
        append(if (t.net < 0) "Amount paid to you: ₹${Billing.inr(abs(t.net))}" else "Amount: ₹${Billing.inr(abs(t.net))}")
        if (shop.phone.isNotBlank()) append("\nFor any help, call ${shop.phone}.")
    }

    // The admin can edit any bill; a billing user can edit the bills they made. Cancelled bills stay as they are.
    val canEdit = !inv.isCancelled && (me.isAdmin || inv.createdBy == me.uid)
    val openEditor = { nav.navigate("edit/${inv.kind.code}/${inv.id}") }
    Screen(inv.number, onBack = { nav.popBackStack() }, actions = {
        if (canEdit) TextButton(onClick = openEditor) { Icon(Icons.Filled.Edit, null); Spacer(Modifier.width(4.dp)); Text("Edit") }
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TypePill(inv.kind)
                        if (inv.isCancelled) Pill("Cancelled", MaterialTheme.colorScheme.error)
                        Spacer(Modifier.weight(1f))
                        Text(Fmt.dateTime(inv.at), style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(inv.customer.name.ifBlank { "Walk-in customer" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(listOf(inv.customer.phone, inv.customer.email).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (inv.customer.gstin.isNotBlank()) Text("GSTIN ${inv.customer.gstin}", style = MaterialTheme.typography.bodySmall)
                    if (inv.kind != InvoiceType.ESTIMATE) IdProof.printable(inv.customer).takeIf { it.isNotEmpty() }?.let {
                        Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Billed by ${inv.createdByName} · ${inv.payMode}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (inv.isCancelled && inv.cancelReason.isNotBlank()) Text("Cancelled: ${inv.cancelReason}", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    if (inv.editCount > 0) Text("Edited ${if (inv.editCount == 1) "once" else "${inv.editCount} times"} · last by ${inv.editedByName} on ${Fmt.dateTime(inv.editedAt)}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }

            // ---- share buttons ----
            item {
                SectionCard("Send to customer") {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { withPdf { Share.whatsapp(ctx, it, inv.customer.phone, message) } }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Chat, null); Spacer(Modifier.width(6.dp)); Text("WhatsApp")
                        }
                        Button(onClick = { withPdf { Share.email(ctx, it, inv.customer.email, "$shopName — $docName ${inv.number}", message) } },
                            enabled = !busy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Email, null); Spacer(Modifier.width(6.dp)); Text("Email")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { withPdf { Share.open(ctx, it) } }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text("View / print")
                        }
                        OutlinedButton(onClick = { withPdf { Share.any(ctx, it, message, "application/pdf") } }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text("Other apps")
                        }
                    }
                    if (inv.customer.phone.isBlank()) Text("No phone number on this bill — WhatsApp will ask you to pick the contact.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (inv.customer.email.isBlank()) Text("No email on this bill — type the address in your email app.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ---- lines ----
            if (inv.items.isNotEmpty() && inv.kind.hasNewItems) item {
                SectionCard(if (inv.kind == InvoiceType.EXCHANGE) "New items sold" else "Items") {
                    inv.items.forEach { it ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (it.photo.isNotBlank()) { ProductThumb(it.photo, 52, onClick = { viewPhoto = it.photo }); Spacer(Modifier.width(10.dp)) }
                            Column(Modifier.weight(1f)) {
                                Text("${it.description.ifBlank { it.metal + " item" }} · ${it.metal} ${it.purity}", fontWeight = FontWeight.Medium)
                                Text("Gross ${Billing.grams(it.grossWt)} · Net ${Billing.grams(it.netWt)} · ₹${Billing.inr(it.rate, false)}/g · making ₹${Billing.inr(it.making, false)}" +
                                    (if (it.huid.isNotBlank()) " · HUID ${it.huid}" else ""), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("₹" + Billing.inr(it.amount), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (inv.oldItems.isNotEmpty() && inv.kind.hasOldItems) item {
                SectionCard(if (inv.kind == InvoiceType.PURCHASE) "Bought from customer" else "Old gold / silver received") {
                    inv.oldItems.forEach { o ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (o.photo.isNotBlank()) { ProductThumb(o.photo, 52, onClick = { viewPhoto = o.photo }); Spacer(Modifier.width(10.dp)) }
                            Column(Modifier.weight(1f)) {
                                Text("${o.description.ifBlank { "Old " + o.metal.lowercase() }} · ${o.metal} ${o.purity}", fontWeight = FontWeight.Medium)
                                Text("Gross ${Billing.grams(o.grossWt)} − ${fmtPct(o.lessPct)}% = ${Billing.grams(o.netWt)} · ₹${Billing.inr(o.rate, false)}/g",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("₹" + Billing.inr(o.value), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item { TotalsCard(inv.kind, t, inv.interState, inv.gstRate) }
            if (inv.remarks.isNotBlank()) item { SectionCard("Remarks") { Text(inv.remarks) } }

            // ---- more actions ----
            item {
                SectionCard {
                    if (canEdit) TextButton(onClick = openEditor) {
                        Icon(Icons.Filled.Edit, null); Spacer(Modifier.width(6.dp)); Text("Edit this bill")
                    }
                    if (inv.customer.phone.isNotBlank()) TextButton(onClick = { Share.call(ctx, inv.customer.phone) }) {
                        Icon(Icons.Filled.Call, null); Spacer(Modifier.width(6.dp)); Text("Call customer")
                    }
                    if (inv.kind == InvoiceType.ESTIMATE && !inv.isCancelled) TextButton(onClick = {
                        Prefill.set(inv); nav.navigate("new/${InvoiceType.SALE.code}")
                    }) { Icon(Icons.Filled.Transform, null); Spacer(Modifier.width(6.dp)); Text("Convert estimate to sale invoice") }
                    TextButton(onClick = { Prefill.set(inv); nav.navigate("new/${inv.kind.code}") }) {
                        Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Create a new bill like this")
                    }
                    if (me.isAdmin && !inv.isCancelled) TextButton(onClick = { cancelAsk = true }) {
                        Icon(Icons.Filled.Block, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(6.dp))
                        Text("Cancel this bill", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    viewPhoto?.let { PhotoViewer(it) { viewPhoto = null } }

    if (cancelAsk) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { cancelAsk = false },
            title = { Text("Cancel ${inv.number}?") },
            text = {
                Column {
                    Text("The bill stays in the records marked CANCELLED (its number isn't reused) and is left out of all totals.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(reason, { reason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = reason.isNotBlank(), onClick = {
                    cancelAsk = false
                    scope.launch { runCatching { Repo.cancelInvoice(inv.id, reason.trim()) }.onFailure { Share.toast(ctx, it.message ?: "Couldn't cancel") } }
                }) { Text("Cancel bill", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { cancelAsk = false }) { Text("Keep") } }
        )
    }
}

// ============================ customers ============================

@Composable
fun CustomersScreen(me: UserProfile, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val customers by remember { Repo.customersFlow() }.collectAsState(initial = emptyList())
    var q by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Customer?>(null) }
    val shown = customers.filter { q.isBlank() || it.name.contains(q, true) || it.phone.contains(q) }.sortedBy { it.name.lowercase() }

    Screen("Customers (${customers.size})", fab = {
        FloatingActionButton(onClick = { editing = Customer() }) { Icon(Icons.Filled.PersonAdd, "Add customer") }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Search name or phone") },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp))
            if (shown.isEmpty()) EmptyState("Customers are saved automatically when you create a bill.", Icons.Filled.People)
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { c ->
                    Card(onClick = { editing = c }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(c.name, 38)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name.ifBlank { c.phone }, fontWeight = FontWeight.SemiBold)
                                Text(listOf(c.phone, c.gstin).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                                Text(IdProof.printable(c).joinToString(" · ").ifBlank { "No ID proof saved" }, style = MaterialTheme.typography.labelSmall,
                                    color = if (IdProof.hasAny(c)) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                                if (c.remarks.isNotBlank()) Text(c.remarks, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (c.phone.isNotBlank()) IconButton(onClick = { Share.call(ctx, c.phone) }) { Icon(Icons.Filled.Call, "Call") }
                        }
                    }
                }
            }
        }
    }

    editing?.let { c ->
        var name by remember(c) { mutableStateOf(c.name) }
        var phone by remember(c) { mutableStateOf(c.phone) }
        var email by remember(c) { mutableStateOf(c.email) }
        var address by remember(c) { mutableStateOf(c.address) }
        var gstin by remember(c) { mutableStateOf(c.gstin) }
        var remarks by remember(c) { mutableStateOf(c.remarks) }
        var aadhaar by remember(c) { mutableStateOf(c.aadhaar) }
        var passport by remember(c) { mutableStateOf(c.passport) }
        var voter by remember(c) { mutableStateOf(c.voterId) }
        var dl by remember(c) { mutableStateOf(c.drivingLicence) }
        var problem by remember(c) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (c.id.isBlank()) "New customer" else "Customer") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(phone, { phone = it }, label = { Text("Mobile / WhatsApp") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(email, { email = it.trim() }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(address, { address = it }, label = { Text("Address") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(gstin, { gstin = it.uppercase().take(15) }, label = { Text("GSTIN") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(remarks, { remarks = it }, label = { Text("Remarks") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Text("ID proof — any one is needed on sale, purchase & exchange bills", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(aadhaar, { v -> aadhaar = IdProof.formatAadhaar(v.filter { it.isDigit() }.take(12)) }, label = { Text("Aadhaar number") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(passport, { passport = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(8) }, label = { Text("Passport no.") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(voter, { voter = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(10) }, label = { Text("Voter ID") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(dl, { dl = it.uppercase().take(20) }, label = { Text("Driving licence no.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val out = IdProof.normalise(c.copy(name = name.trim(), phone = phone.trim(), email = email.trim(), address = address.trim(),
                        gstin = gstin.trim(), remarks = remarks.trim(), stateCode = Billing.stateFromGstin(gstin).ifBlank { c.stateCode },
                        aadhaar = aadhaar.trim(), passport = passport.trim(), voterId = voter.trim(), drivingLicence = dl.trim()))
                    problem = if (IdProof.hasAny(out)) IdProof.problem(out) else null
                    if (problem != null) return@TextButton
                    editing = null
                    scope.launch { runCatching { Repo.saveCustomer(out, keepIds = false) }.onFailure { Share.toast(ctx, it.message ?: "Couldn't save") } }
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (me.isAdmin && c.id.isNotBlank()) TextButton(onClick = {
                        editing = null; scope.launch { runCatching { Repo.deleteCustomer(c.id) } }
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editing = null }) { Text("Close") }
                }
            }
        )
    }
}

