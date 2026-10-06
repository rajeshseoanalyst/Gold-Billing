package com.digiglobal.goldbill.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.digiglobal.goldbill.data.*
import com.digiglobal.goldbill.ui.screens.*
import kotlinx.coroutines.launch

private val LOADING_PROFILE = UserProfile(uid = "__loading")
private val LOADING_ORG = Org(id = "__loading")

@Composable
fun AppRoot() {
    val user by remember { Repo.authFlow() }.collectAsState(initial = Repo.auth.currentUser)
    val u = user
    if (u == null) {
        Repo.me = null
        LoginScreen()
        return
    }
    val isOwner by produceState(false, u.uid) { value = Repo.checkOwner() }
    val profile by remember(u.uid) { Repo.profileFlow(u.uid) }.collectAsState(initial = LOADING_PROFILE)
    val p = profile
    if (p != null && p !== LOADING_PROFILE && p.orgId.isNotBlank()) Repo.me = p else if (p !== LOADING_PROFILE) Repo.me = null
    var ownerOpen by rememberSaveable { mutableStateOf(false) }

    when {
        ownerOpen -> OwnerConsoleScreen(onBack = { ownerOpen = false })
        p === LOADING_PROFILE -> Loading()
        p == null || p.orgId.isBlank() -> OnboardingScreen(u.displayName.orEmpty(), isOwner) { ownerOpen = true }
        else -> {
            val org by remember(p.orgId) { Repo.orgFlow(p.orgId) }.collectAsState(initial = LOADING_ORG)
            val o = org
            when {
                o === LOADING_ORG -> Loading()
                o == null -> BlockedScreen("Shop not found", "This shop was deleted. You can create a new shop or join another one with a code.")
                o.suspended -> BlockedScreen("${o.name} is suspended", "This shop's access has been paused. Please contact the app provider.")
                !p.approved -> PendingScreen(p, o)
                else -> MainScaffold(p, o, isOwner)
            }
        }
    }
}

// ============================ Sign in / sign up ============================

@Composable
fun LoginScreen() {
    val scope = rememberCoroutineScope()
    var signUp by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(Icons.Filled.Receipt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("Gold Billing", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "GST billing for gold & silver — sale, purchase, exchange and estimates",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        TabRow(selectedTabIndex = if (signUp) 1 else 0, containerColor = MaterialTheme.colorScheme.background) {
            Tab(selected = !signUp, onClick = { signUp = false; error = null }, text = { Text("Sign in") })
            Tab(selected = signUp, onClick = { signUp = true; error = null }, text = { Text("Create account") })
        }
        Spacer(Modifier.height(16.dp))

        if (signUp) {
            OutlinedTextField(name, { name = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
        }
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                error = null
                if (email.isBlank() || pass.length < 6 || (signUp && name.isBlank())) {
                    error = if (signUp) "Enter your name, email and a password of at least 6 characters" else "Enter your email and password"
                    return@Button
                }
                busy = true
                scope.launch {
                    try {
                        if (signUp) Repo.signUp(name, email, pass) else Repo.signIn(email, pass)
                    } catch (e: Exception) {
                        error = e.localizedMessage ?: "Something went wrong"
                        busy = false
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(if (signUp) "Create account" else "Sign in")
        }
        if (signUp) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Next you'll create your shop, or join your shop with the code your admin shares.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
            )
        }
    }
}

/** Shown after login when the person isn't part of any company yet. */
@Composable
fun OnboardingScreen(displayName: String, isOwner: Boolean, onOpenOwner: () -> Unit) {
    val scope = rememberCoroutineScope()
    var create by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(displayName) }
    var phone by rememberSaveable { mutableStateOf("") }
    var company by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var photo by rememberSaveable { mutableStateOf("") }
    var logo by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Icon(Icons.Filled.Business, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(12.dp))
        Text("Set up your workspace", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Join your shop with its code, or create a new shop for your business.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        TabRow(selectedTabIndex = if (create) 1 else 0, containerColor = MaterialTheme.colorScheme.background) {
            Tab(selected = !create, onClick = { create = false; error = null }, text = { Text("Join a shop") })
            Tab(selected = create, onClick = { create = true; error = null }, text = { Text("Create a shop") })
        }
        Spacer(Modifier.height(16.dp))

        // Optional pictures: company logo (when creating) and your own photo.
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
            if (create) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PicturePicker(isLogo = true, onPicked = { it?.let { v -> logo = v } ?: run { error = "Couldn't read that picture" } }) {
                        CompanyLogo(company.ifBlank { "Shop" }, logo, 72)
                    }
                    Text(if (logo.isBlank()) "Add logo" else "Change logo", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PicturePicker(isLogo = false, onPicked = { it?.let { v -> photo = v } ?: run { error = "Couldn't read that picture" } }) {
                    Avatar(name.ifBlank { "You" }, 72, photo)
                }
                Text(if (photo.isBlank()) "Add your photo" else "Change photo", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Text("Optional — you can add or change these later in More.", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

        OutlinedTextField(name, { name = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(phone, { phone = it }, label = { Text("Mobile number") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        if (create) {
            OutlinedTextField(company, { company = it }, label = { Text("Shop / company name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        } else {
            OutlinedTextField(code, { v -> code = v.uppercase().filter { it.isLetterOrDigit() }.take(8) },
                label = { Text("Shop code") }, placeholder = { Text("e.g. K7P2QX") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                error = null
                if (name.isBlank()) { error = "Enter your name"; return@Button }
                if (create && company.isBlank()) { error = "Enter your shop name"; return@Button }
                if (!create && code.length < 4) { error = "Enter the shop code your admin gave you"; return@Button }
                busy = true
                scope.launch {
                    try {
                        if (create) Repo.createCompany(company, name, phone, logo, photo) else Repo.joinCompany(code, name, phone, photo)
                    } catch (e: Exception) {
                        error = e.localizedMessage ?: "Something went wrong"
                        busy = false
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(if (create) "Create shop" else "Send join request")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (create) "You'll be the shop's admin and get a code to share with your billing staff."
            else "Your admin will see your request under More → Team and approve you.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        if (isOwner) {
            FilledTonalButton(onClick = onOpenOwner, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AdminPanelSettings, null); Spacer(Modifier.width(8.dp)); Text("Open owner console")
            }
            Spacer(Modifier.height(8.dp))
        }
        TextButton(onClick = { scope.launch { Repo.signOut() } }) { Text("Sign out") }
    }
}

@Composable
fun PendingScreen(p: UserProfile, org: Org) {
    val scope = rememberCoroutineScope()
    Centered {
        Icon(Icons.Filled.HourglassEmpty, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Waiting for approval", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Hi ${p.firstName}, your request to join ${org.name} has been sent. Ask your admin to approve you (More → Team). " +
                "The app opens automatically once you're approved.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = { scope.launch { runCatching { Repo.leaveCompany() } } }) { Text("Cancel request / use another code") }
        TextButton(onClick = { scope.launch { Repo.signOut() } }) { Text("Sign out") }
    }
}

@Composable
fun BlockedScreen(title: String, message: String) {
    val scope = rememberCoroutineScope()
    Centered {
        Icon(Icons.Filled.Block, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = { scope.launch { runCatching { Repo.leaveCompany() } } }) { Text("Leave and join another shop") }
        TextButton(onClick = { scope.launch { Repo.signOut() } }) { Text("Sign out") }
    }
}

// ============================ Main app with bottom tabs ============================

private data class NavTab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun MainScaffold(me: UserProfile, org: Org, isOwner: Boolean) {
    val nav = rememberNavController()
    val shop by remember(org.id) { Repo.shopFlow() }.collectAsState(initial = ShopSettings())
    val rates by remember(org.id) { Repo.ratesFlow() }.collectAsState(initial = Rates())
    val team by remember(org.id) { Repo.teamFlow() }.collectAsState(initial = emptyList())

    val tabs = listOf(
        NavTab("home", "Home", Icons.Filled.Dashboard),
        NavTab("invoices", if (me.isAdmin) "Invoices" else "My bills", Icons.Filled.ReceiptLong),
        NavTab("new", "New bill", Icons.Filled.AddCircle),
        NavTab("customers", "Customers", Icons.Filled.People),
        NavTab("more", "More", Icons.Filled.Menu)
    )
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true; restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, null) }, label = { Text(t.label) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") {
                if (me.isAdmin) AdminDashboardScreen(me, org, rates, team, nav) else UserHomeScreen(me, org, rates, nav)
            }
            composable("invoices") { InvoicesScreen(me, nav) }
            composable("new") { NewBillPicker(nav) }
            composable("new/{type}") { e ->
                InvoiceEditorScreen(InvoiceType.of(e.arguments?.getString("type").orEmpty()), me, org, shop, rates, nav)
            }
            composable("invoice/{id}") { e -> InvoiceViewScreen(e.arguments?.getString("id").orEmpty(), me, org, shop, nav) }
            composable("customers") { CustomersScreen(me, nav) }
            composable("more") { MoreScreen(me, org, isOwner, nav) }
            composable("shop") { ShopSettingsScreen(me, shop, nav) }
            composable("rates") { RatesScreen(me, rates, nav) }
            composable("team") { TeamScreen(me, org, team, nav) }
            composable("report") { ReportScreen(me, org, team, ownerMode = false, onBack = { nav.popBackStack() }) }
            composable("owner") { OwnerConsoleScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

// ============================ More menu ============================

@Composable
fun MoreScreen(me: UserProfile, org: Org, isOwner: Boolean, nav: NavController) {
    val scope = rememberCoroutineScope()
    Screen("More") { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfileCard(me, org)
            SectionCard {
                MenuRow(Icons.Filled.CurrencyRupee, "Today's gold & silver rates", if (me.isAdmin) "Set the rates used in new bills" else "Rates used in new bills") { nav.navigate("rates") }
                if (me.isAdmin) {
                    MenuRow(Icons.Filled.Storefront, "Shop & invoice settings", "Address, GSTIN, bank, terms & conditions, numbering") { nav.navigate("shop") }
                    MenuRow(Icons.Filled.Groups, "Team", "Approve billing users, share the shop code") { nav.navigate("team") }
                    MenuRow(Icons.Filled.TableChart, "Download Excel report", "Sales, purchases, grams and GST — 7 days to 1 year") { nav.navigate("report") }
                }
                if (isOwner) MenuRow(Icons.Filled.AdminPanelSettings, "Owner console", "All shops: suspend or delete shops and users") { nav.navigate("owner") }
            }
            SectionCard {
                MenuRow(Icons.Filled.Logout, "Sign out", "") { scope.launch { Repo.signOut() } }
            }
            Text("Gold Billing v1.0 · by DigiGlobal", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { if (subtitle.isNotBlank()) Text(subtitle) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

// ============================ Profile & company pictures ============================

@Composable
private fun ProfileCard(me: UserProfile, org: Org) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var editName by remember { mutableStateOf(false) }
    var editCompany by remember { mutableStateOf(false) }
    fun save(block: suspend () -> Unit) = scope.launch {
        runCatching { block() }.onFailure { com.digiglobal.goldbill.util.Share.toast(ctx, it.message ?: "Couldn't save") }
    }

    SectionCard("My profile") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PicturePicker(isLogo = false, onPicked = { v ->
                if (v == null) com.digiglobal.goldbill.util.Share.toast(ctx, "Couldn't read that picture") else save { Repo.setMyPhoto(v) }
            }) { Avatar(me.name, 60, me.photo) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(me.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${me.email} · ${if (me.isAdmin) "Admin" else "Billing user"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                    TextButton(onClick = { editName = true }, contentPadding = PaddingValues(0.dp)) { Text("Edit name") }
                    if (me.photo.isNotBlank()) {
                        Spacer(Modifier.width(12.dp))
                        TextButton(onClick = { save { Repo.setMyPhoto("") } }, contentPadding = PaddingValues(0.dp)) { Text("Remove photo") }
                    }
                }
            }
        }
        Text("Tap your picture to change it.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    SectionCard("Shop") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (me.isAdmin) {
                PicturePicker(isLogo = true, onPicked = { v ->
                    if (v == null) com.digiglobal.goldbill.util.Share.toast(ctx, "Couldn't read that picture") else save { Repo.setOrgLogo(org.id, v) }
                }) { CompanyLogo(org.name, org.logo, 60) }
            } else CompanyLogo(org.name, org.logo, 60)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(org.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Shop code ${org.code}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (me.isAdmin) Row {
                    TextButton(onClick = { editCompany = true }, contentPadding = PaddingValues(0.dp)) { Text("Edit name") }
                    if (org.logo.isNotBlank()) {
                        Spacer(Modifier.width(12.dp))
                        TextButton(onClick = { save { Repo.setOrgLogo(org.id, "") } }, contentPadding = PaddingValues(0.dp)) { Text("Remove logo") }
                    }
                }
            }
        }
        if (me.isAdmin) Text("Tap the logo to upload your company logo.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (editName) NameDialog("Your name", me.name, onDismiss = { editName = false }) { v -> editName = false; save { Repo.setMyName(v) } }
    if (editCompany) NameDialog("Shop name", org.name, onDismiss = { editCompany = false }) { v -> editCompany = false; save { Repo.setOrgName(org.id, v) } }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(v, { v = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(enabled = v.isNotBlank(), onClick = { onSave(v) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
