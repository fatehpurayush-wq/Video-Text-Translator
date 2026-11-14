package com.example.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.domain.model.ExportFormat
import com.example.domain.model.LanguageCatalog
import com.example.domain.model.SegmentWithTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportEngine(private val context: Context) {

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun buildOriginalCopy(segments: List<SegmentWithTranslation>, includeTimestamps: Boolean): String {
        return segments.joinToString("\n") { seg ->
            if (includeTimestamps) {
                "[${TimeFormatters.formatShort(seg.startTimeMs)} - ${TimeFormatters.formatShort(seg.endTimeMs)}] ${seg.originalText}"
            } else {
                seg.originalText
            }
        }
    }

    fun buildTranslatedCopy(
        segments: List<SegmentWithTranslation>,
        includeTimestamps: Boolean,
        includeRoman: Boolean
    ): String {
        return segments.joinToString("\n") { seg ->
            val main = seg.translatedText.ifBlank { seg.originalText }
            val romanLine = if (includeRoman && !seg.romanText.isNullOrBlank()) "\n(${seg.romanText})" else ""
            if (includeTimestamps) {
                "[${TimeFormatters.formatShort(seg.startTimeMs)} - ${TimeFormatters.formatShort(seg.endTimeMs)}] $main$romanLine"
            } else {
                "$main$romanLine"
            }
        }
    }

    fun buildSideBySideCopy(
        segments: List<SegmentWithTranslation>,
        includeTimestamps: Boolean,
        includeRoman: Boolean
    ): String {
        return segments.joinToString("\n\n") { seg ->
            val timePrefix = if (includeTimestamps) {
                "${TimeFormatters.formatShort(seg.startTimeMs)} -> ${TimeFormatters.formatShort(seg.endTimeMs)}\n"
            } else ""
            val roman = if (includeRoman && !seg.romanText.isNullOrBlank()) "\nRoman: ${seg.romanText}" else ""
            "${timePrefix}Original: ${seg.originalText}\nTranslation: ${seg.translatedText}$roman"
        }
    }

    suspend fun exportToCacheFile(
        projectTitle: String,
        sourceLangCode: String,
        targetLangCode: String,
        segments: List<SegmentWithTranslation>,
        format: ExportFormat
    ): File = withContext(Dispatchers.IO) {
        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val safeTitle = projectTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(36).ifBlank { "video_subtitles" }
        val outFile = File(exportsDir, "${safeTitle}_${targetLangCode}.${format.extension}")
        FileOutputStream(outFile).use { fos ->
            writeExportBytes(fos, projectTitle, sourceLangCode, targetLangCode, segments, format)
        }
        outFile
    }

    suspend fun exportToUri(
        uri: Uri,
        projectTitle: String,
        sourceLangCode: String,
        targetLangCode: String,
        segments: List<SegmentWithTranslation>,
        format: ExportFormat
    ) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { os ->
            writeExportBytes(os, projectTitle, sourceLangCode, targetLangCode, segments, format)
        } ?: throw IllegalStateException("File write karne ke liye permission nahi mili")
    }

    fun shareUriForFile(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun writeExportBytes(
        out: OutputStream,
        projectTitle: String,
        sourceLangCode: String,
        targetLangCode: String,
        segments: List<SegmentWithTranslation>,
        format: ExportFormat
    ) {
        when (format) {
            ExportFormat.SRT_TRANSLATED -> out.write(buildSrt(segments, mode = 0).toByteArray(Charsets.UTF_8))
            ExportFormat.SRT_ORIGINAL -> out.write(buildSrt(segments, mode = 1).toByteArray(Charsets.UTF_8))
            ExportFormat.SRT_BILINGUAL -> out.write(buildSrt(segments, mode = 2).toByteArray(Charsets.UTF_8))
            ExportFormat.VTT -> out.write(buildVtt(segments).toByteArray(Charsets.UTF_8))
            ExportFormat.TXT -> out.write(buildTxt(projectTitle, sourceLangCode, targetLangCode, segments).toByteArray(Charsets.UTF_8))
            ExportFormat.CSV -> out.write(buildCsv(segments).toByteArray(Charsets.UTF_8))
            ExportFormat.JSON -> out.write(buildJson(projectTitle, sourceLangCode, targetLangCode, segments).toByteArray(Charsets.UTF_8))
            ExportFormat.XLSX -> writeXlsx(out, projectTitle, segments)
            ExportFormat.DOCX -> writeDocx(out, projectTitle, sourceLangCode, targetLangCode, segments)
            ExportFormat.PDF -> writePdf(out, projectTitle, sourceLangCode, targetLangCode, segments)
        }
    }

    private fun buildSrt(segments: List<SegmentWithTranslation>, mode: Int): String {
        val sb = StringBuilder()
        segments.forEachIndexed { index, seg ->
            sb.append(index + 1).append('\n')
            sb.append(TimeFormatters.formatSrt(seg.startTimeMs))
                .append(" --> ")
                .append(TimeFormatters.formatSrt(seg.endTimeMs))
                .append('\n')
            when (mode) {
                0 -> sb.append(seg.translatedText.ifBlank { seg.originalText })
                1 -> sb.append(seg.originalText)
                else -> {
                    sb.append(seg.originalText).append('\n')
                    sb.append(seg.translatedText.ifBlank { seg.originalText })
                }
            }
            sb.append("\n\n")
        }
        return sb.toString()
    }

    private fun buildVtt(segments: List<SegmentWithTranslation>): String {
        val sb = StringBuilder("WEBVTT\n\n")
        segments.forEachIndexed { index, seg ->
            sb.append(index + 1).append('\n')
            sb.append(TimeFormatters.formatVtt(seg.startTimeMs))
                .append(" --> ")
                .append(TimeFormatters.formatVtt(seg.endTimeMs))
                .append('\n')
            sb.append(seg.translatedText.ifBlank { seg.originalText }).append("\n\n")
        }
        return sb.toString()
    }

    private fun buildTxt(
        title: String,
        srcLang: String,
        tgtLang: String,
        segments: List<SegmentWithTranslation>
    ): String {
        val sb = StringBuilder()
        sb.append("$title (${LanguageCatalog.displayName(srcLang)} -> ${LanguageCatalog.displayName(tgtLang)})\n")
        sb.append("=".repeat(48)).append("\n\n")
        segments.forEachIndexed { index, seg ->
            sb.append("#${index + 1} [${TimeFormatters.formatShort(seg.startTimeMs)} - ${TimeFormatters.formatShort(seg.endTimeMs)}]\n")
            sb.append("Original: ${seg.originalText}\n")
            sb.append("Translation: ${seg.translatedText}\n")
            if (!seg.romanText.isNullOrBlank()) {
                sb.append("Roman: ${seg.romanText}\n")
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun buildCsv(segments: List<SegmentWithTranslation>): String {
        val sb = StringBuilder("\uFEFF") // UTF-8 BOM for Excel Indic/CJK compatibility
        sb.append("No,Start,End,DetectedLang,Original,Translation,RomanScript\n")
        segments.forEachIndexed { index, seg ->
            sb.append(index + 1).append(',')
            sb.append(csvEscape(TimeFormatters.formatShort(seg.startTimeMs))).append(',')
            sb.append(csvEscape(TimeFormatters.formatShort(seg.endTimeMs))).append(',')
            sb.append(csvEscape(seg.detectedLangCode)).append(',')
            sb.append(csvEscape(seg.originalText)).append(',')
            sb.append(csvEscape(seg.translatedText)).append(',')
            sb.append(csvEscape(seg.romanText ?: "")).append('\n')
        }
        return sb.toString()
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun buildJson(
        title: String,
        srcLang: String,
        tgtLang: String,
        segments: List<SegmentWithTranslation>
    ): String {
        val root = JSONObject()
        root.put("projectTitle", title)
        root.put("sourceLanguage", srcLang)
        root.put("targetLanguage", tgtLang)
        val arr = JSONArray()
        segments.forEachIndexed { idx, seg ->
            val obj = JSONObject()
            obj.put("index", idx + 1)
            obj.put("startMs", seg.startTimeMs)
            obj.put("endMs", seg.endTimeMs)
            obj.put("startFormatted", TimeFormatters.formatShort(seg.startTimeMs))
            obj.put("endFormatted", TimeFormatters.formatShort(seg.endTimeMs))
            obj.put("detectedLang", seg.detectedLangCode)
            obj.put("sourceType", seg.sourceType)
            obj.put("confidence", seg.confidence)
            obj.put("originalText", seg.originalText)
            obj.put("translatedText", seg.translatedText)
            if (!seg.romanText.isNullOrBlank()) obj.put("romanText", seg.romanText)
            arr.put(obj)
        }
        root.put("segments", arr)
        return root.toString(2)
    }

    private fun writePdf(
        out: OutputStream,
        title: String,
        srcLang: String,
        tgtLang: String,
        segments: List<SegmentWithTranslation>
    ) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f
        val colWidth = ((pageWidth - margin * 2 - 96f) / 2f).toInt()

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val metaPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 10f
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }
        val timePaint = TextPaint().apply {
            color = Color.parseColor("#0E7490")
            textSize = 9.5f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }
        val bodyPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 10.5f
            isAntiAlias = true
        }
        val romanPaint = TextPaint().apply {
            color = Color.DKGRAY
            textSize = 9f
            isAntiAlias = true
        }

        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas

        fun drawHeader() {
            canvas.drawText(title, margin, 44f, titlePaint)
            canvas.drawText(
                "${LanguageCatalog.displayName(srcLang)} -> ${LanguageCatalog.displayName(tgtLang)} | Page $pageNumber",
                margin,
                62f,
                metaPaint
            )
            canvas.drawLine(margin, 72f, pageWidth - margin, 72f, linePaint)
        }

        drawHeader()
        var y = 88f

        segments.forEachIndexed { idx, seg ->
            val timeStr = "#${idx + 1}\n${TimeFormatters.formatShort(seg.startTimeMs)}\n${TimeFormatters.formatShort(seg.endTimeMs)}"
            val origLayout = StaticLayout.Builder.obtain(seg.originalText, 0, seg.originalText.length, bodyPaint, colWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
            val transText = seg.translatedText.ifBlank { "-" }
            val transLayout = StaticLayout.Builder.obtain(transText, 0, transText.length, bodyPaint, colWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
            val romanLayout = if (!seg.romanText.isNullOrBlank()) {
                StaticLayout.Builder.obtain(seg.romanText, 0, seg.romanText.length, romanPaint, colWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .build()
            } else null

            val rightH = transLayout.height + (romanLayout?.height?.plus(4) ?: 0)
            val rowH = maxOf(42, origLayout.height, rightH) + 16

            if (y + rowH > pageHeight - 40f) {
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                drawHeader()
                y = 88f
            }

            val timeLayout = StaticLayout.Builder.obtain(timeStr, 0, timeStr.length, timePaint, 84)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()

            canvas.save()
            canvas.translate(margin, y)
            timeLayout.draw(canvas)
            canvas.restore()

            canvas.save()
            canvas.translate(margin + 90f, y)
            origLayout.draw(canvas)
            canvas.restore()

            canvas.save()
            canvas.translate(margin + 90f + colWidth + 12f, y)
            transLayout.draw(canvas)
            if (romanLayout != null) {
                canvas.translate(0f, transLayout.height + 4f)
                romanLayout.draw(canvas)
            }
            canvas.restore()

            y += rowH
            canvas.drawLine(margin, y - 6f, pageWidth - margin, y - 6f, linePaint)
        }

        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }

    private fun writeXlsx(
        out: OutputStream,
        title: String,
        segments: List<SegmentWithTranslation>
    ) {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>""".toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/workbook.xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="${xmlEscape(title.take(28).ifBlank { "Subtitles" })}" sheetId="1" r:id="rId1"/></sheets>
</workbook>""".toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>""".toByteArray()
            )
            zos.closeEntry()

            val sheetXml = StringBuilder()
            sheetXml.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            sheetXml.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            sheetXml.append(xlsxRow(1, listOf("No.", "Start", "End", "Lang", "Original", "Translation", "Roman")))
            segments.forEachIndexed { idx, seg ->
                sheetXml.append(
                    xlsxRow(
                        idx + 2,
                        listOf(
                            (idx + 1).toString(),
                            TimeFormatters.formatShort(seg.startTimeMs),
                            TimeFormatters.formatShort(seg.endTimeMs),
                            seg.detectedLangCode,
                            seg.originalText,
                            seg.translatedText,
                            seg.romanText ?: ""
                        )
                    )
                )
            }
            sheetXml.append("</sheetData></worksheet>")

            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zos.write(sheetXml.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        out.write(baos.toByteArray())
    }

    private fun xlsxRow(rowNum: Int, cells: List<String>): String {
        val cols = listOf("A", "B", "C", "D", "E", "F", "G")
        val sb = StringBuilder("<row r=\"$rowNum\">")
        cells.forEachIndexed { cIdx, value ->
            val ref = "${cols[cIdx]}$rowNum"
            sb.append("<c r=\"$ref\" t=\"inlineStr\"><is><t>${xmlEscape(value)}</t></is></c>")
        }
        sb.append("</row>")
        return sb.toString()
    }

    private fun writeDocx(
        out: OutputStream,
        title: String,
        srcLang: String,
        tgtLang: String,
        segments: List<SegmentWithTranslation>
    ) {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""".toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""".toByteArray()
            )
            zos.closeEntry()

            val docXml = StringBuilder()
            docXml.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            docXml.append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")
            docXml.append(docxParagraph("$title (${LanguageCatalog.displayName(srcLang)} -> ${LanguageCatalog.displayName(tgtLang)})", bold = true))
            docXml.append("""<w:tbl><w:tblPr><w:tblBorders><w:top w:val="single" w:sz="4" w:space="0" w:color="CCCCCC"/><w:bottom w:val="single" w:sz="4" w:space="0" w:color="CCCCCC"/><w:insideH w:val="single" w:sz="4" w:space="0" w:color="DDDDDD"/><w:insideV w:val="single" w:sz="4" w:space="0" w:color="DDDDDD"/></w:tblBorders></w:tblPr>""")
            docXml.append(docxTableRow(listOf("No. & Timing", "Original", "Translation")))
            segments.forEachIndexed { idx, seg ->
                val t = "#${idx + 1} (${TimeFormatters.formatShort(seg.startTimeMs)} - ${TimeFormatters.formatShort(seg.endTimeMs)})"
                val trans = buildString {
                    append(seg.translatedText)
                    if (!seg.romanText.isNullOrBlank()) append(" [Roman: ${seg.romanText}]")
                }
                docXml.append(docxTableRow(listOf(t, seg.originalText, trans)))
            }
            docXml.append("</w:tbl><w:sectPr/></w:body></w:document>")

            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(docXml.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        out.write(baos.toByteArray())
    }

    private fun docxParagraph(text: String, bold: Boolean = false): String {
        val rPr = if (bold) "<w:rPr><w:b/></w:rPr>" else ""
        return "<w:p><w:r>$rPr<w:t xml:space=\"preserve\">${xmlEscape(text)}</w:t></w:r></w:p>"
    }

    private fun docxTableRow(cells: List<String>): String {
        val sb = StringBuilder("<w:tr>")
        cells.forEach { cell ->
            sb.append("<w:tc>${docxParagraph(cell)}</w:tc>")
        }
        sb.append("</w:tr>")
        return sb.toString()
    }

    private fun xmlEscape(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
