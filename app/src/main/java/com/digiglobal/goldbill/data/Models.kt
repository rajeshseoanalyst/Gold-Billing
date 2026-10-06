package com.digiglobal.goldbill.data

import com.google.firebase.firestore.DocumentSnapshot

data class UserProfile(
    val uid: String = "",
    val orgId: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "user",        // admin | user
    val approved: Boolean = false,
    val photo: String = ""
) {
    val isAdmin: Boolean get() = role == "admin"
    val firstName: String get() = name.trim().substringBefore(' ').ifBlank { "there" }
}

/** A gold shop using the app. Everything it owns lives under gb_orgs/{id}. */
data class Org(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val suspended: Boolean = false,
    val logo: String = ""
)

// ---------------- Firestore mapping ----------------

private fun DocumentSnapshot.str(k: String) = getString(k) ?: ""
private fun DocumentSnapshot.lng(k: String) = (get(k) as? Number)?.toLong() ?: 0L
private fun DocumentSnapshot.dbl(k: String, def: Double = 0.0) = (get(k) as? Number)?.toDouble() ?: def
private fun DocumentSnapshot.bool(k: String, def: Boolean) = getBoolean(k) ?: def
private fun Map<*, *>.s(k: String) = this[k]?.toString() ?: ""
private fun Map<*, *>.d(k: String) = (this[k] as? Number)?.toDouble() ?: 0.0
private fun Map<*, *>.i(k: String) = (this[k] as? Number)?.toInt() ?: 0

fun DocumentSnapshot.toUser() = UserProfile(
    uid = id, orgId = str("orgId"), name = str("name"), email = str("email"), phone = str("phone"),
    role = getString("role") ?: "user", approved = bool("approved", false), photo = str("photo")
)

fun DocumentSnapshot.toOrg() = Org(
    id = id, name = str("name"), code = str("code"), createdBy = str("createdBy"),
    createdAt = lng("createdAt"), suspended = bool("suspended", false), logo = str("logo")
)

fun Customer.toMap(): Map<String, Any> = mapOf(
    "name" to name, "phone" to phone, "email" to email, "address" to address, "gstin" to gstin,
    "pan" to pan, "stateCode" to stateCode, "remarks" to remarks,
    "aadhaar" to aadhaar, "passport" to passport, "voterId" to voterId, "drivingLicence" to drivingLicence
)

private fun Map<*, *>.toCustomer(id: String = "") = Customer(
    id = id.ifBlank { s("id") }, name = s("name"), phone = s("phone"), email = s("email"), address = s("address"),
    gstin = s("gstin"), pan = s("pan"), stateCode = s("stateCode"), remarks = s("remarks"),
    aadhaar = s("aadhaar"), passport = s("passport"), voterId = s("voterId"), drivingLicence = s("drivingLicence")
)

fun DocumentSnapshot.toCustomer(): Customer = (data ?: emptyMap<String, Any>()).toCustomer(id)

private fun SaleItem.toMap(): Map<String, Any> = mapOf(
    "description" to description, "metal" to metal, "purity" to purity, "hsn" to hsn, "huid" to huid, "pcs" to pcs,
    "grossWt" to grossWt, "stoneWt" to stoneWt, "wastagePct" to wastagePct, "rate" to rate,
    "makingType" to makingType, "makingValue" to makingValue, "stoneCharges" to stoneCharges, "photo" to photo,
    // stored for reading in other tools; recomputed on load
    "netWt" to netWt, "amount" to amount
)

private fun Map<*, *>.toSaleItem() = SaleItem(
    description = s("description"), metal = s("metal").ifBlank { Metal.GOLD }, purity = s("purity"), hsn = s("hsn"),
    huid = s("huid"), pcs = i("pcs").coerceAtLeast(1), grossWt = d("grossWt"), stoneWt = d("stoneWt"),
    wastagePct = d("wastagePct"), rate = d("rate"), makingType = s("makingType").ifBlank { MakingType.PER_GRAM.code },
    makingValue = d("makingValue"), stoneCharges = d("stoneCharges"), photo = s("photo")
)

private fun OldItem.toMap(): Map<String, Any> = mapOf(
    "description" to description, "metal" to metal, "purity" to purity, "grossWt" to grossWt,
    "lessPct" to lessPct, "rate" to rate, "netWt" to netWt, "value" to value, "photo" to photo
)

private fun Map<*, *>.toOldItem() = OldItem(
    description = s("description"), metal = s("metal").ifBlank { Metal.GOLD }, purity = s("purity"),
    grossWt = d("grossWt"), lessPct = d("lessPct"), rate = d("rate"), photo = s("photo")
)

/** Invoice + its computed totals, so dashboards and exports don't recompute. */
fun Invoice.toMap(): Map<String, Any> {
    val t = totals
    return mapOf(
        "type" to type, "number" to number, "at" to at,
        "customer" to customer.toMap() + mapOf("id" to customer.id),
        "customerName" to customer.name, "customerPhone" to customer.phone,
        "items" to items.map { it.toMap() }, "oldItems" to oldItems.map { it.toMap() },
        "discount" to discount, "gstRate" to gstRate, "includeGst" to includeGst, "interState" to interState,
        "payMode" to payMode, "amountPaid" to amountPaid, "remarks" to remarks, "terms" to terms,
        "createdBy" to createdBy, "createdByName" to createdByName, "status" to status, "cancelReason" to cancelReason,
        "editedAt" to editedAt, "editedByName" to editedByName, "editCount" to editCount, "customerSign" to customerSign,
        "documents" to documents.map { mapOf("id" to it.id, "label" to it.label) },
        "taxable" to t.taxable, "gst" to t.gst, "grandTotal" to t.grandTotal, "oldTotal" to t.oldTotal, "net" to t.net,
        "goldOut" to t.goldOut, "silverOut" to t.silverOut, "goldIn" to t.goldIn, "silverIn" to t.silverIn
    )
}

fun DocumentSnapshot.toInvoice(): Invoice = Invoice(
    id = id, type = str("type"), number = str("number"), at = lng("at"),
    customer = (get("customer") as? Map<*, *>)?.toCustomer() ?: Customer(),
    items = (get("items") as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.toSaleItem() } ?: emptyList(),
    oldItems = (get("oldItems") as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.toOldItem() } ?: emptyList(),
    discount = dbl("discount"), gstRate = dbl("gstRate", 3.0), includeGst = bool("includeGst", true),
    interState = bool("interState", false), payMode = getString("payMode") ?: "Cash", amountPaid = dbl("amountPaid"),
    remarks = str("remarks"), terms = str("terms"), createdBy = str("createdBy"), createdByName = str("createdByName"),
    status = getString("status") ?: "active", cancelReason = str("cancelReason"),
    editedAt = lng("editedAt"), editedByName = str("editedByName"), editCount = (getLong("editCount") ?: 0L).toInt(),
    customerSign = str("customerSign"),
    documents = (get("documents") as? List<*>)?.mapNotNull { m ->
        (m as? Map<*, *>)?.let { DocImage(it["id"]?.toString() ?: "", it["label"]?.toString() ?: "ID document") }
    }?.filter { it.id.isNotBlank() } ?: emptyList()
)

fun ShopSettings.toMap(): Map<String, Any> = mapOf(
    "name" to name, "address" to address, "phone" to phone, "email" to email, "gstin" to gstin,
    "stateCode" to stateCode, "pan" to pan, "bankName" to bankName, "accountNo" to accountNo, "ifsc" to ifsc,
    "upiId" to upiId, "terms" to terms, "footerNote" to footerNote, "gstRate" to gstRate,
    "invoicePrefix" to invoicePrefix, "purchasePrefix" to purchasePrefix, "estimatePrefix" to estimatePrefix,
    "hsnGold" to hsnGold, "hsnSilver" to hsnSilver,
    "signature" to signature, "signatoryName" to signatoryName, "signatoryTitle" to signatoryTitle, "showSignature" to showSignature,
    "printDocuments" to printDocuments
)

fun DocumentSnapshot.toShop(): ShopSettings = if (!exists()) ShopSettings() else ShopSettings(
    name = str("name"), address = str("address"), phone = str("phone"), email = str("email"), gstin = str("gstin"),
    stateCode = str("stateCode"), pan = str("pan"), bankName = str("bankName"), accountNo = str("accountNo"),
    ifsc = str("ifsc"), upiId = str("upiId"), terms = getString("terms") ?: ShopSettings.DEFAULT_TERMS,
    footerNote = getString("footerNote") ?: "", gstRate = dbl("gstRate", 3.0),
    invoicePrefix = getString("invoicePrefix") ?: "INV", purchasePrefix = getString("purchasePrefix") ?: "PUR",
    estimatePrefix = getString("estimatePrefix") ?: "EST",
    hsnGold = getString("hsnGold") ?: "7113", hsnSilver = getString("hsnSilver") ?: "7113",
    signature = str("signature"), signatoryName = str("signatoryName"),
    signatoryTitle = getString("signatoryTitle")?.ifBlank { null } ?: "Authorised Signatory",
    showSignature = getBoolean("showSignature") ?: true,
    printDocuments = getBoolean("printDocuments") ?: true
)

fun Rates.toMap(): Map<String, Any> = mapOf(
    "gold24" to gold24, "gold22" to gold22, "gold18" to gold18, "silver" to silver,
    "updatedAt" to updatedAt, "updatedBy" to updatedBy
)

fun DocumentSnapshot.toRates(): Rates = if (!exists()) Rates() else Rates(
    gold24 = dbl("gold24"), gold22 = dbl("gold22"), gold18 = dbl("gold18"), silver = dbl("silver"),
    updatedAt = lng("updatedAt"), updatedBy = str("updatedBy")
)

/** A shop the owner has prepared for an admin's email. Holds nothing about the owner. */
data class Invite(val email: String = "", val shopName: String = "", val name: String = "", val phone: String = "", val createdAt: Long = 0L)

fun DocumentSnapshot.toInvite() = Invite(
    email = getString("email") ?: id, shopName = getString("shopName") ?: "", name = getString("name") ?: "",
    phone = getString("phone") ?: "", createdAt = getLong("createdAt") ?: 0L)
