package com.digiglobal.goldbill.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.digiglobal.goldbill.data.*
import com.digiglobal.goldbill.ui.*
import com.digiglobal.goldbill.util.Share
import com.digiglobal.goldbill.util.Fmt
import kotlinx.coroutines.launch

/**
 * Owner (super-admin) console: every shop using the app, with suspend/resume,
 * delete company, and remove any user.
 */
@Composable
fun OwnerConsoleScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val orgs by remember { Repo.allOrgsFlow() }.collectAsState(initial = emptyList())
    val users by remember { Repo.allUsersFlow() }.collectAsState(initial = emptyList())
    var expanded by remember { mutableStateOf<String?>(null) }
    var deleteOrg by remember { mutableStateOf<Org?>(null) }
    var deleteUser by remember { mutableStateOf<UserProfile?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    if (showExport) {
        ReportScreen(Repo.me, null, emptyList(), ownerMode = true, onBack = { showExport = false })
        return
    }

    val byOrg = users.groupBy { it.orgId }

    Screen("Owner console", onBack = onBack, actions = {
        TextButton(onClick = { showExport = true }) { Icon(Icons.Filled.TableChart, null); Spacer(Modifier.width(4.dp)); Text("Report") }
    }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiTile("Shops", orgs.size.toString(), Icons.Filled.Business, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    KpiTile("Users", users.size.toString(), Icons.Filled.People, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    KpiTile("Suspended", orgs.count { it.suspended }.toString(), Icons.Filled.Block, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (orgs.isEmpty()) item { EmptyState("No shops yet.", Icons.Filled.Business) }

            items(orgs.sortedByDescending { it.createdAt }, key = { it.id }) { o ->
                val members = byOrg[o.id].orEmpty()
                val admin = members.firstOrNull { it.uid == o.createdBy }
                val open = expanded == o.id
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CompanyLogo(o.name, o.logo, 44)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(o.name.ifBlank { "(no name)" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (o.suspended) { Spacer(Modifier.width(6.dp)); Pill("Suspended", MaterialTheme.colorScheme.error) }
                            }
                            Text("Code ${o.code} · created ${Fmt.date(o.createdAt)}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${members.count { it.approved }} members · ${members.count { !it.approved }} pending" +
                                    (admin?.let { " · admin ${it.name}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { expanded = if (open) null else o.id }) {
                            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, "Members")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy, onClick = {
                            scope.launch {
                                runCatching { Repo.setOrgSuspended(o.id, !o.suspended) }
                                    .onFailure { Share.toast(ctx, it.message ?: "Couldn't update") }
                            }
                        }) { Text(if (o.suspended) "Resume" else "Suspend") }
                        TextButton(enabled = !busy, onClick = { deleteOrg = o }) {
                            Text("Delete shop", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (open) {
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        if (members.isEmpty()) Text("No members", style = MaterialTheme.typography.bodySmall)
                        members.sortedBy { it.name }.forEach { m ->
                            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(m.name, 34, m.photo)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(m.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        listOf(m.email, if (m.isAdmin) "Admin" else "Billing user", if (m.approved) "" else "Pending")
                                            .filter { it.isNotBlank() }.joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { deleteUser = m }) { Icon(Icons.Filled.PersonRemove, "Remove user", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }

            val orphans = users.filter { u -> orgs.none { it.id == u.orgId } }
            if (orphans.isNotEmpty() && orgs.isNotEmpty()) {
                item { Text("Users without a shop", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                items(orphans, key = { "o_" + it.uid }) { m ->
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name, fontWeight = FontWeight.Medium)
                                Text(m.email, style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { deleteUser = m }) { Icon(Icons.Filled.PersonRemove, "Remove user", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            item {
                Text(
                    "Removing a user takes them out of their shop; their login stays and they can join a shop again. " +
                        "To delete a login completely, use Firebase console → Authentication.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    deleteOrg?.let { o ->
        var typed by remember(o.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { deleteOrg = null },
            title = { Text("Delete ${o.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This permanently deletes the shop with all its bills, customers and settings, and removes all its members. It can't be undone.")
                    OutlinedTextField(typed, { typed = it }, label = { Text("Type DELETE to confirm") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = typed.trim() == "DELETE", onClick = {
                    deleteOrg = null
                    busy = true
                    scope.launch {
                        runCatching { Repo.deleteOrg(o) }
                            .onSuccess { Share.toast(ctx, "${o.name} deleted") }
                            .onFailure { Share.toast(ctx, it.message ?: "Couldn't delete") }
                        busy = false
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteOrg = null }) { Text("Cancel") } }
        )
    }

    deleteUser?.let { m ->
        AlertDialog(
            onDismissRequest = { deleteUser = null },
            title = { Text("Remove ${m.name}?") },
            text = { Text("They'll be taken out of their shop immediately.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteUser = null
                    scope.launch {
                        runCatching { Repo.removeUser(m.uid) }.onFailure { Share.toast(ctx, it.message ?: "Couldn't remove") }
                    }
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteUser = null }) { Text("Cancel") } }
        )
    }
}
