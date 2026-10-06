package com.digiglobal.goldbill.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.digiglobal.goldbill.data.*
import com.digiglobal.goldbill.ui.*
import com.digiglobal.goldbill.util.Fmt
import com.digiglobal.goldbill.util.Share
import kotlinx.coroutines.launch

// ============================ shop & invoice settings (admin) ============================

@Composable
fun ShopSettingsScreen(me: UserProfile, shop: ShopSettings, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val admin = me.isAdmin
    var name by remember(shop) { mutableStateOf(shop.name) }
    var address by remember(shop) { mutableStateOf(shop.address) }
    var phone by remember(shop) { mutableStateOf(shop.phone) }
    var email by remember(shop) { mutableStateOf(shop.email) }
    var gstin by remember(shop) { mutableStateOf(shop.gstin) }
    var state by remember(shop) { mutableStateOf(shop.stateCode) }
    var pan by remember(shop) { mutableStateOf(shop.pan) }
    var bank by remember(shop) { mutableStateOf(shop.bankName) }
    var acc by remember(shop) { mutableStateOf(shop.accountNo) }
    var ifsc by remember(shop) { mutableStateOf(shop.ifsc) }
    var upi by remember(shop) { mutableStateOf(shop.upiId) }
    var terms by remember(shop) { mutableStateOf(shop.terms) }
    var footer by remember(shop) { mutableStateOf(shop.footerNote) }
    var gstRate by remember(shop) { mutableStateOf(fmtPct(shop.gstRate)) }
    var invP by remember(shop) { mutableStateOf(shop.invoicePrefix) }
    var purP by remember(shop) { mutableStateOf(shop.purchasePrefix) }
    var estP by remember(shop) { mutableStateOf(shop.estimatePrefix) }
    var hsnG by remember(shop) { mutableStateOf(shop.hsnGold) }
    var hsnS by remember(shop) { mutableStateOf(shop.hsnSilver) }
    var signature by remember(shop) { mutableStateOf(shop.signature) }
    var sigName by remember(shop) { mutableStateOf(shop.signatoryName) }
    var sigTitle by remember(shop) { mutableStateOf(shop.signatoryTitle) }
    var showSig by remember(shop) { mutableStateOf(shop.showSignature) }
    var printDocs by remember(shop) { mutableStateOf(shop.printDocuments) }
    var saving by remember { mutableStateOf(false) }

    @Composable fun field(label: String, v: String, set: (String) -> Unit, lines: Int = 1, kb: KeyboardType = KeyboardType.Text) =
        OutlinedTextField(v, set, label = { Text(label) }, enabled = admin, singleLine = lines == 1, minLines = lines,
            keyboardOptions = KeyboardOptions(keyboardType = kb), modifier = Modifier.fillMaxWidth())

    Screen("Shop & invoice settings", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!admin) Text("Only the admin can change these.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionCard("Printed at the top of every invoice") {
                field("Shop / legal name", name, { name = it })
                field("Address", address, { address = it }, 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, enabled = admin, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.weight(1f))
                    OutlinedTextField(email, { email = it.trim() }, label = { Text("Email") }, enabled = admin, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(gstin, { v -> gstin = v.uppercase().take(15); Billing.stateFromGstin(gstin).takeIf { it.isNotBlank() }?.let { state = it } },
                    label = { Text("Shop GSTIN") }, enabled = admin, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    isError = gstin.length == 15 && !Billing.isValidGstin(gstin),
                    supportingText = { if (gstin.length == 15 && !Billing.isValidGstin(gstin)) Text("This doesn't look like a valid GSTIN") })
                DropdownField("Shop state", Billing.stateLabel(state).ifBlank { "Select" }, Billing.states.map { "${it.second} (${it.first})" }, Modifier.fillMaxWidth()) {
                    if (admin) state = Billing.stateCodeFromLabel(it)
                }
                field("PAN", pan, { pan = it.uppercase().take(10) })
            }
            SectionCard("GST & HSN") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("GST rate", gstRate, { gstRate = it }, Modifier.weight(1f), "%")
                    OutlinedTextField(hsnG, { hsnG = it.filter(Char::isDigit).take(8) }, label = { Text("HSN gold") }, enabled = admin, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(hsnS, { hsnS = it.filter(Char::isDigit).take(8) }, label = { Text("HSN silver") }, enabled = admin, singleLine = true, modifier = Modifier.weight(1f))
                }
                Text("Gold & silver jewellery is 3% GST (CGST 1.5% + SGST 1.5%, or IGST 3% for other states). HSN 7113 = jewellery, 7108 = gold bullion, 7106 = silver bullion.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionCard("Bill numbering") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(invP, { invP = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) }, label = { Text("Sale/Exch.") }, enabled = admin, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(purP, { purP = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) }, label = { Text("Purchase") }, enabled = admin, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(estP, { estP = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) }, label = { Text("Estimate") }, enabled = admin, singleLine = true, modifier = Modifier.weight(1f))
                }
                Text("Example: ${Billing.formatNumber(invP.ifBlank { "INV" }, Billing.financialYear(System.currentTimeMillis()), 1)}. Numbers restart every financial year (April).",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionCard("Bank & UPI (printed on invoices, optional)") {
                field("Bank name", bank, { bank = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(acc, { acc = it }, label = { Text("Account no.") }, enabled = admin, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(ifsc, { ifsc = it.uppercase() }, label = { Text("IFSC") }, enabled = admin, singleLine = true, modifier = Modifier.weight(0.8f))
                }
                field("UPI ID", upi, { upi = it.trim() })
            }
            SectionCard("Authorised signatory (bottom right of every bill)") {
                val pic = rememberPicture(signature)
                if (pic != null) {
                    Surface(shape = RoundedCornerShape(10.dp), color = androidx.compose.ui.graphics.Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        androidx.compose.foundation.Image(pic, "Signature", contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().height(90.dp).padding(8.dp))
                    }
                } else Text("No signature uploaded yet. Sign on plain white paper (you can add the shop stamp too) and take a photo.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (admin) {
                    PhotoButtons(PicKind.SIGNATURE, onPicked = { b64 ->
                        if (b64 == null) Share.toast(ctx, "Couldn't read that picture") else signature = b64
                    })
                    if (signature.isNotBlank()) TextButton(onClick = { signature = "" }) { Text("Remove signature", color = MaterialTheme.colorScheme.error) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Print the signature on bills", Modifier.weight(1f))
                    Switch(showSig, { showSig = it }, enabled = admin)
                }
                field("Signatory name (optional, e.g. Rajesh Kumar)", sigName, { sigName = it })
                field("Title under the signature", sigTitle, { sigTitle = it })
                Text("Prints as: For ${name.ifBlank { "your shop" }} · signature · ${sigName.ifBlank { "" }} ${sigTitle.ifBlank { "Authorised Signatory" }}".replace("  ", " "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionCard("Customer documents") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Print customer document photos on bills")
                        Text("Aadhaar, PAN or other ID photos taken while billing are printed large at the end of the bill. " +
                            "Switch off to keep them only in the app.", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(printDocs, { printDocs = it }, enabled = admin)
                }
            }
            SectionCard("Terms & conditions") {
                field("One term per line", terms, { terms = it }, 6)
                field("Thank-you note at the bottom", footer, { footer = it })
                if (admin) TextButton(onClick = { terms = ShopSettings.DEFAULT_TERMS }) { Text("Reset to standard terms") }
            }
            if (admin) Button(enabled = !saving, onClick = {
                saving = true
                scope.launch {
                    try {
                        Repo.saveShop(ShopSettings(name.trim(), address.trim(), phone.trim(), email.trim(), gstin.trim(), state, pan.trim(),
                            bank.trim(), acc.trim(), ifsc.trim(), upi.trim(), terms.trim(), footer.trim(), gstRate.toDoubleOrNull() ?: 3.0,
                            invP.ifBlank { "INV" }, purP.ifBlank { "PUR" }, estP.ifBlank { "EST" }, hsnG.ifBlank { "7113" }, hsnS.ifBlank { "7113" },
                            signature, sigName.trim(), sigTitle.trim().ifBlank { "Authorised Signatory" }, showSig, printDocs))
                        Share.toast(ctx, "Saved"); nav.popBackStack()
                    } catch (e: Exception) { Share.toast(ctx, e.message ?: "Couldn't save") } finally { saving = false }
                }
            }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Save settings") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ============================ today's rates ============================

@Composable
fun RatesScreen(me: UserProfile, rates: Rates, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    fun s(v: Double) = if (v == 0.0) "" else if (v == Math.floor(v)) v.toLong().toString() else v.toString()
    var g24 by remember(rates) { mutableStateOf(s(rates.gold24)) }
    var g22 by remember(rates) { mutableStateOf(s(rates.gold22)) }
    var g18 by remember(rates) { mutableStateOf(s(rates.gold18)) }
    var sil by remember(rates) { mutableStateOf(s(rates.silver)) }

    Screen("Today's rates", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RatesCard(rates, me.isAdmin) {}
            if (rates.updatedAt > 0) Text("Last updated ${Fmt.dateTime(rates.updatedAt)} by ${rates.updatedBy}", style = MaterialTheme.typography.labelSmall)
            if (me.isAdmin) {
                SectionCard("Set rates per gram") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("Gold 24K", g24, { g24 = it }, Modifier.weight(1f), "₹")
                        NumField("Gold 22K", g22, { g22 = it }, Modifier.weight(1f), "₹")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("Gold 18K", g18, { g18 = it }, Modifier.weight(1f), "₹")
                        NumField("Silver 999", sil, { sil = it }, Modifier.weight(1f), "₹")
                    }
                    TextButton(onClick = {
                        g24.toDoubleOrNull()?.let { v -> g22 = s(Math.round(v * 0.916).toDouble()); g18 = s(Math.round(v * 0.75).toDouble()) }
                    }) { Text("Work out 22K & 18K from 24K") }
                    Text("These fill in automatically when a new item is added to a bill. Billing users can still change the rate on a bill.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = {
                    scope.launch {
                        runCatching { Repo.saveRates(Rates(g24.toDoubleOrNull() ?: 0.0, g22.toDoubleOrNull() ?: 0.0, g18.toDoubleOrNull() ?: 0.0, sil.toDoubleOrNull() ?: 0.0)) }
                            .onSuccess { Share.toast(ctx, "Rates updated"); nav.popBackStack() }
                            .onFailure { Share.toast(ctx, it.message ?: "Couldn't save") }
                    }
                }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Save today's rates") }
            } else Text("Only the admin can change rates.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ============================ team (admin) ============================

@Composable
fun TeamScreen(me: UserProfile, org: Org, team: List<UserProfile>, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending = team.filter { !it.approved }
    val members = team.filter { it.approved }.sortedWith(compareBy<UserProfile> { !it.isAdmin }.thenBy { it.name })
    var removeAsk by remember { mutableStateOf<UserProfile?>(null) }

    Screen("Team", onBack = { nav.popBackStack() }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CompanyLogo(org.name, org.logo, 52)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(org.name, style = MaterialTheme.typography.labelLarge)
                            Text("Shop code", style = MaterialTheme.typography.labelSmall)
                            Text(org.code, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                            Text("Billing staff enter this code after creating an account.", style = MaterialTheme.typography.labelSmall)
                        }
                        FilledTonalIconButton(onClick = {
                            val text = "Join ${org.name} on Gold Billing: install the app, create an account, choose \"Join a shop\" and enter code ${org.code}"
                            val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
                            ctx.startActivity(android.content.Intent.createChooser(i, "Share shop code"))
                        }) { Icon(Icons.Filled.Share, "Share code") }
                    }
                }
            }
            if (pending.isNotEmpty()) {
                item { Text("Waiting for approval", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                items(pending, key = { "p" + it.uid }) { p ->
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p.name, 40, p.photo); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.SemiBold)
                                Text(listOf(p.email, p.phone).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { scope.launch { runCatching { Repo.removeUser(p.uid) } } }) { Text("Reject") }
                            Button(onClick = { scope.launch { runCatching { Repo.setApproved(p.uid, true) } } }) { Text("Approve") }
                        }
                    }
                }
            }
            item { Text("Members (${members.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
            items(members, key = { it.uid }) { m ->
                var menu by remember { mutableStateOf(false) }
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(m.name, 44, m.photo); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.name + if (m.uid == me.uid) " (you)" else "", fontWeight = FontWeight.SemiBold)
                                if (m.isAdmin) { Spacer(Modifier.width(6.dp)); Pill("Admin", MaterialTheme.colorScheme.primary) }
                            }
                            Text(listOf(m.email, m.phone).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                        }
                        if (m.uid != me.uid) Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Options") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(if (m.isAdmin) "Make billing user" else "Make admin") }, onClick = {
                                    menu = false; scope.launch { runCatching { Repo.setRole(m.uid, if (m.isAdmin) "user" else "admin") } }
                                })
                                DropdownMenuItem(text = { Text("Remove from shop", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; removeAsk = m })
                            }
                        }
                    }
                }
            }
            item {
                Text("Admins see every bill and all totals (amounts and grams). Billing users can create bills and see only their own bills.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    removeAsk?.let { m ->
        AlertDialog(
            onDismissRequest = { removeAsk = null },
            title = { Text("Remove ${m.name}?") },
            text = { Text("They lose access right away. Bills they created stay in the records.") },
            confirmButton = { TextButton(onClick = { removeAsk = null; scope.launch { runCatching { Repo.removeUser(m.uid) } } }) {
                Text("Remove", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { removeAsk = null }) { Text("Cancel") } }
        )
    }
}
