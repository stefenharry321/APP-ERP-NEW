package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.data.model.LedgerEntryType
import com.example.data.model.OfficeCustomer
import com.example.data.model.PartyLedgerEntry
import com.example.data.model.SalesRegisterEntry
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object ErpPdfGenerator {

    private const val PAGE_W = 595 // A4 width in PostScript points (72 dpi)
    private const val PAGE_H = 842 // A4 height in PostScript points (72 dpi)

    fun generateTaxInvoicePdf(context: Context, sale: SalesRegisterEntry): File {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val navy = Color.rgb(18, 52, 92)
        val blueBand = Color.rgb(24, 96, 158)
        val lightBg = Color.rgb(242, 246, 250)
        val textDark = Color.rgb(22, 28, 36)
        val textMuted = Color.rgb(92, 102, 114)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.rgb(198, 210, 224)
            strokeWidth = 0.8f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textDark
            textSize = 9.5f
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textDark
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val left = 28f
        val right = PAGE_W - 28f
        var y = 28f

        // Header Frame
        canvas.drawRoundRect(RectF(left, y, right, y + 92f), 8f, 8f, strokePaint)

        // KF Circular Crest
        fillPaint.color = navy
        canvas.drawCircle(left + 26f, y + 28f, 16f, fillPaint)
        boldPaint.color = Color.WHITE
        boldPaint.textSize = 11f
        canvas.drawText("KF", left + 18f, y + 32f, boldPaint)

        // Company Name
        boldPaint.color = navy
        boldPaint.textSize = 14f
        canvas.drawText("KUMAR FOODS", left + 50f, y + 24f, boldPaint)
        boldPaint.textSize = 10f
        canvas.drawText("PROCESSING COMPANY", left + 50f, y + 38f, boldPaint)

        // Tagline pill
        fillPaint.color = navy
        canvas.drawRoundRect(RectF(left + 50f, y + 46f, left + 204f, y + 66f), 4f, 4f, fillPaint)
        boldPaint.color = Color.WHITE
        boldPaint.textSize = 7f
        canvas.drawText("PREMIUM QUALITY | TRUSTED ALWAYS", left + 56f, y + 59f, boldPaint)

        // UPI Payment Box (center)
        textPaint.color = textDark
        textPaint.textSize = 7f
        canvas.drawText("FOR PAYMENT (UPI)", left + 236f, y + 16f, textPaint)
        strokePaint.color = navy
        canvas.drawRect(left + 242f, y + 20f, left + 294f, y + 72f, strokePaint)
        drawDeterministicQrMatrix(canvas, left + 246f, y + 24f, 44f, navy)
        textPaint.textSize = 6.5f
        canvas.drawText("9758435000-6@ybl", left + 236f, y + 82f, textPaint)

        // Invoice Metadata Box (right)
        fillPaint.color = lightBg
        canvas.drawRoundRect(RectF(right - 188f, y + 10f, right - 10f, y + 82f), 6f, 6f, fillPaint)
        boldPaint.color = navy
        boldPaint.textSize = 9f
        canvas.drawText("INV / ${sale.invoiceNo}", right - 178f, y + 24f, boldPaint)
        textPaint.color = textMuted
        textPaint.textSize = 7.5f
        val metaRows = listOf(
            "Invoice No." to sale.invoiceNo,
            "Sale No." to sale.saleNo,
            "Date" to sale.date,
            "Vehicle No." to sale.vehicleNo.ifBlank { "Local Transport" },
            "Place of Supply" to sale.partyState
        )
        var my = y + 36f
        metaRows.forEach { (k, v) ->
            textPaint.color = textMuted
            canvas.drawText(k, right - 178f, my, textPaint)
            boldPaint.color = textDark
            boldPaint.textSize = 7.5f
            canvas.drawText(v.take(22), right - 110f, my, boldPaint)
            my += 9.5f
        }

        y += 108f

        // TAX INVOICE Title
        boldPaint.color = navy
        boldPaint.textSize = 11.5f
        canvas.drawText("TAX INVOICE", PAGE_W / 2f - 36f, y, boldPaint)
        y += 12f
        boldPaint.color = textDark
        boldPaint.textSize = 7.8f
        canvas.drawText("GSTIN: 09DYOPS4169F2ZO   State: Uttar Pradesh (09)", left, y, boldPaint)
        boldPaint.color = blueBand
        canvas.drawText("ORIGINAL FOR RECIPIENT", right - 112f, y, boldPaint)
        y += 10f

        // BILL TO & SHIP TO boxes
        val boxW = (right - left - 10f) / 2f
        listOf("BILL TO" to left, "SHIP TO" to left + boxW + 10f).forEach { (header, bx) ->
            fillPaint.color = navy
            canvas.drawRoundRect(RectF(bx, y, bx + boxW, y + 16f), 4f, 4f, fillPaint)
            boldPaint.color = Color.WHITE
            boldPaint.textSize = 8f
            canvas.drawText(header, bx + 8f, y + 11f, boldPaint)

            strokePaint.color = Color.rgb(198, 210, 224)
            canvas.drawRoundRect(RectF(bx, y + 16f, bx + boxW, y + 72f), 4f, 4f, strokePaint)
            boldPaint.color = textDark
            boldPaint.textSize = 9f
            canvas.drawText(sale.partyName.take(36), bx + 8f, y + 30f, boldPaint)
            textPaint.color = textMuted
            textPaint.textSize = 7.5f
            canvas.drawText(sale.partyAddress.take(44), bx + 8f, y + 42f, textPaint)
            canvas.drawText("GSTIN: ${sale.partyGstin.ifBlank { "Unregistered" }}", bx + 8f, y + 53f, textPaint)
            canvas.drawText("Mobile: ${sale.partyMobile.ifBlank { "-" }}  |  ${sale.partyState}", bx + 8f, y + 64f, textPaint)
        }
        y += 84f

        // ORDER / ITEM DETAILS Banner
        fillPaint.color = navy
        canvas.drawRoundRect(RectF(left, y, right, y + 18f), 4f, 4f, fillPaint)
        boldPaint.color = Color.WHITE
        boldPaint.textSize = 8.5f
        canvas.drawText("ORDER / ITEM DETAILS", left + 8f, y + 12f, boldPaint)
        y += 18f

        // Table Header
        fillPaint.color = Color.rgb(34, 68, 108)
        canvas.drawRect(left, y, right, y + 22f, fillPaint)
        val cols = listOf(
            "#" to left + 6f,
            "PRODUCT" to left + 24f,
            "HSN" to left + 130f,
            "PACK(KG)" to left + 168f,
            "BAGS" to left + 220f,
            "WEIGHT(KG)" to left + 258f,
            "RATE/KG" to left + 324f,
            "RATE/PACK" to left + 382f,
            "TAXABLE(Rs)" to left + 454f
        )
        boldPaint.color = Color.WHITE
        boldPaint.textSize = 7f
        cols.forEach { (label, xPos) -> canvas.drawText(label, xPos, y + 14f, boldPaint) }
        y += 22f

        // Table Rows
        val itemsToPrint = sale.items.take(14)
        itemsToPrint.forEachIndexed { idx, line ->
            if (idx % 2 == 1) {
                fillPaint.color = lightBg
                canvas.drawRect(left, y, right, y + 18f, fillPaint)
            }
            strokePaint.color = Color.rgb(220, 228, 236)
            canvas.drawRect(left, y, right, y + 18f, strokePaint)
            textPaint.color = textDark
            textPaint.textSize = 7.8f
            canvas.drawText("${idx + 1}", left + 6f, y + 12f, textPaint)
            canvas.drawText(line.productName.take(20), left + 24f, y + 12f, textPaint)
            canvas.drawText(line.hsnCode, left + 130f, y + 12f, textPaint)
            canvas.drawText(String.format(Locale.US, "%.3f", line.packKg), left + 168f, y + 12f, textPaint)
            canvas.drawText("${line.bags}", left + 224f, y + 12f, textPaint)
            canvas.drawText(String.format(Locale.US, "%.3f", line.totalWeightKg), left + 258f, y + 12f, textPaint)
            canvas.drawText(String.format(Locale.US, "%.2f", line.ratePerKgInclGst), left + 324f, y + 12f, textPaint)
            canvas.drawText(String.format(Locale.US, "%.2f", line.ratePerPackExclGst), left + 382f, y + 12f, textPaint)
            canvas.drawText(String.format(Locale.US, "%.2f", line.taxableAmountInr), left + 454f, y + 12f, textPaint)
            y += 18f
        }

        // Total Row
        fillPaint.color = Color.rgb(232, 238, 246)
        canvas.drawRect(left, y, right, y + 20f, fillPaint)
        boldPaint.color = navy
        boldPaint.textSize = 8f
        canvas.drawText("TOTAL", left + 24f, y + 13f, boldPaint)
        canvas.drawText("${sale.totalBags}", left + 224f, y + 13f, boldPaint)
        canvas.drawText(String.format(Locale.US, "%.3f Kg", sale.totalWeightKg), left + 258f, y + 13f, boldPaint)
        canvas.drawText(String.format(Locale.US, "%.2f", sale.taxableValueInr), left + 454f, y + 13f, boldPaint)
        y += 32f

        // Tax Details (Left) & Grand Total Card (Right)
        val taxLeft = left
        val taxRight = left + 306f
        fillPaint.color = navy
        canvas.drawRoundRect(RectF(taxLeft, y, taxRight, y + 16f), 4f, 4f, fillPaint)
        boldPaint.color = Color.WHITE
        boldPaint.textSize = 7.8f
        canvas.drawText("TAX DETAILS", taxLeft + 8f, y + 11f, boldPaint)

        val taxRows = listOf(
            arrayOf("CGST", String.format(Locale.US, "%.2f%%", if (sale.isInterState) 0.0 else sale.gstRatePct / 2.0), String.format(Locale.US, "%.2f", sale.taxableValueInr), String.format(Locale.US, "%.2f", sale.cgstInr)),
            arrayOf("SGST", String.format(Locale.US, "%.2f%%", if (sale.isInterState) 0.0 else sale.gstRatePct / 2.0), String.format(Locale.US, "%.2f", sale.taxableValueInr), String.format(Locale.US, "%.2f", sale.sgstInr)),
            arrayOf("IGST", String.format(Locale.US, "%.2f%%", if (sale.isInterState) sale.gstRatePct else 0.0), "-", String.format(Locale.US, "%.2f", sale.igstInr)),
            arrayOf("TOTAL GST", String.format(Locale.US, "%.2f%%", sale.gstRatePct), String.format(Locale.US, "%.2f", sale.taxableValueInr), String.format(Locale.US, "%.2f", sale.totalGstInr))
        )
        var ty = y + 28f
        taxRows.forEachIndexed { i, row ->
            val p = if (i == 3) boldPaint else textPaint
            p.color = textDark
            p.textSize = 7.8f
            canvas.drawText(row[0], taxLeft + 8f, ty, p)
            canvas.drawText(row[1], taxLeft + 80f, ty, p)
            canvas.drawText(row[2], taxLeft + 146f, ty, p)
            canvas.drawText("Rs. ${row[3]}", taxLeft + 228f, ty, p)
            ty += 14f
        }
        strokePaint.color = Color.rgb(198, 210, 224)
        canvas.drawRect(taxLeft, y + 16f, taxRight, ty - 2f, strokePaint)

        // Right Totals Box
        val sumLeft = taxRight + 14f
        textPaint.color = textMuted
        textPaint.textSize = 8f
        canvas.drawText("Taxable Value", sumLeft + 6f, y + 14f, textPaint)
        canvas.drawText("Total GST", sumLeft + 6f, y + 28f, textPaint)
        boldPaint.color = textDark
        boldPaint.textSize = 8f
        canvas.drawText("Rs. ${String.format(Locale.US, "%.2f", sale.taxableValueInr)}", right - 88f, y + 14f, boldPaint)
        canvas.drawText("Rs. ${String.format(Locale.US, "%.2f", sale.totalGstInr)}", right - 88f, y + 28f, boldPaint)

        fillPaint.color = navy
        canvas.drawRoundRect(RectF(sumLeft, y + 36f, right, y + 60f), 4f, 4f, fillPaint)
        boldPaint.color = Color.WHITE
        boldPaint.textSize = 9.5f
        canvas.drawText("INVOICE TOTAL", sumLeft + 8f, y + 51f, boldPaint)
        canvas.drawText("Rs. ${String.format(Locale.US, "%.2f", sale.totalAmountInr)}", right - 94f, y + 51f, boldPaint)

        fillPaint.color = lightBg
        canvas.drawRect(sumLeft, y + 64f, right, y + 82f, fillPaint)
        boldPaint.color = navy
        boldPaint.textSize = 8.2f
        canvas.drawText("BALANCE DUE", sumLeft + 8f, y + 76f, boldPaint)
        canvas.drawText("Rs. ${String.format(Locale.US, "%.2f", sale.balanceDueInr)}", right - 94f, y + 76f, boldPaint)

        y += 96f

        // Amount in Words & Terms
        fillPaint.color = navy
        canvas.drawRoundRect(RectF(left, y, left + 256f, y + 16f), 4f, 4f, fillPaint)
        canvas.drawRoundRect(RectF(left + 266f, y, right, y + 16f), 4f, 4f, fillPaint)
        boldPaint.color = Color.WHITE
        boldPaint.textSize = 7.8f
        canvas.drawText("AMOUNT IN WORDS", left + 8f, y + 11f, boldPaint)
        canvas.drawText("TERMS & CONDITIONS", left + 274f, y + 11f, boldPaint)

        strokePaint.color = Color.rgb(198, 210, 224)
        canvas.drawRect(left, y + 16f, left + 256f, y + 62f, strokePaint)
        canvas.drawRect(left + 266f, y + 16f, right, y + 62f, strokePaint)

        boldPaint.color = textDark
        boldPaint.textSize = 8f
        val words = "${numberToIndianWords(sale.totalAmountInr.roundToLong())} Rupees Only"
        val wordsLines = wrapText(words, 40)
        var wy = y + 30f
        wordsLines.take(3).forEach { wLine ->
            canvas.drawText(wLine, left + 8f, wy, boldPaint)
            wy += 11f
        }

        textPaint.color = textMuted
        textPaint.textSize = 7f
        canvas.drawText("1. Goods once sold will not be taken back.", left + 274f, y + 28f, textPaint)
        canvas.drawText("2. Interest @ 18% p.a. charged on delayed payments.", left + 274f, y + 38f, textPaint)
        canvas.drawText("3. Subject to Kasganj jurisdiction only.", left + 274f, y + 48f, textPaint)
        canvas.drawText("4. Computer-generated document.", left + 274f, y + 58f, textPaint)

        y += 80f

        // Authorised Signatory Seal
        boldPaint.color = textDark
        boldPaint.textSize = 8f
        canvas.drawText("For KUMAR FOODS PROCESSING COMPANY", right - 178f, y, boldPaint)
        strokePaint.color = Color.rgb(24, 64, 152)
        strokePaint.strokeWidth = 1.4f
        canvas.drawCircle(right - 88f, y + 32f, 24f, strokePaint)
        strokePaint.strokeWidth = 0.8f
        canvas.drawCircle(right - 88f, y + 32f, 19f, strokePaint)
        boldPaint.color = Color.rgb(24, 64, 152)
        boldPaint.textSize = 7.5f
        canvas.drawText("KUMAR", right - 102f, y + 30f, boldPaint)
        canvas.drawText("FOODS", right - 101f, y + 39f, boldPaint)
        textPaint.color = textMuted
        textPaint.textSize = 7.5f
        canvas.drawText("Authorised Signatory", right - 122f, y + 66f, textPaint)

        // Footer Bar
        fillPaint.color = navy
        canvas.drawRect(left, PAGE_H - 46f, right, PAGE_H - 26f, fillPaint)
        boldPaint.color = Color.WHITE
        boldPaint.textSize = 7.3f
        canvas.drawText("BADAMPUR ROAD MOHANPURA DISTRICT KASGANJ", left + 8f, PAGE_H - 33f, boldPaint)
        canvas.drawText("Mob. 9758435000   vedposh.atta@gmail.com", right - 182f, PAGE_H - 33f, boldPaint)

        doc.finishPage(page)

        val outDir = File(context.cacheDir, "erp_pdfs").apply { mkdirs() }
        val safeName = sale.invoiceNo.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val file = File(outDir, "KumarFoods_Invoice_${safeName}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun generatePartyLedgerPdf(
        context: Context,
        customer: OfficeCustomer,
        entries: List<PartyLedgerEntry>,
        fromDate: String,
        toDate: String
    ): File {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val navy = Color.rgb(18, 52, 92)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(24, 30, 38); textSize = 9f }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val left = 28f
        val right = PAGE_W - 28f
        var y = 36f

        fillPaint.color = navy
        canvas.drawRoundRect(RectF(left, y, right, y + 54f), 6f, 6f, fillPaint)
        boldPaint.color = Color.WHITE
        boldPaint.textSize = 13f
        canvas.drawText("KUMAR FOODS PROCESSING COMPANY", left + 14f, y + 24f, boldPaint)
        textPaint.color = Color.rgb(210, 224, 240)
        textPaint.textSize = 8.5f
        canvas.drawText("Party Ledger Statement  |  Period: $fromDate to $toDate", left + 14f, y + 42f, textPaint)
        y += 72f

        boldPaint.color = navy
        boldPaint.textSize = 12f
        canvas.drawText(customer.name, left, y, boldPaint)
        textPaint.color = Color.rgb(80, 90, 102)
        textPaint.textSize = 9f
        canvas.drawText("Mobile: ${customer.mobile.ifBlank { "-" }}  |  City: ${customer.city}", left, y + 15f, textPaint)
        canvas.drawText("Opening Balance: Rs. ${String.format(Locale.US, "%.2f", customer.openingBalanceDr)} Dr", left, y + 30f, textPaint)
        y += 48f

        // Table Header
        fillPaint.color = Color.rgb(232, 240, 248)
        canvas.drawRect(left, y, right, y + 22f, fillPaint)
        boldPaint.color = navy
        boldPaint.textSize = 8.5f
        canvas.drawText("DATE", left + 8f, y + 14f, boldPaint)
        canvas.drawText("VOUCHER / REF", left + 86f, y + 14f, boldPaint)
        canvas.drawText("PARTICULARS", left + 190f, y + 14f, boldPaint)
        canvas.drawText("DEBIT (Rs)", left + 372f, y + 14f, boldPaint)
        canvas.drawText("CREDIT (Rs)", left + 458f, y + 14f, boldPaint)
        y += 28f

        var totalDr = 0.0
        var totalCr = 0.0
        entries.take(28).forEach { e ->
            val isDebit = e.entryTypeEnum == LedgerEntryType.DEBIT_SALE
            if (isDebit) totalDr += e.amountInr else totalCr += e.amountInr
            textPaint.color = Color.rgb(24, 30, 38)
            textPaint.textSize = 8.2f
            canvas.drawText(e.entryDate.take(12), left + 8f, y, textPaint)
            canvas.drawText(e.referenceNo.take(16), left + 86f, y, textPaint)
            canvas.drawText(e.notes.ifBlank { e.entryTypeEnum.label }.take(30), left + 190f, y, textPaint)
            canvas.drawText(if (isDebit) String.format(Locale.US, "%.2f", e.amountInr) else "-", left + 372f, y, textPaint)
            canvas.drawText(if (!isDebit) String.format(Locale.US, "%.2f", e.amountInr) else "-", left + 458f, y, textPaint)
            y += 18f
        }

        y += 14f
        fillPaint.color = navy
        canvas.drawRoundRect(RectF(left, y, right, y + 48f), 6f, 6f, fillPaint)
        boldPaint.color = Color.WHITE
        boldPaint.textSize = 9.5f
        val closing = customer.openingBalanceDr + totalDr - totalCr
        val suffix = if (closing >= 0.0) "Dr" else "Cr"
        canvas.drawText("Total Debit: Rs. ${String.format(Locale.US, "%.2f", totalDr)}", left + 14f, y + 20f, boldPaint)
        canvas.drawText("Total Credit: Rs. ${String.format(Locale.US, "%.2f", totalCr)}", left + 210f, y + 20f, boldPaint)
        canvas.drawText("Closing Balance: Rs. ${String.format(Locale.US, "%.2f", abs(closing))} $suffix", left + 14f, y + 38f, boldPaint)

        doc.finishPage(page)
        val outDir = File(context.cacheDir, "erp_pdfs").apply { mkdirs() }
        val safeName = customer.name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(30)
        val file = File(outDir, "KumarFoods_Ledger_${safeName}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun generateOfficeLedgerPdf(
        context: Context,
        customer: OfficeCustomer,
        vouchers: List<com.example.data.model.OfficeLedgerVoucher>,
        periodOpeningDr: Double,
        fromDate: String,
        toDate: String
    ): File {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val navy = Color.rgb(18, 52, 92)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(24, 30, 38); textSize = 8.5f }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val left = 28f
        val right = PAGE_W - 28f
        var y = 34f

        fillPaint.color = navy
        canvas.drawRoundRect(RectF(left, y, right, y + 54f), 6f, 6f, fillPaint)
        boldPaint.color = Color.WHITE
        boldPaint.textSize = 13f
        canvas.drawText("KUMAR FOODS PROCESSING COMPANY", left + 14f, y + 24f, boldPaint)
        textPaint.color = Color.rgb(210, 224, 240)
        textPaint.textSize = 8.5f
        canvas.drawText("Verified Office Ledger Statement  |  Period: $fromDate to $toDate", left + 14f, y + 42f, textPaint)
        y += 70f

        boldPaint.color = navy
        boldPaint.textSize = 12f
        canvas.drawText(customer.name, left, y, boldPaint)
        textPaint.color = Color.rgb(80, 90, 102)
        textPaint.textSize = 8.8f
        val opSuffix = if (periodOpeningDr >= 0.0) "Dr" else "Cr"
        canvas.drawText("Mobile: ${customer.mobile.ifBlank { "-" }}  |  City: ${customer.city}", left, y + 14f, textPaint)
        canvas.drawText(
            "Period Opening Balance: Rs. ${String.format(Locale.US, "%.2f", abs(periodOpeningDr))} $opSuffix",
            left,
            y + 28f,
            textPaint
        )
        y += 44f

        fillPaint.color = Color.rgb(232, 240, 248)
        canvas.drawRect(left, y, right, y + 22f, fillPaint)
        boldPaint.color = navy
        boldPaint.textSize = 8f
        canvas.drawText("DATE", left + 6f, y + 14f, boldPaint)
        canvas.drawText("TYPE", left + 74f, y + 14f, boldPaint)
        canvas.drawText("PARTICULARS", left + 148f, y + 14f, boldPaint)
        canvas.drawText("DEBIT", left + 334f, y + 14f, boldPaint)
        canvas.drawText("CREDIT", left + 404f, y + 14f, boldPaint)
        canvas.drawText("BALANCE", left + 470f, y + 14f, boldPaint)
        y += 28f

        var running = periodOpeningDr
        var totalDr = 0.0
        var totalCr = 0.0
        vouchers.take(30).forEach { v ->
            totalDr += v.debitInr
            totalCr += v.creditInr
            running += (v.debitInr - v.creditInr)
            val rSuffix = if (running >= 0.0) "Dr" else "Cr"
            textPaint.color = Color.rgb(24, 30, 38)
            textPaint.textSize = 7.8f
            canvas.drawText(v.date.take(10), left + 6f, y, textPaint)
            canvas.drawText(v.referenceType.take(12), left + 74f, y, textPaint)
            canvas.drawText(v.narration.take(32), left + 148f, y, textPaint)
            canvas.drawText(if (v.debitInr > 0.0) String.format(Locale.US, "%.2f", v.debitInr) else "-", left + 334f, y, textPaint)
            canvas.drawText(if (v.creditInr > 0.0) String.format(Locale.US, "%.2f", v.creditInr) else "-", left + 404f, y, textPaint)
            canvas.drawText("${String.format(Locale.US, "%.2f", abs(running))} $rSuffix", left + 470f, y, textPaint)
            y += 17f
        }

        y += 14f
        fillPaint.color = navy
        canvas.drawRoundRect(RectF(left, y, right, y + 48f), 6f, 6f, fillPaint)
        boldPaint.color = Color.WHITE
        boldPaint.textSize = 9.2f
        val closingSuffix = if (running >= 0.0) "Dr" else "Cr"
        canvas.drawText("Total Debit: Rs. ${String.format(Locale.US, "%.2f", totalDr)}", left + 14f, y + 20f, boldPaint)
        canvas.drawText("Total Credit: Rs. ${String.format(Locale.US, "%.2f", totalCr)}", left + 210f, y + 20f, boldPaint)
        canvas.drawText("Closing Balance: Rs. ${String.format(Locale.US, "%.2f", abs(running))} $closingSuffix", left + 14f, y + 38f, boldPaint)

        doc.finishPage(page)
        val outDir = File(context.cacheDir, "erp_pdfs").apply { mkdirs() }
        val safeName = customer.name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(30)
        val file = File(outDir, "KumarFoods_Office_Ledger_${safeName}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun sharePdfFile(context: Context, file: File, subject: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share / WhatsApp PDF"))
    }

    fun printPdfFile(context: Context, file: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(file.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    FileInputStream(file).use { input ->
                        FileOutputStream(destination?.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.localizedMessage)
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }

    fun numberToIndianWords(number: Long): String {
        if (number == 0L) return "Zero"
        val ones = arrayOf(
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
            "Seventeen", "Eighteen", "Nineteen"
        )
        val tens = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

        fun twoDigits(n: Int): String = when {
            n < 20 -> ones[n]
            else -> listOf(tens[n / 10], ones[n % 10]).filter { it.isNotEmpty() }.joinToString(" ")
        }

        var rem = abs(number)
        val parts = mutableListOf<String>()
        val crores = (rem / 10_000_000L).toInt()
        if (crores > 0) {
            parts.add("${twoDigits(crores)} Crore")
            rem %= 10_000_000L
        }
        val lakhs = (rem / 100_000L).toInt()
        if (lakhs > 0) {
            parts.add("${twoDigits(lakhs)} Lakh")
            rem %= 100_000L
        }
        val thousands = (rem / 1_000L).toInt()
        if (thousands > 0) {
            parts.add("${twoDigits(thousands)} Thousand")
            rem %= 1_000L
        }
        val hundreds = (rem / 100L).toInt()
        if (hundreds > 0) {
            parts.add("${ones[hundreds]} Hundred")
            rem %= 100L
        }
        if (rem > 0) {
            parts.add(twoDigits(rem.toInt()))
        }
        return parts.joinToString(" ")
    }

    private fun drawDeterministicQrMatrix(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val grid = 11
        val cell = size / grid
        val pattern = arrayOf(
            "11110101111",
            "10010011001",
            "10110101011",
            "11110111111",
            "00001010000",
            "10110110101",
            "01001011010",
            "11110100110",
            "10010110101",
            "10110010011",
            "11110101101"
        )
        for (r in 0 until grid) {
            for (c in 0 until grid) {
                if (pattern[r][c] == '1') {
                    canvas.drawRect(
                        x + c * cell,
                        y + r * cell,
                        x + (c + 1) * cell,
                        y + (r + 1) * cell,
                        p
                    )
                }
            }
        }
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (w in words) {
            if (current.length + w.length + 1 > maxChars && current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder(w)
            } else {
                if (current.isNotEmpty()) current.append(" ")
                current.append(w)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }
}
