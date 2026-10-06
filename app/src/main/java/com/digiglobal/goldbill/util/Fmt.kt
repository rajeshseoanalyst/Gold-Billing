package com.digiglobal.goldbill.util

import android.app.DatePickerDialog
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Fmt {
    fun dateTime(ms: Long): String = if (ms <= 0) "—" else SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(ms))
    fun date(ms: Long): String = if (ms <= 0) "—" else SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ms))
    fun ago(ms: Long): String {
        if (ms <= 0) return "—"
        val d = (System.currentTimeMillis() - ms) / 1000
        return when { d < 60 -> "just now"; d < 3600 -> "${d / 60}m ago"; d < 86400 -> "${d / 3600}h ago"; else -> "${d / 86400}d ago" }
    }

    fun startOfDay(ms: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
        timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun pickDate(ctx: Context, initial: Long, onPicked: (Long) -> Unit) {
        val c = Calendar.getInstance().apply { if (initial > 0) timeInMillis = initial }
        DatePickerDialog(ctx, { _, y, m, d ->
            onPicked(Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis)
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }
}
