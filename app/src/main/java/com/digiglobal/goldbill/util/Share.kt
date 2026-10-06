package com.digiglobal.goldbill.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** Sending invoice PDFs and Excel files to customers. */
object Share {

    private fun uri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)

    fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    /** 10-digit Indian numbers get the 91 country code. */
    fun waNumber(phone: String): String {
        val d = phone.filter { it.isDigit() }.trimStart('0')
        return if (d.length == 10) "91$d" else d
    }

    /**
     * Opens WhatsApp straight on the customer's chat with the PDF attached.
     * If WhatsApp can't target the number, the normal share sheet opens instead.
     */
    fun whatsapp(ctx: Context, file: File, phone: String, message: String) {
        val u = uri(ctx, file)
        val num = waNumber(phone)
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            val i = Intent(Intent.ACTION_SEND).setType("application/pdf").setPackage(pkg)
                .putExtra(Intent.EXTRA_STREAM, u).putExtra(Intent.EXTRA_TEXT, message)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            if (num.length >= 10) i.putExtra("jid", "$num@s.whatsapp.net")
            try { ctx.startActivity(i); return } catch (e: ActivityNotFoundException) { /* try next */ }
        }
        toast(ctx, "WhatsApp isn't installed — choose another app")
        any(ctx, file, message, "application/pdf")
    }

    fun email(ctx: Context, file: File, to: String, subject: String, body: String) {
        val i = Intent(Intent.ACTION_SEND).setType("application/pdf")
            .putExtra(Intent.EXTRA_EMAIL, if (to.isNotBlank()) arrayOf(to.trim()) else emptyArray())
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
            .putExtra(Intent.EXTRA_STREAM, uri(ctx, file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { ctx.startActivity(Intent.createChooser(i, "Send invoice by email").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { toast(ctx, "No email app found") }
    }

    fun any(ctx: Context, file: File, message: String, mime: String) {
        val i = Intent(Intent.ACTION_SEND).setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri(ctx, file)).putExtra(Intent.EXTRA_TEXT, message)
            .putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Opens the PDF in a viewer, from where it can also be printed. */
    fun open(ctx: Context, file: File) {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri(ctx, file), "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) } catch (e: ActivityNotFoundException) { toast(ctx, "No PDF viewer found") }
    }

    fun call(ctx: Context, phone: String) {
        try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' })).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { toast(ctx, "Couldn't open the dialer") }
    }
}
