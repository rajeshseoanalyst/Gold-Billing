package com.digiglobal.goldbill.data

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * All reads and writes. Collections are prefixed "gb_" so this app can share a Firebase
 * project with Call CRM without the two apps' data ever mixing.
 */
object Repo {
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    val uid: String? get() = auth.currentUser?.uid

    /** Signed-in user's profile; set by the UI before any shop screen opens. */
    @Volatile var me: UserProfile? = null

    private fun users() = db.collection("gb_users")
    private fun orgs() = db.collection("gb_orgs")
    private fun codes() = db.collection("gb_codes")
    private fun invites() = db.collection("gb_invites")
    private fun emailKey(e: String) = e.trim().lowercase()
    private fun orgDoc(orgId: String? = null) = orgs().document(orgId ?: me?.orgId?.ifBlank { null } ?: "__none")
    private fun invoices(orgId: String? = null) = orgDoc(orgId).collection("invoices")
    private fun customers() = orgDoc().collection("customers")
    private fun settings(orgId: String? = null) = orgDoc(orgId).collection("settings")
    /** Product photos live in their own documents so bill lists stay small and fast. */
    private fun photos() = orgDoc().collection("photos")

    private fun now() = System.currentTimeMillis()

    // ---------------- streams ----------------

    private fun <T> docFlow(ref: DocumentReference, map: (DocumentSnapshot) -> T): Flow<T> = callbackFlow {
        val reg = ref.addSnapshotListener { snap, _ -> if (snap != null) trySend(map(snap)) }
        awaitClose { reg.remove() }
    }

    private fun <T> queryFlow(q: Query, map: (DocumentSnapshot) -> T): Flow<List<T>> = callbackFlow {
        val reg = q.addSnapshotListener { snap, err ->
            if (snap != null) trySend(snap.documents.map(map)) else if (err != null) trySend(emptyList())
        }
        awaitClose { reg.remove() }
    }

    fun authFlow(): Flow<FirebaseUser?> = callbackFlow {
        val l = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(l)
        awaitClose { auth.removeAuthStateListener(l) }
    }

    fun profileFlow(uid: String): Flow<UserProfile?> = docFlow(users().document(uid)) { if (it.exists()) it.toUser() else null }
    fun orgFlow(orgId: String): Flow<Org?> = docFlow(orgs().document(orgId)) { if (it.exists()) it.toOrg() else null }
    fun teamFlow(): Flow<List<UserProfile>> = queryFlow(users().whereEqualTo("orgId", me?.orgId ?: "__none")) { it.toUser() }

    fun shopFlow(): Flow<ShopSettings> = docFlow(settings().document("shop")) { it.toShop() }
    fun ratesFlow(): Flow<Rates> = docFlow(settings().document("rates")) { it.toRates() }
    fun customersFlow(): Flow<List<Customer>> = queryFlow(customers()) { it.toCustomer() }
    fun invoiceFlow(id: String): Flow<Invoice?> = docFlow(invoices().document(id)) { if (it.exists()) it.toInvoice() else null }

    /** Billing users only ever load their own invoices (the security rules enforce this too). */
    fun myInvoicesFlow(uid: String): Flow<List<Invoice>> = queryFlow(invoices().whereEqualTo("createdBy", uid)) { it.toInvoice() }

    /** Admin: every invoice of the shop since a time. */
    fun shopInvoicesFlow(since: Long): Flow<List<Invoice>> =
        queryFlow(invoices().whereGreaterThanOrEqualTo("at", since)) { it.toInvoice() }

    // ---------------- one-shot reads ----------------

    suspend fun getUser(id: String): UserProfile? = runCatching { users().document(id).get().await().takeIf { it.exists() }?.toUser() }.getOrNull()
    suspend fun shop(orgId: String? = null): ShopSettings = runCatching { settings(orgId).document("shop").get().await().toShop() }.getOrDefault(ShopSettings())
    suspend fun invoicesSince(orgId: String, since: Long): List<Invoice> =
        invoices(orgId).whereGreaterThanOrEqualTo("at", since).get().await().documents.map { it.toInvoice() }
    suspend fun myInvoicesSince(since: Long): List<Invoice> {
        val id = uid ?: return emptyList()
        return invoices().whereEqualTo("createdBy", id).get().await().documents.map { it.toInvoice() }.filter { it.at >= since }
    }

    suspend fun checkOwner(): Boolean = runCatching { db.collection("gb_platform").document("owner").get().await(); true }.getOrDefault(false)

    // ---------------- auth & company ----------------

    suspend fun signIn(email: String, pass: String) { auth.signInWithEmailAndPassword(email.trim(), pass).await() }

    suspend fun signUp(name: String, email: String, pass: String) {
        val user = auth.createUserWithEmailAndPassword(email.trim(), pass).await().user ?: error("Sign-up failed")
        runCatching { user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await() }
    }

    suspend fun signOut() { me = null; auth.signOut() }

    private fun newProfile(orgId: String, name: String, phone: String, role: String, approved: Boolean, photo: String) = mapOf(
        "orgId" to orgId, "name" to name.trim(), "email" to (auth.currentUser?.email ?: ""), "phone" to phone.trim(),
        "role" to role, "approved" to approved, "photo" to photo
    )

    private val codeChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private fun randomCode() = (1..6).map { codeChars.random() }.joinToString("")

    /**
     * Creates a shop with [user] as its admin, using the given Firestore instance.
     * The owner console calls this with a separate Firebase instance signed in as the new admin,
     * so the shop is created by the admin's own account and nothing in it points back to the owner.
     */
    private suspend fun createCompanyIn(fdb: FirebaseFirestore, user: FirebaseUser, shopName: String, name: String, phone: String, logo: String, photo: String) {
        val orgRef = fdb.collection("gb_orgs").document()
        var done = false
        repeat(5) {
            if (done) return@repeat
            val code = randomCode()
            val codeRef = fdb.collection("gb_codes").document(code)
            done = fdb.runTransaction { tx ->
                if (tx.get(codeRef).exists()) return@runTransaction false
                tx.set(orgRef, mapOf("name" to shopName.trim(), "code" to code, "createdBy" to user.uid, "createdAt" to now(),
                    "suspended" to false, "logo" to logo))
                tx.set(codeRef, mapOf("orgId" to orgRef.id))
                tx.set(fdb.collection("gb_users").document(user.uid), mapOf(
                    "orgId" to orgRef.id, "name" to name.trim(), "email" to (user.email ?: ""), "phone" to phone.trim(),
                    "role" to "admin", "approved" to true, "photo" to photo))
                true
            }.await()
        }
        if (!done) error("Couldn't create the shop, please try again")
        runCatching {
            fdb.collection("gb_orgs").document(orgRef.id).collection("settings").document("shop")
                .set(ShopSettings(name = shopName.trim(), phone = phone.trim()).toMap()).await()
        }
        user.email?.let { e -> runCatching { fdb.collection("gb_invites").document(emailKey(e)).delete().await() } }
    }

    /** An invited admin sets up the shop the owner prepared for them. */
    suspend fun createCompany(shopName: String, name: String, phone: String, logo: String, photo: String) {
        val user = auth.currentUser ?: error("Not signed in")
        if (myInvite() == null) error("New shops are set up by your software provider. Ask them to add your email.")
        createCompanyIn(db, user, shopName, name, phone, logo, photo)
        me = getUser(user.uid)
    }

    /** The shop the owner has prepared for the signed-in email, if any. */
    suspend fun myInvite(): Invite? {
        val e = auth.currentUser?.email ?: return null
        return runCatching { invites().document(emailKey(e)).get().await().takeIf { it.exists() }?.toInvite() }.getOrNull()
    }

    suspend fun joinCompany(code: String, name: String, phone: String, photo: String) {
        val id = uid ?: error("Not signed in")
        val clean = code.trim().uppercase().filter { it.isLetterOrDigit() }
        if (clean.length < 4) error("Enter the shop code your admin gave you")
        val orgId = codes().document(clean).get().await().getString("orgId") ?: error("No shop found with code $clean")
        users().document(id).set(newProfile(orgId, name, phone, "user", false, photo)).await()
    }

    suspend fun leaveCompany() { uid?.let { users().document(it).delete().await() }; me = null }

    // ---------------- team & profile ----------------

    suspend fun setApproved(userId: String, approved: Boolean) { users().document(userId).update("approved", approved).await() }
    suspend fun setRole(userId: String, role: String) { users().document(userId).update("role", role).await() }
    suspend fun removeUser(userId: String) { users().document(userId).delete().await() }
    suspend fun setMyPhoto(photo: String) { uid?.let { users().document(it).update("photo", photo).await() } }
    suspend fun setMyName(name: String) { uid?.let { users().document(it).update("name", name.trim()).await() } }
    suspend fun setOrgLogo(orgId: String, logo: String) { orgs().document(orgId).update("logo", logo).await() }
    suspend fun setOrgName(orgId: String, name: String) { orgs().document(orgId).update("name", name.trim()).await() }

    // ---------------- settings ----------------

    suspend fun saveShop(s: ShopSettings) { settings().document("shop").set(s.toMap()).await() }
    suspend fun saveRates(r: Rates) {
        settings().document("rates").set(r.copy(updatedAt = now(), updatedBy = me?.name ?: "").toMap()).await()
    }

    // ---------------- customers ----------------

    /** Saves or updates a customer; the 10-digit phone number is the key, so repeat customers are found again. */
    /**
     * Saves a customer. With [keepIds] (used when billing) an empty ID field never wipes an ID already saved —
     * estimates don't ask for ID proof. The customer screen passes false so an ID can be removed there.
     */
    suspend fun saveCustomer(c: Customer, keepIds: Boolean = true): Customer {
        val digits = c.phone.filter { it.isDigit() }.takeLast(10)
        val ref = when {
            c.id.isNotBlank() -> customers().document(c.id)
            digits.length == 10 -> customers().document(digits)
            else -> customers().document()
        }
        val idKeys = setOf("aadhaar", "passport", "voterId", "drivingLicence")
        val data = c.toMap().filter { (k, v) -> !(keepIds && k in idKeys && v.toString().isBlank()) }
        ref.set(data + mapOf("updatedAt" to now()), SetOptions.merge()).await()
        return c.copy(id = ref.id)
    }

    suspend fun deleteCustomer(id: String) { customers().document(id).delete().await() }

    // ---------------- invoices ----------------

    /**
     * Saves a new invoice with the next number of its series (e.g. INV/2026-27/0012).
     * Numbers come from a counter in a transaction, so two users never get the same number.
     */
    // ---------------- product photos ----------------

    private val photoCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** A new id for a photo taken in the bill editor; the picture is kept in memory until the bill is saved. */
    fun newPhotoId(): String = photos().document().id

    /** Saves the photos the bill uses that aren't stored yet. */
    private suspend fun storePhotos(inv: Invoice, pending: Map<String, String>) {
        val used = (inv.items.map { it.photo } + inv.oldItems.map { it.photo } + inv.customerSign + inv.documents.map { it.id })
            .filter { it.isNotBlank() }.toSet()
        val batch = db.batch(); var n = 0
        pending.filterKeys { it in used }.forEach { (id, data) ->
            batch.set(photos().document(id), mapOf("data" to data, "invoiceId" to inv.id, "by" to (uid ?: ""), "at" to now()))
            photoCache[id] = data; n++
        }
        if (n > 0) batch.commit().await()
    }

    suspend fun photo(id: String): String? {
        if (id.isBlank()) return null
        photoCache[id]?.let { return it }
        return runCatching { photos().document(id).get().await().getString("data") }.getOrNull()?.also { photoCache[id] = it }
    }

    /** All photos of a bill, id → picture, for the PDF and the bill screen. */
    suspend fun photosOf(inv: Invoice): Map<String, String> =
        (inv.items.map { it.photo } + inv.oldItems.map { it.photo } + inv.customerSign + inv.documents.map { it.id })
            .filter { it.isNotBlank() }.distinct()
            .mapNotNull { id -> photo(id)?.let { id to it } }.toMap()

    suspend fun invoiceOnce(id: String): Invoice? =
        runCatching { invoices().document(id).get().await().takeIf { it.exists() }?.toInvoice() }.getOrNull()

    /**
     * Changes a bill after it was made. Number, date, type and who made it stay the same;
     * the bill records when and by whom it was last edited.
     */
    suspend fun updateInvoice(original: Invoice, draft: Invoice, shop: ShopSettings, pendingPhotos: Map<String, String>): Invoice {
        val meNow = me ?: error("Not signed in")
        if (original.isCancelled) error("A cancelled bill can't be edited")
        val customer = if (draft.customer.phone.isNotBlank() || draft.customer.name.isNotBlank())
            runCatching { saveCustomer(draft.customer) }.getOrDefault(draft.customer) else draft.customer
        val inv = draft.copy(
            id = original.id, type = original.type, number = original.number, at = original.at, customer = customer,
            createdBy = original.createdBy, createdByName = original.createdByName, status = original.status,
            cancelReason = original.cancelReason, gstRate = original.gstRate, terms = draft.terms.ifBlank { shop.terms },
            editedAt = now(), editedByName = meNow.name, editCount = original.editCount + 1
        )
        storePhotos(inv, pendingPhotos)
        invoices().document(original.id).set(inv.toMap()).await()
        return inv
    }

    suspend fun createInvoice(draft: Invoice, shop: ShopSettings, pendingPhotos: Map<String, String> = emptyMap()): Invoice {
        val meNow = me ?: error("Not signed in")
        val at = now()
        val kind = draft.kind
        val prefix = Billing.seriesPrefix(kind, shop)
        val fy = Billing.financialYear(at)
        val key = "${prefix}_$fy".replace("/", "-")
        val counterRef = settings().document("counters")
        val ref = invoices().document()
        val customer = if (draft.customer.phone.isNotBlank() || draft.customer.name.isNotBlank())
            runCatching { saveCustomer(draft.customer) }.getOrDefault(draft.customer) else draft.customer
        storePhotos(draft.copy(id = ref.id), pendingPhotos)
        val saved = db.runTransaction { tx ->
            val snap = tx.get(counterRef)
            val next = ((snap.get(key) as? Number)?.toLong() ?: 0L) + 1
            val inv = draft.copy(
                id = ref.id, number = Billing.formatNumber(prefix, fy, next), at = at, customer = customer,
                createdBy = meNow.uid, createdByName = meNow.name, status = "active",
                gstRate = shop.gstRate, terms = draft.terms.ifBlank { shop.terms }
            )
            tx.set(counterRef, mapOf(key to next), SetOptions.merge())
            tx.set(ref, inv.toMap())
            inv
        }.await()
        return saved
    }

    /** Admin only: an invoice is never deleted, only marked cancelled (keeps the number series intact). */
    suspend fun cancelInvoice(id: String, reason: String) {
        invoices().document(id).update(mapOf("status" to "cancelled", "cancelReason" to reason, "cancelledAt" to now(),
            "cancelledBy" to (me?.name ?: ""))).await()
    }

    // ---------------- owner (super-admin) ----------------

    fun allOrgsFlow(): Flow<List<Org>> = queryFlow(orgs()) { it.toOrg() }
    fun allUsersFlow(): Flow<List<UserProfile>> = queryFlow(users()) { it.toUser() }
    suspend fun allOrgsOnce(): List<Org> = orgs().get().await().documents.map { it.toOrg() }
    suspend fun setOrgSuspended(orgId: String, suspended: Boolean) { orgs().document(orgId).update("suspended", suspended).await() }

    suspend fun deleteOrg(org: Org) {
        val root = orgs().document(org.id)
        val refs = mutableListOf<DocumentReference>()
        for (sub in listOf("invoices", "customers", "settings", "photos")) root.collection(sub).get().await().documents.forEach { refs += it.reference }
        users().whereEqualTo("orgId", org.id).get().await().documents.forEach { refs += it.reference }
        if (org.code.isNotBlank()) refs += codes().document(org.code)
        refs.chunked(400).forEach { chunk -> db.batch().apply { chunk.forEach { delete(it) } }.commit().await() }
        root.delete().await()
    }


    // ---------------- owner: create shops & admins ----------------

    fun invitesFlow(): Flow<List<Invite>> = queryFlow(invites()) { it.toInvite() }
    suspend fun deleteInvite(email: String) { invites().document(emailKey(email)).delete().await() }

    /**
     * Owner creates a shop and its admin login. Returns true when the account and shop were created now;
     * false when the email already has an account (e.g. a Call CRM login) or no password was given — then the
     * shop waits as an invite and is set up the first time that person signs in.
     * A second Firebase instance is used so the owner stays signed in and the shop belongs to the admin's account.
     */
    suspend fun ownerCreateShop(shopName: String, adminName: String, phone: String, email: String, password: String): Boolean {
        val key = emailKey(email)
        invites().document(key).set(mapOf("email" to key, "shopName" to shopName.trim(), "name" to adminName.trim(),
            "phone" to phone.trim(), "createdAt" to now())).await()
        if (password.length < 6) return false
        val base = FirebaseApp.getInstance()
        val app = FirebaseApp.getApps(base.applicationContext).firstOrNull { it.name == "gb-creator" }
            ?: FirebaseApp.initializeApp(base.applicationContext, base.options, "gb-creator")
        val a2 = FirebaseAuth.getInstance(app)
        val d2 = FirebaseFirestore.getInstance(app)
        try {
            val user = try {
                a2.createUserWithEmailAndPassword(key, password).await().user ?: error("Couldn't create the login")
            } catch (e: FirebaseAuthUserCollisionException) { return false }
            runCatching { user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(adminName.trim()).build()).await() }
            createCompanyIn(d2, user, shopName, adminName, phone, "", "")
            return true
        } finally { a2.signOut() }
    }
}
