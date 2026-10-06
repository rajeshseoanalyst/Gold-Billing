package com.digiglobal.goldbill.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.digiglobal.goldbill.data.*
import com.digiglobal.goldbill.ui.*
import com.digiglobal.goldbill.util.Share
import com.digiglobal.goldbill.util.Xlsx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/**
 * Download an Excel report of bills (admin and owner only).
 *  - Shop admin: the whole shop, or one billing user.
 *  - Owner (ownerMode = true): all shops, or one shop.
 */
@Composable
fun ReportScreen(me: UserProfile?, org: Org?, team: List<UserProfile>, ownerMode: Boolean, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isAdmin = me?.isAdmin == true

    var period by remember { mutableStateOf(ReportPeriod.D7) }
    val allMembers = "All billing users"
    var member by remember { mutableStateOf(allMembers) }
    val allCompanies = "All shops"
    var orgs by remember { mutableStateOf<List<Org>>(emptyList()) }
    var company by remember { mutableStateOf(allCompanies) }
    LaunchedEffect(ownerMode) {
        if (ownerMode) orgs = runCatching { Repo.allOrgsOnce() }.getOrDefault(emptyList()).sortedBy { it.name.lowercase() }
    }

    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var resultText by remember { mutableStateOf("") }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        val f = file
        if (uri != null && f != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { ctx.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } } }.isSuccess
                }
                Share.toast(ctx, if (ok) "Saved" else "Couldn't save the file")
            }
        }
    }

    fun share(f: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).setType(XLSX_MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, f.nameWithoutExtension)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Share report"))
    }

    fun build() {
        errorMsg = null; file = null; busy = true
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val since = period.since(now)
                // Decide which companies and which calls are included.
                val targets: List<Org> = when {
                    ownerMode && company == allCompanies -> orgs
                    ownerMode -> orgs.filter { it.name == company }
                    org != null -> listOf(org)
                    else -> emptyList()
                }
                if (targets.isEmpty()) throw IllegalStateException("No company to report on")
                val selectedMember = team.firstOrNull { it.name == member && member != allMembers }
                val data = withContext(Dispatchers.IO) {
                    targets.map { o ->
                        var bills = if (!ownerMode && !isAdmin) Repo.myInvoicesSince(since) else Repo.invoicesSince(o.id, since)
                        if (!ownerMode && selectedMember != null) bills = bills.filter { it.createdBy == selectedMember.uid }
                        OrgBills(o, bills)
                    }
                }
                val total = data.sumOf { it.bills.size }
                if (total == 0) {
                    errorMsg = "No bills found in the last ${period.label}."
                    return@launch
                }
                val sheets = BillReport.sheets(data, period, since, now, me?.name ?: "Owner", showShop = ownerMode && data.size > 1)
                val who = when {
                    ownerMode -> if (company == allCompanies) "AllShops" else company
                    !isAdmin -> (org?.name ?: "") + "_" + (me?.name ?: "")
                    selectedMember != null -> (org?.name ?: "") + "_" + selectedMember.name
                    else -> org?.name ?: "Company"
                }.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(40)
                val name = "GoldBills_${who}_${period.fileTag}_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now))}.xlsx"
                val out = withContext(Dispatchers.IO) {
                    val dir = File(ctx.cacheDir, "exports").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                    File(dir, name).also { f -> f.outputStream().use { Xlsx.write(it, sheets) } }
                }
                file = out
                resultText = "$total bills · ${data.size} ${if (data.size == 1) "shop" else "shops"}"
            } catch (e: Exception) {
                errorMsg = e.message ?: "Couldn't prepare the report"
            } finally {
                busy = false
            }
        }
    }

    Screen("Download Excel report", onBack = onBack) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard("Which bills") {
                when {
                    ownerMode -> DropdownField("Shop", company, listOf(allCompanies) + orgs.map { it.name }, Modifier.fillMaxWidth()) {
                        company = it; file = null
                    }
                    isAdmin -> DropdownField("Billed by", member, listOf(allMembers) + team.filter { it.approved }.map { it.name }, Modifier.fillMaxWidth()) {
                        member = it; file = null
                    }
                    else -> Text("Your own bills", style = MaterialTheme.typography.bodyMedium)
                }
            }

            SectionCard("Time period") {
                ReportPeriod.values().toList().chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                        row.forEach { p ->
                            FilterChip(selected = period == p, onClick = { period = p; file = null }, label = { Text(p.label) })
                        }
                    }
                }
                Text("From ${SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(period.since()))} to today",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(onClick = { build() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (busy) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(10.dp)); Text("Preparing…") }
                else { Icon(Icons.Filled.TableChart, null); Spacer(Modifier.width(8.dp)); Text("Prepare Excel file") }
            }

            errorMsg?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(14.dp)) {
                    Text(it, Modifier.padding(14.dp))
                }
            }

            file?.let { f ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Report ready", fontWeight = FontWeight.SemiBold)
                        }
                        Text(resultText, style = MaterialTheme.typography.bodySmall)
                        Text(f.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { saver.launch(f.name) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Download, null); Spacer(Modifier.width(6.dp)); Text("Save to phone")
                            }
                            OutlinedButton(onClick = { share(f) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text("Share")
                            }
                        }
                    }
                }
            }

            SectionCard("What's in the file") {
                listOf(
                    "Summary — sales, purchases, exchange and estimates with amount and grams of gold & silver",
                    "By user — bills, sales and grams per billing user",
                    "Bills — every bill with customer, GST, totals, payment and status",
                    "Items — every item sold or bought with weight, purity, rate and making",
                    "GST — tax invoices with taxable value, CGST, SGST and IGST (for your GST return)",
                    "Report info — shop, period and who downloaded it"
                ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
                Text("Opens in Excel, Google Sheets and WPS Office.", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
