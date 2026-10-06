package com.digiglobal.goldbill.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.digiglobal.goldbill.data.*
import com.digiglobal.goldbill.ui.*
import com.digiglobal.goldbill.ui.theme.GoldColor
import com.digiglobal.goldbill.ui.theme.SilverColor
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Carries an invoice into the editor (estimate → sale, duplicate a bill). */
object Prefill {
    private var pending: Invoice? = null
    fun set(inv: Invoice) { pending = inv }
    fun take(): Invoice? = pending.also { pending = null }
}

private val PAY_MODES = listOf("Cash", "UPI", "Card", "Bank transfer", "Cheque", "Cash + UPI", "Credit (pay later)")

/** Number text field that keeps what the user types and reports the parsed value. */
@Composable
fun NumField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, suffix: String = "") {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' }.let { s -> if (s.count { it == '.' } > 1) value else s }) },
        label = { Text(label, maxLines = 1) }, singleLine = true,
        suffix = { if (suffix.isNotBlank()) Text(suffix) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

private fun d(s: String) = s.toDoubleOrNull() ?: 0.0
private fun s(v: Double) = if (v == 0.0) "" else if (v == Math.floor(v)) v.toLong().toString() else v.toString()

@Composable
fun InvoiceEditorScreen(type: InvoiceType, me: UserProfile, org: Org, shop: ShopSettings, rates: Rates, nav: NavController, editId: String? = null) {
    val scope = rememberCoroutineScope()
    // Estimates don't ask for ID proof; product photos are for sale, purchase and exchange bills.
    val withIds = type != InvoiceType.ESTIMATE
    val withPhotos = type != InvoiceType.ESTIMATE
    // Photos taken in this session, id → picture, saved together with the bill.
    val pendingPhotos = remember { mutableStateMapOf<String, String>() }
    var original by remember { mutableStateOf<Invoice?>(null) }
    var loading by remember { mutableStateOf(editId != null) }
    var viewPhoto by remember { mutableStateOf<String?>(null) }
    val customers by remember { Repo.customersFlow() }.collectAsState(initial = emptyList())

    // customer
    var cName by remember { mutableStateOf("") }
    var cPhone by remember { mutableStateOf("") }
    var cEmail by remember { mutableStateOf("") }
    var cAddress by remember { mutableStateOf("") }
    var cGstin by remember { mutableStateOf("") }
    var cPan by remember { mutableStateOf("") }
    var cState by remember(shop.stateCode) { mutableStateOf(shop.stateCode) }
    var cRemarks by remember { mutableStateOf("") }
    var cId by remember { mutableStateOf("") }
    var moreCustomer by remember { mutableStateOf(false) }
    var cAadhaar by remember { mutableStateOf("") }
    var cPassport by remember { mutableStateOf("") }
    var cVoter by remember { mutableStateOf("") }
    var cDl by remember { mutableStateOf("") }

    fun fillFrom(c: Customer, withPhone: Boolean) {
        cId = c.id; cName = c.name; if (withPhone) cPhone = c.phone; cEmail = c.email; cAddress = c.address; cGstin = c.gstin; cPan = c.pan
        if (c.stateCode.isNotBlank()) cState = c.stateCode
        cRemarks = c.remarks
        cAadhaar = c.aadhaar; cPassport = c.passport; cVoter = c.voterId; cDl = c.drivingLicence
    }

    // lines
    val items = remember { mutableStateListOf<SaleItem>() }
    val oldItems = remember { mutableStateListOf<OldItem>() }
    var editItem by remember { mutableStateOf<Pair<Int, SaleItem>?>(null) }      // index -1 = new
    var editOld by remember { mutableStateOf<Pair<Int, OldItem>?>(null) }

    // charges & payment
    var discount by remember { mutableStateOf("") }
    var includeGst by remember { mutableStateOf(true) }
    val autoInter = shop.stateCode.isNotBlank() && cState.isNotBlank() && cState != shop.stateCode
    var interOverride by remember { mutableStateOf<Boolean?>(null) }
    val interState = interOverride ?: autoInter
    var payMode by remember { mutableStateOf("Cash") }
    var paid by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var terms by remember(shop.terms) { mutableStateOf(shop.terms) }
    var showTerms by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Copy from an estimate (or another bill) when "Convert" / "Duplicate" was tapped.
    LaunchedEffect(Unit) {
        Prefill.take()?.let { src ->
            fillFrom(src.customer, withPhone = true)
            if (type.hasNewItems) { items.clear(); items.addAll(src.items) }
            if (type.hasOldItems) { oldItems.clear(); oldItems.addAll(src.oldItems) }
            discount = s(src.discount); remarks = src.remarks; payMode = src.payMode
        }
    }

    // Editing a saved bill: load it and fill every field.
    LaunchedEffect(editId) {
        val id = editId ?: return@LaunchedEffect
        val inv = Repo.invoiceOnce(id)
        if (inv == null) { error = "Couldn't load this bill"; loading = false; return@LaunchedEffect }
        original = inv
        fillFrom(inv.customer, withPhone = true)
        items.clear(); items.addAll(inv.items)
        oldItems.clear(); oldItems.addAll(inv.oldItems)
        discount = s(inv.discount); includeGst = inv.includeGst; interOverride = inv.interState
        payMode = inv.payMode; paid = s(inv.amountPaid); remarks = inv.remarks; terms = inv.terms.ifBlank { shop.terms }
        loading = false
    }

    // A bill copied from an estimate has no ID proof: take it from the saved customer when there is one.
    LaunchedEffect(customers, cPhone) {
        if (!withIds || listOf(cAadhaar, cPassport, cVoter, cDl).any { it.isNotBlank() }) return@LaunchedEffect
        val digits = cPhone.filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) return@LaunchedEffect
        customers.firstOrNull { it.phone.filter { c -> c.isDigit() }.takeLast(10) == digits }?.let { c ->
            cAadhaar = c.aadhaar; cPassport = c.passport; cVoter = c.voterId; cDl = c.drivingLicence
        }
    }

    fun draft() = Invoice(
        type = type.code,
        customer = IdProof.normalise(Customer(cId, cName.trim(), cPhone.trim(), cEmail.trim(), cAddress.trim(), cGstin.trim().uppercase(),
            cPan.trim().uppercase(), cState, cRemarks.trim(),
            if (withIds) cAadhaar.trim() else "", if (withIds) cPassport.trim() else "",
            if (withIds) cVoter.trim() else "", if (withIds) cDl.trim() else "")),
        items = items.toList(), oldItems = oldItems.toList(), discount = d(discount), gstRate = shop.gstRate,
        includeGst = includeGst, interState = interState, payMode = payMode, amountPaid = d(paid),
        remarks = remarks.trim(), terms = terms
    )
    val totals = draft().totals

    val newRate = { metal: String, purity: String -> Billing.rateFor(metal, purity, rates) }

    val title = original?.let { "Edit ${it.number}" } ?: if (editId != null) "Edit bill" else "New ${type.label.lowercase()}"
    Screen(title, onBack = { nav.popBackStack() }) { pad ->
        if (loading) { Loading(); return@Screen }
        Column(Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (type != InvoiceType.ESTIMATE && shop.gstin.isBlank() && type != InvoiceType.PURCHASE) item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Text(if (me.isAdmin) "Your shop's GSTIN and address aren't set. Add them in More → Shop & invoice settings so they print on invoices."
                             else "The shop's GSTIN isn't set yet. Ask your admin to add it in Shop settings.",
                            Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }

                // ---------- customer ----------
                item {
                    SectionCard(if (type == InvoiceType.PURCHASE) "Seller (customer)" else "Customer") {
                        OutlinedTextField(cPhone, { v ->
                            cPhone = v
                            val digits = v.filter { it.isDigit() }.takeLast(10)
                            if (digits.length == 10) customers.firstOrNull { it.phone.filter { c -> c.isDigit() }.takeLast(10) == digits }?.let { c -> fillFrom(c, withPhone = false) }
                        }, label = { Text("Mobile / WhatsApp number") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
                            supportingText = { if (cId.isNotBlank()) Text("Existing customer — details filled in") })
                        OutlinedTextField(cName, { cName = it }, label = { Text("Customer name *") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth())
                        // name suggestions
                        if (cName.length >= 2 && cId.isBlank()) {
                            val matches = customers.filter { it.name.contains(cName, true) }.take(3)
                            matches.forEach { c ->
                                AssistChip(onClick = { fillFrom(c, withPhone = true) }, label = { Text("${c.name} · ${c.phone}") }, leadingIcon = { Icon(Icons.Filled.Person, null) })
                            }
                        }
                        OutlinedTextField(cEmail, { cEmail = it.trim() }, label = { Text("Email (to send the invoice)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = { moreCustomer = !moreCustomer }) {
                            Text(if (moreCustomer) "Hide address, GSTIN & remarks" else "Add address, GSTIN, PAN & remarks")
                        }
                        if (moreCustomer) {
                            OutlinedTextField(cAddress, { cAddress = it }, label = { Text("Address") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(cGstin, { v ->
                                    cGstin = v.uppercase().take(15)
                                    Billing.stateFromGstin(cGstin).takeIf { it.isNotBlank() && cGstin.length >= 2 }?.let { cState = it }
                                }, label = { Text("Customer GSTIN") }, singleLine = true, modifier = Modifier.weight(1f),
                                    isError = cGstin.length == 15 && !Billing.isValidGstin(cGstin))
                                OutlinedTextField(cPan, { cPan = it.uppercase().take(10) }, label = { Text("PAN") }, singleLine = true, modifier = Modifier.weight(0.7f))
                            }
                            DropdownField("State (place of supply)", Billing.stateLabel(cState).ifBlank { "Select" },
                                Billing.states.map { "${it.second} (${it.first})" }, Modifier.fillMaxWidth()) { cState = Billing.stateCodeFromLabel(it) }
                            OutlinedTextField(cRemarks, { cRemarks = it }, label = { Text("Customer remarks (saved with customer)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                // ---------- ID proof: at least one (not for estimates) ----------
                if (withIds) item {
                    val okCount = listOf(cAadhaar, cPassport, cVoter, cDl).count { it.isNotBlank() }
                    SectionCard("Customer ID proof *") {
                        Text(if (okCount == 0) "Enter any one — Aadhaar, passport, voter ID or driving licence. The rest are optional."
                             else "$okCount ID entered. Aadhaar prints as XXXX XXXX + last 4 digits only.",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (okCount == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(cAadhaar, { v -> cAadhaar = IdProof.formatAadhaar(v.filter { it.isDigit() }.take(12)) },
                            label = { Text("Aadhaar number") }, placeholder = { Text("1234 5678 9012") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                            isError = cAadhaar.isNotBlank() && IdProof.cleanAadhaar(cAadhaar).length == 12 && !IdProof.isValidAadhaar(cAadhaar),
                            supportingText = { if (cAadhaar.isNotBlank() && IdProof.cleanAadhaar(cAadhaar).length == 12 && !IdProof.isValidAadhaar(cAadhaar)) Text("Not a valid Aadhaar number") })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(cPassport, { cPassport = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(8) },
                                label = { Text("Passport no.") }, placeholder = { Text("K1234567") }, singleLine = true, modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                isError = cPassport.length == 8 && !IdProof.isValidPassport(cPassport))
                            OutlinedTextField(cVoter, { cVoter = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(10) },
                                label = { Text("Voter ID") }, placeholder = { Text("ABC1234567") }, singleLine = true, modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                isError = cVoter.length == 10 && !IdProof.isValidVoterId(cVoter))
                        }
                        OutlinedTextField(cDl, { cDl = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == ' ' || ch == '-' }.take(20) },
                            label = { Text("Driving licence no.") }, placeholder = { Text("MH12 20110012345") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters), modifier = Modifier.fillMaxWidth())
                    }
                }

                // ---------- new items ----------
                if (type.hasNewItems) {
                    item {
                        SectionCard(if (type == InvoiceType.EXCHANGE) "New items sold" else "Items", trailing = {
                            TextButton(onClick = {
                                val purity = "22K (916)"
                                editItem = -1 to SaleItem(metal = Metal.GOLD, purity = purity, hsn = shop.hsnGold, rate = newRate(Metal.GOLD, purity))
                            }) { Icon(Icons.Filled.Add, null); Text("Add item") }
                        }) {
                            if (items.isEmpty()) Text("No items yet. Tap “Add item”.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            items.forEachIndexed { i, it -> ItemLine(it, pendingPhotos, onPhoto = { viewPhoto = it.photo }, onEdit = { editItem = i to it }, onDelete = { items.removeAt(i) }) }
                        }
                    }
                }

                // ---------- old items ----------
                if (type.hasOldItems) {
                    item {
                        SectionCard(if (type == InvoiceType.PURCHASE) "Old gold / silver bought" else "Old gold / silver received", trailing = {
                            TextButton(onClick = {
                                val purity = "22K (916)"
                                editOld = -1 to OldItem(metal = Metal.GOLD, purity = purity, rate = newRate(Metal.GOLD, purity))
                            }) { Icon(Icons.Filled.Add, null); Text("Add old item") }
                        }) {
                            if (oldItems.isEmpty()) Text("No old items yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            oldItems.forEachIndexed { i, it -> OldLine(it, pendingPhotos, onPhoto = { viewPhoto = it.photo }, onEdit = { editOld = i to it }, onDelete = { oldItems.removeAt(i) }) }
                        }
                    }
                }

                // ---------- GST & discount ----------
                if (type.hasNewItems) item {
                    SectionCard("Discount & GST") {
                        NumField("Discount ₹", discount, { discount = it }, Modifier.fillMaxWidth())
                        if (type == InvoiceType.ESTIMATE) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Include GST in estimate", Modifier.weight(1f))
                                Switch(includeGst, { includeGst = it })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (interState) "IGST ${fmtPct(shop.gstRate)}% (other state)" else "CGST ${fmtPct(shop.gstRate / 2)}% + SGST ${fmtPct(shop.gstRate / 2)}%")
                                Text("Other-state customer → IGST", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(interState, { interOverride = it })
                        }
                    }
                }

                // ---------- payment ----------
                item {
                    SectionCard("Payment") {
                        DropdownField("Payment mode", payMode, PAY_MODES, Modifier.fillMaxWidth()) { payMode = it }
                        if (type != InvoiceType.ESTIMATE) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                NumField(if (totals.net < 0) "Paid to customer ₹" else "Amount received ₹", paid, { paid = it }, Modifier.weight(1f))
                                TextButton(onClick = { paid = s(abs(totals.net)) }) { Text("Full") }
                            }
                        }
                        OutlinedTextField(remarks, { remarks = it }, label = { Text("Remarks on this bill") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = { showTerms = !showTerms }) { Text(if (showTerms) "Hide terms & conditions" else "Edit terms & conditions for this bill") }
                        if (showTerms) OutlinedTextField(terms, { terms = it }, label = { Text("Terms & conditions (one per line)") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                    }
                }

                // ---------- totals ----------
                item { TotalsCard(type, totals, interState, shop.gstRate) }
                error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }
                item { Spacer(Modifier.height(8.dp)) }
            }

            // ---------- bottom save bar ----------
            Surface(shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (totals.net < 0) "Pay customer" else "Total", style = MaterialTheme.typography.labelSmall)
                        Text(money(abs(totals.net)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Button(enabled = !busy, onClick = {
                        error = null
                        val dr = draft()
                        error = when {
                            dr.customer.name.isBlank() -> "Enter the customer's name"
                            withIds && IdProof.problem(dr.customer) != null -> IdProof.problem(dr.customer)
                            type.hasNewItems && dr.items.isEmpty() -> "Add at least one item"
                            type.hasOldItems && dr.oldItems.isEmpty() -> "Add at least one old gold / silver item"
                            dr.items.any { it.grossWt <= 0 || it.rate <= 0 } -> "Every item needs a weight and a rate"
                            dr.oldItems.any { it.grossWt <= 0 || it.rate <= 0 } -> "Every old item needs a weight and a rate"
                            else -> null
                        }
                        if (error != null) return@Button
                        busy = true
                        scope.launch {
                            try {
                                val orig = original
                                if (orig != null) {
                                    Repo.updateInvoice(orig, dr, shop, pendingPhotos.toMap())
                                    nav.popBackStack()
                                } else {
                                    val saved = Repo.createInvoice(dr, shop, pendingPhotos.toMap())
                                    nav.navigate("invoice/${saved.id}") { popUpTo("home") }
                                }
                            } catch (e: Exception) {
                                error = e.message ?: "Couldn't save the bill"
                            } finally { busy = false }
                        }
                    }, modifier = Modifier.height(50.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else { Icon(Icons.Filled.Check, null); Spacer(Modifier.width(6.dp)); Text(if (editId != null) "Save changes" else "Save ${type.label.lowercase()}") }
                    }
                }
            }
        }
    }

    editItem?.let { (idx, item) ->
        ItemDialog(item, rates, shop, withPhotos, pendingPhotos, onDismiss = { editItem = null }) { updated ->
            if (idx < 0) items.add(updated) else items[idx] = updated
            editItem = null
        }
    }
    editOld?.let { (idx, item) ->
        OldItemDialog(item, rates, withPhotos, pendingPhotos, onDismiss = { editOld = null }) { updated ->
            if (idx < 0) oldItems.add(updated) else oldItems[idx] = updated
            editOld = null
        }
    }
    viewPhoto?.let { PhotoViewer(it, pendingPhotos) { viewPhoto = null } }
}

fun fmtPct(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

@Composable
private fun ItemLine(it: SaleItem, unsaved: Map<String, String>, onPhoto: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onEdit, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (it.photo.isNotBlank()) { ProductThumb(it.photo, 44, unsaved, onPhoto); Spacer(Modifier.width(10.dp)) }
            Column(Modifier.weight(1f)) {
                Text(it.description.ifBlank { "${it.metal} item" } + " · ${it.purity}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Net ${Billing.grams(it.netWt)} × ₹${Billing.inr(it.rate, false)} · making ₹${Billing.inr(it.making, false)}",
                    style = MaterialTheme.typography.labelSmall, color = if (it.metal == Metal.SILVER) SilverColor else GoldColor)
            }
            Text(money(it.amount), fontWeight = FontWeight.Bold)
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Remove", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun OldLine(it: OldItem, unsaved: Map<String, String>, onPhoto: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onEdit, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (it.photo.isNotBlank()) { ProductThumb(it.photo, 44, unsaved, onPhoto); Spacer(Modifier.width(10.dp)) }
            Column(Modifier.weight(1f)) {
                Text(it.description.ifBlank { "Old ${it.metal.lowercase()}" } + " · ${it.purity}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Gross ${Billing.grams(it.grossWt)} − ${fmtPct(it.lessPct)}% = net ${Billing.grams(it.netWt)} × ₹${Billing.inr(it.rate, false)}",
                    style = MaterialTheme.typography.labelSmall, color = if (it.metal == Metal.SILVER) SilverColor else GoldColor)
            }
            Text(money(it.value), fontWeight = FontWeight.Bold)
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Remove", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun TotalsCard(type: InvoiceType, t: Totals, interState: Boolean, gstRate: Double) {
    SectionCard("Bill summary") {
        @Composable fun line(l: String, v: String, bold: Boolean = false) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(l, Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
                Text(v, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
            }
        }
        if (type.hasNewItems) {
            line("Items total", "₹" + Billing.inr(t.itemsTotal))
            if (t.discount > 0) line("Discount", "− ₹" + Billing.inr(t.discount))
            line("Taxable value", "₹" + Billing.inr(t.taxable))
            if (t.igst > 0) line("IGST ${fmtPct(gstRate)}%", "₹" + Billing.inr(t.igst))
            if (t.cgst > 0) { line("CGST ${fmtPct(gstRate / 2)}%", "₹" + Billing.inr(t.cgst)); line("SGST ${fmtPct(gstRate / 2)}%", "₹" + Billing.inr(t.sgst)) }
            if (t.roundOff != 0.0) line("Round off", "₹" + Billing.inr(t.roundOff))
            line(if (type == InvoiceType.ESTIMATE) "Estimated total" else "Invoice total", "₹" + Billing.inr(t.grandTotal), true)
        }
        if (type == InvoiceType.EXCHANGE) line("Less: old gold / silver", "− ₹" + Billing.inr(t.oldTotal))
        if (type == InvoiceType.PURCHASE) line("Value of items bought", "₹" + Billing.inr(t.oldTotal))
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        line(if (t.net < 0) "Pay to customer" else "Customer pays", "₹" + Billing.inr(abs(t.net)), true)
        if (t.balance > 0 && type != InvoiceType.ESTIMATE) line("Balance due", "₹" + Billing.inr(t.balance))
        val g = listOf(
            if (t.goldOut > 0) "Gold out ${Billing.grams(t.goldOut)}" else null, if (t.silverOut > 0) "Silver out ${Billing.grams(t.silverOut)}" else null,
            if (t.goldIn > 0) "Gold in ${Billing.grams(t.goldIn)}" else null, if (t.silverIn > 0) "Silver in ${Billing.grams(t.silverIn)}" else null
        ).filterNotNull().joinToString(" · ")
        if (g.isNotBlank()) Text(g, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ============================ item dialogs ============================

@Composable
private fun ItemDialog(start: SaleItem, rates: Rates, shop: ShopSettings, withPhoto: Boolean, pending: MutableMap<String, String>,
                       onDismiss: () -> Unit, onSave: (SaleItem) -> Unit) {
    var photo by remember { mutableStateOf(start.photo) }
    var desc by remember { mutableStateOf(start.description) }
    var metal by remember { mutableStateOf(start.metal) }
    var purity by remember { mutableStateOf(start.purity) }
    var hsn by remember { mutableStateOf(start.hsn) }
    var huid by remember { mutableStateOf(start.huid) }
    var pcs by remember { mutableStateOf(start.pcs.toString()) }
    var gross by remember { mutableStateOf(s(start.grossWt)) }
    var stone by remember { mutableStateOf(s(start.stoneWt)) }
    var wastage by remember { mutableStateOf(s(start.wastagePct)) }
    var rate by remember { mutableStateOf(s(start.rate)) }
    var makingType by remember { mutableStateOf(MakingType.of(start.makingType)) }
    var making by remember { mutableStateOf(s(start.makingValue)) }
    var stoneCh by remember { mutableStateOf(s(start.stoneCharges)) }

    val item = SaleItem(desc.trim(), metal, purity, hsn.trim(), huid.trim().uppercase(), pcs.toIntOrNull()?.coerceAtLeast(1) ?: 1,
        d(gross), d(stone), d(wastage), d(rate), makingType.code, d(making), d(stoneCh), if (withPhoto) photo else "")

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.92f)) {
            Column(Modifier.padding(18.dp)) {
                Text(if (start.description.isBlank() && start.grossWt == 0.0) "Add item" else "Edit item", style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(desc, { desc = it }, label = { Text("Item (e.g. Necklace, Ring, Coin)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth())
                    if (withPhoto) ProductPhotoField(photo, pending) { photo = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DropdownField("Metal", metal, Metal.all, Modifier.weight(1f)) { m ->
                            metal = m; purity = Metal.purities(m).first()
                            hsn = if (m == Metal.SILVER) shop.hsnSilver else shop.hsnGold
                            Billing.rateFor(m, purity, rates).takeIf { it > 0 }?.let { rate = s(it) }
                        }
                        DropdownField("Purity", purity, Metal.purities(metal), Modifier.weight(1f)) { p ->
                            purity = p; Billing.rateFor(metal, p, rates).takeIf { it > 0 }?.let { rate = s(it) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(hsn, { hsn = it.filter(Char::isDigit).take(8) }, label = { Text("HSN") }, singleLine = true, modifier = Modifier.weight(0.7f))
                        OutlinedTextField(huid, { huid = it.uppercase().take(6) }, label = { Text("HUID") }, singleLine = true, modifier = Modifier.weight(0.8f))
                        OutlinedTextField(pcs, { pcs = it.filter(Char::isDigit).take(4) }, label = { Text("Pcs") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(0.5f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("Gross wt", gross, { gross = it }, Modifier.weight(1f), "g")
                        NumField("Stone wt", stone, { stone = it }, Modifier.weight(1f), "g")
                    }
                    Text("Net weight: ${Billing.grams(item.netWt)}", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("Rate / gram", rate, { rate = it }, Modifier.weight(1f), "₹")
                        NumField("Wastage / VA", wastage, { wastage = it }, Modifier.weight(0.8f), "%")
                    }
                    DropdownField("Making charges", makingType.label, MakingType.values().map { it.label }, Modifier.fillMaxWidth()) { l ->
                        makingType = MakingType.values().first { it.label == l }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField(when (makingType) { MakingType.PER_GRAM -> "Making / gram"; MakingType.PERCENT -> "Making %"; MakingType.FIXED -> "Making ₹" },
                            making, { making = it }, Modifier.weight(1f))
                        NumField("Stone charges", stoneCh, { stoneCh = it }, Modifier.weight(1f), "₹")
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(12.dp).fillMaxWidth()) {
                            Text("Metal ₹${Billing.inr(item.metalValue)}  (${Billing.grams(item.chargeableWt)} charged)", style = MaterialTheme.typography.bodySmall)
                            Text("Making ₹${Billing.inr(item.making)} · Stone ₹${Billing.inr(item.stoneCharges)}", style = MaterialTheme.typography.bodySmall)
                            Text("Item amount ₹${Billing.inr(item.amount)}  (before GST)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(enabled = item.grossWt > 0 && item.rate > 0, onClick = { onSave(item) }) { Text("Save item") }
                }
            }
        }
    }
}

@Composable
private fun OldItemDialog(start: OldItem, rates: Rates, withPhoto: Boolean, pending: MutableMap<String, String>,
                          onDismiss: () -> Unit, onSave: (OldItem) -> Unit) {
    var photo by remember { mutableStateOf(start.photo) }
    var desc by remember { mutableStateOf(start.description) }
    var metal by remember { mutableStateOf(start.metal) }
    var purity by remember { mutableStateOf(start.purity) }
    var gross by remember { mutableStateOf(s(start.grossWt)) }
    var less by remember { mutableStateOf(s(start.lessPct)) }
    var rate by remember { mutableStateOf(s(start.rate)) }
    val item = OldItem(desc.trim(), metal, purity, d(gross), d(less), d(rate), if (withPhoto) photo else "")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Old gold / silver") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(desc, { desc = it }, label = { Text("Description (e.g. Old chain)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (withPhoto) ProductPhotoField(photo, pending) { photo = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownField("Metal", metal, listOf(Metal.GOLD, Metal.SILVER), Modifier.weight(1f)) { m ->
                        metal = m; purity = Metal.purities(m).first(); Billing.rateFor(m, purity, rates).takeIf { it > 0 }?.let { rate = s(it) }
                    }
                    DropdownField("Purity", purity, Metal.purities(metal), Modifier.weight(1f)) { p ->
                        purity = p; Billing.rateFor(metal, p, rates).takeIf { it > 0 }?.let { rate = s(it) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Gross wt", gross, { gross = it }, Modifier.weight(1f), "g")
                    NumField("Less", less, { less = it }, Modifier.weight(0.8f), "%")
                }
                NumField("Rate / gram", rate, { rate = it }, Modifier.fillMaxWidth(), "₹")
                Text("Net ${Billing.grams(item.netWt)} · Value ₹${Billing.inr(item.value)}", fontWeight = FontWeight.Bold)
                Text("“Less %” covers melting loss, dirt or stones removed after testing.", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(enabled = item.grossWt > 0 && item.rate > 0, onClick = { onSave(item) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Product photo inside an item: preview with remove, or Camera / Gallery buttons. */
@Composable
private fun ProductPhotoField(photo: String, pending: MutableMap<String, String>, onChange: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Product photo (optional)", style = MaterialTheme.typography.labelMedium)
        if (photo.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProductThumb(photo, 72, pending)
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = { onChange("") }) { Icon(Icons.Filled.Delete, null); Text("Remove photo") }
            }
        } else {
            PhotoButtons(PicKind.PRODUCT, onPicked = { b64 ->
                if (b64 == null) com.digiglobal.goldbill.util.Share.toast(ctx, "Couldn't read that photo")
                else { val id = Repo.newPhotoId(); pending[id] = b64; onChange(id) }
            })
        }
    }
}
