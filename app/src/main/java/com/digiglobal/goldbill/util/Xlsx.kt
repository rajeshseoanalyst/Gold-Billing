package com.digiglobal.goldbill.util

import java.io.OutputStream
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A tiny, dependency-free Excel (.xlsx) writer: several sheets, a red bold header row that stays
 * frozen with filter buttons, real numbers, and real Excel date-times.
 */
object Xlsx {

    /** A cell value: text, a number, or a date-time (epoch millis). */
    sealed class Cell {
        data class Text(val v: String) : Cell()
        data class Num(val v: Double) : Cell()
        data class Date(val ms: Long) : Cell()
        object Empty : Cell()
    }

    fun text(v: String?) = if (v.isNullOrEmpty()) Cell.Empty else Cell.Text(v)
    fun num(v: Number) = Cell.Num(v.toDouble())
    fun date(ms: Long) = if (ms > 0) Cell.Date(ms) else Cell.Empty

    data class Sheet(
        val name: String,
        val header: List<String>,
        val rows: List<List<Cell>>,
        val widths: List<Int> = emptyList(),   // column widths in characters
        val boldLastRow: Boolean = false        // e.g. a TOTAL row
    )

    fun write(out: OutputStream, sheets: List<Sheet>) {
        ZipOutputStream(out).use { zip ->
            fun put(path: String, body: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes(sheets.size))
            put("_rels/.rels", ROOT_RELS)
            put("xl/workbook.xml", workbook(sheets))
            put("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            put("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        }
    }

    // ---------------- XML parts ----------------

    private fun contentTypes(n: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..n) append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        append("</Types>")
    }

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private fun workbook(sheets: List<Sheet>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        val used = mutableSetOf<String>()
        sheets.forEachIndexed { i, s ->
            var name = s.name.replace(Regex("""[\\/?*\[\]:]"""), " ").take(31).ifBlank { "Sheet${i + 1}" }
            while (!used.add(name.lowercase())) name = name.take(28) + " ${i + 1}"
            append("""<sheet name="${esc(name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(n: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..n) append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        append("""<Relationship Id="rId${n + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    // Styles: 0 normal, 1 header (white bold on red), 2 date-time, 3 bold, 4 one decimal, 5 bold one decimal
    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="2"><numFmt numFmtId="164" formatCode="dd-mmm-yyyy hh:mm AM/PM"/><numFmt numFmtId="165" formatCode="0.0"/></numFmts><fonts count="3"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFE3262F"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="6"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="165" fontId="2" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""

    private fun sheetXml(s: Sheet): String = buildString {
        val cols = s.header.size
        val lastRow = s.rows.size + 1
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        if (cols > 0) {
            append("<cols>")
            for (c in 0 until cols) {
                val w = s.widths.getOrNull(c) ?: maxOf(10, s.header[c].length + 4)
                append("""<col min="${c + 1}" max="${c + 1}" width="$w" customWidth="1"/>""")
            }
            append("</cols>")
        }
        append("<sheetData>")
        append("""<row r="1">""")
        s.header.forEachIndexed { c, h -> append(textCell(ref(c, 1), h, 1)) }
        append("</row>")
        s.rows.forEachIndexed { i, row ->
            val r = i + 2
            val bold = s.boldLastRow && i == s.rows.lastIndex
            append("""<row r="$r">""")
            row.forEachIndexed { c, cell ->
                val at = ref(c, r)
                when (cell) {
                    is Cell.Text -> append(textCell(at, cell.v, if (bold) 3 else 0))
                    is Cell.Num -> {
                        val whole = cell.v == Math.floor(cell.v) && !cell.v.isInfinite()
                        val style = if (whole) (if (bold) 3 else 0) else (if (bold) 5 else 4)
                        val v = if (whole) cell.v.toLong().toString() else cell.v.toString()
                        append("""<c r="$at" s="$style"><v>$v</v></c>""")
                    }
                    is Cell.Date -> append("""<c r="$at" s="2"><v>${excelDate(cell.ms)}</v></c>""")
                    Cell.Empty -> Unit
                }
            }
            append("</row>")
        }
        append("</sheetData>")
        if (cols > 0 && s.rows.isNotEmpty() && !s.boldLastRow) append("""<autoFilter ref="A1:${ref(cols - 1, lastRow)}"/>""")
        append("</worksheet>")
    }

    private fun textCell(at: String, v: String, style: Int) =
        """<c r="$at" t="inlineStr"${if (style != 0) " s=\"$style\"" else ""}><is><t xml:space="preserve">${esc(v.take(32000))}</t></is></c>"""

    /** Local date-time as an Excel serial number (days since 1899-12-30). */
    private fun excelDate(ms: Long): String {
        val local = ms + TimeZone.getDefault().getOffset(ms)
        return "%.6f".format(java.util.Locale.US, local / 86_400_000.0 + 25569.0)
    }

    private fun ref(col: Int, row: Int): String {
        var n = col + 1
        val sb = StringBuilder()
        while (n > 0) { val m = (n - 1) % 26; sb.insert(0, ('A' + m)); n = (n - 1) / 26 }
        return sb.append(row).toString()
    }

    private fun esc(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\t' || ch == '\n' || ch == '\r' -> sb.append(ch)
                ch.code < 0x20 || ch == '￾' || ch == '￿' -> Unit   // not allowed in XML
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
